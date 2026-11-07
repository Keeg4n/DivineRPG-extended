package com.divinerpgextended.accessory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Очки откровения Thaumcraft в шлеме Divine или Странника. Шлем в них встраивают на Наковальне Странника ({@link Sockets}), а Таум и
 * другие моды сборки узнают шлем с очками по интерфейсу вещи: его добавляет {@link com.divinerpgextended.asm.GogglesTransformer},
 * методы которого вызывают {@link #reveal} и {@link #discount}. Без Таума ничего из этого не работает и не загружается.
 */
public final class Goggles {

    /** Скидка на визу, как у самих очков. */
    public static final int VIS_DISCOUNT = 5;

    private static Boolean thaumcraft;
    private static Item item;

    private Goggles() {}

    /** Очки из Таума (null - его нет). Поиск только по реестру: своих полей Таума здесь нет. */
    public static Item item() {
        if (thaumcraft == null) thaumcraft = Loader.isModLoaded("Thaumcraft");
        if (!thaumcraft) return null;
        if (item == null) item = GameRegistry.findItem("Thaumcraft", "ItemGoggles");
        return item;
    }

    public static boolean available() {
        return item() != null;
    }

    public static boolean is(ItemStack s) {
        return s != null && available() && s.getItem() == item;
    }

    /** Встроены ли очки в эту часть брони: спрашивают на каждом кадре, поэтому читаем NBT без создания вещей. */
    public static boolean reveal(ItemStack armor) {
        return available() && Sockets.has(armor, item);
    }

    public static int discount(ItemStack armor) {
        return reveal(armor) ? VIS_DISCOUNT : 0;
    }
}
