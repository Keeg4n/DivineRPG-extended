package com.divinerpgextended.vethea;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.accessory.Accessories;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Combat in Vethea: why carry different weapons.
 * <ul>
 * <li>Weaknesses ({@link Config#vetheaWeakness}): special mobs have their own weapon kind - x2 damage to them, x0.25 to the rest (everyone
 * can be hit, but slowly). Finished off with their kind, they drop loot twice.</li>
 * <li>Rhythm (Heiva Belt in the accessory slot): a hit of a different kind than the previous one, +15% damage, up to +60%; 3 s without hits -
 * reset.</li>
 * <li>Helmet roles of the full Vethean set: helmet - melee, mask - ranged, hood - arcana, +5% damage to its
 * group per armor tier (1-5). Any full set dampens explosions: 20% + 10% per tier.</li>
 * <li>In Vethea explosions do not destroy lying items (the inventory dropped on death).</li>
 * <li>The queue of the last bosses holds gear ({@link #gate}): the Wreck is seriously hurt by a Heliosis weapon (boulders from
 * Raglok) and better, Lady Luna - by Arksiane (boulders from the Wreck) and better. One tier lower hits for {@link #GATE_PREV}, the rest
 * for {@link #GATE_LOW} - there is damage, but the boss can hardly be killed this way.</li>
 * </ul>
 */
public class VetheaCombat {

    public static final float WEAK = 2.0F, OTHER = 0.25F, RHYTHM_STEP = 0.15F;
    public static final int RHYTHM_MAX = 4, RHYTHM_RESET = 60;
    static final String LAST = "dreLastKind";

    private static Map<String, EnumSet<WeaponKind>> weak;

    /** Mob weaknesses (null - an ordinary mob). */
    public static synchronized EnumSet<WeaponKind> weakness(Entity e) {
        if (e instanceof com.divinerpgextended.entity.mini.EntitySleepwalker) return ((com.divinerpgextended.entity.mini.EntitySleepwalker) e).weak();
        if (weak == null) {
            weak = new HashMap<String, EnumSet<WeaponKind>>();
            for (String line : Config.vetheaWeakness) {
                String[] kv = line.split("=");
                if (kv.length != 2) continue;
                EnumSet<WeaponKind> set = EnumSet.noneOf(WeaponKind.class);
                for (String k : kv[1].split(",")) {
                    try {
                        set.add(WeaponKind.valueOf(k.trim().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {}
                }
                if (!set.isEmpty()) weak.put(kv[0].trim(), set);
            }
        }
        return weak.get(e.getClass().getSimpleName());
    }

    // ---------------- Rhythm ----------------

    static class Rhythm {

        WeaponKind last;
        int stacks;
        long tick;
    }

    private static final Map<UUID, Rhythm> RHYTHM = new HashMap<UUID, Rhythm>();

    /** Rhythm multiplier for a hit of kind (and remembers the hit). */
    static float rhythm(EntityPlayer p, WeaponKind kind) {
        if (!Accessories.wearsItem(p, VetheaItems.heivaBelt)) return 1;
        Rhythm r = RHYTHM.get(p.getUniqueID());
        if (r == null) RHYTHM.put(p.getUniqueID(), r = new Rhythm());
        long now = p.worldObj.getTotalWorldTime();
        if (now - r.tick > RHYTHM_RESET) {
            r.stacks = 0;
            r.last = null;
        }
        if (r.last != null && r.last != kind) r.stacks = Math.min(RHYTHM_MAX, r.stacks + 1);
        r.last = kind;
        r.tick = now;
        return 1 + RHYTHM_STEP * r.stacks;
    }

    public static int rhythmStacks(EntityPlayer p) {
        Rhythm r = RHYTHM.get(p.getUniqueID());
        return r == null || p.worldObj.getTotalWorldTime() - r.tick > RHYTHM_RESET ? 0 : r.stacks;
    }

    // ---------------- Vethean armor ----------------

    private static final String[] TIERS = { "degraded", "finished", "glistening", "demonized", "tormented" };
    private static final String[] HEADS = { "Helmet", "Mask", "Hood" };
    private static Item[][] sets;

    private static synchronized Item[][] sets() {
        if (sets != null) return sets;
        // [tier][0..2 helmet/mask/hood, 3 chestplate, 4 leggings, 5 boots]
        Item[][] s = new Item[TIERS.length][6];
        for (int t = 0; t < TIERS.length; t++) {
            for (int h = 0; h < 3; h++) s[t][h] = Infusion.vethea(TIERS[t] + HEADS[h]);
            s[t][3] = Infusion.vethea(TIERS[t] + "Body", TIERS[t] + "Chestplate");
            s[t][4] = Infusion.vethea(TIERS[t] + "Legs", TIERS[t] + "Leggings");
            s[t][5] = Infusion.vethea(TIERS[t] + "Boots");
        }
        return sets = s;
    }

    /** Full Vethean set: {tier 1..5, head 0 helmet / 1 mask / 2 hood} or null. */
    public static int[] vetheanSet(EntityPlayer p) {
        Item[][] s = sets();
        ItemStack head = p.getCurrentArmor(3), body = p.getCurrentArmor(2), legs = p.getCurrentArmor(1), boots = p.getCurrentArmor(0);
        if (head == null || body == null || legs == null || boots == null) return null;
        for (int t = 0; t < s.length; t++) {
            if (body.getItem() != s[t][3] || legs.getItem() != s[t][4] || boots.getItem() != s[t][5]) continue;
            for (int h = 0; h < 3; h++) if (s[t][h] != null && head.getItem() == s[t][h]) return new int[] { t + 1, h };
        }
        return null;
    }

    /** The share of an explosion dampened by a full set of tier tier. */
    public static float blastGuard(int tier) {
        return 0.1F + 0.1F * tier;
    }

    // ---------------- Materials and boss queue ----------------

    /** Vethean weapon materials by strength (1..9); the Luna weapon - 10. */
    private static final String[] MATERIALS = { "teaker", "amthirmis", "amthrimis", "darven", "cermile", "pardimal", "quadrotic", "karos", "heliosis", "arksiane" };
    private static final int[] RANK = { 1, 2, 2, 3, 4, 5, 6, 7, 8, 9 };
    public static final int HELIOSIS = 8, ARKSIANE = 9, LUNAR = 10;

    /** Vethean weapon material (0 - not Vethean or without a material). */
    public static int material(ItemStack s) {
        if (s == null || WeaponKind.ofItem(s) == null) return 0;
        String u = String.valueOf(s.getItem().getUnlocalizedName()).toLowerCase();
        if (u.contains("ever")) return LUNAR;
        for (int i = MATERIALS.length - 1; i >= 0; i--) if (u.contains(MATERIALS[i])) return RANK[i];
        return 0;
    }

    /** What material is needed to hurt the boss (0 - any). */
    public static int gate(Entity boss) {
        String n = boss.getClass().getSimpleName();
        return n.equals("EntityWreck") ? HELIOSIS : n.equals("EntityLadyLuna") ? ARKSIANE : 0;
    }

    public static final float GATE_PREV = 0.5F, GATE_LOW = 0.1F;

    /** The wrong material barely hurts the boss: one tier below the needed one - 0.5 per hit, the rest - 0.1. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBossHurt(LivingHurtEvent e) {
        int need = gate(e.entityLiving);
        if (need == 0 || e.entityLiving.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        int m = material(p.getHeldItem());
        if (p.capabilities.isCreativeMode || m >= need) return;
        e.ammount = Math.min(e.ammount, m == need - 1 ? GATE_PREV : GATE_LOW);
        Nightmare.hint(p, need == HELIOSIS ? "gate_heliosis" : "gate_arksiane");
    }

    // ---------------- Events ----------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase target = e.entityLiving;
        if (target.worldObj.isRemote) return;
        // explosion on a player in a full Vethean set
        if (e.source.isExplosion() && target instanceof EntityPlayer) {
            int[] set = vetheanSet((EntityPlayer) target);
            if (set != null) {
                e.ammount *= 1 - blastGuard(set[0]);
                com.divinerpgextended.debug.Debug.note(target, "Vethea set " + set[0] + ": explosion x" + String.format("%.2f", 1 - blastGuard(set[0])));
            }
        }
        if (!(e.source.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        WeaponKind kind = WeaponKind.of(e.source);
        float k = 1;
        EnumSet<WeaponKind> w = weakness(target);
        if (w != null) {
            k *= kind != null && w.contains(kind) ? WEAK : OTHER;
            target.getEntityData().setString(LAST, kind == null ? "" : kind.name());
        }
        if (kind != null) {
            k *= rhythm(p, kind);
            int[] set = vetheanSet(p);
            if (set != null && set[1] == kind.group()) k *= 1 + 0.05F * set[0];
        }
        if (k != 1) {
            e.ammount *= k;
            com.divinerpgextended.debug.Debug.note(p, "Vethea: " + (kind == null ? "foreign weapon/no weapon" : kind.key()) + " x" + String.format("%.2f", k)
                + (w != null ? " (weakness " + w + ")" : "") + ", rhythm " + rhythmStacks(p));
        }
    }

    /** Finished off with their own weapon kind - loot twice. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        EnumSet<WeaponKind> w = weakness(e.entityLiving);
        if (w == null || e.entityLiving.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer)) return;
        String last = e.entityLiving.getEntityData().getString(LAST);
        WeaponKind kind = null;
        try {
            kind = last.isEmpty() ? null : WeaponKind.valueOf(last);
        } catch (IllegalArgumentException ignored) {}
        if (kind == null || !w.contains(kind)) return;
        int n = e.drops.size();
        for (int i = 0; i < n; i++) {
            EntityItem it = e.drops.get(i);
            e.drops.add(new EntityItem(it.worldObj, it.posX, it.posY, it.posZ, it.getEntityItem().copy()));
        }
        com.divinerpgextended.debug.Debug.note(e.source.getEntity(), "Vethea: finished with its own kind (" + kind.key() + ") - loot x2");
    }

    /** In Vethea an explosion does not destroy lying items. */
    @SubscribeEvent
    public void onExplode(ExplosionEvent.Detonate e) {
        if (!Nightmare.inVetheaWorld(e.world)) return;
        @SuppressWarnings("unchecked")
        List<Entity> list = e.getAffectedEntities();
        for (Iterator<Entity> it = list.iterator(); it.hasNext();) if (it.next() instanceof EntityItem) it.remove();
    }
}
