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

import com.divinerpgextended.world.twilight.WorldWood;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Палка мира (мета - {@link WorldWood}): из досок дерева мира, годится и как обычная палка (stickWood). */
public class ItemWorldStick extends Item {

    private IIcon[] icons;

    public ItemWorldStick() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setFull3D();
        setUnlocalizedName("dre.worldStick");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.worldStick." + WorldWood.byMeta(s.getItemDamage()).key();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[WorldWood.values().length];
        for (WorldWood w : WorldWood.values()) icons[w.ordinal()] = reg.registerIcon("divinerpgextended:world_stick_" + w.key());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[WorldWood.byMeta(meta).ordinal()];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (WorldWood w : WorldWood.values()) list.add(new ItemStack(item, 1, w.ordinal()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.world_stick"));
    }
}
