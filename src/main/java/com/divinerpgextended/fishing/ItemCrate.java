package com.divinerpgextended.fishing;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Рыболовный ящик зоны (мета = номер в {@link Zone}): правый клик - открыть, добро зоны падает в инвентарь. */
public class ItemCrate extends Item {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemCrate() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
        setUnlocalizedName("dre.crate");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private static Zone zone(ItemStack s) {
        int m = s.getItemDamage();
        return Zone.values()[m >= 0 && m < Zone.values().length ? m : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.crate." + zone(s).key();
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote) return s;
        for (ItemStack loot : FishingLoot.crate(w.rand, zone(s))) {
            if (!p.inventory.addItemStackToInventory(loot)) {
                w.spawnEntityInWorld(new EntityItem(w, p.posX, p.posY + 0.5, p.posZ, loot));
            }
        }
        w.playSoundAtEntity(p, "random.chestopen", 0.8F, 1.0F);
        if (!p.capabilities.isCreativeMode) s.stackSize--;
        p.inventoryContainer.detectAndSendChanges();
        return s;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[Zone.values().length];
        for (Zone z : Zone.values()) icons[z.ordinal()] = reg.registerIcon("divinerpgextended:crate_" + z.key());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int m) {
        return icons[m >= 0 && m < icons.length ? m : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (Zone z : Zone.values()) list.add(new ItemStack(item, 1, z.ordinal()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.crate"));
    }
}
