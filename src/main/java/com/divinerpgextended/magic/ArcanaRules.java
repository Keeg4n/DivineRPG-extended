package com.divinerpgextended.magic;

import java.util.HashMap;
import java.util.Map;

import net.divinerpg.utils.events.ArcanaHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.trinket.Trinket;

/**
 * The addon's arcana rules. All arcana spends from Divine's code arrive here (via
 * {@link com.divinerpgextended.asm.ArcanaUseTransformer}) with the name of the calling class:
 * <ul>
 * <li>weapon price and cooldown - from the {@link #SPECS} table (Divine weapons without their own pause fired every tick);</li>
 * <li>not enough arcana - nothing is spent and the recovery delay is not reset (in Divine it was reset);</li>
 * <li>delay after a spend {@link #DELAY} ticks (Crystal of Clarity - {@link #DELAY_CLARITY}, under a potion - none);</li>
 * <li>the Angelic set spends {@link #ANGELIC} of Divine's price on flight;</li>
 * <li>full Korma set: shots 20% cheaper, +50 to the supply ({@link ArcanaHooks#max});</li>
 * <li>arcana potions give not instant arcana but the "Arcana Regeneration" effect ({@link #buff}).</li>
 * </ul>
 * Arcane damage grows with the player's stage ({@link #stage}): from x1.0 on entering Arcana to x2.2 after Lady Luna.
 */
public final class ArcanaRules {

    public static final int DELAY = 40, DELAY_CLARITY = 15;
    /** Share of the Angelic set's flight price: it was 20 arcana/s, now 6. */
    public static final float ANGELIC = 0.3F, KORMA_DISCOUNT = 0.8F, KORMA_MAX = 50.0F;
    /** Potion effect: duration (ticks) and the recovery multiplier for weak and strong. */
    public static final int BUFF_TICKS = 45 * 20, BUFF_TICKS_STRONG = 60 * 20;

    /** Price (less than 0 - as in Divine) and cooldown in ticks. */
    static final class Spec {
        final float cost;
        final int cooldown;

        Spec(float cost, int cooldown) {
            this.cost = cost;
            this.cooldown = cooldown;
        }
    }

    static final Map<String, Spec> SPECS = new HashMap<String, Spec>();
    /** Starlight staff and Starlight are one Divine class: we tell them apart by their price (25 and 5). */
    static final float STARLIGHT_STAFF = 15F, STARLIGHT = 4F;
    public static final float MAGE_STAFF = 8F;
    public static final int MAGE_STAFF_COOLDOWN = 10;

    static {
        SPECS.put("ItemMeteorMash", new Spec(30, 10));
        SPECS.put("ItemCaptainsSparkler", new Spec(8, 4));
        SPECS.put("ItemFirefly", new Spec(4, 6));
        SPECS.put("ItemMeriksMissile", new Spec(30, 10));
        SPECS.put("ItemLaVekor", new Spec(5, 8));
        SPECS.put("ItemArcaniteBlaster", new Spec(30, 20));
        SPECS.put("ItemGeneralsStaff", new Spec(20, 12));
        SPECS.put("ItemGhostbane", new Spec(60, 40));
        SPECS.put("ItemEnderScepter", new Spec(40, 10));
        SPECS.put("ItemDivineAccumulator", new Spec(40, 10));
        SPECS.put("ItemAttractor", new Spec(10, 6));
        SPECS.put("ItemReflector", new Spec(10, 6));
        SPECS.put("ItemStaffStarlight", new Spec(-1, 5));
        SPECS.put(ItemMageStaff.OWNER, new Spec(MAGE_STAFF, MAGE_STAFF_COOLDOWN));
        SPECS.put("EnderBlink", new Spec(25, 8)); // End armor void step: a short cooldown - step after step while running
    }

    private static final Map<String, Long> LAST_USE = new HashMap<String, Long>();
    private static java.lang.reflect.Field playerField;

