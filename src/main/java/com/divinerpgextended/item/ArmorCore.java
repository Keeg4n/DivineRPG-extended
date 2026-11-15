package com.divinerpgextended.item;

import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.Config;

/**
 * Wanderer armor core: the final set of the Overworld from which the first tier is forged. The set's property stays in the armor on
 * all tiers, the look of the first tier is different for each core. The core is stored in the NBT of each piece; old armor without a record is
 * Divine, it was the only entry.
 * <p>
 * The sets are very different in cost (divine shards drop only from bosses, Bedrock is crafted from rupee and obsidian),
 * so for the other sets the forging costs divine stones ({@link Config#coreForgeStones}). Changing the core is the same
 * set and the same cost.
 */
public enum ArmorCore {

    DIVINE, SHADOW, BEDROCK, ENDER, CORRUPTED;

    public String key() {
        return name().toLowerCase();
    }

    /** Set variants (End has colors): [variant][helmet, chestplate, leggings, boots]. Divine's fields are read at call time: they are filled in its preInit. */
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

    /** Set variants from outside (NEI): [variant][helmet, chestplate, leggings, boots]. */
    public Item[][] variants() {
        return sets();
    }

    /** Full set: armor = [helmet, chestplate, leggings, boots]; End colors can be mixed, as in the properties of the set itself. */
    public boolean matches(Item[] armor) {
        Item[][] sets = sets();
        for (int slot = 0; slot < 4; slot++) {
            boolean ok = false;
            for (Item[] set : sets) if (armor[slot] != null && armor[slot] == set[slot]) ok = true;
            if (!ok) return false;
        }
        return true;
    }

    /** Which core such a set gives (null - none). */
    public static ArmorCore ofSet(Item[] armor) {
        for (ArmorCore c : values()) if (c.matches(armor)) return c;
        return null;
    }

    /** What to pay for forging and changing the core: for Divine - the former realmite ingots, its stones are already in the set itself. */
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

    /** The core of the worn full Wanderer set (null - not a full set, someone else's, or pieces with different cores). */
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

    /** The final Divine set worn in full (null - not it): for bosses it equals the first tier. */
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
