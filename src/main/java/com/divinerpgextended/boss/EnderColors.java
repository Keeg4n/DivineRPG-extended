package com.divinerpgextended.boss;

import java.util.HashMap;
import java.util.Map;

import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.boss.AyeracoHorde.Hue;

/**
 * Divine End armor colors against the Ayeraco horde: each piece of a color removes the Ayeraco aura of that color from the wearer and
 * strengthens hits against it; ordinary (black) End armor works against purple, gray works a little against all (+10% per
 * piece). A full set of any colors gives the armor abilities ({@link EnderGear}).
 */
public final class EnderColors {

    private static Map<Item, Hue> HUES;
    private static java.util.Set<Item> GRAY, SWORDS;

    private EnderColors() {}

    private static synchronized void init() {
        if (HUES != null) return;
        HUES = new HashMap<Item, Hue>();
        put(Hue.PURPLE, VanillaItemsArmor.enderHelmet, VanillaItemsArmor.enderBody, VanillaItemsArmor.enderLegs, VanillaItemsArmor.enderBoots);
        put(Hue.YELLOW, VanillaItemsArmor.yellowEnderHelmet, VanillaItemsArmor.yellowEnderBody, VanillaItemsArmor.yellowEnderLegs, VanillaItemsArmor.yellowEnderBoots);
        put(Hue.RED, VanillaItemsArmor.redEnderHelmet, VanillaItemsArmor.redEnderBody, VanillaItemsArmor.redEnderLegs, VanillaItemsArmor.redEnderBoots);
        put(Hue.GREEN, VanillaItemsArmor.greenEnderHelmet, VanillaItemsArmor.greenEnderBody, VanillaItemsArmor.greenEnderLegs, VanillaItemsArmor.greenEnderBoots);
        put(Hue.BLUE, VanillaItemsArmor.blueEnderHelmet, VanillaItemsArmor.blueEnderBody, VanillaItemsArmor.blueEnderLegs, VanillaItemsArmor.blueEnderBoots);
        GRAY = new java.util.HashSet<Item>(java.util.Arrays.asList(VanillaItemsArmor.grayEnderHelmet, VanillaItemsArmor.grayEnderBody,
            VanillaItemsArmor.grayEnderLegs, VanillaItemsArmor.grayEnderBoots));
        SWORDS = new java.util.HashSet<Item>(java.util.Arrays.asList(VanillaItemsWeapons.enderSword, VanillaItemsWeapons.blueEnderSword,
            VanillaItemsWeapons.grayEnderSword, VanillaItemsWeapons.greenEnderSword, VanillaItemsWeapons.redEnderSword, VanillaItemsWeapons.yellowEnderSword,
            VanillaItemsWeapons.enderBow, net.divinerpg.utils.items.IceikaItems.enderice)); // both the End bow and the End ice sword
    }

    private static void put(Hue h, Item... items) {
        for (Item i : items) if (i != null) HUES.put(i, h);
    }

    /** Color of an End armor piece (null - gray or not End armor). */
    public static Hue hue(Item i) {
        init();
        return HUES.get(i);
    }

    public static boolean ender(Item i) {
        init();
        return HUES.containsKey(i) || GRAY.contains(i);
    }

    /** Whether at least one End armor piece of this color is worn. */
    public static boolean wears(EntityPlayer p, Hue h) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s != null && hue(s.getItem()) == h) return true;
        }
        return false;
    }

    /** How many End armor pieces of color h are worn (null - gray ones). */
    public static int count(EntityPlayer p, Hue h) {
        init();
        int n = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null) continue;
            if (h == null ? GRAY.contains(s.getItem()) : hue(s.getItem()) == h) n++;
        }
        return n;
    }

    /** A full End armor set (colors can be mixed). */
    public static boolean fullSet(EntityPlayer p) {
        if (com.divinerpgextended.item.ArmorCore.worn(p) == com.divinerpgextended.item.ArmorCore.ENDER) return true; // End core in Wanderer armor
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || !ender(s.getItem())) return false;
        }
        return true;
    }

    public static boolean sword(ItemStack s) {
        init();
        return s != null && SWORDS.contains(s.getItem());
    }
}
