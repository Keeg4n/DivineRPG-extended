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
 * Наведение арканных снарядов игрока.
 * <ul>
 * <li>Светляк и Массив Мерика у Divine летят к ближайшему существу (любому, хоть к корове) и целятся ему в ноги - уходили в
 * землю. Теперь цель - тот, на кого смотрит игрок, иначе ближний враг ({@link AnchorSummons#foe}), а лететь - в середину
 * тела.</li>
 * <li>Звёзды звёздных посохов падают в точку под прицелом и мимо идущего моба: сползают к ближнему врагу в
 * {@link #STAR_R} блоках от точки падения.</li>
 * <li>Серенада льда: враждебные мобы рядом с местом попадания сразу вмерзают ({@link com.divinerpgextended.perk.FrostBite#freezeNow}).</li>
 * <li>Граната ЛаВекора: у Divine только взрыв без урона по существам рядом; теперь ещё арканный удар {@link #GRENADE_DMG} в
 * {@link #GRENADE_R} блоках.</li>
 * </ul>
 */
public class ArcaneHoming {

    public static final double SEEK_R = 32, STAR_R = 5, GRENADE_R = 3;
    public static final float GRENADE_DMG = 12F;
    static final int SEEK = 0, STAR = 1, GRENADE = 2, ICE = 3;
    public static final double ICE_R = 3.5;

    /** {снаряд, хозяин, цель, вид}. */
    private static final List<Object[]> LIVE = new ArrayList<Object[]>();
    private static java.lang.reflect.Field seekTarget;

    static void track(EntityThrowable t, EntityPlayer owner, Entity target, int kind) {
        LIVE.add(new Object[] { new WeakReference<EntityThrowable>(t), owner, target, kind });
    }

    /** Самонаводка Divine: цель - кого видит игрок, иначе ближний враг. preset - уже выбранная (босс Арканы) или null. */
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

    /** Существо под прицелом игрока (в SEEK_R блоках, не сквозь стены) или null. */
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

    /** Чужого игрока трогаем только там, где можно PvP; своих призывов - никогда. */
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
            } else { // звезда: падает как падала, а вбок - к цели
                double hl = Math.sqrt(dx * dx + dz * dz);
                if (hl < 0.05) continue;
                double side = Math.min(0.6, hl * 0.25);
                t.motionX = dx / hl * side;
                t.motionZ = dz / hl * side;
            }
            t.velocityChanged = true;
        }
    }

    /** Ближний враг у места, куда падает звезда (под ней, в STAR_R блоках по горизонтали). */
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

    /** Серенада льда попала: враждебные вокруг вмерзают сразу. */
    static void chill(EntityThrowable t, EntityPlayer owner) {
        long now = t.worldObj.getTotalWorldTime();
        for (Object o : t.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, t.boundingBox.expand(ICE_R, ICE_R, ICE_R))) {
            EntityLivingBase v = (EntityLivingBase) o;
            if (v.isEntityAlive() && v instanceof net.minecraft.entity.monster.IMob && (owner == null || mayHit(owner, v)))
                com.divinerpgextended.perk.FrostBite.freezeNow(v, now);
        }
    }

    /** Граната ЛаВекора взорвалась: арканный удар вокруг. */
    static void burst(EntityThrowable t, EntityPlayer owner) {
        if (owner == null || owner.worldObj != t.worldObj) return;
        float dmg = GRENADE_DMG * ArcanaRules.stage(owner) * ArcanaCombat.crown(owner);
        for (Object o : t.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, t.boundingBox.expand(GRENADE_R, GRENADE_R, GRENADE_R))) {
            EntityLivingBase v = (EntityLivingBase) o;
            if (!v.isEntityAlive() || !mayHit(owner, v) || v.getDistanceToEntity(t) > GRENADE_R + v.width / 2) continue;
            if (!(v instanceof net.minecraft.entity.monster.IMob) && !(v instanceof EntityPlayer) && Protection.spare(owner, v)) continue; // мирных в привате не трогаем
            v.hurtResistantTime = 0; // взрыв гранаты только что задел - арканный удар отдельный
            v.attackEntityFrom(ArcanaCombat.arcaneSource(owner, v), dmg);
        }
    }
}
