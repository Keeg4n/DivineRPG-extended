package com.divinerpgextended.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.entities.arcana.EntityWraith;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Ghostbane summon: instead of Divine's 6-second ghost - {@link EntityArcaneWraith}, as long as arcana lasts. No more than
 * {@link #CAP} ghosts (with a full Korma set - {@link #CAP_KORMA}), the extra one - the oldest - disperses. The target is what the
 * owner hit in the last {@link #MEMORY} ticks, otherwise whoever targeted the owner.
 */
public class ArcaneSummons {

    public static final int CAP = 2, CAP_KORMA = 3, MEMORY = 200;
    /** Ghost aggro radius - the largest of the summons (slime 6 - crab 8 - eye 10 - shark 12). */
    public static final double AGGRO = 16;
    static final Map<UUID, List<EntityArcaneWraith>> LIVE = new HashMap<UUID, List<EntityArcaneWraith>>();
    static final Map<UUID, Object[]> TARGET = new HashMap<UUID, Object[]>();

    static List<EntityArcaneWraith> of(EntityPlayer p) {
        List<EntityArcaneWraith> l = LIVE.get(p.getUniqueID());
        if (l == null) LIVE.put(p.getUniqueID(), l = new ArrayList<EntityArcaneWraith>());
        for (java.util.Iterator<EntityArcaneWraith> it = l.iterator(); it.hasNext();) if (it.next().isDead) it.remove();
        return l;
    }

    public static int count(EntityPlayer p) {
        return of(p).size();
    }

    static int index(EntityArcaneWraith w) {
        EntityPlayer o = w.owner();
        return o == null ? 0 : Math.max(0, of(o).indexOf(w));
    }

    public static void dismissAll(EntityPlayer p) {
        for (EntityArcaneWraith w : new ArrayList<EntityArcaneWraith>(of(p))) AnchorSummons.dismiss(w);
        LIVE.remove(p.getUniqueID());
    }

    /** The target of the owner's ghosts (or null). */
    static Entity target(EntityPlayer p) {
        Object[] t = TARGET.get(p.getUniqueID());
        if (t == null) return null;
        Entity e = (Entity) t[0];
        if (e.isDead || !(e instanceof EntityLivingBase) || ((EntityLivingBase) e).getHealth() <= 0 || e.worldObj != p.worldObj
            || p.worldObj.getTotalWorldTime() - (Long) t[1] > MEMORY || e.getDistanceSqToEntity(p) > 24 * 24
            || com.divinerpgextended.perk.Protection.spare(p, e)) { // a player or a peaceful creature in a no-PvP claim
            TARGET.remove(p.getUniqueID());
            return null;
        }
        return e;
    }

    /** Forget the target of the owner's ghosts (whistle). */
    public static void forget(EntityPlayer p) {
        TARGET.remove(p.getUniqueID());
    }

    public static void mark(EntityPlayer p, Entity t) {
        if (t == p || AnchorSummons.own(p, t) || com.divinerpgextended.perk.Protection.spare(p, t)) return; // claims protect players and peaceful creatures
        TARGET.put(p.getUniqueID(), new Object[] { t, p.worldObj.getTotalWorldTime() });
    }

    /** A Divine ghost with a player owner - replace with the addon's summon (before spawn filters: it is a summon, not a mob). */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || e.entity.getClass() != EntityWraith.class) return;
        EntityPlayer o = owner((EntityWraith) e.entity);
        if (o == null) return;
        e.setCanceled(true);
        summon(o, e.entity.posX, e.entity.posY, e.entity.posZ);
    }

    /** Owner of a Divine ghost: Divine writes the player's display name into the owner field, the vanilla lookup expects a UUID. */
    static EntityPlayer owner(EntityWraith w) {
        EntityLivingBase o = w.func_70902_q();
        if (o instanceof EntityPlayer) return (EntityPlayer) o;
        String n = w.func_152113_b();
        if (n == null || n.isEmpty()) return null;
        EntityPlayer best = null; // several matched by name (fake players of mods) - the nearest
        for (Object x : w.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) x;
            if (!(n.equals(p.getDisplayName()) || n.equals(p.getCommandSenderName()) || n.equals(p.getUniqueID().toString()))) continue;
            if (best == null || p.getDistanceSqToEntity(w) < best.getDistanceSqToEntity(w)) best = p;
        }
        return best;
    }

    public static EntityArcaneWraith summon(EntityPlayer p, double x, double y, double z) {
        List<EntityArcaneWraith> l = of(p);
        AnchorSummons.dismissAll(p); // one owner's summons are of one kind
        int cap = AnchorSummons.summoner(p) ? CAP_KORMA : CAP; // Korma or the R8 arcana Crown
        while (l.size() >= cap) l.remove(0).setDead(); // the extra one is the oldest
        EntityArcaneWraith w = new EntityArcaneWraith(p.worldObj, p);
        w.setLocationAndAngles(x, y + 1, z, p.rotationYaw, 0);
        p.worldObj.spawnEntityInWorld(w);
        l.add(w);
        p.worldObj.playSoundAtEntity(w, "mob.ghast.moan", 0.4F, 1.6F);
        return w;
    }

    /** The owner hits - the ghosts go to the same target; the owner is hit - they go at the attacker. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity a = e.source.getEntity();
        if (a instanceof EntityPlayer && LIVE.containsKey(a.getUniqueID())
            && (!(e.entityLiving instanceof EntityPlayer) || !com.divinerpgextended.perk.Protection.spare(a, e.entityLiving))) mark((EntityPlayer) a, e.entityLiving); // a player - where PvP is allowed
        // someone hits the owner - the summons go at the attacker (many mobs use the old AI and never announce their target);
        // a projectile counts by its shooter, a player only where PvP is allowed
        if (e.entityLiving instanceof EntityPlayer && a instanceof net.minecraft.entity.EntityLivingBase && a != e.entityLiving && LIVE.containsKey(e.entityLiving.getUniqueID())
            && !(a instanceof EntityPlayer && com.divinerpgextended.perk.Protection.spare(a, e.entityLiving))) mark((EntityPlayer) e.entityLiving, a);
    }

    /** The owner has been targeted - the ghosts go at this enemy (if the owner is not hitting anyone himself). */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.target;
        if (LIVE.containsKey(p.getUniqueID()) && target(p) == null) mark(p, e.entityLiving);
    }
}
