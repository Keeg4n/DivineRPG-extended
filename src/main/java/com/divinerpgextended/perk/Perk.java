package com.divinerpgextended.perk;

import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.item.Item;

import com.divinerpgextended.boss.Boss;

/**
 * Minor perks of the minimal version. The donor is a full Divine armor set (helmet, chestplate, leggings, boots),
 * a perk may have several suitable sets.
 */
public enum Perk {

    FIRE_PROTECTION(Boss.KING_OF_SCORCHERS, new Item[][] {
        { VanillaItemsArmor.infernoHelmet, VanillaItemsArmor.infernoBody, VanillaItemsArmor.infernoLegs,
            VanillaItemsArmor.infernoBoots },
        { VanillaItemsArmor.netheriteHelmet, VanillaItemsArmor.netheriteBody, VanillaItemsArmor.netheriteLegs,
            VanillaItemsArmor.netheriteBoots },
        { VanillaItemsArmor.bedrockHelmet, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs,
            VanillaItemsArmor.bedrockBoots } }),

    EXPLOSION_PROTECTION(Boss.WATCHER, new Item[][] {
        { VanillaItemsArmor.enderHelmet, VanillaItemsArmor.enderBody, VanillaItemsArmor.enderLegs,
            VanillaItemsArmor.enderBoots },
        { VanillaItemsArmor.bedrockHelmet, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs,
            VanillaItemsArmor.bedrockBoots } }),

    WATER_BREATHING(Boss.CARCINUS, new Item[][] {
        { VanillaItemsArmor.krakenHelmet, VanillaItemsArmor.krakenBody, VanillaItemsArmor.krakenLegs,
            VanillaItemsArmor.krakenBoots } }),

    POISON_RESISTANCE(Boss.JUNGLE_DRAMCRYX, new Item[][] {
        { VanillaItemsArmor.jungleHelmet, VanillaItemsArmor.jungleBody, VanillaItemsArmor.jungleLegs,
            VanillaItemsArmor.jungleBoots } }),

    WITHER_PROTECTION(null, new Item[][] {
        { VanillaItemsArmor.witherReaperHelmet, VanillaItemsArmor.witherReaperBody,
            VanillaItemsArmor.witherReaperLegs, VanillaItemsArmor.witherReaperBoots } }),

    /** Digging underwater without slowdown and underwater vision. */
    AQUA_MINER(Boss.CARCINUS, new Item[][] {
        { VanillaItemsArmor.aquastriveHelmet, VanillaItemsArmor.aquastriveBody, VanillaItemsArmor.aquastriveLegs,
            VanillaItemsArmor.aquastriveBoots } }),

    /** Slows hostile mobs around. */
    COLD_AURA(Boss.SNOW_GLACON, new Item[][] {
        { VanillaItemsArmor.frozenHelmet, VanillaItemsArmor.frozenBody, VanillaItemsArmor.frozenLegs,
            VanillaItemsArmor.frozenBoots } }),

    /** Warmth: Iceika cold is harmless (as in the Santa set). */
    WARMTH(Boss.LICH_KING, new Item[][] {
        { IceikaItems.santaCap, IceikaItems.santaTunic, IceikaItems.santaPants, IceikaItems.santaBoots } }),

    /** Satiety (donor - the skeleton set): hunger drains twice as slowly, you do not die of hunger, you can run even when hungry ({@link Satiety}). */
    SATIETY(null, new Item[][] {
        { VanillaItemsArmor.skelemanHelmet, VanillaItemsArmor.skelemanBody, VanillaItemsArmor.skelemanLegs,
            VanillaItemsArmor.skelemanBoots } }),

    /** Night Reaper: +20% damage at night (Jack o'Man set). */
    NIGHT_REAPER(null, new Item[][] {
        { VanillaItemsArmor.jackOManHelmet, VanillaItemsArmor.jackOManBody, VanillaItemsArmor.jackOManLegs,
            VanillaItemsArmor.jackOManBoots } }),

    /** Regeneration (Vemos set): health grows by itself. Since 0.99.114 - only the R8 Crown ({@link com.divinerpgextended.item.ArmorCrown}). */
    REGENERATION(Boss.RAGLOK, new Item[][] {
        { net.divinerpg.utils.items.ArcanaItems.vemosHelmet, net.divinerpg.utils.items.ArcanaItems.vemosBody,
            net.divinerpg.utils.items.ArcanaItems.vemosLegs, net.divinerpg.utils.items.ArcanaItems.vemosBoots } }),

    /** Arcana Flow (Korma set): arcana regenerates faster, arcane damage x2. Since 0.99.114 - only the R8 Crown. */
    ARCANA_FLOW(Boss.WRECK, new Item[][] {
        { net.divinerpg.utils.items.ArcanaItems.kormaHelmet, net.divinerpg.utils.items.ArcanaItems.kormaBody,
            net.divinerpg.utils.items.ArcanaItems.kormaLegs, net.divinerpg.utils.items.ArcanaItems.kormaBoots } }),

