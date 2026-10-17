package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.world.CadillionWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Сердце стража (мета - {@link CadillionWorld}): трофей Великого кадиллиона, из него собирают камни врат. */
public class ItemGuardianHeart extends Item {

    private IIcon[] icons;

    public ItemGuardianHeart() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
        setUnlocalizedName("dre.guardianHeart");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.guardianHeart." + CadillionWorld.byMeta(s.getItemDamage()).key();
    }

    @Override
    public EnumRarity getRarity(ItemStack s) {
        return EnumRarity.rare;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[CadillionWorld.values().length];
        for (CadillionWorld w : CadillionWorld.values()) icons[w.ordinal()] = reg.registerIcon("divinerpgextended:guardian_heart_" + w.key());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[CadillionWorld.byMeta(meta).ordinal()];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (CadillionWorld w : CadillionWorld.values()) list.add(new ItemStack(item, 1, w.ordinal()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        int next = Math.min(s.getItemDamage() + 1, 4);
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.guardian_heart",
            StatCollector.translateToLocal("dre.world." + com.divinerpgextended.world.twilight.TwilightWorld.values()[next].key())));
    }
}
