package com.divinerpgextended.world;

import java.util.List;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.item.Item;

import com.divinerpgextended.Config;
import com.divinerpgextended.DivineRPGExtended;

/** Правки чужих вещей после загрузки всех модов: неизнашиваемая броня из бедрока и острова Tinkers вне миров Divine. */
public final class ForeignTweaks {

    private ForeignTweaks() {}

    /** Измерения Divine: сумеречные миры, Айсика, Ветея, Аркана. */
    public static int[] divineDims() {
        return new int[] { ConfigurationHelper.eden, ConfigurationHelper.wildwood, ConfigurationHelper.apalachia, ConfigurationHelper.skythern,
            ConfigurationHelper.mortum, ConfigurationHelper.iceika, ConfigurationHelper.vethea, ConfigurationHelper.arcana };
    }

    public static void apply() {
        log(jackSetsArmor() + " частей брони сетов Джека получили защиту по этапу");
        if (Config.bedrockArmorUnbreakable) log(bedrockUnbreakable() + " частей брони из бедрока больше не изнашиваются");
        if (Config.noTinkersIslandsInDivine) {
            int n = noTinkersIslands();
            if (n >= 0) log("острова слизи Tinkers закрыты в " + n + " мирах Divine");
        }
    }

    private static void log(String s) {
        DivineRPGExtended.LOG.info(s);
    }

    /**
     * Броня из бедрока: игра снимает прочность только у вещей с maxDamage больше нуля, а Divine по своему флагу unbreakable
     * пишет в подсказке "бесконечная прочность" и не даёт чаровать на прочность. Ставим и то, и другое.
     */
    public static int bedrockUnbreakable() {
        int n = 0;
        java.lang.reflect.Field flag = null;
        try {
            flag = net.divinerpg.items.base.ItemDivineArmor.class.getDeclaredField("unbreakable");
            flag.setAccessible(true);
        } catch (Exception ex) {
            DivineRPGExtended.LOG.warn("Броня из бедрока: нет флага unbreakable у Divine, правлю только прочность - " + ex);
        }
        for (Item i : new Item[] { VanillaItemsArmor.bedrockHelmet, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBoots }) {
            if (i == null) continue;
            i.setMaxDamage(0);
            try {
                if (flag != null && flag.getDeclaringClass().isInstance(i)) flag.setBoolean(i, true);
            } catch (Exception ignored) {
                // подсказка останется прежней, износа всё равно нет
            }
            n++;
        }
        return n;
    }

    private static final int[] SHARE = { 5, 8, 7, 4 }; // доли шлема, нагрудника, поножей и ботинок у Divine (из 24)

    /**
     * Сеты, которые продаёт Джек (скелет - с начала, Джек-О-Мен - после Карцинуса, Увядший жнец - после Короля-лича), у
     * Divine без защиты. Divine считает защиту части из полной защиты сета (fullReduction, проценты) по долям SHARE - ставим
     * оба поля, тогда и подсказка, и значки брони покажут новое.
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
            DivineRPGExtended.LOG.warn("Сеты Джека: не удалось поставить защиту - " + ex);
        }
        return n;
    }

    /**
     * Tinkers' Construct ставит острова слизи во всех измерениях с положительным номером, кроме своего чёрного списка
     * (DimensionBlacklist.blacklistedDims). Дописываем туда миры Divine. Нет Tinkers - ничего. @return сколько миров
     * добавлено (-1 - Tinkers нет или список недоступен)
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
            DivineRPGExtended.LOG.warn("Острова Tinkers: не удалось дополнить чёрный список измерений - " + t);
            return -1;
        }
    }

    /** Разрешены ли Tinkers острова в этом измерении (null - Tinkers нет). Для самопроверки. */
    public static Boolean tinkersIslandsAllowed(int dim) {
        try {
            return (Boolean) Class.forName("tconstruct.util.config.DimensionBlacklist").getMethod("isDimInBlacklist", int.class).invoke(null, dim);
        } catch (Throwable t) {
            return null;
        }
    }
}
