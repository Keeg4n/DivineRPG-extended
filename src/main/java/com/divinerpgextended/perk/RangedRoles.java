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
 * <li>фазер - пробой: снаряд проходит насквозь до {@link #PIERCE} целей, по частям голема x2; тратит пыль своего мира
 * (у Divine он бесконечный - слишком сильный);</li>
 * <li>слайсер - рикошет до {@link #RICOCHET} целей подряд, перерезает лиану голема, сбивает замах Короля-воина и
 * издалека заряжает ветряные тотемы (как снежки в кристаллы дракона).</li>
 * </ul>
 * Колчаны-аксессуары (как в Террарии) дают бесконечные боеприпасы своему виду: бездонный колчан - лукам, подсумок пыли -
 * блицам и фазерам, перевязь метателя - слайсерам. Пока колчан надет и оружие в руке, в инвентаре держится один
 * помеченный боеприпас ({@link #QUIVER_TAG}); выстрел его тратит - появляется снова. Снял колчан - метка пропадает.
 * Чтобы колчан носили не только ради удобства: колчан +10% урона стрелами, подсумок +15% урона блица и фазера,
 * перевязь +1 цель рикошета.
 */
public class RangedRoles {

    public static final String KIND = "dreRanged", GEN = "dreRangedGen";
    public static final int BLITZ = 1, PHASER = 2, SLICER = 3;
    public static final int PELLETS = 5, PIERCE = 3, RICOCHET = 3;
    public static final float PELLET_DMG = 0.5F, ADD_BONUS = 2.0F;
    /** Боевые бонусы колчанов: стрелы +10%, блиц и фазер +15%, слайсер +1 цель рикошета. */
    public static final float QUIVER_DMG = 1.10F, POUCH_DMG = 1.15F;
    private static final double SPREAD = 9; // градусов между дробинами

    /** Летящие слайсеры - для подзарядки тотемов. */
    private static final List<Entity> TOTEM_SHOTS = new ArrayList<Entity>();
    public static final String QUIVER_TAG = "dreQuiver";

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

    public static boolean wears(EntityPlayer p, com.divinerpgextended.trinket.Trinket t) {
        return com.divinerpgextended.accessory.Accessories.wears(p, t);
    }

    /** Пыль мира, которую тратит этот фазер. */
    public static Item phaserDust(Item phaser) {
        for (TwilightWorld w : TwilightWorld.values()) if (TwilightCrafts.weapons(w)[2] == phaser) return w.dust();
        return null;
    }

    /** Дробь блица бьёт по одной цели вся: неуязвимость после попадания для неё не действует. */
    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!e.entityLiving.worldObj.isRemote && kind(e.source.getSourceOfDamage()) == BLITZ) e.entityLiving.hurtResistantTime = 0;
    }

    /** Боеприпас, нужный оружию в руке, и колчан для него (null - не наш случай). */
    private static Object[] ammoFor(ItemStack held) {
        if (held == null) return null;
        Item i = held.getItem();
        if (i instanceof net.divinerpg.items.base.ItemModBow) {
            Item a = field(net.divinerpg.items.base.ItemModBow.class, i, "arrow");
            return a == null ? null : new Object[] { a, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER };
        }
        // ванильный лук - после луков Divine (они от него наследуются)
        if (i instanceof net.minecraft.item.ItemBow) return new Object[] { net.minecraft.init.Items.arrow, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER };
        if (kind(held) == BLITZ) {
            Item a = field(net.divinerpg.items.base.ItemProjectileShooter.class, i, "ammo");
            return a == null ? null : new Object[] { a, com.divinerpgextended.trinket.Trinket.DUST_POUCH };
        }
        return null;
    }

    private static Item field(Class<?> c, Object o, String name) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(o);
            return v instanceof Item ? (Item) v : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Колчан: держит один помеченный боеприпас, пока надет и оружие его вида в руке; иначе метку убирает. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote || e.player.ticksExisted % 2 != 0) return;
        EntityPlayer p = e.player;
        Object[] need = ammoFor(p.getHeldItem());
        Item keep = null;
        if (need != null && wears(p, (com.divinerpgextended.trinket.Trinket) need[1])) {
            keep = (Item) need[0];
            if (!p.inventory.hasItem(keep)) {
                ItemStack a = new ItemStack(keep);
                a.setTagCompound(new NBTTagCompound());
                a.getTagCompound().setBoolean(QUIVER_TAG, true);
                p.inventory.addItemStackToInventory(a);
            }
        }
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.hasTagCompound() && s.getTagCompound().getBoolean(QUIVER_TAG) && s.getItem() != keep) p.inventory.mainInventory[i] = null;
        }
    }

    /** Стрелы из колчана не подбираются обратно (иначе колчан - ферма стрел). */
    @SubscribeEvent
    public void onArrow(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.minecraft.entity.projectile.EntityArrow)) return;
        net.minecraft.entity.projectile.EntityArrow a = (net.minecraft.entity.projectile.EntityArrow) e.entity;
        if (a.shootingEntity instanceof EntityPlayer && wears((EntityPlayer) a.shootingEntity, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER)
            && a.canBePickedUp == 1) a.canBePickedUp = 2;
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
            if (d.getInteger(KIND) == SLICER) TOTEM_SHOTS.add(b);
            return;
        }
        EntityLivingBase thrower = ((EntityShooterBullet) b).getThrower();
        if (!(thrower instanceof EntityPlayer)) return;
        EntityPlayer pl = (EntityPlayer) thrower;
        ItemStack held = pl.getHeldItem();
        int k = kind(held);
        if (k == 0) return;
        if (k == PHASER && !pl.capabilities.isCreativeMode && !wears(pl, com.divinerpgextended.trinket.Trinket.DUST_POUCH)) {
            // фазер тратит пыль своего мира; нет пыли - выстрела нет
            Item dust = phaserDust(held.getItem());
            if (dust != null && !pl.inventory.consumeInventoryItem(dust)) {
                e.setCanceled(true);
                pl.worldObj.playSoundAtEntity(pl, "random.click", 1.0F, 1.6F);
                return;
            }
        }
        if (k == SLICER && wears(pl, com.divinerpgextended.trinket.Trinket.SLICER_SLING) && !pl.capabilities.isCreativeMode) {
            // перевязь: брошенный слайсер возвращается в руку
            if (held.stackSize < held.getMaxStackSize()) held.stackSize++;
            else pl.inventory.addItemStackToInventory(new ItemStack(held.getItem()));
        }
        d.setInteger(KIND, k);
        if (k == SLICER) TOTEM_SHOTS.add(b);
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
        if (b instanceof net.minecraft.entity.projectile.EntityArrow && kind(b) == 0) {
            Entity sh = ((net.minecraft.entity.projectile.EntityArrow) b).shootingEntity;
            if (sh instanceof EntityPlayer && wears((EntityPlayer) sh, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER)) e.ammount *= QUIVER_DMG;
            return;
        }
        int k = kind(b);
        if (k == 0) return;
        EntityLivingBase owner = b instanceof EntityShooterBullet ? ((EntityShooterBullet) b).getThrower() : null;
        boolean pouch = owner instanceof EntityPlayer && wears((EntityPlayer) owner, com.divinerpgextended.trinket.Trinket.DUST_POUCH);
        boolean sling = owner instanceof EntityPlayer && wears((EntityPlayer) owner, com.divinerpgextended.trinket.Trinket.SLICER_SLING);
        if (pouch && (k == BLITZ || k == PHASER)) e.ammount *= POUCH_DMG;
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
                if (gen + 1 < RICOCHET + (sling ? 1 : 0)) {
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

    /** Слайсеры рядом с ветряным тотемом подзаряжают его. */
    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || TOTEM_SHOTS.isEmpty()) return;
        for (Iterator<Entity> it = TOTEM_SHOTS.iterator(); it.hasNext();) {
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
