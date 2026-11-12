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

/**
 * Шестерня времени сумеречного мира: падает со стража мира, улучшает Часы Странника и нужна в рецепте брони следующего мира.
 * В верстаке не тратится - остаётся в сетке, как ключ мира.
 */
public class ItemTimeGear extends Item {

    private IIcon[] icons;

    public ItemTimeGear() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1); // по одной: не тратится, а с двумя в ячейке лишняя выпала бы из сетки
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
        for (CadillionWorld w : CadillionWorld.values()) icons[w.ordinal()] = reg.registerIcon("divinerpgextended:time_gear_" + w.key());
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

    /** После крафта шестерня возвращается в свою ячейку. */
    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getContainerItem(ItemStack stack) {
        ItemStack back = stack.copy();
        back.stackSize = 1;
        return back;
    }

    @Override
    public boolean doesContainerItemLeaveCraftingGrid(ItemStack stack) {
        return false;
    }

    private static final String[] WORLDS = { "eden", "wildwood", "apalachia", "skythern", "mortum" };

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.time_gear",
            stack.getItemDamage() + 1, stack.getItemDamage() + 2));
        int next = Math.max(0, stack.getItemDamage()) + 1;
        if (next < WORLDS.length) list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.time_gear_armor",
            StatCollector.translateToLocal("dre.world." + WORLDS[next])));
        list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.tooltip.time_gear_keep"));
    }
}
