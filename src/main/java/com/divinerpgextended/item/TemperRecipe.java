package com.divinerpgextended.item;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.twilight.TwilightMaterial;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Tempering twilight dimension items (in any order on the crafting table):
 * <ul>
 * <li>a dimension armor piece + 2 first materials of the dimension + 2 third ones + a world stone;</li>
 * <li>a dimension bow + 3 first materials (for Eden - sun feathers) + a world stone.</li>
 * </ul>
 * The item is the same, but with the dreTempered marker and a glow. The tempered set and bow - see {@link com.divinerpgextended.perk.HomeArmor}
 * and {@link com.divinerpgextended.perk.HomingArrows}.
 */
public class TemperRecipe implements IRecipe {

    public static final String TAG = "dreTempered";

    public static boolean tempered(ItemStack s) {
        return s != null && s.hasTagCompound() && s.getTagCompound().getBoolean(TAG);
    }

    /** The item's dimension (armor piece or bow) or null. */
    public static TwilightWorld worldOf(Item i) {
        for (TwilightWorld w : TwilightWorld.values()) {
            for (Item a : w.armor()) if (a == i) return w;
            if (bow(w) == i) return w;
        }
        return null;
    }

    public static Item bow(TwilightWorld w) {
        switch (w) {
            case EDEN: return net.divinerpg.utils.items.TwilightItemsWeapons.edenBow;
            case WILDWOOD: return net.divinerpg.utils.items.TwilightItemsWeapons.wildwoodBow;
            case APALACHIA: return net.divinerpg.utils.items.TwilightItemsWeapons.apalachiaBow;
            case SKYTHERN: return net.divinerpg.utils.items.TwilightItemsWeapons.skythernBow;
            default: return net.divinerpg.utils.items.TwilightItemsWeapons.mortumBow;
        }
    }

    @Override
    public boolean matches(InventoryCrafting inv, World w) {
        return getCraftingResult(inv) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack base = null;
        int[] mats = new int[TwilightMaterial.values().length];
        Item gem = null;
        int gems = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null) continue;
            if (s.getItem() == ModItems.twilightMaterial) {
                mats[TwilightMaterial.byMeta(s.getItemDamage()).ordinal()]++;
            } else if (worldOf(s.getItem()) != null) {
                if (base != null) return null;
                base = s;
            } else {
                if (gem != null && gem != s.getItem()) return null;
                gem = s.getItem();
                gems++;
            }
        }
        if (base == null || tempered(base)) return null;
        TwilightWorld w = worldOf(base.getItem());
        if (gem != w.gem() || gems != 1) return null;
        TwilightMaterial[] m = TwilightMaterial.ofWorld(w);
        int[] need = new int[mats.length];
        if (base.getItem() == bow(w)) need[m[0].ordinal()] = 3;
        else {
            need[m[0].ordinal()] = 2;
            need[m[2].ordinal()] = 2;
        }
        for (int i = 0; i < mats.length; i++) if (mats[i] != need[i]) return null;
        ItemStack out = base.copy();
        out.stackSize = 1;
        if (!out.hasTagCompound()) out.setTagCompound(new NBTTagCompound());
        out.getTagCompound().setBoolean(TAG, true);
        if (!out.getTagCompound().hasKey("ench")) out.getTagCompound().setTag("ench", new NBTTagList()); // glow without enchantments
        return out;
    }

    @Override
    public int getRecipeSize() {
        return 6;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }
}
