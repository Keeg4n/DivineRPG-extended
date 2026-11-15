package com.divinerpgextended.world;

import java.util.List;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.item.Item;

import com.divinerpgextended.Config;
import com.divinerpgextended.DivineRPGExtended;

/** Tweaks to foreign items after all mods load: non-wearing Bedrock armor and Tinkers islands outside Divine worlds. */
public final class ForeignTweaks {

    private ForeignTweaks() {}

    /** Divine dimensions: twilight dimensions, Iceika, Vethea, Arcana. */
    public static int[] divineDims() {
        return new int[] { ConfigurationHelper.eden, ConfigurationHelper.wildwood, ConfigurationHelper.apalachia, ConfigurationHelper.skythern,
            ConfigurationHelper.mortum, ConfigurationHelper.iceika, ConfigurationHelper.vethea, ConfigurationHelper.arcana };
    }

    public static void apply() {
        log(jackSetsArmor() + " armor pieces of Jack sets received stage-based protection");
        if (Config.bedrockArmorUnbreakable) log(bedrockUnbreakable() + " bedrock armor pieces no longer wear out");
        if (Config.noTinkersIslandsInDivine) {
            int n = noTinkersIslands();
            if (n >= 0) log("Tinkers slime islands closed in " + n + " Divine worlds");
        }
    }

    private static void log(String s) {
        DivineRPGExtended.LOG.info(s);
    }

    /**
     * Bedrock armor: the game removes durability only from items with maxDamage above zero, while Divine by its unbreakable flag
     * writes "infinite durability" in the tooltip and does not allow enchanting for durability. We set both.
     */
    public static int bedrockUnbreakable() {
        int n = 0;
        java.lang.reflect.Field flag = null;
        try {
            flag = net.divinerpg.items.base.ItemDivineArmor.class.getDeclaredField("unbreakable");
            flag.setAccessible(true);
        } catch (Exception ex) {
            DivineRPGExtended.LOG.warn("Bedrock armor: Divine has no unbreakable flag, adjusting durability only - " + ex);
        }
        for (Item i : new Item[] { VanillaItemsArmor.bedrockHelmet, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBoots }) {
            if (i == null) continue;
            i.setMaxDamage(0);
            try {
                if (flag != null && flag.getDeclaringClass().isInstance(i)) flag.setBoolean(i, true);
            } catch (Exception ignored) {
                // the tooltip stays the same, there is no wear anyway
            }
            n++;
        }
        return n;
    }

    private static final int[] SHARE = { 5, 8, 7, 4 }; // shares of helmet, chestplate, leggings and boots in Divine (out of 24)

    /**
     * Sets sold by Jack (skeleton - from the start, Jack O Man - after Carcinus, Withered Reaper - after the Lich King) have
     * no protection in Divine. Divine computes a part's protection from the set's full protection (fullReduction, percent) by SHARE
     * fractions - we set both fields, then both the tooltip and the armor icons show the new value.
     */
    public static int jackSetsArmor() {
        int n = 0;
        try {
            java.lang.reflect.Field full = net.divinerpg.items.base.ItemDivineArmor.class.getDeclaredField("fullReduction");
            java.lang.reflect.Field part = net.divinerpg.items.base.ItemDivineArmor.class.getDeclaredField("damageReduction");
            full.setAccessible(true);
            part.setAccessible(true);
            Object[][] sets = {
                { Config.skelemanArmor, VanillaItemsArmor.skelemanHelmet, VanillaItemsArmor.skelemanBody, VanillaItemsArmor.skelemanLegs, VanillaItemsArmor.skelemanBoots },
                { Config.jackOManArmor, VanillaItemsArmor.jackOManHelmet, VanillaItemsArmor.jackOManBody, VanillaItemsArmor.jackOManLegs, VanillaItemsArmor.jackOManBoots },
                { Config.witherReaperArmor, VanillaItemsArmor.witherReaperHelmet, VanillaItemsArmor.witherReaperBody, VanillaItemsArmor.witherReaperLegs,
                    VanillaItemsArmor.witherReaperBoots } };
            for (Object[] set : sets) {
                int pct = (Integer) set[0];
                if (pct <= 0) continue;
                for (int i = 1; i < set.length; i++) {
                    if (!(set[i] instanceof net.divinerpg.items.base.ItemDivineArmor)) continue;
                    net.minecraft.item.ItemArmor a = (net.minecraft.item.ItemArmor) set[i];
                    full.setInt(a, pct);
                    part.setDouble(a, pct / 100.0 * SHARE[a.armorType] / 24.0);
                    n++;
                }
            }
        } catch (Exception ex) {
            DivineRPGExtended.LOG.warn("Jack sets: could not set protection - " + ex);
        }
        return n;
    }

    /**
     * Tinkers' Construct places slime islands in all dimensions with a positive number except its blacklist
     * (DimensionBlacklist.blacklistedDims). We append the Divine worlds there. No Tinkers - nothing. @return how many worlds
     * were added (-1 - no Tinkers or the list is unavailable)
     */
    @SuppressWarnings("unchecked")
    public static int noTinkersIslands() {
        if (!cpw.mods.fml.common.Loader.isModLoaded("TConstruct")) return -1;
        try {
            List<Integer> list = (List<Integer>) Class.forName("tconstruct.util.config.DimensionBlacklist").getField("blacklistedDims").get(null);
            int n = 0;
            for (int d : divineDims()) if (!list.contains(d)) {
                list.add(d);
                n++;
            }
            return n;
        } catch (Throwable t) {
            DivineRPGExtended.LOG.warn("Tinkers islands: could not extend the dimension blacklist - " + t);
            return -1;
        }
    }

    /** Whether Tinkers islands are allowed in this dimension (null - no Tinkers). For the selftest. */
    public static Boolean tinkersIslandsAllowed(int dim) {
        try {
            return (Boolean) Class.forName("tconstruct.util.config.DimensionBlacklist").getMethod("isDimInBlacklist", int.class).invoke(null, dim);
        } catch (Throwable t) {
            return null;
        }
    }
}
