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

    /** Infusion altar decrStackSize: the result slot is taken with its cost, the inputs - as usual, then the result is recomputed at once. */
    public static ItemStack infusionDecr(ItemStack[] inv, int slot, int n) {
        if (inv == null || slot < 0 || slot >= inv.length || inv[slot] == null || n <= 0) return null;
        if (slot == 2) return com.divinerpgextended.vethea.Infusion.takeResult(inv);
        ItemStack out;
        if (inv[slot].stackSize <= n) {
            out = inv[slot];
            inv[slot] = null;
        } else {
            out = inv[slot].splitStack(n);
            if (inv[slot].stackSize <= 0) inv[slot] = null;
        }
        com.divinerpgextended.vethea.Infusion.update(inv);
        return out;
    }

    /** Infusion altar setInventorySlotContents: the result slot is computed only (nothing is put there), the inputs - as usual. */
    public static void infusionSet(IInventory te, ItemStack[] inv, int slot, ItemStack s) {
        if (inv == null || slot < 0 || slot >= inv.length) return;
        if (slot != 2) {
            inv[slot] = s;
            if (s != null && s.stackSize > te.getInventoryStackLimit()) s.stackSize = te.getInventoryStackLimit();
        }
        com.divinerpgextended.vethea.Infusion.update(inv);
    }

    /** Hoppers and pipes do not put anything into the result slot. */
    public static boolean infusionValid(int slot) {
        return slot != 2;
    }

    /** Instead of "is it in the inventory": the Heiva Belt is no longer needed for L Heiva, the Miner's Amulet is in the accessory slot. */
    public static boolean has(InventoryPlayer inv, Item item) {
        if (item == net.divinerpg.utils.items.VetheaItems.heivaBelt) return true;
        return inv != null && inv.player != null && com.divinerpgextended.accessory.Accessories.wearsItem(inv.player, item);
    }
}
