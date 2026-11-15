package com.divinerpgextended.halite;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/** Twilight Spirit: three Mortum soul sparks from three different bosses (identical ones will not do - one boss is not farmed). */
public class RecipeTwilightSpirit implements IRecipe {

    public static final int NEED = 3;

    @Override
    public boolean matches(InventoryCrafting inv, World w) {
        Set<Integer> kinds = new HashSet<Integer>();
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null) continue;
            if (s.getItem() != ModItems.spiritSpark || !Halite.Spark.values()[Math.min(s.getItemDamage(), Halite.Spark.values().length - 1)].mortum()) return false;
            kinds.add(s.getItemDamage());
            n++;
        }
        return n == NEED && kinds.size() == NEED;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        return getRecipeOutput();
    }

    @Override
    public int getRecipeSize() {
        return NEED;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return Halite.spirit(Halite.Spirit.TWILIGHT);
    }
}
