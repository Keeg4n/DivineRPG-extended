package com.divinerpgextended.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Призывные якоря: крабовый якорь (с Карцинуса) зовёт краба, якорь лиоплевродона (с Лиоплевродона) - акулу; ПКМ - только
 * призыв (броска у этих якорей нет). Как призрак кости призрака ({@link ArcaneSummons}): держатся, пока хозяин платит арканой
 * ({@link Kind#upkeep} в секунду за каждого), бьют то, что бьёт хозяин (или того, кто напал на хозяина), не ранятся и не
 * сохраняются; одновременно не больше {@link Kind#cap} каждого вида, лишний - самый старый. Лестница призывов: краб - акула -
 * призрак.
 */
public class AnchorSummons {

    public enum Kind {
        /** Бегает по земле. */
        CRAB(2F, 2, 4F),
        /** Летает, как призрак. */
        SHARK(3F, 2, 8F);

        public final float upkeep, damage;
        public final int cap;

        Kind(float upkeep, int cap, float damage) {
            this.upkeep = upkeep;
            this.cap = cap;
            this.damage = damage;
        }
    }

    public static final int MEMORY = 200, LEASH = 48;
    static final Map<UUID, List<EntityLiving>> LIVE = new HashMap<UUID, List<EntityLiving>>();
    static final Map<UUID, Object[]> TARGET = new HashMap<UUID, Object[]>();

    public static Kind kind(Item i) {
        if (i == null) return null;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.crabAnchor) return Kind.CRAB;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.liopleurodonAnchor) return Kind.SHARK;
        return null;
    }

    static Kind kind(EntityLiving e) {
        return e instanceof EntitySummonCrab ? Kind.CRAB : Kind.SHARK;
    }

    static List<EntityLiving> of(EntityPlayer p) {
        List<EntityLiving> l = LIVE.get(p.getUniqueID());
        if (l == null) LIVE.put(p.getUniqueID(), l = new ArrayList<EntityLiving>());
        for (java.util.Iterator<EntityLiving> it = l.iterator(); it.hasNext();) if (it.next().isDead) it.remove();
        return l;
    }

    public static int count(EntityPlayer p, Kind k) {
        int n = 0;
        for (EntityLiving e : of(p)) if (kind(e) == k) n++;
        return n;
    }

    static int index(EntityLiving e, EntityPlayer o) {
        return o == null ? 0 : Math.max(0, of(o).indexOf(e));
    }

    public static void dismissAll(EntityPlayer p) {
        for (EntityLiving e : new ArrayList<EntityLiving>(of(p))) e.setDead();
        LIVE.remove(p.getUniqueID());
    }

    /** Призвать у хозяина. */
    public static EntityLiving summon(EntityPlayer p, Kind k) {
        List<EntityLiving> l = of(p);
        while (count(p, k) >= k.cap) for (EntityLiving e : l) if (kind(e) == k) {
            e.setDead();
            l.remove(e);
            break;
        }
        EntityLiving e = k == Kind.CRAB ? new EntitySummonCrab(p.worldObj, p) : new EntitySummonShark(p.worldObj, p);
        e.setLocationAndAngles(p.posX + p.worldObj.rand.nextGaussian(), p.posY + (k == Kind.SHARK ? 1.5 : 0.2), p.posZ + p.worldObj.rand.nextGaussian(), p.rotationYaw, 0);
        p.worldObj.spawnEntityInWorld(e);
        l.add(e);
        p.worldObj.playSoundAtEntity(e, k == Kind.CRAB ? "mob.silverfish.say" : "random.splash", 0.6F, k == Kind.CRAB ? 0.6F : 1.2F);
        return e;
    }

    /** Цель призывов хозяина (или null). */
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

    public static void mark(EntityPlayer p, Entity t) {
        if (t instanceof EntitySummonCrab || t instanceof EntitySummonShark || t == p) return;
        TARGET.put(p.getUniqueID(), new Object[] { t, p.worldObj.getTotalWorldTime() });
    }

    /** Плата арканой за секунду жизни; не хватило - false. */
    static boolean upkeep(EntityPlayer o, float cost) {
        if (o.capabilities.isCreativeMode) return true;
        ArcanaHelper h = ArcanaHelper.getProperties(o);
        if (h == null || h.getBarValue() < cost) return false;
        h.setBarValue(h.getBarValue() - cost);
        return true;
    }

    /** Удар призыва: урон вида, растёт со стадией (как у призрака). */
    static float damage(EntityPlayer o, Kind k) {
        return k.damage * ArcanaRules.stage(o);
    }

    /** Подсказка призывных якорей. */
    @SubscribeEvent
    public void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e) {
        Kind k = e.itemStack == null ? null : kind(e.itemStack.getItem());
        if (k == null) return;
        e.toolTip.add(net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE + net.minecraft.util.StatCollector.translateToLocalFormatted("dre.tooltip.summon_" + k.name().toLowerCase(),
            (int) k.upkeep, k.cap));
    }

    /** ПКМ призывным якорем: призыв вместо броска. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getHeldItem();
        Kind k = held == null ? null : kind(held.getItem());
        if (k == null) return;
        e.useItem = Event.Result.DENY;
        if (p.worldObj.isRemote) return;
        long now = p.worldObj.getTotalWorldTime();
        if (p.getEntityData().hasKey("dreAnchorSummon") && now - p.getEntityData().getLong("dreAnchorSummon") < 10 && now >= p.getEntityData().getLong("dreAnchorSummon"))
            return; // двойной пакет от одного клика
        p.getEntityData().setLong("dreAnchorSummon", now);
        summon(p, k);
    }

    /** Хозяин бьёт - призывы туда же. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity a = e.source.getEntity();
        if (a instanceof EntityPlayer && !(e.entityLiving instanceof EntityPlayer) && LIVE.containsKey(a.getUniqueID())) mark((EntityPlayer) a, e.entityLiving);
    }

    /** На хозяина нацелились - призывы на этого врага (если хозяин сам никого не бьёт). */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.target;
        if (LIVE.containsKey(p.getUniqueID()) && target(p) == null) mark(p, e.entityLiving);
    }
}
