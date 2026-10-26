package com.divinerpgextended.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import codechicken.lib.gui.GuiDraw;

import com.divinerpgextended.fishing.FishingLoot.View;
import com.divinerpgextended.vethea.Infusion;

/** NEI: алтарь наполнения Ветеи - глыбы (сколько) + шаблон = результат; рецепты Divine и аддона (крылья, лунное оружие). */
public class InfusionHandler extends DreHandler {

    public static final String ID = "dre.infusion";
    private static final int ARROW_X = 42;

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("dre.nei.infusion");
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    private Page page(Infusion.Recipe r) {
        Page p = new Page(StatCollector.translateToLocal("dre.nei.infusion"));
        ItemStack lump = r.lump.copy();
        lump.stackSize = r.count;
        p.add(lump, gx(0), gy(0), 0xFF8B8B8B, null, true);
        p.add(r.template.copy(), gx(1), gy(0), tierColor(View.UNCOMMON), null, true);
        p.result = p.add(r.output.copy(), gx(3), gy(0), tierColor(View.RARE), null, false);
        p.others.remove(p.result);
        p.noteY = gy(0) + 20;
        p.notes.add(StatCollector.translateToLocal("dre.nei.infusion.how"));
        return p;
    }

    private List<Page> pages() {
        List<Page> l = new ArrayList<Page>();
        for (Infusion.Recipe r : Infusion.all()) l.add(page(r));
        return l;
    }

    @Override
    public void loadCraftingRecipes(String id, Object... results) {
        if (ID.equals(id) || "all".equals(id)) arecipes.addAll(pages());
        else super.loadCraftingRecipes(id, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (Infusion.Recipe r : Infusion.all()) if (same(r.output, result) && tagMatch(r.output, result)) arecipes.add(page(r));
    }

    @Override
    public void loadUsageRecipes(ItemStack s) {
        for (Infusion.Recipe r : Infusion.all()) if (same(r.lump, s) || same(r.template, s) && tagMatch(r.template, s)) arecipes.add(page(r));
    }

    /** Закалённые крылья отличаются только меткой. */
    private static boolean tagMatch(ItemStack a, ItemStack b) {
        return Infusion.tempered(a) == Infusion.tempered(b);
    }

    @Override
    public void drawExtras(int recipe) {
        super.drawExtras(recipe);
        GuiDraw.drawString("»", ARROW_X + 2, gy(0) + 4, 0x404040, false);
    }
}