    private ArcanaRules() {}

    public static EntityPlayer player(ArcanaHelper h) {
        try {
            if (playerField == null) playerField = cpw.mods.fml.relauncher.ReflectionHelper.findField(ArcanaHelper.class, "player");
            return (EntityPlayer) playerField.get(h);
        } catch (Throwable t) {
            return null;
        }
    }

    static String simple(String owner) {
        int i = owner.lastIndexOf('.');
        return i < 0 ? owner : owner.substring(i + 1);
    }

    /** The price of this spend by the addon's rules (before the Korma discount). */
    public static float cost(String owner, float divineCost) {
        String s = simple(owner);
        if ("EventArmorTick".equals(s)) return divineCost * ANGELIC;
        if ("ItemStaffStarlight".equals(s)) return divineCost >= 20 ? STARLIGHT_STAFF : STARLIGHT;
        Spec sp = SPECS.get(s);
        return sp != null && sp.cost >= 0 ? sp.cost : divineCost;
    }

    /** Full Korma set. */
    public static boolean korma(EntityPlayer p) {
        if (p == null) return false;
        Item[] set = { ArcanaItems.kormaBoots, ArcanaItems.kormaLegs, ArcanaItems.kormaBody, ArcanaItems.kormaHelmet };
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || s.getItem() != set[i]) return false;
        }
        return true;
    }

    /** Full Vemos set. */
    public static boolean vemos(EntityPlayer p) {
        if (p == null) return false;
        Item[] set = { ArcanaItems.vemosBoots, ArcanaItems.vemosLegs, ArcanaItems.vemosBody, ArcanaItems.vemosHelmet };
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || s.getItem() != set[i]) return false;
        }
        return true;
    }

    /** Arcana spend from Divine's code (instead of ArcanaHelper.useBar). */
    public static boolean useBar(ArcanaHelper h, float divineCost, String owner) {
        if (h == null) return false;
        EntityPlayer p = player(h);
        String s = simple(owner);
        boolean armor = "EventArmorTick".equals(s);
        float c = cost(owner, divineCost);
        if (!armor && korma(p)) c *= KORMA_DISCOUNT;
        if (!armor) c *= ArcaneEffects.costFactor(p); // Composure
        Spec sp = SPECS.get(s);
        String key = null;
        long now = 0;
        if (sp != null && sp.cooldown > 0 && p != null) {
            key = (p.worldObj.isRemote ? "c" : "s") + p.getUniqueID() + s + ("ItemStaffStarlight".equals(s) && divineCost < 20 ? "1" : "");
            now = p.worldObj.getTotalWorldTime();
            Long last = LAST_USE.get(key);
            if (last != null && now - last < sp.cooldown && now >= last) return false;
        }
        if (h.getBarValue() < c) return false; // not enough - we do not touch the recovery delay
        int was = h.regenDelay;
        if (!h.useBar(c)) return false; // Divine sets its own pause of 50 ticks - we replace it below
        h.regenDelay = swordSpend(s) && swordFlow(p) ? was : delay(p);
        if (key != null) {
            if (LAST_USE.size() > 5000) LAST_USE.clear();
            LAST_USE.put(key, now);
        }
        return true;
    }

    /** Arcana sword spend per hit: the saber and blade (per click), the Shadow Sword (per impact), the arcane hit of Storm Sword and the Livicia Sword. */
    static boolean swordSpend(String s) {
        return "ItemArcaniumSaber".equals(s) || "ItemArcaniteBlade".equals(s) || "ItemShadowSword".equals(s) || "ArcaneEdge".equals(s);
    }

    /** Full Korma set or the Arcana Flow perk: Arcana sword hits do not stop arcana recovery. */
    public static boolean swordFlow(EntityPlayer p) {
        return p != null && (korma(p) || com.divinerpgextended.item.ArmorData.hasEquippedPerk(p, com.divinerpgextended.perk.Perk.ARCANA_FLOW));
    }

    /** Recovery delay after a spend. */
    public static int delay(EntityPlayer p) {
        if (p == null || p.worldObj.isRemote) return DELAY;
        if (buffMult(p) > 0) return 0;
        return com.divinerpgextended.accessory.Accessories.wears(p, Trinket.CLARITY_CRYSTAL) ? DELAY_CLARITY : DELAY;
    }

    /** Instant arcana from Divine's code: for potions - the "Arcana Regeneration" effect, the rest as before. */
    public static void forceRegen(ArcanaHelper h, float amount, String owner) {
        if (h == null) return;
        EntityPlayer p = player(h);
        if (p != null && "ItemArcanaPotion".equals(simple(owner))) {
            if (!p.worldObj.isRemote) buff(p, amount >= 200 ? 3 : 2, amount >= 200 ? BUFF_TICKS_STRONG : BUFF_TICKS);
            return;
        }
        h.forceRegen(amount);
    }

    /** The "Clarity" effect (arcana potions): no recovery delay, recovery x mult (2 or 3). */
    public static void buff(EntityPlayer p, int mult, int ticks) {
        ArcaneEffects.apply(p, ArcaneEffect.CLARITY, Math.max(1, mult - 1), ticks);
    }

    /** Recovery multiplier under Clarity or 0 if there is no effect. */
    public static int buffMult(EntityPlayer p) {
        int l = ArcaneEffects.level(p, ArcaneEffect.CLARITY);
        return l == 0 ? 0 : l + 1;
    }

    /** Bosses after entering Arcana: each +8% to the player's arcane damage. */
    static final Boss[] STAGE = { Boss.CADILLION_EDEN, Boss.CADILLION_WILDWOOD, Boss.CADILLION_APALACHIA, Boss.CADILLION_SKYTHERN,
        Boss.TWILIGHT_DEMON, Boss.KAROT, Boss.ETERNAL_ARCHER, Boss.TWINS, Boss.SOUL_FIEND, Boss.VAMACHERON, Boss.DRAMIX,
        Boss.PARASECTA, Boss.WRECK, Boss.RAGLOK, Boss.LADY_LUNA };
    public static final float PER_BOSS = 0.08F;

    public static float stage(EntityPlayer p) {
        if (p == null) return 1.0F;
        int n = 0;
        for (Boss b : STAGE) if (com.divinerpgextended.boss.ProgressSync.has(p, b)) n++; // on the client - by the mask sent over
        return 1.0F + PER_BOSS * n;
    }

    // ---------------- defense by arcana ----------------

    /** The share of incoming arcane damage that arcana takes, and the arcana price per unit of damage. */
    public static float shieldShare(EntityPlayer p) {
        float s = com.divinerpgextended.accessory.Accessories.wears(p, Trinket.ARCANE_WARD) ? 0.75F : 0.6F;
        return vemos(p) ? s + 0.1F : s;
    }

    public static float shieldCost(EntityPlayer p) {
        return com.divinerpgextended.accessory.Accessories.wears(p, Trinket.ARCANE_WARD) ? 1.0F : 2.0F;
    }

    /** Deduct part of the arcane damage from arcana. @return the damage that will reach health */
    public static float absorb(EntityPlayer p, float amount) {
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (h == null || amount <= 0) return amount;
        float per = shieldCost(p), want = amount * shieldShare(p), can = h.getBarValue() / per, took = Math.min(want, can);
        if (took <= 0) return amount;
        if (p instanceof com.divinerpgextended.dummy.DummyGhost) com.divinerpgextended.dummy.DummyGhost.setBar(h, h.getBarValue() - took * per);
        else h.setBarValue(h.getBarValue() - took * per);
        h.regenDelay = Math.max(h.regenDelay, delay(p));
        return amount - took;
    }

    /** For the autotest. */
    public static void resetCooldowns() {
        LAST_USE.clear();
    }
}
