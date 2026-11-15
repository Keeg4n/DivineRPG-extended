package com.divinerpgextended.asm;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagList;

/**
 * Calls from the Nightmare Bed and the Vethea portal ({@link VetheaSwapTransformer}). While {@link #blocked}, the inventory is not
 * saved into the Divine copy, not cleared and not swapped: the saved copies stay empty.
 */
public class VetheaSwapHooks {

    /** Sets {@link com.divinerpgextended.vethea.VetheaStash#disableDivineSwap()}. */
    public static volatile boolean blocked;

    public static NBTTagList write(InventoryPlayer inv, NBTTagList list) {
        return blocked ? list : inv.writeToNBT(list);
    }

    public static void read(InventoryPlayer inv, NBTTagList list) {
        if (!blocked) inv.readFromNBT(list);
    }

    public static int clear(InventoryPlayer inv, Item item, int meta) {
        return blocked ? 0 : inv.clearInventory(item, meta);
    }
}
