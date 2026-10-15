package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.world.CadillionWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Шестерня времени сумеречного мира: падает с великого кадиллиона, улучшает Часы Странника. */
public class ItemTimeGear extends Item {

    private IIcon[] icons;

    public ItemTimeGear() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setUnlocalizedName("dre.timeGear");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item.dre.timeGear." + CadillionWorld.byMeta(stack.getItemDamage())
            .key();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[CadillionWorld.values().length];
        for (CadillionWorld w : CadillionWorld.values()) icons[w.ordinal()] = reg.registerIcon("divinerpg:" + w.key() + "Gem");
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icons[CadillionWorld.byMeta(meta)
            .ordinal()];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (CadillionWorld w : CadillionWorld.values()) list.add(new ItemStack(item, 1, w.ordinal()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.time_gear",
            stack.getItemDamage() + 1, stack.getItemDamage() + 2));
    }
}
