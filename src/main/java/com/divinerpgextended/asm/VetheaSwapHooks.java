package com.divinerpgextended.asm;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagList;

/**
 * Вызовы из кровати кошмаров и портала Ветеи ({@link VetheaSwapTransformer}). Пока {@link #blocked}, инвентарь не
 * сохраняется в копию Divine, не очищается и не подменяется: сохранённые копии остаются пустыми.
 */
public class VetheaSwapHooks {

    /** Ставит {@link com.divinerpgextended.vethea.VetheaStash#disableDivineSwap()}. */
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
