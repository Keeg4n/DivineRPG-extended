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

import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * World keys (meta - TwilightWorld dimension number): open the reliquary of their own dimension
 * ({@link com.divinerpgextended.block.BlockLockedChest}). Craft: any Arcana key in the middle and 4 world stones in a cross. The icon is
 * Divine's key, recolored to the dimension's color at startup ({@link com.divinerpgextended.client.ChestTextures#keyPath}).
 */
public class ItemWorldKey extends Item {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemWorldKey() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
        setUnlocalizedName("dre.worldKey");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static TwilightWorld world(ItemStack s) {
        TwilightWorld[] v = TwilightWorld.values();
        int m = s.getItemDamage();
        return v[m >= 0 && m < v.length ? m : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.worldKey." + world(s).key();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (TwilightWorld w : TwilightWorld.values()) list.add(new ItemStack(item, 1, w.ordinal()));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[TwilightWorld.values().length];
        for (TwilightWorld w : TwilightWorld.values()) icons[w.ordinal()] = reg.registerIcon("divinerpgextended:worldKey_" + w.key());
        itemIcon = icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[meta >= 0 && meta < icons.length ? meta : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List l, boolean adv) {
        l.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.world_key",
            StatCollector.translateToLocal("tile.dre.lockedChest." + world(s).key() + ".name")));
    }

    /** Craft: any of the four Arcana keys and 4 world stones in a cross. */
    public static void recipes(Item key) {
        Item[] arcana = { net.divinerpg.utils.items.ArcanaItems.sludgeKey, net.divinerpg.utils.items.ArcanaItems.ancientKey,
            net.divinerpg.utils.items.ArcanaItems.soulKey, net.divinerpg.utils.items.ArcanaItems.degradedKey };
        for (TwilightWorld w : TwilightWorld.values()) for (Item a : arcana)
            if (a != null && w.gem() != null) GameRegistry.addRecipe(new ItemStack(key, 1, w.ordinal()), " G ", "GKG", " G ", 'G', w.gem(), 'K', a);
    }
}
