package com.divinerpgextended.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

import com.divinerpgextended.fishing.FishingLoot.View;

/** Общее для страниц NEI аддона: сетка ячеек с цветной рамкой по редкости, заголовок и пояснения. */
public abstract class DreHandler extends TemplateRecipeHandler {

    public static final int COLS = 9, GRID_X = 3, GRID_Y = 13;

    /** Строка в ячейку сетки по номеру. */
    static int gx(int i) {
        return GRID_X + (i % COLS) * 18;
    }

    static int gy(int i) {
        return GRID_Y + (i / COLS) * 18;
    }

    /** Высота страницы под n предметов в сетке и строки пояснений. */
    public static int height(int n, int noteLines) {
        return GRID_Y + ((n + COLS - 1) / COLS) * 18 + noteLines * 10 + 2;
    }

    static int tierColor(int tier) {
        switch (tier) {
            case View.UNCOMMON: return 0xFF3FBF3F;
            case View.RARE: return 0xFF3F6FFF;
            case View.VERY_RARE: return 0xFFC040F0;
            case View.CRATE: return 0xFFE0A020;
            case View.BAIT: return 0xFF30C8C8;
            case View.JUNK: return 0xFF5A4A3A;
            case View.RELIC: return 0xFFFF5020;
            default: return 0xFF8B8B8B;
        }
    }

    static EnumChatFormatting tierChat(int tier) {
        switch (tier) {
            case View.UNCOMMON: return EnumChatFormatting.GREEN;
            case View.RARE: return EnumChatFormatting.BLUE;
            case View.VERY_RARE: return EnumChatFormatting.LIGHT_PURPLE;
            case View.CRATE: return EnumChatFormatting.GOLD;
            case View.BAIT: return EnumChatFormatting.AQUA;
            case View.JUNK: return EnumChatFormatting.DARK_GRAY;
            case View.RELIC: return EnumChatFormatting.RED;
            default: return EnumChatFormatting.GRAY;
        }
    }

    static List<String> tip(View v) {
        List<String> l = new ArrayList<String>();
        l.add(tierChat(v.tier) + StatCollector.translateToLocal("dre.nei.tier." + v.tier));
        if (v.max > 1) l.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.nei.count", v.min, v.max));
        return l;
    }

    static boolean same(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getItem() != b.getItem()) return false;
        return !a.getHasSubtypes() || a.getItemDamage() == b.getItemDamage() || b.getItemDamage() == Short.MAX_VALUE;
    }

    /** Страница: ячейки с цветами рамок, результат (одна из ячеек), заголовок и пояснения. */
    public class Page extends CachedRecipe {

        final String title;
        final List<String> notes = new ArrayList<String>();
        final List<PositionedStack> inputs = new ArrayList<PositionedStack>();
        final List<PositionedStack> others = new ArrayList<PositionedStack>();
        final List<Integer> colors = new ArrayList<Integer>();
        final List<PositionedStack> all = new ArrayList<PositionedStack>();
        PositionedStack result;
        int noteY;

        Page(String title) {
            this.title = title;
        }

        PositionedStack add(Object stacks, int x, int y, int color, List<String> tooltip, boolean input) {
            PositionedStack ps = new PositionedStack(stacks, x, y, false);
            if (tooltip != null) ps.setTooltip(tooltip);
            (input ? inputs : others).add(ps);
            all.add(ps);
            colors.add(color);
            return ps;
        }

        /** Сетка из строк таблицы; последняя становится результатом страницы. */
        void grid(List<View> views, boolean asInputs) {
            grid(views, asInputs, 0);
        }

        void grid(List<View> views, boolean asInputs, int offset) {
            for (int i = 0; i < views.size(); i++) {
                View v = views.get(i);
                ItemStack s = v.stack.copy();
                add(s, gx(i + offset), gy(i + offset), tierColor(v.tier), tip(v), asInputs);
            }
            noteY = gy(views.size() + offset - 1) + 20;
            if (!asInputs && !others.isEmpty()) result = others.remove(others.size() - 1);
        }

        boolean has(ItemStack s) {
            for (PositionedStack ps : all) for (ItemStack it : ps.items) if (same(it, s)) return true;
            return false;
        }

        @Override
        public PositionedStack getResult() {
            return result;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return cycle(inputs);
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return cycle(others);
        }

        private List<PositionedStack> cycle(List<PositionedStack> l) {
            for (PositionedStack ps : l) {
                if (ps.items.length > 1) ps.setPermutationToRender((cycleticks / 24) % ps.items.length);
            }
            return l;
        }
    }

    @Override
    public String getGuiTexture() {
        return "textures/gui/container/furnace.png";
    }

    /** Невидимая зона перехода: без неё NEI не открывает вкладку по катализатору. */
    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new Rectangle(0, 0, 1, 1), getOverlayIdentifier()));
    }

    @Override
    public abstract String getOverlayIdentifier();

    @Override
    public void drawBackground(int recipe) {
        Page p = (Page) arecipes.get(recipe);
        GL11.glColor4f(1, 1, 1, 1);
        for (int i = 0; i < p.all.size(); i++) {
            PositionedStack ps = p.all.get(i);
            slot(ps.relx, ps.rely, p.colors.get(i));
        }
    }

    static void slot(int x, int y, int border) {
        GuiDraw.drawRect(x - 1, y - 1, 18, 18, border);
        GuiDraw.drawRect(x, y, 16, 16, 0xFF8B8B8B);
        GuiDraw.drawRect(x, y, 16, 1, 0xFF373737);
        GuiDraw.drawRect(x, y, 1, 16, 0xFF373737);
    }

    @Override
    public void drawExtras(int recipe) {
        Page p = (Page) arecipes.get(recipe);
        GuiDraw.drawString(p.title, 2, 1, 0x404040, false);
        int y = p.noteY;
        for (String n : p.notes) {
            GuiDraw.drawString(n, 2, y, 0x505050, false);
            y += 10;
        }
    }

    static List<String> none() {
        return Collections.emptyList();
    }
}
