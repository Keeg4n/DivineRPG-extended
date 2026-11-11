package com.divinerpgextended.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;

import com.divinerpgextended.Config;
import com.divinerpgextended.item.ArmorCore;
import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.item.ItemWandererArmor;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.ArmorTier;

/**
 * NEI: Наковальня Странника, слоты как в ней самой: сверху броня, ниже донорский сет, внизу топливо, справа - что получится.
 * Ковка R1: финальный сет обычного мира (у Края цвета перебираются) и его плата. Закалка R2-R7: сет Странника прошлой ступени,
 * сет брони следующего мира в доноре, камни мира. R8: сет R7, сет свойства Венца в доноре, Дух вселенной. Страница открывается с той
 * частью, которую искали.
 */
public class AnvilHandler extends DreHandler {

    public static final String ID = "dre.anvil";
    private static final int ROW1 = 14, ROW2 = 34, FUEL_Y = 56, OUT_X = 104, ARROW_X = 84;
    private static final int ARMOR_C = 0xFF8B8B8B, DONOR_C = 0xFF3F6FFF, FUEL_C = 0xFFE0A020, OUT_C = 0xFF3FBF3F;

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("dre.nei.anvil");
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    private static ItemStack wanderer(int type, int tier, ArmorCore core) {
        return ItemWandererArmor.stack(ModItems.wanderer[type], tier, core);
    }

    private Page forge(ArmorCore c, int want) {
        Page p = new Page(StatCollector.translateToLocalFormatted("dre.nei.anvil.forge", StatCollector.translateToLocal("dre.core." + c.key() + ".name")));
        Item[][] sets = c.variants();
        for (int i = 0; i < 4; i++) {
            List<ItemStack> l = new ArrayList<ItemStack>();
            for (Item[] set : sets) l.add(new ItemStack(set[i]));
            p.add(l, 3 + i * 18, ROW1, ARMOR_C, null, true);
        }
        if (c.fuelCount() > 0) p.add(new ItemStack(c.fuel(), c.fuelCount()), 3, FUEL_Y, FUEL_C, null, true);
        outputs(p, 1, c, want);
        p.notes.add(StatCollector.translateToLocal("dre.nei.anvil.no_donor"));
        return p;
    }

    private Page upgrade(ArmorTier t, int want) {
        Page p = new Page(StatCollector.translateToLocalFormatted("dre.nei.anvil.upgrade", t.tier));
        Item[] donor = t.donorSet();
        for (int i = 0; i < 4; i++) {
            p.add(wanderer(i, t.tier - 1, ArmorCore.DIVINE), 3 + i * 18, ROW1, ARMOR_C, null, true);
            p.add(new ItemStack(donor[i]), 3 + i * 18, ROW2, DONOR_C, null, true);
        }
        p.add(new ItemStack(t.fuel(), Config.upgradeTwilightFuelCount), 3, FUEL_Y, FUEL_C, null, true);
        outputs(p, t.tier, ArmorCore.DIVINE, want);
        p.notes.add(StatCollector.translateToLocalFormatted("dre.nei.anvil.boss", StatCollector.translateToLocal(t.boss.langKey())));
        return p;
    }

    private Page crown(com.divinerpgextended.item.ArmorCrown c, int want) {
        Page p = new Page(StatCollector.translateToLocalFormatted("dre.nei.anvil.crown", StatCollector.translateToLocal("dre.crown." + c.key() + ".name")));
        Item[] donor = c.perk.donorSet();
        for (int i = 0; i < 4; i++) {
            p.add(wanderer(i, 7, ArmorCore.DIVINE), 3 + i * 18, ROW1, ARMOR_C, null, true);
            p.add(new ItemStack(donor[i]), 3 + i * 18, ROW2, DONOR_C, null, true);
        }
        ItemStack fuel = com.divinerpgextended.station.AnvilLogic.r8Fuel();
        fuel.stackSize = Math.max(1, Config.upgradeR8Spirits);
        p.add(fuel, 3, FUEL_Y, FUEL_C, null, true);
        outputs(p, 8, ArmorCore.DIVINE, want, c);
        p.notes.add(StatCollector.translateToLocalFormatted("dre.nei.anvil.boss", StatCollector.translateToLocal(com.divinerpgextended.boss.Boss.LADY_LUNA.langKey())));
        return p;
    }

