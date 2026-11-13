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

    /** Зона ящика по мете (шахтёрские - пещеры). */
    private static Zone zone(int m) {
        if (FishingLoot.crateTier(m) >= 0) return Zone.CAVE;
        return Zone.values()[m >= 0 && m < Zone.values().length ? m : 0];
    }

    private Page page(ItemStack crate) {
        int m = crate.getItemDamage();
        Page p = new Page(crate.getDisplayName());
        p.add(crate, gx(0), gy(0), tierColor(View.CRATE), null, true);
        p.grid(FishingLoot.crateContents(zone(m), FishingLoot.crateTier(m)), false, 1);
        p.notes.add(StatCollector.translateToLocal("dre.nei.hint.crate"));
        return p;
    }

    @Override
    public void loadCraftingRecipes(String id, Object... results) {
        if (ID.equals(id) || "all".equals(id)) {
            for (ItemStack c : FishingLoot.crateKinds()) arecipes.add(page(c));
        } else {
            super.loadCraftingRecipes(id, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result.getItem() == ModItems.crate) return;
        for (ItemStack c : FishingLoot.crateKinds()) {
            Page p = page(c);
            if (p.has(result)) arecipes.add(p);
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack s) {
        if (s.getItem() != ModItems.crate) return;
        int m = s.getItemDamage();
        if (m >= 0 && m < Zone.values().length || FishingLoot.crateTier(m) >= 0) arecipes.add(page(s.copy()));
    }

    public static int maxHeight() {
        int n = 0;
        for (ItemStack c : FishingLoot.crateKinds())
            n = Math.max(n, FishingLoot.crateContents(zone(c.getItemDamage()), FishingLoot.crateTier(c.getItemDamage())).size() + 1);
        return height(n, 1);
    }
}
