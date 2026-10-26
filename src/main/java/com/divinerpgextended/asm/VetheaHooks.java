package com.divinerpgextended.asm;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Вызовы из классов Divine, исправленных {@link VetheaTransformer}. */
public final class VetheaHooks {

    private VetheaHooks() {}

    public static void infusionUpdate(ItemStack[] inv) {
        com.divinerpgextended.vethea.Infusion.update(inv);
    }

    public static void infusionTake(IInventory inv) {
        com.divinerpgextended.vethea.Infusion.take(inv);
    }

    /** Вместо "есть ли в инвентаре": Пояс хейвы для Лхейвы больше не нужен, Шахтёрский амулет - в слоте аксессуаров. */
    public static boolean has(InventoryPlayer inv, Item item) {
        if (item == net.divinerpg.utils.items.VetheaItems.heivaBelt) return true;
        return inv != null && inv.player != null && com.divinerpgextended.accessory.Accessories.wearsItem(inv.player, item);
    }
}
