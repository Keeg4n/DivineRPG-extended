package com.divinerpgextended.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import codechicken.lib.gui.GuiDraw;

import com.divinerpgextended.fishing.FishingLoot.View;
import com.divinerpgextended.magic.ArcaneEffect;
import com.divinerpgextended.magic.ItemArcanePotion;

/**
 * NEI: арканная стойка зельеварения. По странице на зелье: склянка воды + ингредиент = зелье, ниже - что оно делает и
 * как его усилить (светопыль - сильное, редстоун - долгое, порох - общее; арканиум - топливо на 4 варки).
 */
public class BrewingHandler extends DreHandler {

    public static final String ID = "dre.brewing";
    private static final int ARROW_X = 42;

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("dre.nei.brewing");
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    private Page page(ArcaneEffect e) {
        Page p = new Page(StatCollector.translateToLocal("dre.nei.brewing"));
        p.add(new ItemStack(Items.potionitem, 1, 0), gx(0), gy(0), 0xFF8B8B8B, null, true);
        p.add(new ItemStack(e.ingredient(), 1, e == ArcaneEffect.FISHERS_FORTUNE ? Short.MAX_VALUE : 0), gx(1), gy(0), tierColor(View.UNCOMMON), null, true);
        p.result = p.add(ItemArcanePotion.of(e, false, false, false), gx(3), gy(0), tierColor(View.RARE), null, false);
        p.others.remove(p.result);
        // варианты: сильное, долгое, общее (что есть у этого зелья)
        int col = 5;
        if (e.leveled()) p.add(ItemArcanePotion.of(e, true, false, false), gx(col++), gy(0), tierColor(View.VERY_RARE), null, false);
        if (!e.instant()) p.add(ItemArcanePotion.of(e, false, true, false), gx(col++), gy(0), tierColor(View.VERY_RARE), null, false);
        p.add(ItemArcanePotion.of(e, false, false, true), gx(col), gy(0), tierColor(View.VERY_RARE), null, false);
        p.noteY = gy(0) + 20;
        p.notes.add(StatCollector.translateToLocal("dre.potion." + e.key() + ".desc1"));
        p.notes.add(StatCollector.translateToLocal("dre.nei.brewing.mods"));
        p.notes.add(StatCollector.translateToLocal("dre.nei.brewing.fuel"));
        return p;
    }

    private List<Page> pages() {
        List<Page> l = new ArrayList<Page>();
        for (ArcaneEffect e : ArcaneEffect.values()) if (!e.removed()) l.add(page(e));
        return l;
    }

    @Override
    public void loadCraftingRecipes(String id, Object... results) {
        if (ID.equals(id) || "all".equals(id)) arecipes.addAll(pages());
        else super.loadCraftingRecipes(id, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result == null || result.getItem() != com.divinerpgextended.registry.ModItems.arcanePotion || ArcaneEffect.of(result.getItemDamage()).removed()) return;
        arecipes.add(page(ArcaneEffect.of(result.getItemDamage())));
    }

    @Override
    public void loadUsageRecipes(ItemStack s) {
        if (s == null) return;
        ArcaneEffect e = ArcaneEffect.byIngredient(s.getItem());
        if (e != null) {
            arecipes.add(page(e));
            return;
        }
        // склянка воды, усилители и топливо - все зелья
        boolean any = s.getItem() == Items.potionitem && s.getItemDamage() == 0 || s.getItem() == Items.glowstone_dust || s.getItem() == Items.redstone
            || s.getItem() == Items.gunpowder || s.getItem() == net.divinerpg.utils.items.ArcanaItems.arcanium
            || s.getItem() == net.minecraft.item.Item.getItemFromBlock(com.divinerpgextended.registry.ModBlocks.arcaneBrewer);
        if (any) arecipes.addAll(pages());
    }

    @Override
    public void drawExtras(int recipe) {
        super.drawExtras(recipe);
        GuiDraw.drawString("»", ARROW_X + 2, gy(0) + 4, 0x404040, false);
        GuiDraw.drawString("+", gx(1) - 6, gy(0) + 4, 0x404040, false);
    }

    public static int maxHeight() {
        return height(9, 3);
    }
}
