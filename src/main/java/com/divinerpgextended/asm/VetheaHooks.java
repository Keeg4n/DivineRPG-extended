package com.divinerpgextended.asm;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Calls from Divine classes patched by {@link VetheaTransformer}. */
public final class VetheaHooks {

    private VetheaHooks() {}

    public static void infusionUpdate(ItemStack[] inv) {
        com.divinerpgextended.vethea.Infusion.update(inv);
    }

    public static void infusionTake(IInventory inv) {
        com.divinerpgextended.vethea.Infusion.take(inv);
    }

    /** Instead of "is it in the inventory": the Heiva Belt is no longer needed for L Heiva, the Miner's Amulet is in the accessory slot. */
    public static boolean has(InventoryPlayer inv, Item item) {
        if (item == net.divinerpg.utils.items.VetheaItems.heivaBelt) return true;
        return inv != null && inv.player != null && com.divinerpgextended.accessory.Accessories.wearsItem(inv.player, item);
    }
}
