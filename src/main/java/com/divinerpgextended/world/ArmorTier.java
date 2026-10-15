package com.divinerpgextended.world;

import net.divinerpg.utils.items.TwilightItemsArmor;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.minecraft.item.Item;

import com.divinerpgextended.boss.Boss;

/**
 * Ступени закалки брони Странника R2-R7: каждый сумеречный мир добавляет свою ступень. Донор - полный сет брони мира,
 * топливо - камни мира, требование - босс мира.
 */
public enum ArmorTier {

    R2_EDEN(2, Boss.CADILLION_EDEN),
    R3_WILDWOOD(3, Boss.CADILLION_WILDWOOD),
    R4_APALACHIA(4, Boss.CADILLION_APALACHIA),
    R5_SKYTHERN(5, Boss.CADILLION_SKYTHERN),
    R6_MORTUM(6, Boss.TWILIGHT_DEMON),
    R7_HALITE(7, Boss.KAROT);

    public final int tier;
    public final Boss boss;

    ArmorTier(int tier, Boss boss) {
        this.tier = tier;
        this.boss = boss;
    }

    /** [шлем, нагрудник, поножи, ботинки] */
    public Item[] donorSet() {
        switch (this) {
            case R2_EDEN:
                return new Item[] { TwilightItemsArmor.edenHelmet, TwilightItemsArmor.edenChestplate,
                    TwilightItemsArmor.edenLeggings, TwilightItemsArmor.edenBoots };
            case R3_WILDWOOD:
                return new Item[] { TwilightItemsArmor.wildwoodHelmet, TwilightItemsArmor.wildwoodChestplate,
                    TwilightItemsArmor.wildwoodLeggings, TwilightItemsArmor.wildwoodBoots };
            case R4_APALACHIA:
                return new Item[] { TwilightItemsArmor.apalachiaHelmet, TwilightItemsArmor.apalachiaChestplate,
                    TwilightItemsArmor.apalachiaLeggings, TwilightItemsArmor.apalachiaBoots };
            case R5_SKYTHERN:
                return new Item[] { TwilightItemsArmor.skythernHelmet, TwilightItemsArmor.skythernChestplate,
                    TwilightItemsArmor.skythernLeggings, TwilightItemsArmor.skythernBoots };
            case R6_MORTUM:
                return new Item[] { TwilightItemsArmor.mortumHelmet, TwilightItemsArmor.mortumChestplate,
                    TwilightItemsArmor.mortumLeggings, TwilightItemsArmor.mortumBoots };
            default:
                return new Item[] { TwilightItemsArmor.haliteHelmet, TwilightItemsArmor.haliteChestplate,
                    TwilightItemsArmor.haliteLeggings, TwilightItemsArmor.haliteBoots };
        }
    }

    public Item fuel() {
        switch (this) {
            case R2_EDEN:
                return TwilightItemsOther.edenGem;
            case R3_WILDWOOD:
                return TwilightItemsOther.wildwoodGem;
            case R4_APALACHIA:
                return TwilightItemsOther.apalachiaGem;
            case R5_SKYTHERN:
                return TwilightItemsOther.skythernGem;
            case R6_MORTUM:
                return TwilightItemsOther.mortumGem;
            default:
                return TwilightItemsOther.mortumChunk;
        }
    }

    /** Ступень, в которую ведёт улучшение из текущей, или null. */
    public static ArmorTier next(int currentTier) {
        for (ArmorTier t : values()) if (t.tier == currentTier + 1) return t;
        return null;
    }
}