    /** Angelic Flight (Angelic set). Does not work in the dream (in Vethea). Since 0.99.114 - only the R8 Crown. */
    ANGEL_FLIGHT(Boss.LADY_LUNA, new Item[][] {
        { VanillaItemsArmor.angelicHelmet, VanillaItemsArmor.angelicBody, VanillaItemsArmor.angelicLegs,
            VanillaItemsArmor.angelicBoots } }),

    /** Light Step (Shadow set): speed x{@link PerkEffects#SWIFT_SPEED} and a high step of one block up. */
    SWIFTNESS(null, new Item[][] {
        { VanillaItemsArmor.shadowHelmet, VanillaItemsArmor.shadowBody, VanillaItemsArmor.shadowLegs, VanillaItemsArmor.shadowBoots } }),

    /** Miner (Terran set): Haste II. */
    MINING_HASTE(null, new Item[][] {
        { VanillaItemsArmor.terranHelmet, VanillaItemsArmor.terranBody, VanillaItemsArmor.terranLegs, VanillaItemsArmor.terranBoots } }),

    /** Marksman (corrupted set): projectiles are stronger. */
    MARKSMAN(null, new Item[][] {
        { VanillaItemsArmor.corruptedHelmet, VanillaItemsArmor.corruptedBody, VanillaItemsArmor.corruptedLegs, VanillaItemsArmor.corruptedBoots } }),

    /** Deflection (arlemite set): projectiles are weaker. */
    ARROW_GUARD(null, new Item[][] {
        { VanillaItemsArmor.arlemiteHelmet, VanillaItemsArmor.arlemiteBody, VanillaItemsArmor.arlemiteLegs, VanillaItemsArmor.arlemiteBoots } }),

    /** Firm stance (rupee set of any color): mob hits are weaker. */
    BLOW_GUARD(null, new Item[][] {
        { VanillaItemsArmor.rupeeHelmet, VanillaItemsArmor.rupeeBody, VanillaItemsArmor.rupeeLegs, VanillaItemsArmor.rupeeBoots },
        { VanillaItemsArmor.redRupeeHelmet, VanillaItemsArmor.redRupeeBody, VanillaItemsArmor.redRupeeLegs, VanillaItemsArmor.redRupeeBoots },
        { VanillaItemsArmor.blueRupeeHelmet, VanillaItemsArmor.blueRupeeBody, VanillaItemsArmor.blueRupeeLegs, VanillaItemsArmor.blueRupeeBoots },
        { VanillaItemsArmor.greenRupeeHelmet, VanillaItemsArmor.greenRupeeBody, VanillaItemsArmor.greenRupeeLegs, VanillaItemsArmor.greenRupeeBoots },
        { VanillaItemsArmor.yellowRupeeHelmet, VanillaItemsArmor.yellowRupeeBody, VanillaItemsArmor.yellowRupeeLegs, VanillaItemsArmor.yellowRupeeBoots },
        { VanillaItemsArmor.grayRupeeHelmet, VanillaItemsArmor.grayRupeeBody, VanillaItemsArmor.grayRupeeLegs, VanillaItemsArmor.grayRupeeBoots } }),

    /** Soft landing (elite realmite set): no fall damage. Before 0.99.99 it was built into every Wanderer tier. */
    SOFT_LANDING(null, new Item[][] {
        { VanillaItemsArmor.eliteRealmiteHelmet, VanillaItemsArmor.eliteRealmiteBody, VanillaItemsArmor.eliteRealmiteLegs, VanillaItemsArmor.eliteRealmiteBoots } });

    /** The boss whose kill unlocks the perk, or null. */
    public final Boss unlock;
    /** Suitable donor sets: [variant][helmet, chestplate, leggings, boots]. */
    private final Item[][] donorSets;

    Perk(Boss unlock, Item[][] donorSets) {
        this.unlock = unlock;
        this.donorSets = donorSets;
    }

    /** A property of the R8 Crown, not a minor perk: it cannot be put in slots, and does not work from old armor. */
    public boolean crown() {
        return this == REGENERATION || this == ARCANA_FLOW || this == ANGEL_FLIGHT;
    }

    public String langKey() {
        return "dre.perk." + name().toLowerCase();
    }

    /** The first suitable donor set [helmet, chestplate, leggings, boots] (for NEI). */
    public Item[] donorSet() {
        return donorSets[0];
    }

    public boolean matchesDonor(Item[] pieces) {
        for (Item[] set : donorSets) {
            boolean ok = true;
            for (int i = 0; i < 4; i++) {
                if (pieces[i] != set[i]) {
                    ok = false;
                    break;
                }
            }
            if (ok) return true;
        }
        return false;
    }

    public static Perk byDonor(Item[] pieces) {
        for (Perk p : values()) {
            if (p.matchesDonor(pieces)) return p;
        }
        return null;
    }

    public static Perk byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
