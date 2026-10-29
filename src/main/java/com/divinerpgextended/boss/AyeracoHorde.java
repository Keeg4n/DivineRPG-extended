package com.divinerpgextended.boss;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
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
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
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
 * Орда Айрако гнездовья (дальний остров Энда) - бой как с Гидрой Сумеречного леса: пятеро драконов кружат широким
 * кругом высоко над островом и по очереди подлетают к игроку. Подлетевший {@link #ENGAGE_TICKS} тиков держится рядом,
 * облетая цель, и атакует:
 * <ul>
 * <li>огненное дыхание - конус пламени, медленно доворачивает за целью (уйти вбок можно), поджигает;</li>
 * <li>огненный снаряд навесом - падает на место цели, взрывается пламенем и оставляет горящую землю на 5 секунд;</li>
 * <li>укус, если подошёл вплотную.</li>
 * </ul>
 * Когда стая редеет (три и меньше), подлетают по двое. Огонь - огненный урон: зелье огнестойкости спасает.
 * Ауры живых по цвету действуют на всю стаю: зелёный лечит остальных; синий отбивает снаряды; красный - ярость
 * (урон x1.5); жёлтый - стая быстрее, попадания замедляют; фиолетовый перебрасывает собрата к игроку. Столпы гнезда:
 * пять цветных столпов на обсидиановых башнях острова; пока цел столп цвета, его Айрако восстанавливается. Сердце орды:
 * последний выживший забирает ауры павших и бьёт сильнее. Броня Энда того же цвета ({@link EnderColors}) снимает с
 * носителя ауру этого Айрако и усиливает удары по нему. Лучи Divine (блоки луча от рога) после боя убираются.
 */
public class AyeracoHorde {

    public static final String TAG = "dreHorde", CX = "dreHordeX", CY = "dreHordeY", CZ = "dreHordeZ";
    static final String PH = "dreHordePh", UNTIL = "dreHordeUntil", NEXT = "dreHordeAtk", BREATH = "dreHordeBreath", AIM_YAW = "dreHordeYaw",
        AIM_PITCH = "dreHordePitch";

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

    /** Круг стаи: радиус, высота над полом, угловая скорость. */
    public static final double ORBIT_R = 36, ORBIT_H = 20, ORBIT_W = 0.012, LEASH = 64;
    /** Подлёт: на каком расстоянии от цели и выше её держится. */
    public static final double PERCH = 9, PERCH_UP = 5;
    public static final int APPROACH_TICKS = 100, ENGAGE_TICKS = 160, RETREAT_TICKS = 50, STAGGER = 50;
    /** Столпы: кольцо башен, высота башни над полом, высота глиняного столпа. */
    public static final int PILLAR_RING = 36, PILLAR_LIFT = 8, PILLAR_H = 5;
    /** Дыхание: длительность (первые BREATH_WIND - замах без урона), дальность, полуугол конуса, урон за 5 тиков. */
    public static final int BREATH_TICKS = 50, BREATH_WIND = 12;
    public static final double BREATH_RANGE = 13, BREATH_CONE = 0.32;
    public static final float BREATH_DMG = 4F, MORTAR_DMG = 7F, FIRE_DMG = 2F, BITE_RANGE = 4.5F;
    /** Снаряд навесом: время полёта, тяжесть; горящая земля - сколько тиков. */
    public static final int MORTAR_FLIGHT = 30, FIRE_TICKS = 100;
    static final double MORTAR_G = 0.04;
    public static final int ORBIT = 0, APPROACH = 1, ENGAGE = 2, RETREAT = 3;

    /** Снаряд навесом в полёте. */
    static final class Mortar {
        double x, y, z, vx, vy, vz;
        final EntityLiving owner;
        int age;

        Mortar(EntityLiving owner) {
            this.owner = owner;
        }
    }

    /** Состояние стаи (по центру гнездовья). */
    static final class Pack {
        final int x, y, z;
        int turn;
        long nextDive, nextPurple;
        boolean seen;
        final Set<Hue> fallen = EnumSet.noneOf(Hue.class);
        final List<EntityLiving> alive = new ArrayList<EntityLiving>();
        long aliveAt = -1;
        final List<Mortar> mortars = new ArrayList<Mortar>();
        /** Горящая земля: {x, y, z, до какого тика}; кто поджёг - в fireOwners. */
        final List<double[]> fires = new ArrayList<double[]>();
        final List<EntityLiving> fireOwners = new ArrayList<EntityLiving>();

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
        p.seen = true;
        return p;
    }

    /** Живые Айрако стаи (раз в тик). */
    static List<EntityLiving> alive(Pack p, World w) {
        long now = w.getTotalWorldTime();
        if (p.aliveAt == now) return p.alive;
        p.aliveAt = now;
        p.alive.clear();
        for (Object o : w.getEntitiesWithinAABB(EntityAyeraco.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(p.x - 128, p.y - 48, p.z - 128, p.x + 128,
            p.y + 96, p.z + 128))) {
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
        double bd = 72 * 72;
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

    public static int phase(Entity e) {
        return e.getEntityData().getInteger(PH);
    }

    static void phase(Entity e, int ph, long until) {
        e.getEntityData().setInteger(PH, ph);
        e.getEntityData().setLong(UNTIL, until);
    }

    /** Сколько подлетают одновременно: по одному, при трёх и меньше - по двое. */
    public static int maxEngaged(int alive) {
        return alive <= 3 ? 2 : 1;
    }

    static int engaged(List<EntityLiving> all) {
        int n = 0;
        for (EntityLiving m : all) if (phase(m) == APPROACH || phase(m) == ENGAGE) n++;
        return n;
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
        NBTTagCompound d = e.getEntityData();
        // очередь подлёта: следующий по кругу из тех, кто кружит
        if (t != null && now >= p.nextDive && engaged(all) < maxEngaged(all.size())) {
            for (int k = 1; k <= n; k++) {
                int j = (p.turn + k) % n;
                EntityLiving m = all.get(j);
                if (phase(m) != ORBIT) continue;
                p.turn = j;
                phase(m, APPROACH, now + APPROACH_TICKS);
                p.nextDive = now + (long) (STAGGER / speedK);
                break;
            }
        }
        int ph = phase(e);
        if (t == null && ph != ORBIT) phase(e, ph = ORBIT, now);
        double ang = now * ORBIT_W + idx * Math.PI * 2 / n;
        double ox = p.x + 0.5 + Math.cos(ang) * ORBIT_R, oz = p.z + 0.5 + Math.sin(ang) * ORBIT_R, oy = p.y + ORBIT_H + (idx % 2) * 5;
        double tx = ox, ty = oy, tz = oz, speed = 0.5 * speedK;
        boolean face = false;
        if (ph == APPROACH) {
            double[] at = perch(e, t, 0);
            tx = at[0];
            ty = at[1];
            tz = at[2];
            speed = 0.8 * speedK;
            double ex = e.posX - tx, ey = e.posY - ty, ez = e.posZ - tz;
            if (ex * ex + ey * ey + ez * ez < 3 * 3 || now > d.getLong(UNTIL)) {
                phase(e, ENGAGE, now + ENGAGE_TICKS);
                d.setLong(NEXT, now + 15);
            }
        } else if (ph == ENGAGE) {
            double[] at = perch(e, t, 0.02);
            tx = at[0];
            ty = at[1];
            tz = at[2];
            speed = 0.25 * speedK;
            face = true;
            boolean breathing = d.getLong(BREATH) > now;
            if (breathing) breathe(e, p, t, now);
            else if (now >= d.getLong(NEXT)) attack(e, p, t, now);
            if (!breathing && now > d.getLong(UNTIL)) phase(e, RETREAT, now + RETREAT_TICKS);
        } else if (ph == RETREAT) {
            speed = 0.6 * speedK;
            if (now > d.getLong(UNTIL)) phase(e, ORBIT, now);
        }
        // привязь: далеко от гнездовья или ниже его - назад к кругу
        double hx = e.posX - p.x, hz = e.posZ - p.z;
        if (hx * hx + hz * hz > LEASH * LEASH || e.posY < p.y - 8) {
            tx = ox;
            ty = oy;
            tz = oz;
            speed = 1.0;
            face = false;
        }
        double dx = tx - e.posX, dy = ty - e.posY, dz = tz - e.posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double v = Math.min(speed, len * 0.3);
        if (len > 0.01) {
            e.motionX = dx / len * v;
            e.motionY = dy / len * v + 0.08; // тяга против тяжести (её прибавит движение моба)
            e.motionZ = dz / len * v;
        }
        if (face && t != null) {
            e.rotationYaw = e.renderYawOffset = e.rotationYawHead = d.getLong(BREATH) > now ? d.getFloat(AIM_YAW)
                : (float) (Math.atan2(t.posZ - e.posZ, t.posX - e.posX) * 180 / Math.PI) - 90F;
        } else if (len > 0.01) {
            e.rotationYaw = e.renderYawOffset = e.rotationYawHead = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        }
        e.fallDistance = 0;
        return true;
    }

    /** Точка подлёта: в PERCH от цели со стороны дракона, выше на PERCH_UP; turn - сдвиг по кругу (облёт). */
    static double[] perch(EntityLiving e, EntityPlayer t, double turn) {
        if (t == null) return new double[] { e.posX, e.posY, e.posZ };
        double a = Math.atan2(e.posZ - t.posZ, e.posX - t.posX) + turn;
        return new double[] { t.posX + Math.cos(a) * PERCH, t.posY + PERCH_UP, t.posZ + Math.sin(a) * PERCH };
    }

    /** Пасть дракона: перед головой. */
    static Vec3 mouth(EntityLiving e) {
        float yaw = e.rotationYawHead * (float) Math.PI / 180F;
        return Vec3.createVectorHelper(e.posX - MathHelper.sin(yaw) * e.width * 0.8, e.posY + e.height * 0.55, e.posZ + MathHelper.cos(yaw) * e.width * 0.8);
    }

    /** Огненный урон от дракона: зелье огнестойкости его гасит. */
    public static DamageSource fire(Entity e) {
        return new EntityDamageSource("dre.ayeracoFire", e) {

            {
                setFireDamage();
            }
        };
    }

    /** Выбор атаки: вплотную - укус, в пределах дыхания - чаще дыхание, иначе снаряд навесом. */
    static void attack(EntityLiving e, Pack p, EntityPlayer t, long now) {
        NBTTagCompound d = e.getEntityData();
        double dist = e.getDistanceToEntity(t);
        d.setLong(NEXT, now + 45 + e.getRNG().nextInt(25));
        if (dist < BITE_RANGE) {
            e.attackEntityAsMob(t);
            if (aura(p, e.worldObj, Hue.YELLOW) && !EnderColors.wears(t, Hue.YELLOW)) t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
        } else if (dist <= BREATH_RANGE && e.getRNG().nextInt(100) < 60) {
            startBreath(e, t, now);
        } else {
            launch(e, p, t);
        }
    }

    public static void startBreath(EntityLiving e, EntityPlayer t, long now) {
        NBTTagCompound d = e.getEntityData();
        d.setLong(BREATH, now + BREATH_TICKS);
        Vec3 m = mouth(e);
        double dx = t.posX - m.xCoord, dy = t.posY + t.getEyeHeight() * 0.6 - m.yCoord, dz = t.posZ - m.zCoord;
        d.setFloat(AIM_YAW, (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F);
        d.setFloat(AIM_PITCH, (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI));
        e.worldObj.playSoundAtEntity(e, "mob.ghast.charge", 3F, 0.6F);
    }

    /** Направление дыхания (единичный вектор) из угла прицела. */
    static Vec3 aimDir(Entity e) {
        float yaw = e.getEntityData().getFloat(AIM_YAW) * (float) Math.PI / 180F, pitch = e.getEntityData().getFloat(AIM_PITCH) * (float) Math.PI / 180F;
        return Vec3.createVectorHelper(-MathHelper.sin(yaw) * MathHelper.cos(pitch), -MathHelper.sin(pitch), MathHelper.cos(yaw) * MathHelper.cos(pitch));
    }

    /** Тик дыхания: прицел медленно доворачивает за целью; после замаха - пламя и урон всем в конусе раз в 5 тиков. */
    public static void breathe(EntityLiving e, Pack p, EntityPlayer t, long now) {
        NBTTagCompound d = e.getEntityData();
        long end = d.getLong(BREATH);
        Vec3 m = mouth(e);
        if (t != null) {
            double dx = t.posX - m.xCoord, dy = t.posY + t.getEyeHeight() * 0.6 - m.yCoord, dz = t.posZ - m.zCoord;
            float wantYaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F, wantPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
            d.setFloat(AIM_YAW, d.getFloat(AIM_YAW) + MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(wantYaw - d.getFloat(AIM_YAW)), -2.5F, 2.5F));
            d.setFloat(AIM_PITCH, d.getFloat(AIM_PITCH) + MathHelper.clamp_float(wantPitch - d.getFloat(AIM_PITCH), -2F, 2F));
        }
        Vec3 dir = aimDir(e);
        WorldServer ws = e.worldObj instanceof WorldServer ? (WorldServer) e.worldObj : null;
        boolean wind = now < end - BREATH_TICKS + BREATH_WIND;
        if (ws != null) {
            if (wind) ws.func_147487_a("flame", m.xCoord, m.yCoord, m.zCoord, 4, 0.2, 0.2, 0.2, 0.01);
            else if (now % 2 == 0) {
                for (int i = 1; i <= 12; i++) {
                    double k = i * BREATH_RANGE / 12, s = 0.15 + k * BREATH_CONE * 0.5;
                    ws.func_147487_a(i % 3 == 0 ? "lava" : "flame", m.xCoord + dir.xCoord * k, m.yCoord + dir.yCoord * k, m.zCoord + dir.zCoord * k, 2, s, s, s, 0.02);
                }
            }
        }
        if (wind || now % 5 != 0) return;
        if (now % 20 == 0) e.worldObj.playSoundAtEntity(e, "mob.ghast.fireball", 2F, 0.7F);
        for (EntityPlayer pl : inCone(e.worldObj, m, dir, BREATH_RANGE, BREATH_CONE)) {
            if (pl.attackEntityFrom(fire(e), BREATH_DMG)) pl.setFire(3);
        }
    }

    /** Игроки в конусе: от вершины m по направлению dir на range, полуугол cone (рад). */
    public static List<EntityPlayer> inCone(World w, Vec3 m, Vec3 dir, double range, double cone) {
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        for (Object o : w.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if (pl.capabilities.isCreativeMode || !pl.isEntityAlive()) continue;
            double vx = pl.posX - m.xCoord, vy = pl.posY + pl.height / 2 - m.yCoord, vz = pl.posZ - m.zCoord, len = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (len > range + 0.5 || len < 0.01) continue;
            double cos = (vx * dir.xCoord + vy * dir.yCoord + vz * dir.zCoord) / len;
            // тело игрока шириной ~0.6: вблизи конус узкий - даём запас на полтела
            if (cos >= Math.cos(cone + Math.atan2(0.6, len))) out.add(pl);
        }
        return out;
    }

    /** Огненный снаряд навесом в место цели (с упреждением). */
    static void launch(EntityLiving e, Pack p, EntityPlayer t) {
        Vec3 m = mouth(e);
        Mortar s = new Mortar(e);
        s.x = m.xCoord;
        s.y = m.yCoord;
        s.z = m.zCoord;
        // упреждение по сдвигу за тик (скорость игрока на сервере - отдача и мусор, её задаёт клиент), не дальше 4 блоков
        double lx = MathHelper.clamp_double((t.posX - t.prevPosX) * 8, -4, 4), lz = MathHelper.clamp_double((t.posZ - t.prevPosZ) * 8, -4, 4);
        double tx = t.posX + lx, ty = t.boundingBox.minY, tz = t.posZ + lz;
        s.vx = (tx - s.x) / MORTAR_FLIGHT;
        s.vz = (tz - s.z) / MORTAR_FLIGHT;
        s.vy = (ty - s.y) / MORTAR_FLIGHT + 0.5 * MORTAR_G * MORTAR_FLIGHT;
        p.mortars.add(s);
        e.worldObj.playSoundAtEntity(e, "mob.ghast.fireball", 2F, 0.5F);
    }

    /** Полёт снарядов и горящая земля (каждый тик). */
    static void tickFire(World w, Pack p) {
        long now = w.getTotalWorldTime();
        WorldServer ws = w instanceof WorldServer ? (WorldServer) w : null;
        for (Iterator<Mortar> it = p.mortars.iterator(); it.hasNext();) {
            Mortar s = it.next();
            s.age++;
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
            s.vy -= MORTAR_G;
            if (ws != null) {
                ws.func_147487_a("flame", s.x, s.y, s.z, 3, 0.15, 0.15, 0.15, 0.01);
                ws.func_147487_a("smoke", s.x, s.y, s.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
            int bx = MathHelper.floor_double(s.x), by = MathHelper.floor_double(s.y), bz = MathHelper.floor_double(s.z);
            boolean hitBlock = w.blockExists(bx, by, bz) && w.getBlock(bx, by, bz).getCollisionBoundingBoxFromPool(w, bx, by, bz) != null;
            boolean hitPlayer = false;
            for (Object o : w.playerEntities) {
                EntityPlayer pl = (EntityPlayer) o;
                if (!pl.capabilities.isCreativeMode && pl.boundingBox.expand(0.4, 0.4, 0.4).isVecInside(Vec3.createVectorHelper(s.x, s.y, s.z))) hitPlayer = true;
            }
            if (hitBlock || hitPlayer) {
                explode(w, p, s, hitBlock ? by + 1 : s.y);
                it.remove();
            } else if (s.age > MORTAR_FLIGHT * 3 || s.y < 0) it.remove();
        }
        for (int i = p.fires.size() - 1; i >= 0; i--) {
            double[] f = p.fires.get(i);
            if (now > f[3]) {
                p.fires.remove(i);
                p.fireOwners.remove(i);
                continue;
            }
            if (now % 4 != 0) continue;
            if (ws != null) {
                ws.func_147487_a("flame", f[0], f[1] + 0.1, f[2], 6, 1.0, 0.1, 1.0, 0.01);
                ws.func_147487_a("lava", f[0], f[1] + 0.1, f[2], 1, 0.8, 0.1, 0.8, 0);
            }
            EntityLiving owner = p.fireOwners.get(i);
            for (Object o : w.playerEntities) {
                EntityPlayer pl = (EntityPlayer) o;
                double dx = pl.posX - f[0], dz = pl.posZ - f[2];
                if (pl.capabilities.isCreativeMode || dx * dx + dz * dz > 1.8 * 1.8 || Math.abs(pl.boundingBox.minY - f[1]) > 1.5) continue;
                if (pl.attackEntityFrom(owner != null && owner.isEntityAlive() ? fire(owner) : DamageSource.inFire, FIRE_DMG)) pl.setFire(2);
            }
        }
    }

    /** Разрыв снаряда: пламя вокруг, урон с расстоянием, горящая земля. Блоки не ломает. */
    static void explode(World w, Pack p, Mortar s, double groundY) {
        if (w instanceof WorldServer) {
            ((WorldServer) w).func_147487_a("largeexplode", s.x, s.y, s.z, 1, 0, 0, 0, 0);
            ((WorldServer) w).func_147487_a("flame", s.x, s.y, s.z, 40, 1.5, 0.6, 1.5, 0.05);
        }
        w.playSoundEffect(s.x, s.y, s.z, "random.explode", 1.5F, 1.2F);
        for (Object o : w.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if (pl.capabilities.isCreativeMode) continue;
            double dist = pl.getDistance(s.x, s.y, s.z);
            if (dist > 4) continue;
            float dmg = (float) (MORTAR_DMG * (1 - dist / 5.5));
            if (pl.attackEntityFrom(s.owner.isEntityAlive() ? fire(s.owner) : DamageSource.inFire, dmg)) {
                pl.setFire(4);
                if (aura(p, w, Hue.YELLOW) && !EnderColors.wears(pl, Hue.YELLOW)) pl.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
            }
        }
        p.fires.add(new double[] { s.x, groundY, s.z, w.getTotalWorldTime() + FIRE_TICKS });
        p.fireOwners.add(s.owner);
    }

    /** Телепорт Айрако орды: в случайную точку круга над островом, не в бездну. */
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

    /** Основание глиняного столпа цвета (на верху башни). */
    public static int[] pillarAt(int cx, int cy, int cz, Hue h) {
        double a = h.ordinal() * Math.PI * 2 / 5 + Math.PI / 10;
        return new int[] { cx + (int) Math.round(Math.cos(a) * PILLAR_RING), cy + PILLAR_LIFT + 1, cz + (int) Math.round(Math.sin(a) * PILLAR_RING) };
    }

    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || PACKS.isEmpty()
            || e.world.provider.dimensionId != WorldGenOuterLairs.dim(WorldGenOuterLairs.AYERACO)) return;
        long now = e.world.getTotalWorldTime();
        for (Pack p : new ArrayList<Pack>(PACKS.values())) {
            tickFire(e.world, p);
            if (now % 20 != 0) continue;
            List<EntityLiving> all = alive(p, e.world);
            if (all.isEmpty()) {
                // бой кончен (пали или ушли с арены): убрать лучи Divine, пока вокруг всё загружено
                if (p.seen && e.world.blockExists(p.x, p.y, p.z)) {
                    sweepBeams(e.world, p.x, p.y, p.z);
                    PACKS.remove(key(p.x, p.z));
                }
                continue;
            }
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
                    && a.getEntityData().getLong("dreNoHeal") < now) a.heal(2F);
                if (heart && a.getEntityData().getBoolean("dreHeartShown") == false) {
                    a.getEntityData().setBoolean("dreHeartShown", true);
                    a.addPotionEffect(new PotionEffect(Potion.resistance.id, 20 * 600, 0));
                    say(e.world, p, "dre.chat.horde_heart");
                }
            }
            // фиолетовый перебрасывает собрата к игроку
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

    /**
     * Убрать блоки лучей Divine вокруг гнездовья. Рог ставит пять лучей, каждый Айрако убирает свой при смерти, но место луча
     * Divine не сохраняет: после перезахода, выгрузки чанка или ухода орды с арены лучи оставались и копились.
     */
    public static int sweepBeams(World w, int cx, int cy, int cz) {
        int r = WorldGenOuterLairs.RADIUS[WorldGenOuterLairs.AYERACO] + 20, n = 0;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!w.blockExists(x, cy, z)) continue;
                for (int y = cy - 4; y <= cy + 40; y++) {
                    if (w.getBlock(x, y, z) instanceof net.divinerpg.blocks.vanilla.BlockAyeracoBeam) {
                        w.setBlockToAir(x, y, z);
                        n++;
                    }
                }
            }
        }
        return n;
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
            if ((pl.posX - p.x) * (pl.posX - p.x) + (pl.posZ - p.z) * (pl.posZ - p.z) < 128 * 128)
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
        // по игроку: ярость красного (и огнём тоже)
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
        p.aliveAt = -1;
    }

    /** Для автотеста. */
    public static void reset() {
        PACKS.clear();
    }

    /** Для автотеста: снарядов в полёте и горящих мест у стаи. */
    public static int[] fireCount(EntityLiving e) {
        Pack p = pack(e);
        return new int[] { p.mortars.size(), p.fires.size() };
    }

    /** Для автотеста: где горит последнее место. */
    public static double[] lastFire(EntityLiving e) {
        Pack p = pack(e);
        return p.fires.isEmpty() ? null : p.fires.get(p.fires.size() - 1);
    }

    /** Для автотеста: прогнать снаряды и огонь стаи на тик. */
    public static void tickFireFor(EntityLiving e) {
        tickFire(e.worldObj, pack(e));
    }

    /** Для автотеста: выпустить снаряд в цель. */
    public static void launchAt(EntityLiving e, EntityPlayer t) {
        launch(e, pack(e), t);
    }

    /** Для автотеста: тик дыхания. */
    public static void breatheAt(EntityLiving e, EntityPlayer t, long now) {
        breathe(e, pack(e), t, now);
    }
}
