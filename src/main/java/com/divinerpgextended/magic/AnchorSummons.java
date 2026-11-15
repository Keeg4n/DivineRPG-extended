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
 * Summons on arcana: the Slime Sword calls a slime, the Crab Anchor (from Carcinus) - a crab, the Cyclops Wand - an eye, the Liopleurodon
 * Anchor (from the Liopleurodon) - a shark; right click - summon only (the anchors have no throw and the wand has no shot, the sword hits as before).
 * Like the ghost from the ghost bone ({@link ArcaneSummons}): they stay while the owner pays in arcana ({@link Kind#upkeep} per second for
 * each), hit what the owner hits (or whoever attacked the owner), and on their own - the nearest enemy within their aggro radius
 * ({@link Kind#aggro}); only other players hurt them, where PvP is allowed; they are not saved; no more than {@link #cap} of each kind at once (a full Korma set or
 * the R8 arcana Crown - one more), the extra one is the oldest. Summon ladder: slime - crab - eye - shark - ghost.
 * <p>
 * Since 0.99.120: one owner's summons are of one kind only (call a crab - the rest, ghosts too, leave); right click on your own
 * summon or a hit with the whistle - dismiss it; the whistle calls them to the owner; ground ones (slime, crab) push each other and when
 * carried to the owner stand in a ring.
 * <p>
 * Since 0.99.121 there is one payment for all of the owner's summons once a second ({@link #pay}): {@link #UPKEEP} for each, as an ordinary arcana
 * spend - with a recovery pause (while summons live, arcana does not regenerate on its own), with the Korma and Composure discount.
 * Without upgrades two summons on 200 arcana live 10 seconds; longer with Korma (cheaper, bigger supply), Crystal of Clarity (shorter
 * pause), Vatticus's tasks (bigger supply), the R8 arcana Crown (regeneration twice as fast), arcana potions (Clarity: no pause).
 */
public class AnchorSummons {

    /** Summon price in arcana per second - the same for all (the ghost too). */
    public static final float UPKEEP = 10F;

    public enum Kind {
        /** Slime of the Slime Sword: hops along the ground, the shortest aggro. */
        SLIME(UPKEEP, 2, 3F, 6),
        /** Runs on the ground. */
        CRAB(UPKEEP, 2, 4F, 8),
        /** Eye of the Cyclops Wand: a companion overhead, hits weakest of all but knocks the enemy back. */
        EYE(UPKEEP, 2, 2F, 10),
        /** Flies, like the ghost. */
        SHARK(UPKEEP, 2, 8F, 12);

        public final float upkeep, damage;
        public final int cap;
        /** Aggro radius (blocks from the owner): it rushes at enemies within it on its own; it does not chase the owner's target farther away. */
        public final double aggro;

        Kind(float upkeep, int cap, float damage, double aggro) {
            this.upkeep = upkeep;
            this.cap = cap;
            this.damage = damage;
            this.aggro = aggro;
        }
    }

    /** An owner's arcana summon: what kind it is. */
    public interface Summon {

        Kind kind();

        /** The owner or null (on the client - always null). */
        EntityPlayer owner();
    }

    /** Summoner: a full Korma set or the R8 arcana Crown - one more of each summon. */
    public static boolean summoner(EntityPlayer p) {
        return p != null && (ArcanaRules.korma(p) || com.divinerpgextended.item.ArmorData.equippedCrown(p) == com.divinerpgextended.item.ArmorCrown.ARCANA);
    }

    /** How many summons of a kind the player holds. */
    public static int cap(EntityPlayer p, Kind k) {
        return k.cap + (summoner(p) ? 1 : 0);
    }

    /** Dismiss a summon (owner right click, whistle): sparks and sound. */
    public static void dismiss(Entity e) {
        if (e.worldObj instanceof net.minecraft.world.WorldServer)
            ((net.minecraft.world.WorldServer) e.worldObj).func_147487_a("witchMagic", e.posX, e.posY + e.height / 2, e.posZ, 16, e.width * 0.4, e.height * 0.4, e.width * 0.4, 0.05);
        e.worldObj.playSoundAtEntity(e, "random.pop", 0.5F, 0.6F);
        e.setDead();
    }

    /** Where a summon stands when carried to the owner: in a ring, so they do not stand inside each other. */
    static double[] near(EntityPlayer o, int index) {
        double a = index * 2.4 + 0.6;
        return new double[] { o.posX + Math.cos(a) * 1.3, o.posY, o.posZ + Math.sin(a) * 1.3 };
    }

    /** Knockback of the eye's hit (speed, blocks per tick): for bosses - {@link #EYE_PUSH_BOSS} of it. */
    public static final double EYE_PUSH = 0.9, EYE_PUSH_BOSS = 0.25;

    public static final int MEMORY = 200, LEASH = 48;
    static final Map<UUID, List<EntityLiving>> LIVE = new HashMap<UUID, List<EntityLiving>>();
    static final Map<UUID, Object[]> TARGET = new HashMap<UUID, Object[]>();

    public static Kind kind(Item i) {
        if (i == null) return null;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.slimeSword) return Kind.SLIME;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.cyclopsianStaff) return Kind.EYE;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.crabAnchor) return Kind.CRAB;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.liopleurodonAnchor) return Kind.SHARK;
        return null;
    }

    static Kind kind(EntityLiving e) {
        return e instanceof Summon ? ((Summon) e).kind() : Kind.SHARK;
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
        for (EntityLiving e : new ArrayList<EntityLiving>(of(p))) dismiss(e);
        LIVE.remove(p.getUniqueID());
    }

    /** Summon at the owner. */
    public static EntityLiving summon(EntityPlayer p, Kind k) {
        List<EntityLiving> l = of(p);
        // one owner's summons are of one kind: other kinds and ghosts leave
        for (EntityLiving o : new ArrayList<EntityLiving>(l)) if (kind(o) != k) {
            dismiss(o);
            l.remove(o);
        }
        ArcaneSummons.dismissAll(p);
        int cap = cap(p, k);
        while (count(p, k) >= cap) for (EntityLiving e : l) if (kind(e) == k) {
            e.setDead();
            l.remove(e);
            break;
        }
        EntityLiving e;
        switch (k) {
            case SLIME:
                e = new EntitySummonSlime(p.worldObj, p);
                break;
            case CRAB:
                e = new EntitySummonCrab(p.worldObj, p);
                break;
            case EYE:
                e = new EntitySummonEye(p.worldObj, p);
                break;
            default:
                e = new EntitySummonShark(p.worldObj, p);
        }
        boolean flies = k == Kind.SHARK || k == Kind.EYE;
        e.setLocationAndAngles(p.posX + p.worldObj.rand.nextGaussian(), p.posY + (flies ? 1.5 : 0.2), p.posZ + p.worldObj.rand.nextGaussian(), p.rotationYaw, 0);
        p.worldObj.spawnEntityInWorld(e);
        l.add(e);
        String[] snd = { "mob.slime.small", "mob.silverfish.say", "random.orb", "random.splash" };
        float[] pitch = { 0.8F, 0.6F, 0.5F, 1.2F };
        p.worldObj.playSoundAtEntity(e, snd[k.ordinal()], 0.6F, pitch[k.ordinal()]);
        return e;
    }

    /** The target of the owner's summons (or null). */
    static Entity target(EntityPlayer p) {
        Object[] t = TARGET.get(p.getUniqueID());
        if (t == null) return null;
        Entity e = (Entity) t[0];
        if (e.isDead || !(e instanceof EntityLivingBase) || ((EntityLivingBase) e).getHealth() <= 0 || e.worldObj != p.worldObj
            || p.worldObj.getTotalWorldTime() - (Long) t[1] > MEMORY || e.getDistanceSqToEntity(p) > 24 * 24
            || e instanceof EntityPlayer && com.divinerpgextended.perk.Protection.spare(p, e)) { // the player went into a no-PvP claim
            TARGET.remove(p.getUniqueID());
            return null;
        }
        return e;
    }

    /** A summon or ghost of this owner. */
    public static boolean own(EntityPlayer p, Entity t) {
        return t instanceof Summon && ((Summon) t).owner() == p || t instanceof EntityArcaneWraith && ((EntityArcaneWraith) t).owner() == p;
    }

    /**
     * A hit on a summon that goes through: from another player (or their summons), where PvP is allowed. Nothing else hurts the summon -
     * mobs, fire, falling and the owner himself.
     */
    public static boolean rivalHit(EntityPlayer owner, net.minecraft.util.DamageSource s) {
        Entity by = s.getEntity();
        return by instanceof EntityPlayer && owner != null && by != owner && !com.divinerpgextended.perk.Protection.spare(by, owner);
    }

    /**
     * The summon's target: the owner's target, if it is within the aggro radius r of the owner, otherwise its own enemy in that radius (searched once every
     * {@link Aim#SCAN} ticks). Held by one summon - each has its own.
     */
    public static final class Aim {

        public static final int SCAN = 10;
        private EntityLivingBase auto;
        private int wait;

        public Entity pick(EntityPlayer o, Entity marked, double r) {
            if (marked != null && marked.getDistanceSqToEntity(o) <= r * r) return marked;
            if (auto != null && (!auto.isEntityAlive() || auto.worldObj != o.worldObj || auto.getDistanceSqToEntity(o) > r * r)) auto = null;
            if (auto == null && --wait <= 0) {
                wait = SCAN;
                auto = foe(o, r);
            }
            return auto;
        }
    }

    /**
     * The enemy nearest to the owner within radius r that the owner sees: monsters, except summons, tamed ones, the dummy and
     * neutral ones, while they are not angry (zombie pigmen, endermen - otherwise a summon would raise the whole pack against the owner).
     */
    public static EntityLivingBase foe(EntityPlayer o, double r) {
        EntityLivingBase best = null;
        double bd = r * r;
        for (Object x : o.worldObj.getEntitiesWithinAABB(EntityLiving.class, o.boundingBox.expand(r, r / 2 + 2, r))) {
            EntityLiving m = (EntityLiving) x;
            if (!m.isEntityAlive() || !(m instanceof net.minecraft.entity.monster.IMob) || m instanceof Summon || m instanceof EntityArcaneWraith
                || m instanceof com.divinerpgextended.dummy.EntityTrainingDummy) continue;
            if (m instanceof net.minecraft.entity.passive.EntityTameable && ((net.minecraft.entity.passive.EntityTameable) m).isTamed()) continue;
            if (m instanceof net.minecraft.entity.monster.EntityPigZombie && ((net.minecraft.entity.monster.EntityPigZombie) m).getEntityToAttack() == null) continue;
            if (m instanceof net.minecraft.entity.monster.EntityEnderman && !((net.minecraft.entity.monster.EntityEnderman) m).isScreaming()) continue;
            double d = m.getDistanceSqToEntity(o);
            if (d < bd && o.canEntityBeSeen(m)) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    public static void mark(EntityPlayer p, Entity t) {
        if (t == p || own(p, t)) return; // other people's summons - allowed (PvP)
        TARGET.put(p.getUniqueID(), new Object[] { t, p.worldObj.getTotalWorldTime() });
    }

    /**
     * Payment for a second of life of all the owner's summons at once ({@link #UPKEEP} for each) - an ordinary arcana spend: includes the recovery
     * pause, with the Korma and Composure discount. One spend for all, otherwise each would reset the pause anew. Not enough -
     * false (nothing is spent).
     */
    public static boolean pay(EntityPlayer o, int count) {
        if (o.capabilities.isCreativeMode || count <= 0) return true;
        ArcanaHelper h = ArcanaHelper.getProperties(o);
        return h != null && ArcanaRules.useBar(h, UPKEEP * count, "Summon");
    }

    /** Summon's hit: the kind's damage, grows with the stage (like the ghost's). */
    static float damage(EntityPlayer o, Kind k) {
        return k.damage * ArcanaRules.stage(o);
    }

    /** Tooltip of the summoning anchors. */
    @SubscribeEvent
    public void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e) {
        Kind k = e.itemStack == null ? null : kind(e.itemStack.getItem());
        if (k == null) return;
        e.toolTip.add(net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE + net.minecraft.util.StatCollector.translateToLocalFormatted("dre.tooltip.summon_" + k.name().toLowerCase(),
            (int) k.upkeep, cap(e.entityPlayer, k)));
        e.toolTip.add(net.minecraft.util.EnumChatFormatting.GRAY + net.minecraft.util.StatCollector.translateToLocalFormatted("dre.tooltip.summon_stats",
            String.format("%.0f", damage(e.entityPlayer, k)), (int) k.aggro));
    }

    /**
     * Right click with a summoning anchor: summon instead of a throw. The sword and wand summon only by a click in the air (a click on a block also arrives
     * as in the air if the block did nothing): otherwise a chest would open together with the summon.
     */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getHeldItem();
        Kind k = held == null ? null : kind(held.getItem());
        if (k == null) return;
        if ((k == Kind.SLIME || k == Kind.EYE) && e.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;
        if (e.useItem == Event.Result.DENY) return; // the item is already forbidden earlier (Vethea nightmare, claims)
        e.useItem = Event.Result.DENY;
        if (p.worldObj.isRemote) return;
        long now = p.worldObj.getTotalWorldTime();
        String key = "dreAnchorSummon" + (k == Kind.CRAB ? "" : k.name()); // each kind has its own: slime and eye in a row - both
        if (p.getEntityData().hasKey(key) && now - p.getEntityData().getLong(key) < 10 && now >= p.getEntityData().getLong(key))
            return; // a double packet from one click
        p.getEntityData().setLong(key, now);
        summon(p, k);
    }

    /** The owner hits - the summons go to the same target; the owner is hit - they go at the attacker. */
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

    /** The owner has been targeted - the summons go at this enemy (if the owner is not hitting anyone himself). */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.target;
        if (LIVE.containsKey(p.getUniqueID()) && target(p) == null) mark(p, e.entityLiving);
    }
}
