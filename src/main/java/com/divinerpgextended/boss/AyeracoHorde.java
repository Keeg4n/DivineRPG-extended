package com.divinerpgextended.boss;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.divinerpg.entities.vanilla.EntityAyeraco;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.world.WorldGenOuterLairs;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Орда Айрако гнездовья (дальний остров Энда). Пятеро кружат над ареной, как коршуны, и по очереди пикируют на игрока:
 * удар, пара секунд низко над ним (окно для ответа), подъём обратно в круг. Телепорт фиолетового - только в пределах
 * острова. Ауры живых по цвету действуют на всю стаю:
 * <ul>
 * <li>зелёный - лечит остальных; синий - отбивает снаряды; красный - ярость (удары x1.5); жёлтый - стая быстрее,
 * пикирование замедляет; фиолетовый - перебрасывает собрата к игроку.</li>
 * <li>Столпы гнезда: пять цветных столпов на скалах вокруг острова; пока цел столп цвета, его Айрако восстанавливается.</li>
 * <li>Сердце орды: последний выживший забирает ауры павших и бьёт сильнее.</li>
 * </ul>
 * Броня Энда того же цвета ({@link EnderColors}) снимает с носителя ауру этого Айрако и усиливает удары по нему.
 */
public class AyeracoHorde {

    public static final String TAG = "dreHorde", CX = "dreHordeX", CY = "dreHordeY", CZ = "dreHordeZ";
    /** Цвета стаи по порядку столпов. */
    public enum Hue {
        GREEN, BLUE, RED, YELLOW, PURPLE;

        public static Hue of(Entity e) {
            String n = e.getClass().getSimpleName();
            for (Hue h : values()) if (n.toUpperCase().endsWith(h.name())) return h;
            return null;
        }

        /** Цвет блока столпа (обожжённая глина). */
        public int clay() {
            switch (this) {
                case GREEN: return 5;
                case BLUE: return 11;
                case RED: return 14;
                case YELLOW: return 4;
                default: return 10;
            }
        }
    }

    public static final double ORBIT_R = 16, ORBIT_H = 12, LEASH = 48;
    public static final int DIVE_TICKS = 60, HOVER_TICKS = 50, CLIMB_TICKS = 40, PAUSE_TICKS = 30, PILLAR_RING = 30, PILLAR_LIFT = 8,
        PILLAR_H = 5;
    static final int DIVE = 1, HOVER = 2, CLIMB = 3;

    /** Состояние стаи (по центру гнездовья). */
    static final class Pack {
        final int x, y, z;
        int diver = -1, phase, turn;
        long phaseUntil, nextDive, nextPurple;
        final Set<Hue> fallen = EnumSet.noneOf(Hue.class);
        final List<EntityLiving> alive = new ArrayList<EntityLiving>();
        long aliveAt = -1;

