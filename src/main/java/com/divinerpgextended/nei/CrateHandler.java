package com.divinerpgextended.nei;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.fishing.FishingLoot;
import com.divinerpgextended.fishing.FishingLoot.View;
import com.divinerpgextended.fishing.Zone;
import com.divinerpgextended.registry.ModItems;

/** NEI: содержимое рыболовного ящика каждой зоны (ящик - первая ячейка). */
public class CrateHandler extends DreHandler {

    public static final String ID = "dre.crates";

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("dre.nei.crates");
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    private Page page(Zone z) {
        ItemStack crate = new ItemStack(ModItems.crate, 1, z.ordinal());
        Page p = new Page(crate.getDisplayName());
        p.add(crate, gx(0), gy(0), tierColor(View.CRATE), null, true);
        p.grid(FishingLoot.crateContents(z), false, 1);
        p.notes.add(StatCollector.translateToLocal("dre.nei.hint.crate"));
        return p;
    }

    @Override
    public void loadCraftingRecipes(String id, Object... results) {
        if (ID.equals(id) || "all".equals(id)) {
            for (Zone z : Zone.values()) arecipes.add(page(z));
        } else {
            super.loadCraftingRecipes(id, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result.getItem() == ModItems.crate) return;
        for (Zone z : Zone.values()) {
            Page p = page(z);
            if (p.has(result)) arecipes.add(p);
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack s) {
        if (s.getItem() != ModItems.crate) return;
        int m = s.getItemDamage();
        if (m >= 0 && m < Zone.values().length) arecipes.add(page(Zone.values()[m]));
    }

    public static int maxHeight() {
        int n = 0;
        for (Zone z : Zone.values()) n = Math.max(n, FishingLoot.crateContents(z).size() + 1);
        return height(n, 1);
    }
}
