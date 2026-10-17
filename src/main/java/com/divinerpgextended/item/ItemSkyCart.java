package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.entity.EntitySkyCart;
import com.divinerpgextended.registry.ModBlocks;

/** Вагонетка Апалачии: ставится на рельс Апалачии (правый клик по нему). */
public class ItemSkyCart extends Item {

    public ItemSkyCart() {
        setMaxStackSize(1);
        setUnlocalizedName("dre.skyCart");
        setTextureName("minecart_normal");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public boolean onItemUse(ItemStack s, EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        if (w.getBlock(x, y, z) != ModBlocks.skyRail) return false;
        if (!w.isRemote) {
            w.spawnEntityInWorld(new EntitySkyCart(w, x, y, z, p.rotationYaw));
            if (!p.capabilities.isCreativeMode) s.stackSize--;
        }
        return true;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.sky_cart"));
    }
}
