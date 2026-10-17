package com.divinerpgextended.perk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.vanilla.projectile.EntityShooterBullet;
import net.divinerpg.utils.items.TwilightItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.world.twilight.TwilightCrafts;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Роли сумеречного дальнобоя (снаряды Divine у блица, фазера и слайсера одни - различаем по оружию в руке при выстреле):
 * <ul>
 * <li>лук - дальность и точность (без изменений);</li>
 * <li>блиц - дробовик: {@link #PELLETS} снарядов веером, каждый слабее ({@link #PELLET_DMG}), по подручным боссов x2 -
 * против толпы;</li>
 * <li>фазер - пробой: снаряд проходит насквозь до {@link #PIERCE} целей, по частям голема x2, издалека подзаряжает
 * ветряной тотем;</li>
 * <li>слайсер - рикошет до {@link #RICOCHET} целей подряд, перерезает лиану голема и сбивает замах Короля-воина.</li>
 * </ul>
 */
public class RangedRoles {

    public static final String KIND = "dreRanged", GEN = "dreRangedGen";
    public static final int BLITZ = 1, PHASER = 2, SLICER = 3;
    public static final int PELLETS = 5, PIERCE = 3, RICOCHET = 3;
    public static final float PELLET_DMG = 0.5F, ADD_BONUS = 2.0F;
    private static final double SPREAD = 9; // градусов между дробинами

    /** Летящие снаряды фазера - для подзарядки тотемов. */
    private static final List<Entity> PHASER_SHOTS = new ArrayList<Entity>();

    public static int kind(ItemStack held) {
        if (held == null) return 0;
        Item i = held.getItem();
        for (TwilightWorld w : TwilightWorld.values()) {
            Item[] wp = TwilightCrafts.weapons(w);
            if (i == wp[3]) return BLITZ;
            if (i == wp[2]) return PHASER;
        }
        if (i == TwilightItemsWeapons.edenSlicer || i == TwilightItemsWeapons.wildwoodSlicer || i == TwilightItemsWeapons.apalachiaSlicer
            || i == TwilightItemsWeapons.skythernSlicer || i == TwilightItemsWeapons.mortumSlicer) return SLICER;
        return 0;
    }

    public static int kind(Entity bullet) {
        return bullet == null ? 0 : bullet.getEntityData().getInteger(KIND);
    }

    /** Подручный босса (по меткам призыва). */
    public static boolean bossAdd(Entity e) {
        NBTTagCompound d = e.getEntityData();
        return d.hasKey(com.divinerpgextended.entity.EntityGroveGuardian.ADD_TAG) || d.hasKey("dreCadillionAdd") || d.hasKey("dreDemonAdd");
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityShooterBullet)) return;
        Entity b = e.entity;
        NBTTagCompound d = b.getEntityData();
        if (d.hasKey(KIND)) {
            if (d.getInteger(KIND) == PHASER) PHASER_SHOTS.add(b);
            return;
        }
        EntityLivingBase thrower = ((EntityShooterBullet) b).getThrower();
        if (!(thrower instanceof EntityPlayer)) return;
        int k = kind(((EntityPlayer) thrower).getHeldItem());
        if (k == 0) return;
        d.setInteger(KIND, k);
        if (k == PHASER) PHASER_SHOTS.add(b);
        if (k == BLITZ) {
            // дробь: ещё PELLETS - 1 снарядов веером по горизонтали
            for (int i = 1; i < PELLETS; i++) {
                double ang = Math.toRadians(SPREAD * ((i + 1) / 2) * (i % 2 == 0 ? 1 : -1));
                double c = Math.cos(ang), s = Math.sin(ang);
                spawnClone(b, b.posX, b.posY, b.posZ, b.motionX * c - b.motionZ * s, b.motionY + (e.world.rand.nextDouble() - 0.5) * 0.08,
                    b.motionX * s + b.motionZ * c, 1);
            }
        }
    }

    /** Копия снаряда Divine (через NBT - класс, текстура, урон, стрелок) с новым курсом и поколением. */
    public static Entity spawnClone(Entity src, double x, double y, double z, double mx, double my, double mz, int gen) {
        NBTTagCompound tag = new NBTTagCompound();
        if (!src.writeToNBTOptional(tag)) return null;
        Entity c = EntityList.createEntityFromNBT(tag, src.worldObj);
        if (c == null) return null;
        c.setPosition(x, y, z);
        c.motionX = mx;
        c.motionY = my;
        c.motionZ = mz;
        c.getEntityData().setInteger(KIND, kind(src));
        c.getEntityData().setInteger(GEN, gen);
        src.worldObj.spawnEntityInWorld(c);
        return c;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity b = e.source.getSourceOfDamage();
        int k = kind(b);
        if (k == 0) return;
        EntityLivingBase victim = e.entityLiving;
        int gen = b.getEntityData().getInteger(GEN);
        switch (k) {
            case BLITZ:
                e.ammount *= PELLET_DMG * (bossAdd(victim) ? ADD_BONUS : 1);
                break;
            case PHASER:
                // пробой: снаряд летит дальше за спину цели
                if (gen + 1 < PIERCE) {
                    double v = Math.sqrt(b.motionX * b.motionX + b.motionY * b.motionY + b.motionZ * b.motionZ);
                    if (v > 0.01) {
                        double k2 = (victim.width / 2 + 1.0) / v;
                        spawnClone(b, victim.posX + b.motionX * k2, b.posY + b.motionY * k2, victim.posZ + b.motionZ * k2, b.motionX, b.motionY, b.motionZ, gen + 1);
                    }
                }
                break;
            default: // слайсер: рикошет в ближайшего другого врага
                if (gen + 1 < RICOCHET) {
                    EntityLivingBase next = nearestFoe(victim, 8);
                    if (next != null) {
                        double dx = next.posX - victim.posX, dy = next.posY + next.height / 2 - (victim.posY + victim.height / 2), dz = next.posZ - victim.posZ;
                        double d = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
                        double sp = Math.max(1.0, Math.sqrt(b.motionX * b.motionX + b.motionY * b.motionY + b.motionZ * b.motionZ));
                        double ox = dx / d * (victim.width / 2 + 0.6), oz = dz / d * (victim.width / 2 + 0.6);
                        spawnClone(b, victim.posX + ox, victim.posY + victim.height / 2, victim.posZ + oz, dx / d * sp, dy / d * sp + 0.05, dz / d * sp, gen + 1);
                    }
                }
        }
    }

    private static EntityLivingBase nearestFoe(EntityLivingBase from, double r) {
        EntityLivingBase best = null;
        double bd = r * r;
        for (Object o : from.worldObj.getEntitiesWithinAABB(EntityLiving.class, from.boundingBox.expand(r, r / 2, r))) {
            EntityLivingBase m = (EntityLivingBase) o;
            if (m == from || !m.isEntityAlive() || !(m instanceof IMob || m instanceof net.minecraft.entity.boss.IBossDisplayData)) continue;
            double d = m.getDistanceSqToEntity(from);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** Снаряды фазера рядом с ветряным тотемом подзаряжают его. */
    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || PHASER_SHOTS.isEmpty()) return;
        for (Iterator<Entity> it = PHASER_SHOTS.iterator(); it.hasNext();) {
            Entity b = it.next();
            if (b.isDead) {
                it.remove();
                continue;
            }
            if (b.worldObj != e.world) continue;
            World w = b.worldObj;
            int bx = (int) Math.floor(b.posX), by = (int) Math.floor(b.posY), bz = (int) Math.floor(b.posZ);
            for (int x = bx - 1; x <= bx + 1; x++) for (int y = by - 1; y <= by + 1; y++) for (int z = bz - 1; z <= bz + 1; z++) {
                if (w.getBlock(x, y, z) != com.divinerpgextended.registry.ModBlocks.windTotem || w.getBlockMetadata(x, y, z) != 0) continue;
                for (Object o : w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityWindLord.class,
                    net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 48, y - 32, z - 48, x + 48, y + 32, z + 48))) {
                    ((com.divinerpgextended.entity.EntityWindLord) o).remoteCharge(x, y, z);
                    break;
                }
                b.setDead();
                it.remove();
                return;
            }
        }
    }
}
