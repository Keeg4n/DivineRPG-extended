package com.divinerpgextended.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.divinerpg.entities.vanilla.EntityTheWatcher;
import net.divinerpg.entities.vanilla.projectile.EntityWatcherShot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.divinerpgextended.entity.EntityWatcherEye;
import com.divinerpgextended.world.WatcherVault;
import com.divinerpgextended.world.WorldGenOuterLairs;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Смотритель в своём логове ({@link WatcherVault}) - глаз, которого бьют издалека. Вместо его поведения из Divine (через
 * {@link com.divinerpgextended.asm.WatcherTransformer}).
 * <ul>
 * <li>Первая фаза: висит над залом и смотрит на ближайшего бойца. Видит - заряжает выстрел ({@link #CHARGE} тиков, тянется
 * луч, за треть до выстрела прицел замирает). Пока заряжается, глаз открыт: снаряды бьют его вдвое сильнее. Боец спрятался
 * за колонну - выпускает оков-прислужников ({@link EntityWatcherEye}): они летят за укрытие и кусают вплотную.</li>
 * <li>На половине здоровья ревёт и крутится ({@link #ROAR} тиков, неуязвим), выпускает стаю глаз и переходит во вторую
 * фазу - рывки. Замах ({@link #WIND} тиков, глаз открыт), рывок к бойцу; задел - урон и отброс, врезался в колонну - оглушён
 * и перегрет. После {@link #DASHES} рывков - передышка с одним выстрелом. Ниже 20% - рывков больше, замах короче.</li>
 * <li>Перегретый (после выстрела, оглушения, удара о колонну) получает двойной урон.</li>
 * <li>Адское оружие ({@link #onHurt}): пуля пушки гаста в открытый глаз (заряд или замах) сбивает его и оглушает на
 * {@link #STUN} тиков; пушка гаста, Maelstrom и адское оружие ближнего боя бьют в полтора раза сильнее; огненные мечи и
 * стрелы Inferno копят жар - {@link #WARMTH} подряд перегревают; стрела Bluefire ослепляет на {@link #BLIND} тиков.</li>
 * <li>Взрывы в логове блоков не ломают, предметы и опыт на земле не трогают, урон от взрыва бойцу - не больше
 * {@link #BLAST_CAP} ({@link #onDetonate}).</li>
 * </ul>
 * Логово старого вида (с завалом) перестраивается в новое, когда туда зашёл игрок, а Смотрителя нет ({@link #tidy}).
 */
public class WatcherHunt {

    public static final int CHARGE = 30, COOLDOWN = 45, HEAT = 40, STUN = 30, BLIND = 60, WARMTH = 4, CHASE_RANGE = 48;
    /** Прислужники: боец не виден столько тиков - выпустить; пауза между выпусками; не больше стольких сразу. */
    public static final int HIDE_TICKS = 25, EYES_EVERY = 90, EYES_MAX = 5, EYES_MAX_2 = 6;
    /** Переход во вторую фазу; рывки: замах, длительность, число до передышки, передышка, оглушение о колонну. */
    public static final int ROAR = 40, WIND = 18, WIND_LOW = 13, DASH = 14, DASHES = 3, DASHES_LOW = 4, REST = 50, CRASH = 50;
    public static final float PHASE2 = 0.5F, LOW = 0.2F, SHOT_SPEED = 1.3F, DASH_SPEED = 1.25F, DASH_HIT = 10F, BLAST_CAP = 14F;
    /** Множители: открытый глаз - снарядам; перегретому - всему; адскому оружию. */
    public static final float OPEN_MULT = 2.0F, HEAT_MULT = 2.0F, NETHER_MULT = 1.5F;

    /** Состояние боя (на время жизни босса; сохранять не нужно). */
    static final class State {
        int phase = 1, charge, cooldown = 40, heat, stun, blind, warmth, hidden, eyesTimer, roar;
        int wind, dash, dashes, rest, hoverTimer;
        double aimX, aimY, aimZ, hoverX, hoverY, hoverZ, dirX, dirY, dirZ;
        boolean locked;
        final Set<Entity> hitThisDash = new HashSet<Entity>();
        final List<EntityWatcherEye> eyes = new ArrayList<EntityWatcherEye>();
    }

    static final Map<Entity, State> STATES = new WeakHashMap<Entity, State>();
    /** Логова, уже нового вида (за этот запуск сервера): x и z центра. */
    static final Set<Long> TIDY = new HashSet<Long>();

    static State state(Entity e) {
        State s = STATES.get(e);
        if (s == null) STATES.put(e, s = new State());
        return s;
    }

    static long key(int[] c) {
        return (long) c[0] << 32 ^ (c[2] & 0xffffffffL);
    }

    /** Центр логова Смотрителя, где он сейчас (или null - не в логове). */
    public static int[] vault(World w, double x, double y, double z) {
        if (!WorldGenOuterLairs.enabled(WorldGenOuterLairs.WATCHER) || w.provider.dimensionId != WorldGenOuterLairs.dim(WorldGenOuterLairs.WATCHER)) return null;
        int[] c = WorldGenOuterLairs.nearest(w.getSeed(), WorldGenOuterLairs.WATCHER, x, z);
        if (c == null) return null;
        int dx = MathHelper.floor_double(x) - c[0], dz = MathHelper.floor_double(z) - c[2], iy = MathHelper.floor_double(y);
        return dx * dx + dz * dz < (WatcherVault.R + 1) * (WatcherVault.R + 1) && iy >= WatcherVault.FLOOR - 1 && iy <= WatcherVault.TOP + 2 ? c : null;
    }

    public static boolean inVault(Entity e) {
        return vault(e.worldObj, e.posX, e.posY + e.height / 2, e.posZ) != null;
    }

    // ---------------------------------------------------------------- для проверок

    /** Фаза: 1, 2 или 0 - рёв перехода. */
    public static int phase(Entity e) {
        State s = STATES.get(e);
        return s == null ? 1 : s.roar > 0 ? 0 : s.phase;
    }

    public static boolean charging(Entity e) {
        State s = STATES.get(e);
        return s != null && s.charge > 0;
    }

    public static boolean winding(Entity e) {
        State s = STATES.get(e);
        return s != null && s.wind > 0;
    }

    public static boolean dashing(Entity e) {
        State s = STATES.get(e);
        return s != null && s.dash > 0;
    }

    public static boolean stunned(Entity e) {
        State s = STATES.get(e);
        return s != null && s.stun > 0;
    }

    public static boolean overheated(Entity e) {
        State s = STATES.get(e);
        return s != null && s.heat > 0;
    }

    /** Скольких бойцов задел последний рывок. */
    public static int dashHits(Entity e) {
        State s = STATES.get(e);
        return s == null ? 0 : s.hitThisDash.size();
    }

    public static int eyes(Entity e) {
        State s = STATES.get(e);
        if (s == null) return 0;
        prune(s);
        return s.eyes.size();
    }

    public static void startCharge(EntityTheWatcher w) {
        State s = state(w);
        s.charge = CHARGE;
        s.locked = false;
    }

    /** Сразу во вторую фазу, в замах рывка. */
    public static void startWind(EntityTheWatcher w) {
        State s = state(w);
        s.phase = 2;
        s.roar = 0;
        s.rest = 0;
        s.wind = WIND;
    }

    public static void reset(Entity e) {
        STATES.remove(e);
    }

    /** Для проверок: забыть, какие логова уже нового вида. */
    public static void forgetTidy() {
        TIDY.clear();
    }

    // ---------------------------------------------------------------- ход

    /** Смотритель в своём логове (на сервере): его собственный полёт и выстрел из Divine отключены. */
    public static boolean lair(EntityTheWatcher w) {
        return !w.worldObj.isRemote && inVault(w);
    }

    /** Ход Смотрителя (вместо его хода из Divine); false - не в логове, ведёт себя как в Divine. */
    public static boolean act(EntityTheWatcher w) {
        if (w.worldObj.isRemote) return false;
        int[] c = vault(w.worldObj, w.posX, w.posY + w.height / 2, w.posZ);
        if (c == null) return false;
        State s = state(w);
        WorldServer ws = (WorldServer) w.worldObj;
        double ex = w.posX, ey = w.posY + w.height * 0.5, ez = w.posZ;
        if (s.heat > 0 && --s.heat % 4 == 0) ws.func_147487_a("smoke", ex, ey + 1, ez, 6, 1.0, 0.8, 1.0, 0.02);
        if (s.eyesTimer > 0) s.eyesTimer--;
        if (s.stun > 0) { // оглушён: висит, искрит
            s.stun--;
            damp(w, 0.5);
            if (s.stun % 3 == 0) ws.func_147487_a("crit", ex, ey, ez, 8, 1.2, 1.2, 1.2, 0.1);
            return true;
        }
        if (s.phase == 1 && w.getHealth() <= w.getMaxHealth() * PHASE2) { // рёв: переход во вторую фазу
            s.phase = 2;
            s.roar = ROAR;
            s.charge = 0;
            ws.playSoundAtEntity(w, "mob.enderdragon.growl", 4.0F, 0.6F);
        }
        if (s.roar > 0) {
            s.roar--;
            damp(w, 0.6);
            w.rotationYaw = w.renderYawOffset = w.rotationYaw + 27F;
            if (s.roar % 2 == 0) ws.func_147487_a("reddust", ex, ey, ez, 20, 2.2, 2.2, 2.2, 0);
            if (s.roar == 0) {
                for (int i = 0; i < 4; i++) spawnEye(w, s, EYES_MAX_2);
                s.wind = WIND;
                s.dashes = 0;
            }
            return true;
        }
        if (s.blind > 0) { // ослеплён Bluefire: цель потеряна, плывёт куда глаза глядят
            s.blind--;
            s.charge = s.wind = s.dash = 0;
            if (s.blind % 20 == 0) newHover(w, s, c);
            fly(w, s.hoverX, s.hoverY, s.hoverZ, 0.07);
            return true;
        }
        EntityPlayer target = nearestFighter(w.worldObj, w);
        if (target == null) { // никого - висит над оком
            s.charge = s.wind = s.dash = 0;
            fly(w, c[0] + 0.5, WatcherVault.bossY() - w.height / 2 + 0.5, c[2] + 0.5, 0.08);
            return true;
        }
        double tx = target.posX, ty = target.posY + target.getEyeHeight() * 0.7, tz = target.posZ;
        if (s.dash == 0) face(w, tx - ex, ty - ey, tz - ez);
        MovingObjectPosition hit = w.worldObj.func_147447_a(Vec3.createVectorHelper(ex, ey, ez), Vec3.createVectorHelper(tx, ty, tz), false, true, false);
        boolean sight = hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
        s.hidden = sight ? 0 : s.hidden + 1;
        int max = s.phase == 1 ? EYES_MAX : EYES_MAX_2;
        if (s.hidden >= HIDE_TICKS && s.eyesTimer == 0) { // спрятался - за ним летят глаза
            spawnEye(w, s, max);
            spawnEye(w, s, max);
            s.eyesTimer = s.phase == 1 ? EYES_EVERY : EYES_EVERY + 40;
        }
        if (s.phase == 1) phaseOne(w, ws, s, c, sight, tx, ty, tz, ex, ey, ez);
        else phaseTwo(w, ws, s, c, sight, w.getHealth() <= w.getMaxHealth() * LOW, tx, ty, tz, ex, ey, ez);
        return true;
    }

    /** Первая фаза: парит над залом, заряжает выстрел, когда видит бойца. */
    static void phaseOne(EntityTheWatcher w, WorldServer ws, State s, int[] c, boolean sight, double tx, double ty, double tz, double ex, double ey, double ez) {
        if (--s.hoverTimer <= 0 || Math.abs(w.posX - s.hoverX) + Math.abs(w.posZ - s.hoverZ) < 1) newHover(w, s, c);
        fly(w, s.hoverX, s.hoverY, s.hoverZ, 0.11);
        if (s.charge > 0) {
            s.charge--;
            if (!s.locked) aim(s, tx, ty, tz);
            if (s.charge <= CHARGE / 3) s.locked = true;
            beam(ws, ex, ey, ez, s);
            if (s.charge == 0) fire(w, s, ex, ey, ez, COOLDOWN);
        } else if (--s.cooldown <= 0 && sight) {
            s.charge = CHARGE;
            s.locked = false;
            aim(s, tx, ty, tz);
            ws.playSoundAtEntity(w, "mob.ghast.charge", 3.0F, 0.6F);
        }
    }

    /** Вторая фаза: замах - рывок - снова; после серии передышка с одним выстрелом. */
    static void phaseTwo(EntityTheWatcher w, WorldServer ws, State s, int[] c, boolean sight, boolean low, double tx, double ty, double tz, double ex, double ey,
        double ez) {
        if (s.rest > 0) { // передышка: парит, один быстрый выстрел
            s.rest--;
            if (--s.hoverTimer <= 0) newHover(w, s, c);
            fly(w, s.hoverX, s.hoverY, s.hoverZ, 0.12);
            if (s.charge > 0) {
                s.charge--;
                if (!s.locked) aim(s, tx, ty, tz);
                if (s.charge <= 6) s.locked = true;
                beam(ws, ex, ey, ez, s);
                if (s.charge == 0) fire(w, s, ex, ey, ez, 0);
            } else if (s.rest == REST - 10 && sight) {
                s.charge = 18;
                s.locked = false;
                ws.playSoundAtEntity(w, "mob.ghast.charge", 3.0F, 0.8F);
            }
            if (s.rest == 0) {
                s.charge = 0;
                s.wind = low ? WIND_LOW : WIND;
                s.dashes = 0;
            }
            return;
        }
        if (s.dash > 0) {
            s.dash--;
            double sp = DASH_SPEED * (low ? 1.15 : 1.0);
            w.motionX = s.dirX * sp;
            w.motionY = s.dirY * sp;
            w.motionZ = s.dirZ * sp;
            if (s.dash % 2 == 0) ws.func_147487_a("flame", ex, ey, ez, 6, 1.5, 1.5, 1.5, 0.02);
            for (Object o : w.worldObj.getEntitiesWithinAABB(EntityPlayer.class, w.boundingBox.expand(0.6, 0.6, 0.6))) {
                EntityPlayer p = (EntityPlayer) o;
                if (!p.capabilities.disableDamage && s.hitThisDash.add(p)) {
                    p.attackEntityFrom(DamageSource.causeMobDamage(w), DASH_HIT);
                    p.addVelocity(s.dirX * 1.2, 0.4, s.dirZ * 1.2);
                    p.velocityChanged = true;
                }
            }
            if (w.isCollidedHorizontally && s.dash < DASH - 2) { // врезался в колонну или стену
                s.dash = 0;
                s.stun = CRASH;
                s.heat = Math.max(s.heat, CRASH + 20);
                damp(w, 0.0);
                ws.func_147487_a("largeexplode", ex + s.dirX * 2, ey, ez + s.dirZ * 2, 1, 0, 0, 0, 0);
                ws.playSoundAtEntity(w, "random.explode", 2.0F, 0.6F);
                afterDash(s, low);
                return;
            }
            if (s.dash == 0) {
                damp(w, 0.3);
                afterDash(s, low);
            }
            return;
        }
        if (s.wind > 0) { // замах: глаз открыт, трясётся, ищет бойца
            s.wind--;
            damp(w, 0.5);
            w.motionX += (w.getRNG().nextDouble() - 0.5) * 0.12;
            w.motionZ += (w.getRNG().nextDouble() - 0.5) * 0.12;
            if (s.wind % 3 == 0) ws.func_147487_a("reddust", ex, ey, ez, 10, 1.6, 1.6, 1.6, 0);
            if (s.wind == 0) {
                double dx = tx - ex, dy = ty - ey, dz = tz - ez, d = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
                s.dirX = dx / d;
                s.dirY = Math.max(-0.4, Math.min(0.4, dy / d)); // в пол и в свод не ныряет
                s.dirZ = dz / d;
                s.dash = DASH;
                s.hitThisDash.clear();
                ws.playSoundAtEntity(w, "mob.enderdragon.wings", 4.0F, 0.7F);
            }
            return;
        }
        s.wind = low ? WIND_LOW : WIND;
    }

    /** Рывок кончился (или сбит): следующий замах или передышка после серии. */
    static void afterDash(State s, boolean low) {
        if (++s.dashes >= (low ? DASHES_LOW : DASHES)) {
            s.rest = REST;
            s.hoverTimer = 0;
            s.wind = 0;
        } else s.wind = (low ? WIND_LOW : WIND) + 8;
    }

    /** Притормозить по горизонтали и зависнуть (тяга вниз от игры каждый тик гасится). */
    static void damp(EntityTheWatcher w, double k) {
        w.motionX *= k;
        w.motionY = 0;
        w.motionZ *= k;
    }

    static void face(EntityTheWatcher w, double dx, double dy, double dz) {
        w.rotationYaw = w.renderYawOffset = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        w.rotationPitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
    }

    /** Новая точка парения: над оком, не дальше 8 от центра (колонны не задевает), под сводом. */
    static void newHover(EntityTheWatcher w, State s, int[] c) {
        double a = w.getRNG().nextDouble() * Math.PI * 2, r = 3 + w.getRNG().nextDouble() * 5;
        s.hoverX = c[0] + 0.5 + Math.cos(a) * r;
        s.hoverZ = c[2] + 0.5 + Math.sin(a) * r;
        s.hoverY = WatcherVault.bossY() - w.height / 2 + w.getRNG().nextInt(4);
        s.hoverTimer = 60 + w.getRNG().nextInt(40);
    }

    static void prune(State s) {
        for (Iterator<EntityWatcherEye> it = s.eyes.iterator(); it.hasNext();) if (it.next().isDead) it.remove();
    }

    /** Выпустить око-прислужника из Смотрителя (если их меньше max). */
    static EntityWatcherEye spawnEye(EntityTheWatcher w, State s, int max) {
        prune(s);
        if (s.eyes.size() >= max) return null;
        EntityWatcherEye e = new EntityWatcherEye(w.worldObj, w);
        double a = w.getRNG().nextDouble() * Math.PI * 2;
        e.setLocationAndAngles(w.posX + Math.cos(a) * 2.5, w.posY + w.height * 0.4, w.posZ + Math.sin(a) * 2.5, w.rotationYaw, 0);
        e.motionX = Math.cos(a) * 0.4;
        e.motionZ = Math.sin(a) * 0.4;
        BossMinions.mark(e, w);
        w.worldObj.spawnEntityInWorld(e);
        s.eyes.add(e);
        ((WorldServer) w.worldObj).func_147487_a("largesmoke", e.posX, e.posY, e.posZ, 10, 0.3, 0.3, 0.3, 0.02);
        w.worldObj.playSoundAtEntity(w, "mob.slime.big", 1.5F, 0.5F);
        return e;
    }

    /** Для проверок: выпустить око. */
    public static EntityWatcherEye spawnEyeFor(EntityTheWatcher w) {
        return spawnEye(w, state(w), EYES_MAX_2);
    }

    /** Ближайший живой боец в логове Смотрителя (для босса и его прислужников; творческий режим не в счёт). */
    public static EntityPlayer nearestFighter(World world, Entity from) {
        EntityPlayer best = null;
        double bd = CHASE_RANGE * CHASE_RANGE;
        for (Object o : world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.disableDamage || vault(p.worldObj, p.posX, p.posY + 1, p.posZ) == null) continue;
            double d = p.getDistanceSqToEntity(from);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * К точке со скоростью speed * 3 блока за тик, у цели - медленнее. Скорость задаётся целиком каждый тик: Смотритель у
     * Divine - не летун, игра после хода тянет его вниз и тормозит, это не должно копиться.
     */
    static void fly(EntityTheWatcher w, double x, double y, double z, double speed) {
        double dx = x - w.posX, dy = y - w.posY, dz = z - w.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.3) {
            damp(w, 0.3);
            return;
        }
        double v = Math.min(speed * 3, d * 0.25);
        w.motionX = dx / d * v;
        w.motionY = dy / d * v;
        w.motionZ = dz / d * v;
    }

    static void aim(State s, double tx, double ty, double tz) {
        s.aimX = tx;
        s.aimY = ty;
        s.aimZ = tz;
    }

    /** Луч от глаза к прицелу: краснеет, пока прицел ещё ведёт, белеет, когда замер. */
    static void beam(WorldServer ws, double ex, double ey, double ez, State s) {
        if (s.charge % 2 != 0) return;
        double dx = s.aimX - ex, dy = s.aimY - ey, dz = s.aimZ - ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        String part = s.locked ? "fireworksSpark" : "reddust";
        for (double t = 2.0; t < d; t += 0.8) ws.func_147487_a(part, ex + dx / d * t, ey + dy / d * t, ez + dz / d * t, 1, 0, 0, 0, 0);
    }

    /** Выстрел снарядом Смотрителя по прицелу (с поправкой на падение снаряда). */
    static void fire(EntityTheWatcher w, State s, double ex, double ey, double ez, int cooldown) {
        EntityWatcherShot shot = new EntityWatcherShot(w.worldObj, w);
        double dx = s.aimX - ex, dz = s.aimZ - ez, flat = Math.sqrt(dx * dx + dz * dz);
        double t = Math.sqrt(flat * flat + (s.aimY - ey) * (s.aimY - ey)) / SHOT_SPEED;
        double dy = s.aimY - ey + 0.5 * 0.03 * t * t;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        shot.setPosition(ex + dx / d * 2.4, ey + dy / d * 2.4, ez + dz / d * 2.4);
        shot.setThrowableHeading(dx, dy, dz, SHOT_SPEED, 0F);
        w.worldObj.spawnEntityInWorld(shot);
        w.worldObj.playSoundAtEntity(w, "mob.ghast.fireball", 3.0F, 0.7F);
        s.heat = HEAT;
        s.cooldown = cooldown;
        s.locked = false;
    }

    // ---------------------------------------------------------------- урон

    static final Set<net.minecraft.item.Item> FIRE_SWORDS = new HashSet<net.minecraft.item.Item>();

    static boolean fireSword(net.minecraft.item.Item i) {
        if (FIRE_SWORDS.isEmpty()) {
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.infernoSword);
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.moltenSword);
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.flamingFury);
        }
        return i != null && FIRE_SWORDS.contains(i);
    }

    /** Глаз открыт: заряд выстрела или замах рывка. */
    static boolean open(State s) {
        return s.charge > 0 || s.wind > 0;
    }

    /** Рёв перехода: неуязвим. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityTheWatcher) || e.entityLiving.worldObj.isRemote) return;
        State s = STATES.get(e.entityLiving);
        if (s != null && s.roar > 0 && e.source != DamageSource.outOfWorld) e.setCanceled(true);
    }

    /** Адское оружие против Смотрителя (только в логове), открытый глаз, перегрев; урон взрывом по бойцам - с потолком. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        if (e.entityLiving instanceof EntityPlayer && e.source.isExplosion() && inVault(e.entityLiving)) {
            e.ammount = Math.min(e.ammount, BLAST_CAP);
            return;
        }
        if (!(e.entityLiving instanceof EntityTheWatcher)) return;
        EntityTheWatcher w = (EntityTheWatcher) e.entityLiving;
        if (!inVault(w)) return;
        State s = state(w);
        WorldServer ws = (WorldServer) w.worldObj;
        double ex = w.posX, ey = w.posY + w.height / 2, ez = w.posZ;
        float mult = 1F;
        boolean hot = s.heat > 0, wasOpen = open(s); // перегрев и открытый глаз от этого же удара - со следующего
        net.minecraft.item.Item melee = com.divinerpgextended.entity.ThroneCombat.melee(e.source);
        if (NetherArsenal.ghastCannon(e.source)) {
            mult = NETHER_MULT;
            if (wasOpen) { // сбил заряд или замах: оглушён и перегрет
                s.charge = 0;
                s.wind = 0;
                s.locked = false;
                s.stun = STUN;
                s.heat = Math.max(s.heat, HEAT);
                s.cooldown = Math.max(s.cooldown, COOLDOWN);
                if (s.phase == 2) afterDash(s, w.getHealth() <= w.getMaxHealth() * LOW);
                ws.func_147487_a("largeexplode", ex, ey, ez, 1, 0, 0, 0, 0);
                w.worldObj.playSoundAtEntity(w, "random.break", 2.0F, 0.5F);
            }
        } else if (NetherArsenal.maelstrom(e.source)) mult = NETHER_MULT;
        else if (fireSword(melee) || com.divinerpgextended.entity.ThroneCombat.isArrow(e.source, "infernoArrow")) {
            if (++s.warmth >= WARMTH) {
                s.warmth = 0;
                s.heat = Math.max(s.heat, HEAT);
                ws.func_147487_a("flame", ex, ey, ez, 40, 1.5, 1.5, 1.5, 0.05);
                w.worldObj.playSoundAtEntity(w, "fire.ignite", 2.0F, 0.6F);
            }
        } else if (com.divinerpgextended.entity.ThroneCombat.isArrow(e.source, "bluefireArrow")) {
            s.blind = BLIND;
            s.charge = s.wind = 0;
            ws.func_147487_a("magicCrit", ex, ey, ez, 30, 1.5, 1.5, 1.5, 0.1);
        }
        if (melee != null && com.divinerpgextended.arcana.WeaponOrigin.of(new net.minecraft.item.ItemStack(melee)) == com.divinerpgextended.arcana.WeaponOrigin.NETHER)
            mult = Math.max(mult, NETHER_MULT);
        if (wasOpen && e.source.isProjectile()) mult *= OPEN_MULT;
        if (hot) mult *= HEAT_MULT;
        e.ammount *= mult;
    }

    /** Взрывы в логове: блоки целы, предметы и опыт на земле не страдают, Смотритель и его глаза - тоже. */
    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate e) {
        World w = e.world;
        if (w.isRemote || vault(w, e.explosion.explosionX, e.explosion.explosionY, e.explosion.explosionZ) == null) return;
        for (Iterator<Entity> it = e.getAffectedEntities().iterator(); it.hasNext();) {
            Entity en = it.next();
            if (en instanceof EntityItem || en instanceof EntityXPOrb || en instanceof EntityTheWatcher || en instanceof EntityWatcherEye) it.remove();
        }
        e.getAffectedBlocks().clear();
    }

    // ---------------------------------------------------------------- старые логова

    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 100 != 0
            || e.world.provider.dimensionId != WorldGenOuterLairs.dim(WorldGenOuterLairs.WATCHER)) return;
        tidy(e.world);
    }

    /** Игрок в логове старого вида, а Смотрителя там нет - перестроить зал в новый. */
    public static int tidy(World world) {
        int changed = 0;
        for (Object o : world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            int[] c = vault(world, p.posX, p.posY + 1, p.posZ);
            if (c == null || TIDY.contains(key(c)) || bossIn(world, c)) continue;
            changed += upgrade(world, c);
        }
        return changed;
    }

    /** Перестроить логово, если оно старого вида (бойцам - карманы); запомнить, что оно уже новое. */
    public static int upgrade(World world, int[] c) {
        TIDY.add(key(c));
        if (!WatcherVault.old(world, c[0], c[2])) return 0;
        List<EntityPlayer> ps = new ArrayList<EntityPlayer>();
        for (Object q : world.playerEntities) {
            EntityPlayer p = (EntityPlayer) q;
            if (vault(world, p.posX, p.posY + 1, p.posZ) != null) ps.add(p);
        }
        return new WatcherVault(c[0], c[2]).rebuild(world, c[0], c[2], ps);
    }

    static boolean bossIn(World world, int[] c) {
        for (Object en : world.loadedEntityList)
            if (en instanceof EntityTheWatcher && !((Entity) en).isDead) {
                int[] bc = vault(world, ((Entity) en).posX, ((Entity) en).posY + 2, ((Entity) en).posZ);
                if (bc != null && key(bc) == key(c)) return true;
            }
        return false;
    }
}
