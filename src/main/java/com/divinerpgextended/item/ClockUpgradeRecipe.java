package com.divinerpgextended.item;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Часы Странника уровня N + шестерня времени N-го мира (Eden для I, Wildwood для II, ...) = часы уровня N+1.
 * Бесформенный рецепт в верстаке.
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
