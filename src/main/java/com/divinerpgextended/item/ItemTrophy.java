package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/** Простой трофей босса с подсказкой (Ледяное сердце, Осколок Трона). */
public class ItemTrophy extends Item {

    private final String tooltip;
    private final boolean glow;

    public ItemTrophy(String name, String texture, String tooltip, boolean glow) {
        this.tooltip = tooltip;
        this.glow = glow;
        setUnlocalizedName(name);
        setTextureName(texture);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return glow;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal(tooltip));
    }
}
