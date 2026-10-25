package com.divinerpgextended.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;

import com.divinerpgextended.Config;
import com.divinerpgextended.fishing.Bait;
import com.divinerpgextended.fishing.FishingEvents;
import com.divinerpgextended.fishing.Fish;
import com.divinerpgextended.fishing.FishingLoot;
import com.divinerpgextended.fishing.FishingLoot.View;
import com.divinerpgextended.fishing.Rod;
import com.divinerpgextended.registry.ModItems;

/** NEI: удочки у торговцев (Леорна, Джек о'Мэн, Голод) и награды за рыбные задания Леорны. */
public class TradeHandler extends DreHandler {

    public static final String ID = "dre.trades";
    private static final int ARROW_X = 42;

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("dre.nei.trades");
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    private static ItemStack rod(Rod r) {
        return new ItemStack(ModItems.rods[r.ordinal()]);
    }

    private static List<String> lines(String... keys) {
        List<String> l = new ArrayList<String>();
        for (String k : keys) l.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(k));
        return l;
    }

    private Page trade(String who, ItemStack a, ItemStack b, ItemStack out) {
        Page p = new Page(StatCollector.translateToLocal("dre.nei.merchant." + who));
        p.add(a, gx(0), gy(0), 0xFF8B8B8B, null, true);
        if (b != null) p.add(b, gx(1), gy(0), 0xFF8B8B8B, null, true);
        p.result = p.add(out, gx(3), gy(0), tierColor(View.RARE), null, false);
        p.others.remove(p.result);
        p.noteY = gy(0) + 20;
        p.notes.add(StatCollector.translateToLocal("dre.nei.merchant." + who + ".where"));
        return p;
    }

    private Page quest() {
        Page p = new Page(StatCollector.translateToLocal("dre.nei.merchant.quest"));
        List<ItemStack> fish = new ArrayList<ItemStack>();
        List<ItemStack> crates = new ArrayList<ItemStack>();
        for (Fish f : FishingLoot.questFish()) {
            fish.add(new ItemStack(ModItems.fish, 1, f.ordinal()));
            crates.add(new ItemStack(ModItems.crate, 1, f.zone.ordinal()));
        }
        List<ItemStack> baits = new ArrayList<ItemStack>();
        for (Bait b : Bait.values()) if (b.arcana) baits.add(new ItemStack(b.item(), 3));
        p.add(fish, gx(0), gy(0), tierColor(View.CRATE), lines("dre.nei.quest.fish"), true);
        p.result = p.add(new ItemStack(ArcanaItems.arcanium, 2), gx(3), gy(0), tierColor(View.UNCOMMON),
            lines("dre.nei.quest.each"), false);
        p.others.remove(p.result);
        p.add(baits, gx(4), gy(0), tierColor(View.BAIT), lines("dre.nei.quest.each"), false);
        p.add(crates, gx(5), gy(0), tierColor(View.CRATE), lines("dre.nei.quest.fifth"), false);
        // лестница наград - второй ряд
        int i = COLS;
        for (int m : FishingEvents.MILESTONES) {
            List<String> tip = new ArrayList<String>();
            tip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("dre.nei.quest.nth", m));
            p.add(FishingEvents.milestone(m), gx(i), gy(i), tierColor(View.VERY_RARE), tip, false);
            i++;
        }
        p.noteY = gy(i - 1) + 20;
        p.notes.add(StatCollector.translateToLocal("dre.nei.quest.how"));
        p.notes.add(StatCollector.translateToLocal("dre.nei.quest.daily"));
        return p;
    }

    private Page worms() {
        Page p = new Page(StatCollector.translateToLocal("dre.nei.worms"));
        p.add(new ItemStack(ModItems.wormShovel), gx(0), gy(0), tierColor(View.RARE), null, true);
        p.add(java.util.Arrays.asList(new ItemStack(Blocks.dirt), new ItemStack(Blocks.grass), new ItemStack(Blocks.farmland)),
            gx(1), gy(0), 0xFF8B8B8B, null, true);
        p.result = p.add(new ItemStack(ModItems.worm), gx(3), gy(0), tierColor(View.BAIT), null, false);
        p.others.remove(p.result);
        p.noteY = gy(0) + 20;
        p.notes.add(StatCollector.translateToLocalFormatted("dre.nei.worms.chance", (int) Math.round(Config.wormChance * 100),
            (int) Math.round(Config.wormRainChance * 100)));
        p.notes.add(StatCollector.translateToLocal("dre.nei.worms.only"));
        return p;
    }

    private List<Page> pages() {
        return Arrays.asList(
            trade("leorna", new ItemStack(ArcanaItems.arcanium, 12), rod(Rod.RUPEE), rod(Rod.ARCANA)),
            trade("jack", new ItemStack(Items.bone, 40), new ItemStack(Blocks.pumpkin, 16), rod(Rod.JACK)),
            trade("hunger", new ItemStack(VetheaItems.polishedPearls, 16), rod(Rod.MORTUM), rod(Rod.HUNGER)),
            // рельсы арканы Divine у Датикона в инвентаре сразу становятся рельсами аддона
            trade("datticon", new ItemStack(ArcanaItems.arcanium, 5), null, new ItemStack(ModItems.arcanaRails, 8)),
            quest(), worms());
    }

    @Override
    public void loadCraftingRecipes(String id, Object... results) {
        if (ID.equals(id) || "all".equals(id)) arecipes.addAll(pages());
        else super.loadCraftingRecipes(id, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (Page p : pages()) {
            if (contains(p.others, result) || p.result != null && contains(Arrays.asList(p.result), result)) arecipes.add(p);
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack s) {
        for (Page p : pages()) if (contains(p.inputs, s)) arecipes.add(p);
    }

    private static boolean contains(List<PositionedStack> l, ItemStack s) {
        for (PositionedStack ps : l) for (ItemStack it : ps.items) if (same(it, s)) return true;
        return false;
    }

    @Override
    public void drawExtras(int recipe) {
        super.drawExtras(recipe);
        GuiDraw.drawString("»", ARROW_X + 2, gy(0) + 4, 0x404040, false);
    }

    public static int maxHeight() {
        return height(2 * COLS, 2);
    }
}
