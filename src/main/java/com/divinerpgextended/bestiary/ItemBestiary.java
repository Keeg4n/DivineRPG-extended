package com.divinerpgextended.bestiary;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/** Бестиарий: ПКМ - открыть книгу (прогресс приходит с сервера). Продаёт торговец скотом. */
public class ItemBestiary extends Item {

    public ItemBestiary() {
        setUnlocalizedName("dre.bestiary");
        setTextureName("divinerpgextended:bestiary");
        setMaxStackSize(1);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (!w.isRemote) Bestiary.open(p);
        return s;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.bestiary.desc"));
    }
}
