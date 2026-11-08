package com.divinerpgextended.item;

import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.Config;

/**
 * Ядро брони Странника: финальный сет обычного мира, из которого скована первая ступень. Свойство сета остаётся в броне на
 * всех ступенях, облик первой ступени у каждого ядра свой. Ядро хранится в NBT каждой части; старая броня без записи -
 * божественная, она и была единственным входом.
 * <p>
 * Сеты стоят очень по-разному (божественные осколки падают только с боссов, Бедрок крафтится из рупия и обсидиана),
 * поэтому у остальных сетов за ковку плата божественными камнями ({@link Config#coreForgeStones}). Смена ядра - тот же
 * сет и та же плата.
 */
public enum ArmorCore {

    DIVINE, SHADOW, BEDROCK, ENDER, CORRUPTED;

    public String key() {
        return name().toLowerCase();
    }

    /** Варианты сета (у Энда - цвета): [вариант][шлем, нагрудник, поножи, ботинки]. Поля Divine читаются при вызове: они заполняются в её preInit. */
    private Item[][] sets() {
        switch (this) {
            case SHADOW:
                return new Item[][] { { VanillaItemsArmor.shadowHelmet, VanillaItemsArmor.shadowBody, VanillaItemsArmor.shadowLegs, VanillaItemsArmor.shadowBoots } };
            case BEDROCK:
                return new Item[][] { { VanillaItemsArmor.bedrockHelmet, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBoots } };
            case ENDER:
                return new Item[][] { { VanillaItemsArmor.enderHelmet, VanillaItemsArmor.enderBody, VanillaItemsArmor.enderLegs, VanillaItemsArmor.enderBoots },
                    { VanillaItemsArmor.redEnderHelmet, VanillaItemsArmor.redEnderBody, VanillaItemsArmor.redEnderLegs, VanillaItemsArmor.redEnderBoots },
                    { VanillaItemsArmor.blueEnderHelmet, VanillaItemsArmor.blueEnderBody, VanillaItemsArmor.blueEnderLegs, VanillaItemsArmor.blueEnderBoots },
                    { VanillaItemsArmor.greenEnderHelmet, VanillaItemsArmor.greenEnderBody, VanillaItemsArmor.greenEnderLegs, VanillaItemsArmor.greenEnderBoots },
                    { VanillaItemsArmor.yellowEnderHelmet, VanillaItemsArmor.yellowEnderBody, VanillaItemsArmor.yellowEnderLegs, VanillaItemsArmor.yellowEnderBoots },
                    { VanillaItemsArmor.grayEnderHelmet, VanillaItemsArmor.grayEnderBody, VanillaItemsArmor.grayEnderLegs, VanillaItemsArmor.grayEnderBoots } };
            case CORRUPTED:
                return new Item[][] { { VanillaItemsArmor.corruptedHelmet, VanillaItemsArmor.corruptedBody, VanillaItemsArmor.corruptedLegs, VanillaItemsArmor.corruptedBoots } };
            default:
                return new Item[][] { { VanillaItemsArmor.divineHelmet, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots } };
        }
    }

    /** Варианты сета снаружи (NEI): [вариант][шлем, нагрудник, поножи, ботинки]. */
    public Item[][] variants() {
        return sets();
    }

    /** Полный сет: armor = [шлем, нагрудник, поножи, ботинки]; цвета Края можно мешать, как и в свойствах самого сета. */
    public boolean matches(Item[] armor) {
        Item[][] sets = sets();
        for (int slot = 0; slot < 4; slot++) {
            boolean ok = false;
            for (Item[] set : sets) if (armor[slot] != null && armor[slot] == set[slot]) ok = true;
            if (!ok) return false;
        }
        return true;
    }

    /** Какое ядро даёт такой сет (null - никакое). */
    public static ArmorCore ofSet(Item[] armor) {
        for (ArmorCore c : values()) if (c.matches(armor)) return c;
        return null;
    }

    /** Чем платить за ковку и смену ядра: у божественной - прежние слитки реалмита, её камни уже в самом сете. */
    public Item fuel() {
        return this == DIVINE ? VanillaItemsOther.realmiteIngot : VanillaItemsOther.divineStone;
    }

    public int fuelCount() {
        if (this == DIVINE) return Config.upgradeR1FuelRealmiteIngots;
        return Config.coreForgeStones[Math.min(ordinal() - 1, Config.coreForgeStones.length - 1)];
    }

    public static ArmorCore byName(String name) {
        for (ArmorCore c : values()) if (c.name().equals(name)) return c;
        return DIVINE;
    }

    /** Ядро надетого полного сета Странника (null - не полный сет, чужой или части с разным ядром). */
    public static ArmorCore worn(EntityPlayer p) {
        ArmorCore core = null;
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || !(s.getItem() instanceof ItemWandererArmor) || !ArmorData.owns(s, p)) return null;
            ArmorCore c = ArmorData.core(s);
            if (core != null && core != c) return null;
            core = c;
        }
        return core;
    }

    /** Финальный сет Divine, надетый целиком (null - не он): для боссов он равен первой ступени. */
    public static ArmorCore wornSet(EntityPlayer p) {
        Item[] a = new Item[4];
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null) return null;
            a[3 - i] = s.getItem();
        }
        return ofSet(a);
    }
}
