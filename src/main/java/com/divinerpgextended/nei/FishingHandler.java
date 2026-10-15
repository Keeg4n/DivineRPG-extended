package com.divinerpgextended.nei;

import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.fishing.Bait;
import com.divinerpgextended.fishing.FishingLoot;
import com.divinerpgextended.fishing.Zone;

/** NEI: что ловится в каждой зоне (рамка ячейки - редкость), отдельная страница мусора. */
public class FishingHandler extends DreHandler {

    public static final String ID = "dre.fishing";

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("dre.nei.fishing");
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    private Page zone(Zone z) {
        Page p = new Page(StatCollector.translateToLocal("dre.zone." + z.key()));
        p.grid(FishingLoot.catches(z), false);
        String hint = "dre.nei.hint." + z.key();
        if (StatCollector.canTranslate(hint)) p.notes.add(StatCollector.translateToLocal(hint));
        return p;
    }

    private Page junk() {
        Page p = new Page(StatCollector.translateToLocal("dre.nei.junk"));
        p.grid(FishingLoot.junk(), false);
        p.notes.add(StatCollector.translateToLocal("dre.nei.hint.junk"));
        return p;
    }

    private void all() {
        for (Zone z : Zone.values()) arecipes.add(zone(z));
        arecipes.add(junk());
    }

    @Override
    public void loadCraftingRecipes(String id, Object... results) {
        if (ID.equals(id) || "all".equals(id)) all();
        else super.loadCraftingRecipes(id, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (Zone z : Zone.values()) {
            Page p = zone(z);
            if (p.has(result)) arecipes.add(p);
        }
        Page j = junk();
        if (j.has(result)) arecipes.add(j);
    }

    @Override
    public void loadUsageRecipes(String id, Object... ingredients) {
        if (ID.equals(id)) all();
        else super.loadUsageRecipes(id, ingredients);
    }

    @Override
    public void loadUsageRecipes(ItemStack s) {
        if (s.getItem() instanceof ItemFishingRod || Bait.of(s.getItem()) != null) all();
    }

    /** Самая высокая страница - под неё NEI отводит место. */
    public static int maxHeight() {
        int n = FishingLoot.junk().size();
        for (Zone z : Zone.values()) n = Math.max(n, FishingLoot.catches(z).size());
        return height(n, 1);
    }
}