        Pack(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static final Map<Long, Pack> PACKS = new HashMap<Long, Pack>();

    static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    public static boolean member(Entity e) {
        return e instanceof EntityAyeraco && e.getEntityData().getBoolean(TAG);
    }

    static Pack pack(EntityLiving e) {
        NBTTagCompound d = e.getEntityData();
        int x = d.getInteger(CX), y = d.getInteger(CY), z = d.getInteger(CZ);
        Pack p = PACKS.get(key(x, z));
        if (p == null) PACKS.put(key(x, z), p = new Pack(x, y, z));
        return p;
    }

    /** Живые Айрако стаи (раз в тик). */
    static List<EntityLiving> alive(Pack p, World w) {
        long now = w.getTotalWorldTime();
        if (p.aliveAt == now) return p.alive;
        p.aliveAt = now;
        p.alive.clear();
        for (Object o : w.getEntitiesWithinAABB(EntityAyeraco.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(p.x - 96, p.y - 48, p.z - 96, p.x + 96,
            p.y + 96, p.z + 96))) {
            EntityLiving e = (EntityLiving) o;
            if (member(e) && e.isEntityAlive()) p.alive.add(e);
        }
        return p.alive;
    }

    static boolean heart(Pack p, World w) {
        return alive(p, w).size() == 1 && p.fallen.size() >= 4;
    }

    /** Действует ли аура цвета: жив такой Айрако или он пал, а остался последний (сердце орды). */
    static boolean aura(Pack p, World w, Hue h) {
        for (EntityLiving e : alive(p, w)) if (Hue.of(e) == h) return true;
        return heart(p, w) && p.fallen.contains(h);
    }

    static EntityPlayer target(Pack p, World w) {
        EntityPlayer best = null;
        double bd = 64 * 64;
        for (Object o : w.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if (pl.capabilities.isCreativeMode || pl.isDead) continue;
            double d = (pl.posX - p.x) * (pl.posX - p.x) + (pl.posZ - p.z) * (pl.posZ - p.z);
            if (d < bd) {
                bd = d;
                best = pl;
            }
        }
        return best;
    }

    // ---------------- полёт

    public static boolean steer(EntityLiving e) {
        if (!member(e) || e.worldObj.isRemote) return false;
        World w = e.worldObj;
        Pack p = pack(e);
        List<EntityLiving> all = alive(p, w);
        int idx = Math.max(0, all.indexOf(e)), n = Math.max(1, all.size());
        long now = w.getTotalWorldTime();
        EntityPlayer t = target(p, w);
        double speedK = aura(p, w, Hue.YELLOW) ? 1.4 : 1.0;
        // очередь пикирования
        if (t != null && (p.diver < 0 || p.diver >= all.size() || now > p.phaseUntil && p.phase == CLIMB) && now >= p.nextDive) {
            p.turn = (p.turn + 1) % n;
            p.diver = p.turn;
            p.phase = DIVE;
            p.phaseUntil = now + DIVE_TICKS;
        }
        double ox = p.x + 0.5 + Math.cos(now * 0.03 + idx * Math.PI * 2 / n) * ORBIT_R, oz = p.z + 0.5 + Math.sin(now * 0.03 + idx * Math.PI * 2 / n) * ORBIT_R,
            oy = p.y + ORBIT_H + (idx % 2) * 3;
        double tx = ox, ty = oy, tz = oz, speed = 0.45 * speedK;
        if (t != null && idx == p.diver) {
            if (p.phase == DIVE) {
                tx = t.posX;
                ty = t.posY + 1;
                tz = t.posZ;
                speed = 0.9 * speedK;
                if (e.getDistanceSqToEntity(t) < 2.5 * 2.5) {
                    e.attackEntityAsMob(t);
                    if (aura(p, w, Hue.YELLOW) && !EnderColors.wears(t, Hue.YELLOW)) t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
                    p.phase = HOVER;
                    p.phaseUntil = now + HOVER_TICKS;
                } else if (now > p.phaseUntil) {
                    p.phase = HOVER;
                    p.phaseUntil = now + HOVER_TICKS;
                }
            } else if (p.phase == HOVER) {
                tx = t.posX + Math.cos(now * 0.05) * 3;
                ty = t.posY + 3;
                tz = t.posZ + Math.sin(now * 0.05) * 3;
                speed = 0.18;
                if (now > p.phaseUntil) {
                    p.phase = CLIMB;
                    p.phaseUntil = now + CLIMB_TICKS;
                }
            } else if (p.phase == CLIMB) {
                speed = 0.6 * speedK;
                if (now > p.phaseUntil) {
                    p.diver = -1;
                    p.nextDive = now + (long) (PAUSE_TICKS / speedK);
                }
            }
        }
        // привязь: далеко от гнездовья или ниже его - назад к кругу
        double hx = e.posX - p.x, hz = e.posZ - p.z;
        if (hx * hx + hz * hz > LEASH * LEASH || e.posY < p.y - 8) {
            tx = ox;
            ty = oy;
            tz = oz;
            speed = 1.0;
        }
        double dx = tx - e.posX, dy = ty - e.posY, dz = tz - e.posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double v = Math.min(speed, len * 0.3);
        if (len > 0.01) {
            e.motionX = dx / len * v;
            e.motionY = dy / len * v + 0.08; // тяга против тяжести (её прибавит движение моба)
            e.motionZ = dz / len * v;
            e.rotationYaw = e.renderYawOffset = e.rotationYawHead = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        }
        e.fallDistance = 0;
        return true;
    }

    /** Телепорт Айрако орды: в случайную точку над островом, не в бездну. */
    public static boolean blink(EntityLiving e) {
        if (!member(e) || e.worldObj.isRemote) return false;
        Pack p = pack(e);
        double a = e.getRNG().nextDouble() * Math.PI * 2, r = e.getRNG().nextDouble() * ORBIT_R;
        e.setPosition(p.x + 0.5 + Math.cos(a) * r, p.y + ORBIT_H + e.getRNG().nextInt(4), p.z + 0.5 + Math.sin(a) * r);
        e.worldObj.playSoundAtEntity(e, "mob.endermen.portal", 1F, 1F);
        return true;
    }

    // ---------------- стая: метка, ауры, столпы

    /** Айрако, появившийся в гнездовье (рог орды), становится ордой: метка и центр гнездовья. */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityAyeraco) || e.entity.getEntityData().getBoolean(TAG)) return;
        if (e.world.provider.dimensionId != WorldGenOuterLairs.dim(WorldGenOuterLairs.AYERACO)) return;
        int[] c = WorldGenOuterLairs.nearest(e.world.getSeed(), WorldGenOuterLairs.AYERACO, e.entity.posX, e.entity.posZ);
        if (c == null) return;
        double dx = e.entity.posX - c[0], dz = e.entity.posZ - c[2];
        if (dx * dx + dz * dz > 80 * 80) return;
        NBTTagCompound d = e.entity.getEntityData();
        d.setBoolean(TAG, true);
        d.setInteger(CX, c[0]);
        d.setInteger(CY, c[1]);
        d.setInteger(CZ, c[2]);
        Pack p = PACKS.get(key(c[0], c[2]));
        if (p != null && alive(p, e.world).isEmpty()) PACKS.remove(key(c[0], c[2])); // новая стая - заново
    }

    /** Столп цвета цел: в его колонне есть хоть один блок своей глины. */
    static boolean pillar(World w, Pack p, Hue h) {
        int[] at = pillarAt(p.x, p.y, p.z, h);
        for (int y = at[1]; y < at[1] + PILLAR_H; y++) if (w.getBlock(at[0], y, at[2]) == Blocks.stained_hardened_clay && w.getBlockMetadata(at[0], y, at[2]) == h.clay()) return true;
        return false;
    }

    /** Столп цвета у гнездовья с этим центром цел. */
    public static boolean pillar(World w, int cx, int cy, int cz, Hue h) {
        return pillar(w, new Pack(cx, cy, cz), h);
    }

    /** Основание столпа цвета (над скалой). */
    public static int[] pillarAt(int cx, int cy, int cz, Hue h) {
        double a = h.ordinal() * Math.PI * 2 / 5 + Math.PI / 10;
        return new int[] { cx + (int) Math.round(Math.cos(a) * PILLAR_RING), cy + PILLAR_LIFT + 1, cz + (int) Math.round(Math.sin(a) * PILLAR_RING) };
    }

    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || PACKS.isEmpty() || e.world.getTotalWorldTime() % 20 != 0) return;
        for (Pack p : new ArrayList<Pack>(PACKS.values())) {
            List<EntityLiving> all = alive(p, e.world);
            if (all.isEmpty()) continue;
            EntityPlayer t = target(p, e.world);
            boolean heart = heart(p, e.world);
            for (EntityLiving a : all) {
                Hue h = Hue.of(a);
                // столп своего цвета - восстановление
                if (h != null && pillar(e.world, p, h) && a.getHealth() < a.getMaxHealth()) {
                    a.heal(a.getMaxHealth() * 0.01F);
                    beam(e.world, pillarAt(p.x, p.y, p.z, h), a);
                }
                // зелёный лечит остальных (сердце - себя)
                if (aura(p, e.world, Hue.GREEN) && (h != Hue.GREEN || heart) && a.getHealth() < a.getMaxHealth()
                    && a.getEntityData().getLong("dreNoHeal") < e.world.getTotalWorldTime()) a.heal(2F);
                if (heart && a.getEntityData().getBoolean("dreHeartShown") == false) {
                    a.getEntityData().setBoolean("dreHeartShown", true);
                    a.addPotionEffect(new PotionEffect(Potion.resistance.id, 20 * 600, 0));
                    say(e.world, p, "dre.chat.horde_heart");
                }
            }
            // фиолетовый перебрасывает собрата к игроку
            long now = e.world.getTotalWorldTime();
            if (t != null && aura(p, e.world, Hue.PURPLE) && now >= p.nextPurple && !EnderColors.wears(t, Hue.PURPLE) && all.size() > 1) {
                p.nextPurple = now + 160;
                EntityLiving m = all.get(e.world.rand.nextInt(all.size()));
                if (Hue.of(m) != Hue.PURPLE) {
                    m.setPosition(t.posX + e.world.rand.nextGaussian() * 3, t.posY + 4, t.posZ + e.world.rand.nextGaussian() * 3);
                    e.world.playSoundAtEntity(m, "mob.endermen.portal", 1F, 0.8F);
                }
            }
        }
    }

    static void beam(World w, int[] from, Entity to) {
        if (!(w instanceof WorldServer)) return;
        double fx = from[0] + 0.5, fy = from[1] + PILLAR_H, fz = from[2] + 0.5;
        for (int i = 1; i <= 8; i++) {
            double k = i / 9.0;
            ((WorldServer) w).func_147487_a("witchMagic", fx + (to.posX - fx) * k, fy + (to.posY + 1 - fy) * k, fz + (to.posZ - fz) * k, 1, 0, 0, 0, 0);
        }
    }

    static void say(World w, Pack p, String key) {
        for (Object o : w.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if ((pl.posX - p.x) * (pl.posX - p.x) + (pl.posZ - p.z) * (pl.posZ - p.z) < 96 * 96)
                pl.addChatMessage(new net.minecraft.util.ChatComponentTranslation(key));
        }
    }

    /** Синий отбивает снаряды; удары по Айрако броней его цвета - сильнее; зелёный цвет брони гасит лечение. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent e) {
        if (!member(e.entityLiving) || e.entityLiving.worldObj.isRemote) return;
        Pack p = pack((EntityLiving) e.entityLiving);
        Entity shooter = e.source.getEntity();
        if (e.source.isProjectile() && aura(p, e.entityLiving.worldObj, Hue.BLUE)
            && !(shooter instanceof EntityPlayer && EnderColors.wears((EntityPlayer) shooter, Hue.BLUE))) {
            e.setCanceled(true);
            e.entityLiving.worldObj.playSoundAtEntity(e.entityLiving, "random.anvil_land", 0.6F, 1.8F);
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity a = e.source.getEntity();
        // по игроку: ярость красного
        if (e.entityLiving instanceof EntityPlayer && member(a)) {
            Pack p = pack((EntityLiving) a);
            if (aura(p, a.worldObj, Hue.RED) && !EnderColors.wears((EntityPlayer) e.entityLiving, Hue.RED)) e.ammount *= 1.5F;
            if (heart(p, a.worldObj)) e.ammount *= 1.3F;
            return;
        }
        // по Айрако: цвет брони игрока
        if (member(e.entityLiving) && a instanceof EntityPlayer) {
            EntityPlayer pl = (EntityPlayer) a;
            Hue h = Hue.of(e.entityLiving);
            float k = 1F + 0.1F * EnderColors.count(pl, null);
            if (h != null && EnderColors.wears(pl, h)) k += 0.5F;
            if (EnderColors.wears(pl, Hue.GREEN)) e.entityLiving.getEntityData().setLong("dreNoHeal", a.worldObj.getTotalWorldTime() + 100);
            e.ammount *= k;
        }
    }

    /** Павший Айрако орды - его цвет переходит к последнему. */
    @SubscribeEvent
    public void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (!member(e.entityLiving) || e.entityLiving.worldObj.isRemote) return;
        Pack p = pack((EntityLiving) e.entityLiving);
        Hue h = Hue.of(e.entityLiving);
        if (h != null) p.fallen.add(h);
        p.diver = -1;
        p.aliveAt = -1;
    }

    /** Для автотеста. */
    public static void reset() {
        PACKS.clear();
    }
}
