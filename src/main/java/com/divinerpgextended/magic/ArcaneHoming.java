package com.divinerpgextended.magic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.divinerpgextended.perk.Protection;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Homing of the player's arcane projectiles.
 * <ul>
 * <li>Glowbug and Merik's Array in Divine fly to the nearest creature (any, even a cow) and aim at its feet - they went into the
 * ground. Now the target is whoever the player is looking at, otherwise the nearest enemy ({@link AnchorSummons#foe}), and they fly to the middle of the
 * body.</li>
 * <li>Stars of the star staffs fall at the point under the aim and miss a walking mob: they slide toward the nearest enemy within
 * {@link #STAR_R} blocks of the landing point.</li>
 * <li>Ice Serenade: hostile mobs near the impact point freeze at once ({@link com.divinerpgextended.perk.FrostBite#freezeNow}).</li>
 * <li>LaVecor Grenade: in Divine only an explosion with no damage to nearby creatures; now also an arcane hit {@link #GRENADE_DMG} within
 * {@link #GRENADE_R} blocks.</li>
 * </ul>
 */
public class ArcaneHoming {

    public static final double SEEK_R = 32, STAR_R = 5, GRENADE_R = 3;
    public static final float GRENADE_DMG = 12F;
    static final int SEEK = 0, STAR = 1, GRENADE = 2, ICE = 3;
    public static final double ICE_R = 3.5;

    /** {projectile, owner, target, kind}. */
    private static final List<Object[]> LIVE = new ArrayList<Object[]>();
    private static java.lang.reflect.Field seekTarget;

    static void track(EntityThrowable t, EntityPlayer owner, Entity target, int kind) {
        LIVE.add(new Object[] { new WeakReference<EntityThrowable>(t), owner, target, kind });
    }

    /** Divine's homing: the target is whoever the player sees, otherwise the nearest enemy. preset - already chosen (an Arcana boss) or null. */
    public static Entity seeker(EntityThrowable t, EntityPlayer p, Entity preset) {
        Entity aim = preset != null ? preset : lookAt(p);
        if (aim == null) aim = AnchorSummons.foe(p, SEEK_R);
        setDivineTarget(t, aim);
        track(t, p, aim, SEEK);
        return aim;
    }

    static void star(EntityThrowable t, EntityPlayer p) {
        track(t, p, null, STAR);
    }

    static void grenade(EntityThrowable t, EntityPlayer p) {
        track(t, p, null, GRENADE);
    }

    static void ice(EntityThrowable t, EntityPlayer p) {
        track(t, p, null, ICE);
    }

    /** The creature under the player's aim (within SEEK_R blocks, not through walls) or null. */
    static EntityLivingBase lookAt(EntityPlayer p) {
        Vec3 eye = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ), look = p.getLookVec();
        Vec3 far = eye.addVector(look.xCoord * SEEK_R, look.yCoord * SEEK_R, look.zCoord * SEEK_R);
        MovingObjectPosition blk = p.worldObj.rayTraceBlocks(Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
            Vec3.createVectorHelper(far.xCoord, far.yCoord, far.zCoord));
        double reach = blk != null && blk.hitVec != null ? blk.hitVec.distanceTo(eye) : SEEK_R;
        EntityLivingBase best = null;
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p,
            p.boundingBox.addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach).expand(1, 1, 1))) {
            if (!(o instanceof EntityLivingBase) || !((Entity) o).canBeCollidedWith() || AnchorSummons.own(p, (Entity) o)) continue;
            EntityLivingBase en = (EntityLivingBase) o;
            MovingObjectPosition hit = en.boundingBox.expand(0.5, 0.5, 0.5).calculateIntercept(eye, far);
            if (hit == null) continue;
            double d = hit.hitVec.distanceTo(eye);
            if (d < reach) {
                reach = d;
                best = en;
            }
        }
        return best;
    }

    static void setDivineTarget(EntityThrowable t, Entity target) {
        if (target == null || !(t instanceof net.divinerpg.entities.base.EntityHeatSeekingProjectile)) return;
        try {
            if (seekTarget == null) seekTarget = cpw.mods.fml.relauncher.ReflectionHelper.findField(net.divinerpg.entities.base.EntityHeatSeekingProjectile.class, "target");
            seekTarget.set(t, target);
        } catch (Throwable ignored) {}
    }

    private static boolean alive(Entity e) {
        return e != null && !e.isDead && (!(e instanceof EntityLivingBase) || ((EntityLivingBase) e).getHealth() > 0);
    }

    /** Another player is touched only where PvP is allowed; one's own summons - never. */
    private static boolean mayHit(EntityPlayer owner, Entity v) {
        return v != owner && !AnchorSummons.own(owner, v) && !(v instanceof EntityPlayer && Protection.spare(owner, v));
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || LIVE.isEmpty()) return;
        for (Iterator<Object[]> it = LIVE.iterator(); it.hasNext();) {
            Object[] h = it.next();
            EntityThrowable t = ((WeakReference<EntityThrowable>) h[0]).get();
            EntityPlayer owner = (EntityPlayer) h[1];
            int kind = (Integer) h[3];
            if (t == null) {
                it.remove();
                continue;
            }
            if (t.worldObj != e.world) continue;
            if (t.isDead) {
                if (kind == GRENADE) burst(t, owner);
                if (kind == ICE) chill(t, owner);
                it.remove();
                continue;
            }
            if (kind == GRENADE || kind == ICE) continue;
            Entity target = (Entity) h[2];
            if (!alive(target) || target.worldObj != t.worldObj) {
                target = kind == STAR ? starFoe(t, owner) : AnchorSummons.foe(owner, SEEK_R);
                h[2] = target;
                if (kind == SEEK) setDivineTarget(t, target);
            }
            if (target == null) continue;
            double dx = target.posX - t.posX, dy = target.boundingBox.minY + target.height * 0.5 - t.posY, dz = target.posZ - t.posZ;
            if (kind == SEEK) {
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double v = Math.max(0.4, Math.sqrt(t.motionX * t.motionX + t.motionY * t.motionY + t.motionZ * t.motionZ));
                if (len < 0.01) continue;
                t.motionX = dx / len * v;
                t.motionY = dy / len * v;
                t.motionZ = dz / len * v;
            } else { // star: falls as it fell, and sideways - toward the target
                double hl = Math.sqrt(dx * dx + dz * dz);
                if (hl < 0.05) continue;
                double side = Math.min(0.6, hl * 0.25);
                t.motionX = dx / hl * side;
                t.motionZ = dz / hl * side;
            }
            t.velocityChanged = true;
        }
    }

    /** The nearest enemy near where the star is falling (under it, within STAR_R blocks horizontally). */
    static EntityLivingBase starFoe(EntityThrowable t, EntityPlayer owner) {
        EntityLivingBase best = null;
        double bd = STAR_R * STAR_R;
        for (Object o : t.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, t.boundingBox.expand(STAR_R, 16, STAR_R).offset(0, -12, 0))) {
            EntityLivingBase m = (EntityLivingBase) o;
            if (!m.isEntityAlive() || !(m instanceof net.minecraft.entity.monster.IMob) || !mayHit(owner, m) || m.posY > t.posY) continue;
            double dx = m.posX - t.posX, dz = m.posZ - t.posZ, d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** Ice Serenade hit: hostile ones around freeze at once. */
    static void chill(EntityThrowable t, EntityPlayer owner) {
        long now = t.worldObj.getTotalWorldTime();
        for (Object o : t.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, t.boundingBox.expand(ICE_R, ICE_R, ICE_R))) {
            EntityLivingBase v = (EntityLivingBase) o;
            if (v.isEntityAlive() && v instanceof net.minecraft.entity.monster.IMob && (owner == null || mayHit(owner, v)))
                com.divinerpgextended.perk.FrostBite.freezeNow(v, now);
        }
    }

    /** LaVecor Grenade exploded: an arcane hit around. */
    static void burst(EntityThrowable t, EntityPlayer owner) {
        if (owner == null || owner.worldObj != t.worldObj) return;
        float dmg = GRENADE_DMG * ArcanaRules.stage(owner) * ArcanaCombat.crown(owner);
        for (Object o : t.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, t.boundingBox.expand(GRENADE_R, GRENADE_R, GRENADE_R))) {
            EntityLivingBase v = (EntityLivingBase) o;
            if (!v.isEntityAlive() || !mayHit(owner, v) || v.getDistanceToEntity(t) > GRENADE_R + v.width / 2) continue;
            if (!(v instanceof net.minecraft.entity.monster.IMob) && !(v instanceof EntityPlayer) && Protection.spare(owner, v)) continue; // we do not touch peaceful ones in a claim
            v.hurtResistantTime = 0; // the grenade explosion has just hit - the arcane hit is separate
            v.attackEntityFrom(ArcanaCombat.arcaneSource(owner, v), dmg);
        }
    }
}
