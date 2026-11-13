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

/**
 * Рыболовный ящик зоны (мета = номер в {@link Zone}): правый клик - открыть, добро зоны падает в инвентарь. Шахтёрские
 * ящики пещер - мета {@link FishingLoot#CRATE_MINE} + ярус (верхние, средние, глубины); старый ящик пещер открывается как
 * шахтёрский средних пещер.
 */
public class ItemCrate extends Item {

    private static final String[] TIER_KEYS = { "mine_upper", "mine_middle", "mine_deep" };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons, mineIcons;

    public ItemCrate() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
        setUnlocalizedName("dre.crate");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private static Zone zone(ItemStack s) {
        int m = s.getItemDamage();
        if (mineTier(m) >= 0) return Zone.CAVE;
        return Zone.values()[m >= 0 && m < Zone.values().length ? m : 0];
    }

    /** Ярус шахтёрского ящика (мета CRATE_MINE + ярус) или -1. */
    private static int mineTier(int m) {
        return m >= FishingLoot.CRATE_MINE && m < FishingLoot.CRATE_MINE + FishingLoot.TIERS ? m - FishingLoot.CRATE_MINE : -1;
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        int t = mineTier(s.getItemDamage());
        return "item.dre.crate." + (t >= 0 ? TIER_KEYS[t] : zone(s).key());
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote) return s;
        int tier = FishingLoot.crateTier(s.getItemDamage());
        for (ItemStack loot : FishingLoot.crate(w.rand, zone(s), tier)) {
            if (!p.inventory.addItemStackToInventory(loot)) {
                w.spawnEntityInWorld(new EntityItem(w, p.posX, p.posY + 0.5, p.posZ, loot));
            }
        }
        w.playSoundAtEntity(p, "random.chestopen", 0.8F, 1.0F);
        com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.fishCrate);
        if (!p.capabilities.isCreativeMode) s.stackSize--;
        p.inventoryContainer.detectAndSendChanges();
        return s;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[Zone.values().length];
        for (Zone z : Zone.values()) icons[z.ordinal()] = reg.registerIcon("divinerpgextended:crate_" + z.key());
        mineIcons = new IIcon[TIER_KEYS.length];
        for (int t = 0; t < TIER_KEYS.length; t++) mineIcons[t] = reg.registerIcon("divinerpgextended:crate_" + TIER_KEYS[t]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int m) {
        int t = mineTier(m);
        if (t >= 0) return mineIcons[t];
        return icons[m >= 0 && m < icons.length ? m : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.addAll(FishingLoot.crateKinds()); // вместо ящика пещер - три шахтёрских
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        int tier = FishingLoot.crateTier(s.getItemDamage());
        if (tier >= 0) list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("dre.tooltip.crate.mine",
            StatCollector.translateToLocal("dre.tooltip.crate.tier." + tier)));
        else if (zone(s) == Zone.HELL) list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.crate.lava"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.crate"));
    }
}
