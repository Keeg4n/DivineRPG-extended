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
import com.divinerpgextended.world.twilight.TwilightMaterial;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Материалы мобов сумеречных миров (мета - {@link TwilightMaterial}) и печати святилища (отдельный предмет, мета -
 * {@link CadillionWorld}): печать лежит в подземельях островов и нужна для Зова кадиллиона.
 */
public class ItemTwilightMaterial extends Item {

    private final boolean seal;
    private IIcon[] icons;

    public ItemTwilightMaterial(boolean seal) {
        this.seal = seal;
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(seal ? 16 : 64);
        setUnlocalizedName(seal ? "dre.sanctumSeal" : "dre.twilightMaterial");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private int count() {
        return seal ? CadillionWorld.values().length : TwilightMaterial.values().length;
    }

    private String key(int meta) {
        return seal ? CadillionWorld.byMeta(meta).key() : TwilightMaterial.byMeta(meta).key();
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return (seal ? "item.dre.sanctumSeal." : "item.dre.twilightMaterial.") + key(s.getItemDamage());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[count()];
        for (int i = 0; i < icons.length; i++) {
            icons[i] = reg.registerIcon("divinerpgextended:" + (seal ? "sanctum_seal_" : "twilight_mat_") + key(i));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[Math.max(0, Math.min(meta, icons.length - 1))];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < count(); i++) list.add(new ItemStack(item, 1, i));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(seal ? "dre.tooltip.sanctum_seal" : "dre.tooltip.twilight_material"));
    }
}
