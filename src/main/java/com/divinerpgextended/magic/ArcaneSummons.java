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
 * Призыв кости призрака: вместо призрака Divine на 6 с - {@link EntityArcaneWraith}, пока хватает арканы. Не больше
 * {@link #CAP} призраков (с полным сетом Кормы - {@link #CAP_KORMA}), лишний - самый старый - рассеивается. Цель - то, что
 * хозяин бил последние {@link #MEMORY} тиков, иначе тот, кто нацелился на хозяина.
 */
public class ArcaneSummons {

    public static final int CAP = 2, CAP_KORMA = 3, MEMORY = 200;
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
        for (EntityArcaneWraith w : new ArrayList<EntityArcaneWraith>(of(p))) w.setDead();
        LIVE.remove(p.getUniqueID());
    }

    /** Цель призраков хозяина (или null). */
    static Entity target(EntityPlayer p) {
        Object[] t = TARGET.get(p.getUniqueID());
        if (t == null) return null;
        Entity e = (Entity) t[0];
        if (e.isDead || !(e instanceof EntityLivingBase) || ((EntityLivingBase) e).getHealth() <= 0 || e.worldObj != p.worldObj
            || p.worldObj.getTotalWorldTime() - (Long) t[1] > MEMORY || e.getDistanceSqToEntity(p) > 24 * 24) {
            TARGET.remove(p.getUniqueID());
            return null;
        }
        return e;
    }

    /** Забыть цель призраков хозяина (свисток). */
    public static void forget(EntityPlayer p) {
        TARGET.remove(p.getUniqueID());
    }

    public static void mark(EntityPlayer p, Entity t) {
        if (t instanceof EntityArcaneWraith || t == p) return;
        TARGET.put(p.getUniqueID(), new Object[] { t, p.worldObj.getTotalWorldTime() });
    }

    /** Призрак Divine с хозяином-игроком - заменить на призыв аддона (раньше фильтров спавна: это призыв, а не моб). */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || e.entity.getClass() != EntityWraith.class) return;
        EntityPlayer o = owner((EntityWraith) e.entity);
        if (o == null) return;
        e.setCanceled(true);
        summon(o, e.entity.posX, e.entity.posY, e.entity.posZ);
    }

    /** Хозяин призрака Divine: Divine пишет в поле владельца отображаемое имя игрока, ванильный поиск ждёт UUID. */
    static EntityPlayer owner(EntityWraith w) {
        EntityLivingBase o = w.func_70902_q();
        if (o instanceof EntityPlayer) return (EntityPlayer) o;
        String n = w.func_152113_b();
        if (n == null || n.isEmpty()) return null;
        EntityPlayer best = null; // совпавших по имени несколько (фейковые игроки модов) - ближайший
        for (Object x : w.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) x;
            if (!(n.equals(p.getDisplayName()) || n.equals(p.getCommandSenderName()) || n.equals(p.getUniqueID().toString()))) continue;
            if (best == null || p.getDistanceSqToEntity(w) < best.getDistanceSqToEntity(w)) best = p;
        }
        return best;
    }

    public static EntityArcaneWraith summon(EntityPlayer p, double x, double y, double z) {
        List<EntityArcaneWraith> l = of(p);
        int cap = ArcanaRules.korma(p) ? CAP_KORMA : CAP;
        while (l.size() >= cap) l.remove(0).setDead(); // лишний - самый старый
        EntityArcaneWraith w = new EntityArcaneWraith(p.worldObj, p);
        w.setLocationAndAngles(x, y + 1, z, p.rotationYaw, 0);
        p.worldObj.spawnEntityInWorld(w);
        l.add(w);
        p.worldObj.playSoundAtEntity(w, "mob.ghast.moan", 0.4F, 1.6F);
        return w;
    }

    /** Хозяин бьёт - призраки туда же. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity a = e.source.getEntity();
        if (a instanceof EntityPlayer && !(e.entityLiving instanceof EntityPlayer) && LIVE.containsKey(a.getUniqueID())) mark((EntityPlayer) a, e.entityLiving);
    }

    /** На хозяина нацелились - призраки на этого врага (если хозяин сам никого не бьёт). */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.target;
        if (LIVE.containsKey(p.getUniqueID()) && target(p) == null) mark(p, e.entityLiving);
    }
}
