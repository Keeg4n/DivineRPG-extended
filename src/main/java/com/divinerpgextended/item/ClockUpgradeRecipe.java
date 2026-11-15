package com.divinerpgextended.item;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Wanderer's Clock of level N + the time gear of the N-th dimension (Eden for I, Wildwood for II, ...) = clock of level N+1.
 * A shapeless recipe in the crafting table.
 */
public class ClockUpgradeRecipe implements IRecipe {

    private ItemStack[] find(InventoryCrafting inv) {
        ItemStack clock = null, gear = null;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null) continue;
            if (s.getItem() == ModItems.wandererClock && clock == null) clock = s;
            else if (s.getItem() == ModItems.timeGear && gear == null) gear = s;
            else return null;
        }
        return clock == null || gear == null ? null : new ItemStack[] { clock, gear };
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        ItemStack[] f = find(inv);
        if (f == null) return false;
        int tier = ItemWandererClock.tier(f[0]);
        return tier < ItemWandererClock.MAX_TIER && f[1].getItemDamage() == tier - 1;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack[] f = find(inv);
        if (f == null) return null;
        return ItemWandererClock.withTier(ModItems.wandererClock, ItemWandererClock.tier(f[0]) + 1);
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ItemWandererClock.withTier(ModItems.wandererClock, 2);
    }
}