    /** Четыре части результата квадратом; искомая - результат страницы. */
    private void outputs(Page p, int tier, ArmorCore core, int want) {
        outputs(p, tier, core, want, null);
    }

    private void outputs(Page p, int tier, ArmorCore core, int want, com.divinerpgextended.item.ArmorCrown crown) {
        for (int i = 0; i < 4; i++) {
            ItemStack out = crown == null ? wanderer(i, tier, core) : ItemWandererArmor.stack(ModItems.wanderer[i], crown);
            PositionedStack ps = p.add(out, OUT_X + i % 2 * 18, ROW1 + i / 2 * 20, OUT_C, null, false);
            if (i == want) p.result = ps;
        }
        p.others.remove(p.result);
        p.noteY = FUEL_Y + 22;
    }

    private List<Page> pages(int want) {
        List<Page> l = new ArrayList<Page>();
        for (ArmorCore c : ArmorCore.values()) l.add(forge(c, want));
        for (ArmorTier t : ArmorTier.values()) l.add(upgrade(t, want));
        for (com.divinerpgextended.item.ArmorCrown c : com.divinerpgextended.item.ArmorCrown.values()) l.add(crown(c, want));
        return l;
    }

    @Override
    public void loadCraftingRecipes(String id, Object... results) {
        if (ID.equals(id) || "all".equals(id)) arecipes.addAll(pages(1));
        else super.loadCraftingRecipes(id, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result == null || !(result.getItem() instanceof ItemWandererArmor)) return;
        int type = ((ItemWandererArmor) result.getItem()).armorType, tier = ArmorData.tier(result);
        if (tier == 1) arecipes.add(forge(ArmorData.core(result), type));
        else if (tier >= 8 && ArmorData.crown(result) != null) arecipes.add(crown(ArmorData.crown(result), type));
        else if (ArmorTier.next(tier - 1) != null) arecipes.add(upgrade(ArmorTier.next(tier - 1), type));
    }

    @Override
    public void loadUsageRecipes(ItemStack s) {
        if (s == null) return;
        if (s.getItem() instanceof ItemWandererArmor) { // броня Странника идёт только в закалку следующей ступени
            ArmorTier next = ArmorTier.next(ArmorData.tier(s));
            if (next != null) arecipes.add(upgrade(next, ((ItemWandererArmor) s.getItem()).armorType));
            if (ArmorData.tier(s) == 7) for (com.divinerpgextended.item.ArmorCrown c : com.divinerpgextended.item.ArmorCrown.values())
                arecipes.add(crown(c, ((ItemWandererArmor) s.getItem()).armorType));
            return;
        }
        for (Page p : pages(1)) if (p.has(s)) arecipes.add(p);
    }

    /** Пустые ячейки донора и топлива тоже видны: страница похожа на саму наковальню. */
    @Override
    public void drawBackground(int recipe) {
        Page p = (Page) arecipes.get(recipe);
        for (int i = 0; i < 4; i++) if (!occupied(p, 3 + i * 18, ROW2)) slot(3 + i * 18, ROW2, DONOR_C);
        if (!occupied(p, 3, FUEL_Y)) slot(3, FUEL_Y, FUEL_C);
        super.drawBackground(recipe);
    }

    private static boolean occupied(Page p, int x, int y) {
        for (PositionedStack ps : p.all) if (ps.relx == x && ps.rely == y) return true;
        return false;
    }

    @Override
    public void drawExtras(int recipe) {
        super.drawExtras(recipe);
        GuiDraw.drawString("»", ARROW_X, ROW1 + 14, 0x404040, false);
    }
}
