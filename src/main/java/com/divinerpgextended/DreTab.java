package com.divinerpgextended;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.divinerpgextended.fishing.Rod;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Своя вкладка творческого режима: все предметы и блоки аддона в одном месте. */
public final class DreTab extends CreativeTabs {

    public static final DreTab TAB = new DreTab();

    private DreTab() {
        super("divinerpgextended");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getTabIconItem() {
        Item rod = ModItems.rods[Rod.HUNGER.ordinal()];
        return rod != null ? rod : ModItems.wandererClock;
    }
}
