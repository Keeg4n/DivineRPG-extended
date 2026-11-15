package com.divinerpgextended.accessory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Thaumcraft Goggles of Revealing in a Divine or Wanderer helmet. The helmet is fitted with them at the Wanderer Anvil ({@link Sockets}), and Thaum and
 * other mods of the modpack recognize a helmet with goggles by the item interface: it is added by {@link com.divinerpgextended.asm.GogglesTransformer},
 * whose methods call {@link #reveal} and {@link #discount}. Without Thaum none of this works or loads.
 */
public final class Goggles {

    /** Discount on vis, like the goggles themselves. */
    public static final int VIS_DISCOUNT = 5;

    private static Boolean thaumcraft;
    private static Item item;

    private Goggles() {}

    /** Goggles from Thaum (null - it is absent). Lookup only through the registry: there are no Thaum fields of its own here. */
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

    /** Whether the goggles are built into this armor part: asked every frame, so we read NBT without creating items. */
    public static boolean reveal(ItemStack armor) {
        return available() && Sockets.has(armor, item);
    }

    public static int discount(ItemStack armor) {
        return reveal(armor) ? VIS_DISCOUNT : 0;
    }
}
