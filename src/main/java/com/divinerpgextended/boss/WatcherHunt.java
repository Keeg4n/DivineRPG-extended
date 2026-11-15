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
 * The Watcher in his lair ({@link WatcherVault}) is an eye that is hit from afar. Instead of his behavior from Divine (via
 * {@link com.divinerpgextended.asm.WatcherTransformer}).
 * <ul>
 * <li>First phase: hangs above the hall and watches the nearest fighter. If he sees one - charges a shot ({@link #CHARGE} ticks, a
 * beam stretches, the aim freezes a third before the shot). While charging, the eye is open: projectiles hit him twice as hard. If the fighter hid
 * behind a column - he releases servant eyes ({@link EntityWatcherEye}): they fly behind the cover and bite at point blank.</li>
 * <li>At half health he roars and spins ({@link #ROAR} ticks, invulnerable), releases a flock of eyes and moves to the second
 * phase - dashes. Windup ({@link #WIND} ticks, eye open), a dash at the fighter; if he hits - damage and knockback, if he crashes into a column - stunned
 * and overheated. After {@link #DASHES} dashes - a breather with one shot. Below 20% - more dashes, shorter windup.</li>
 * <li>An overheated one (after a shot, a stun, a hit against a column) takes double damage.</li>
 * <li>Nether weapons ({@link #onHurt}): a ghast cannon bullet into an open eye (charge or windup) knocks it down and stuns it for
 * {@link #STUN} ticks; the ghast cannon, Maelstrom and Nether melee weapons hit 1.5 times harder; fire swords and
 * Inferno arrows build up heat - {@link #WARMTH} in a row overheat him; a Bluefire arrow blinds for {@link #BLIND} ticks.</li>
 * <li>In the second phase (rams) projectiles deal only {@link #RAM_RANGED} of the damage - you have to hit at point blank; best of all with End swords and
 * Bedrock weapons ({@link #RAM_MELEE}, in both phases).</li>
 * <li>Explosions in the lair do not break blocks, do not touch items and experience on the ground, and explosion damage to a fighter is at most
 * {@link #BLAST_CAP} ({@link #onDetonate}).</li>
 * </ul>
 * A lair of the old kind (with a rubble pile) is rebuilt into the new one when a player enters it and the Watcher is absent ({@link #tidy}).
 */
public class WatcherHunt {

    public static final int CHARGE = 30, COOLDOWN = 45, HEAT = 40, STUN = 30, BLIND = 60, WARMTH = 4, CHASE_RANGE = 48;
    /** Servants: the fighter is not visible for this many ticks - release; pause between releases; no more than this many at once. */
    public static final int HIDE_TICKS = 25, EYES_EVERY = 90, EYES_MAX = 5, EYES_MAX_2 = 6;
    /** Transition to the second phase; dashes: windup, duration, number before the breather, breather, stun from a column. */
    public static final int ROAR = 40, WIND = 18, WIND_LOW = 13, DASH = 14, DASHES = 3, DASHES_LOW = 4, REST = 50, CRASH = 50;
    public static final float PHASE2 = 0.5F, LOW = 0.2F, SHOT_SPEED = 1.3F, DASH_SPEED = 1.25F, DASH_HIT = 10F, BLAST_CAP = 14F;
    /** Multipliers: open eye - for projectiles; overheated - for everything; for Nether weapons. */
    public static final float OPEN_MULT = 2.0F, HEAT_MULT = 2.0F, NETHER_MULT = 1.5F;
    /** Second phase (rams): projectiles - a share of the damage; End swords and Bedrock weapons - a multiplier. */
    public static final float RAM_RANGED = 0.15F, RAM_MELEE = 2.0F;

    /** The weapons best for hitting the Watcher at point blank: End swords (of any color) and Bedrock sword and maul. */
    public static boolean ramBreaker(net.minecraft.item.Item i) {
        return com.divinerpgextended.perk.EnderGear.enderSword(new net.minecraft.item.ItemStack(i)) || i == net.divinerpg.utils.items.VanillaItemsWeapons.bedrockSword
            || i == net.divinerpg.utils.items.VanillaItemsWeapons.bedrockMaul;
    }

    /** Fight state (for the boss's lifetime; no need to save). */
    static final class State {
        int phase = 1, charge, cooldown = 40, heat, stun, blind, warmth, hidden, eyesTimer, roar;
        int wind, dash, dashes, rest, hoverTimer;
        double aimX, aimY, aimZ, hoverX, hoverY, hoverZ, dirX, dirY, dirZ;
        boolean locked;
        final Set<Entity> hitThisDash = new HashSet<Entity>();
        final List<EntityWatcherEye> eyes = new ArrayList<EntityWatcherEye>();
    }

    static final Map<Entity, State> STATES = new WeakHashMap<Entity, State>();
    /** Lairs that are already of the new kind (during this server run): center x and z. */
    static final Set<Long> TIDY = new HashSet<Long>();

    static State state(Entity e) {
        State s = STATES.get(e);
        if (s == null) STATES.put(e, s = new State());
        return s;
    }

    static long key(int[] c) {
        return (long) c[0] << 32 ^ (c[2] & 0xffffffffL);
    }

    /** Center of the Watcher's lair where he currently is (or null - not in a lair). */
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

    // ---------------------------------------------------------------- for tests

    /** Phase: 1, 2 or 0 - the transition roar. */
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

    /** How many fighters the last dash hit. */
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

    /** Straight to the second phase, to the dash windup. */
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

    /** For tests: forget which lairs are already of the new kind. */
    public static void forgetTidy() {
        TIDY.clear();
    }

    // ---------------------------------------------------------------- turn

    /** The Watcher in his lair (on the server): his own flight and shot from Divine are disabled. */
    public static boolean lair(EntityTheWatcher w) {
        return !w.worldObj.isRemote && inVault(w);
    }

    /** The Watcher's turn (instead of his turn from Divine); false - not in a lair, behaves as in Divine. */
    public static boolean act(EntityTheWatcher w) {
        if (w.worldObj.isRemote) return false;
        int[] c = vault(w.worldObj, w.posX, w.posY + w.height / 2, w.posZ);
        if (c == null) return false;
        State s = state(w);
        WorldServer ws = (WorldServer) w.worldObj;
        double ex = w.posX, ey = w.posY + w.height * 0.5, ez = w.posZ;
        if (s.heat > 0 && --s.heat % 4 == 0) ws.func_147487_a("smoke", ex, ey + 1, ez, 6, 1.0, 0.8, 1.0, 0.02);
        if (s.eyesTimer > 0) s.eyesTimer--;
        if (s.stun > 0) { // stunned: hangs, sparks
            s.stun--;
            damp(w, 0.5);
            if (s.stun % 3 == 0) ws.func_147487_a("crit", ex, ey, ez, 8, 1.2, 1.2, 1.2, 0.1);
            return true;
        }
        if (s.phase == 1 && w.getHealth() <= w.getMaxHealth() * PHASE2) { // roar: transition to the second phase
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
        if (s.blind > 0) { // blinded by Bluefire: lost the target, drifts aimlessly
            s.blind--;
            s.charge = s.wind = s.dash = 0;
            if (s.blind % 20 == 0) newHover(w, s, c);
            fly(w, s.hoverX, s.hoverY, s.hoverZ, 0.07);
            return true;
        }
        EntityPlayer target = nearestFighter(w.worldObj, w);
        if (target == null) { // nobody - hangs above the eye
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
        if (s.hidden >= HIDE_TICKS && s.eyesTimer == 0) { // hid - the eyes fly after him
            spawnEye(w, s, max);
            spawnEye(w, s, max);
            s.eyesTimer = s.phase == 1 ? EYES_EVERY : EYES_EVERY + 40;
        }
        if (s.phase == 1) phaseOne(w, ws, s, c, sight, tx, ty, tz, ex, ey, ez);
        else phaseTwo(w, ws, s, c, sight, w.getHealth() <= w.getMaxHealth() * LOW, tx, ty, tz, ex, ey, ez);
        return true;
    }

    /** First phase: hovers above the hall, charges a shot when he sees a fighter. */
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

    /** Second phase: windup - dash - again; after a series a breather with one shot. */
    static void phaseTwo(EntityTheWatcher w, WorldServer ws, State s, int[] c, boolean sight, boolean low, double tx, double ty, double tz, double ex, double ey,
        double ez) {
        if (s.rest > 0) { // breather: hovers, one quick shot
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
            if (w.isCollidedHorizontally && s.dash < DASH - 2) { // crashed into a column or a wall
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
        if (s.wind > 0) { // windup: eye open, shaking, looking for a fighter
            s.wind--;
            damp(w, 0.5);
            w.motionX += (w.getRNG().nextDouble() - 0.5) * 0.12;
            w.motionZ += (w.getRNG().nextDouble() - 0.5) * 0.12;
            if (s.wind % 3 == 0) ws.func_147487_a("reddust", ex, ey, ez, 10, 1.6, 1.6, 1.6, 0);
            if (s.wind == 0) {
                double dx = tx - ex, dy = ty - ey, dz = tz - ez, d = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
                s.dirX = dx / d;
                s.dirY = Math.max(-0.4, Math.min(0.4, dy / d)); // does not dive into the floor or the vault
                s.dirZ = dz / d;
                s.dash = DASH;
                s.hitThisDash.clear();
                ws.playSoundAtEntity(w, "mob.enderdragon.wings", 4.0F, 0.7F);
            }
            return;
        }
        s.wind = low ? WIND_LOW : WIND;
    }

    /** The dash is over (or knocked down): the next windup or a breather after a series. */
    static void afterDash(State s, boolean low) {
        if (++s.dashes >= (low ? DASHES_LOW : DASHES)) {
            s.rest = REST;
            s.hoverTimer = 0;
            s.wind = 0;
        } else s.wind = (low ? WIND_LOW : WIND) + 8;
    }

    /** Slow down horizontally and hang (the downward pull from the game is cancelled every tick). */
    static void damp(EntityTheWatcher w, double k) {
        w.motionX *= k;
        w.motionY = 0;
        w.motionZ *= k;
    }

    static void face(EntityTheWatcher w, double dx, double dy, double dz) {
        w.rotationYaw = w.renderYawOffset = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        w.rotationPitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
    }

    /** A new hover point: above the eye, no further than 8 from the center (does not touch the columns), under the vault. */
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

    /** Release a servant eye from the Watcher (if there are fewer than max). */
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

    /** For tests: release an eye. */
    public static EntityWatcherEye spawnEyeFor(EntityTheWatcher w) {
        return spawnEye(w, state(w), EYES_MAX_2);
    }

    /** The nearest living fighter in the Watcher's lair (for the boss and his servants; creative mode does not count). */
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
     * To a point at speed * 3 blocks per tick, slower near the target. The velocity is set in full every tick: the Watcher in
     * Divine is not a flier, the game pulls him down after the turn and brakes him, and this must not accumulate.
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

    /** A beam from the eye to the aim: turns red while the aim is still tracking, turns white when it froze. */
    static void beam(WorldServer ws, double ex, double ey, double ez, State s) {
        if (s.charge % 2 != 0) return;
        double dx = s.aimX - ex, dy = s.aimY - ey, dz = s.aimZ - ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        String part = s.locked ? "fireworksSpark" : "reddust";
        for (double t = 2.0; t < d; t += 0.8) ws.func_147487_a(part, ex + dx / d * t, ey + dy / d * t, ez + dz / d * t, 1, 0, 0, 0, 0);
    }

    /** A shot with the Watcher's projectile at the aim (compensating for the projectile's drop). */
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

    // ---------------------------------------------------------------- damage

    static final Set<net.minecraft.item.Item> FIRE_SWORDS = new HashSet<net.minecraft.item.Item>();

    static boolean fireSword(net.minecraft.item.Item i) {
        if (FIRE_SWORDS.isEmpty()) {
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.infernoSword);
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.moltenSword);
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.flamingFury);
        }
        return i != null && FIRE_SWORDS.contains(i);
    }

    /** The eye is open: shot charge or dash windup. */
    static boolean open(State s) {
        return s.charge > 0 || s.wind > 0;
    }

    /** Transition roar: invulnerable. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityTheWatcher) || e.entityLiving.worldObj.isRemote) return;
        State s = STATES.get(e.entityLiving);
        if (s != null && s.roar > 0 && e.source != DamageSource.outOfWorld) e.setCanceled(true);
    }

    /** Nether weapons against the Watcher (only in the lair), the open eye, overheating; explosion damage to fighters is capped. */
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
        boolean hot = s.heat > 0, wasOpen = open(s); // overheating and the open eye from this same hit - from the next one
        net.minecraft.item.Item melee = com.divinerpgextended.entity.ThroneCombat.melee(e.source);
        if (NetherArsenal.ghastCannon(e.source)) {
            mult = NETHER_MULT;
            if (wasOpen) { // knocked down the charge or windup: stunned and overheated
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
        if (melee != null && ramBreaker(melee)) mult = Math.max(mult, RAM_MELEE); // the best against him at point blank - End and Bedrock
        if (wasOpen && e.source.isProjectile()) mult *= OPEN_MULT;
        if (s.phase != 1 && e.source.isProjectile()) mult = RAM_RANGED; // on the rams projectiles barely work: only at point blank
        if (hot) mult *= HEAT_MULT;
        e.ammount *= mult;
    }

    /** Explosions in the lair: blocks stay intact, items and experience on the ground are unharmed, and so are the Watcher and his eyes. */
    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate e) {
        World w = e.world;
        if (w.isRemote) return;
        if (vault(w, e.explosion.explosionX, e.explosion.explosionY, e.explosion.explosionZ) == null) {
            if (WorldGenOuterLairs.inside(w, WorldGenOuterLairs.SCORCHERS, e.explosion.explosionX, e.explosion.explosionY, e.explosion.explosionZ)) e.getAffectedBlocks().clear();
            return;
        }
        for (Iterator<Entity> it = e.getAffectedEntities().iterator(); it.hasNext();) {
            Entity en = it.next();
            if (en instanceof EntityItem || en instanceof EntityXPOrb || en instanceof EntityTheWatcher || en instanceof EntityWatcherEye) it.remove();
        }
        e.getAffectedBlocks().clear();
    }

    // ---------------------------------------------------------------- old lairs

    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 100 != 0
            || e.world.provider.dimensionId != WorldGenOuterLairs.dim(WorldGenOuterLairs.WATCHER)
                && e.world.provider.dimensionId != WorldGenOuterLairs.dim(WorldGenOuterLairs.SCORCHERS)) return;
        tidy(e.world);
    }

    /** A player in a lair of the old kind and the Watcher is not there - rebuild the hall into the new one. */
    public static int tidy(World world) {
        int changed = 0;
        for (Object o : world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (WorldGenOuterLairs.enabled(WorldGenOuterLairs.SCORCHERS) && world.provider.dimensionId == WorldGenOuterLairs.dim(WorldGenOuterLairs.SCORCHERS)
                && WorldGenOuterLairs.inside(world, WorldGenOuterLairs.SCORCHERS, p.posX, p.posY + 1, p.posZ)) {
                int[] k = WorldGenOuterLairs.nearest(world.getSeed(), WorldGenOuterLairs.SCORCHERS, p.posX, p.posZ);
                if (k != null && com.divinerpgextended.world.ScorcherPit.isNew(world, k[0], k[2]) && TIDY.add(key(k)))
                    changed += WatcherVault.seal(world, k[0], k[2]); // Scorcher chamber before 0.99.56 - patch the windows
            }
            int[] c = vault(world, p.posX, p.posY + 1, p.posZ);
            if (c == null || TIDY.contains(key(c)) || bossIn(world, c)) continue;
            changed += upgrade(world, c);
        }
        return changed;
    }

    /** Rebuild the lair if it is of the old kind (pockets for fighters); remember that it is already new. */
    public static int upgrade(World world, int[] c) {
        TIDY.add(key(c));
        if (!WatcherVault.old(world, c[0], c[2])) return WatcherVault.seal(world, c[0], c[2]);
        List<EntityPlayer> ps = new ArrayList<EntityPlayer>();
        for (Object q : world.playerEntities) {
            EntityPlayer p = (EntityPlayer) q;
            if (vault(world, p.posX, p.posY + 1, p.posZ) != null) ps.add(p);
        }
        return new WatcherVault(c[0], c[2]).rebuild(world, c[0], c[2], ps) + WatcherVault.seal(world, c[0], c[2]);
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
