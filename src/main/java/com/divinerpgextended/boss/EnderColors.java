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
 * Цвета брони Энда Divine против орды Айрако: каждая вещь цвета снимает с носителя ауру Айрако этого цвета и
 * усиливает удары по нему; обычная (чёрная) броня Энда - против фиолетового, серая - понемногу против всех (+10% за
 * вещь). Полный сет из любых цветов - способности брони ({@link EnderGear}).
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
            VanillaItemsWeapons.grayEnderSword, VanillaItemsWeapons.greenEnderSword, VanillaItemsWeapons.redEnderSword, VanillaItemsWeapons.yellowEnderSword));
    }

    private static void put(Hue h, Item... items) {
        for (Item i : items) if (i != null) HUES.put(i, h);
    }

    /** Цвет вещи брони Энда (null - серая или не броня Энда). */
    public static Hue hue(Item i) {
        init();
        return HUES.get(i);
    }

    public static boolean ender(Item i) {
        init();
        return HUES.containsKey(i) || GRAY.contains(i);
    }

    /** Надета ли хоть одна вещь брони Энда этого цвета. */
    public static boolean wears(EntityPlayer p, Hue h) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s != null && hue(s.getItem()) == h) return true;
        }
        return false;
    }

    /** Сколько надето вещей брони Энда цвета h (null - серых). */
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

    /** Полный сет брони Энда (цвета можно смешивать). */
    public static boolean fullSet(EntityPlayer p) {
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
