package com.divinerpgextended.arcana;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Модуль печи Даттикона: 0 - удвоение, 1 - ускорение. Присесть + ПКМ по печи Divine - поставить. */
public class ItemFurnaceModule extends Item {

    private static final String[] TYPES = { "double", "speed" };
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemFurnaceModule() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
        setUnlocalizedName("dre.furnaceModule");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private static int type(ItemStack s) {
        return s.getItemDamage() == 1 ? 1 : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.furnaceModule." + TYPES[type(s)];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[TYPES.length];
        for (int i = 0; i < TYPES.length; i++) icons[i] = reg.registerIcon("divinerpgextended:furnaceModule_" + TYPES[i]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int m) {
        return icons[m == 1 ? 1 : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < TYPES.length; i++) list.add(new ItemStack(item, 1, i));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.furnace_module." + TYPES[type(s)]));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.furnace_module.how"));
    }
}
