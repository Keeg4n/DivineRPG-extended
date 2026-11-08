package com.divinerpgextended.item;

import net.minecraft.block.BlockColored;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Покраска части брони Странника в верстаке: часть + красители, цвета смешиваются как у кожаной брони (и с уже нанесённым).
 * Только облик: ступень, ядро, перки, гнёзда и владелец не меняются. Отмывается правым кликом по котлу с водой ({@link Wash}).
 */
public class RecipeWandererDye implements IRecipe {

    /** [часть, краситель...] или null, если в сетке что-то лишнее. */
    private static java.util.List<ItemStack> find(InventoryCrafting inv) {
        ItemStack armor = null;
        java.util.List<ItemStack> out = new java.util.ArrayList<ItemStack>();
        out.add(null);
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null) continue;
            if (s.getItem() instanceof ItemWandererArmor) {
                if (armor != null) return null;
                armor = s;
            } else if (s.getItem() == Items.dye) out.add(s);
            else return null;
        }
        if (armor == null || out.size() < 2) return null;
        out.set(0, armor);
        return out;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        return find(inv) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        java.util.List<ItemStack> f = find(inv);
        if (f == null) return null;
        ItemStack out = f.get(0).copy();
        out.stackSize = 1;
        // как RecipesArmorDyes: среднее по цветам, яркость - средняя из максимумов
        int[] sum = new int[3];
        int bright = 0, n = 0, old = ArmorData.color(out);
        if (old >= 0) {
            int r = old >> 16 & 255, g = old >> 8 & 255, b = old & 255;
            bright += Math.max(r, Math.max(g, b));
            sum[0] += r;
            sum[1] += g;
            sum[2] += b;
            n++;
        }
        for (int i = 1; i < f.size(); i++) {
            float[] c = EntitySheep.fleeceColorTable[BlockColored.func_150032_b(f.get(i).getItemDamage())];
            int r = (int) (c[0] * 255F), g = (int) (c[1] * 255F), b = (int) (c[2] * 255F);
            bright += Math.max(r, Math.max(g, b));
            sum[0] += r;
            sum[1] += g;
            sum[2] += b;
            n++;
        }
        int r = sum[0] / n, g = sum[1] / n, b = sum[2] / n;
        float avg = (float) bright / n, max = Math.max(1, Math.max(r, Math.max(g, b)));
        r = (int) (r * avg / max);
        g = (int) (g * avg / max);
        b = (int) (b * avg / max);
        ArmorData.setColor(out, (r << 16) + (g << 8) + b);
        return out;
    }

    @Override
    public int getRecipeSize() {
        return 10;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }

    /** Котёл с водой отмывает крашеную часть и тратит треть воды, как у кожаной брони. */
    public static class Wash {

        @SubscribeEvent
        public void onUse(PlayerInteractEvent e) {
            if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world == null || e.world.isRemote) return;
            ItemStack s = e.entityPlayer.getCurrentEquippedItem();
            if (s == null || !(s.getItem() instanceof ItemWandererArmor) || ArmorData.color(s) < 0) return;
            if (e.world.getBlock(e.x, e.y, e.z) != Blocks.cauldron) return;
            int level = e.world.getBlockMetadata(e.x, e.y, e.z);
            if (level <= 0) return;
            ArmorData.clearColor(s);
            e.world.setBlockMetadataWithNotify(e.x, e.y, e.z, level - 1, 2);
            e.setCanceled(true);
        }
    }
}
