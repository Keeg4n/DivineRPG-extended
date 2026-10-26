package com.divinerpgextended.perk;

import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.item.Item;

import com.divinerpgextended.boss.Boss;

/**
 * Малые перки минимальной версии. Донор - полный сет брони Divine (шлем, нагрудник, поножи, ботинки),
 * у перка может быть несколько подходящих сетов.
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

    /** Копание под водой без замедления и обзор под водой. */
    AQUA_MINER(Boss.CARCINUS, new Item[][] {
        { VanillaItemsArmor.aquastriveHelmet, VanillaItemsArmor.aquastriveBody, VanillaItemsArmor.aquastriveLegs,
            VanillaItemsArmor.aquastriveBoots } }),

    /** Замедляет враждебных мобов вокруг. */
    COLD_AURA(Boss.SNOW_GLACON, new Item[][] {
        { VanillaItemsArmor.frozenHelmet, VanillaItemsArmor.frozenBody, VanillaItemsArmor.frozenLegs,
            VanillaItemsArmor.frozenBoots } }),

    /** Тепло: холод Iceika не страшен (как в сете Санты). */
    WARMTH(Boss.LICH_KING, new Item[][] {
        { IceikaItems.santaCap, IceikaItems.santaTunic, IceikaItems.santaPants, IceikaItems.santaBoots } }),

    /** Сытость: голод сам понемногу утоляется (как сет Skeleman у Divine). */
    SATIETY(null, new Item[][] {
        { VanillaItemsArmor.skelemanHelmet, VanillaItemsArmor.skelemanBody, VanillaItemsArmor.skelemanLegs,
            VanillaItemsArmor.skelemanBoots } }),

    /** Ночной жнец: +20% урона ночью (сет Jack o'Man). */
    NIGHT_REAPER(null, new Item[][] {
        { VanillaItemsArmor.jackOManHelmet, VanillaItemsArmor.jackOManBody, VanillaItemsArmor.jackOManLegs,
            VanillaItemsArmor.jackOManBoots } }),

    /** Восстановление (сет Vemos): здоровье само растёт. Открывает Крушитель. */
    REGENERATION(Boss.WRECK, new Item[][] {
        { net.divinerpg.utils.items.ArcanaItems.vemosHelmet, net.divinerpg.utils.items.ArcanaItems.vemosBody,
            net.divinerpg.utils.items.ArcanaItems.vemosLegs, net.divinerpg.utils.items.ArcanaItems.vemosBoots } }),

    /** Поток арканы (сет Korma): аркана восстанавливается быстрее. Открывает Раглок (после Крушителя). */
    ARCANA_FLOW(Boss.RAGLOK, new Item[][] {
        { net.divinerpg.utils.items.ArcanaItems.kormaHelmet, net.divinerpg.utils.items.ArcanaItems.kormaBody,
            net.divinerpg.utils.items.ArcanaItems.kormaLegs, net.divinerpg.utils.items.ArcanaItems.kormaBoots } }),

    /** Ангельский полёт (сет Angelic). Открывает Леди Луна (после Раглока). Во сне (в Ветее) не работает. */
    ANGEL_FLIGHT(Boss.LADY_LUNA, new Item[][] {
        { VanillaItemsArmor.angelicHelmet, VanillaItemsArmor.angelicBody, VanillaItemsArmor.angelicLegs,
            VanillaItemsArmor.angelicBoots } });

    /** Босс, убийство которого открывает перк, или null. */
    public final Boss unlock;
    /** Подходящие донорские сета: [вариант][шлем, нагрудник, поножи, ботинки]. */
    private final Item[][] donorSets;

    Perk(Boss unlock, Item[][] donorSets) {
        this.unlock = unlock;
        this.donorSets = donorSets;
    }

    public String langKey() {
        return "dre.perk." + name().toLowerCase();
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
