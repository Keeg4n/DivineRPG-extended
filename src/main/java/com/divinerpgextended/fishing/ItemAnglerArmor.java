package com.divinerpgextended.fishing;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.EnumHelper;

/** Набор рыбака (шляпа, жилет, штаны) - награды Леорны: каждая часть добавляет силу рыбалки. */
public class ItemAnglerArmor extends ItemArmor {

    public static final ArmorMaterial MATERIAL = EnumHelper.addArmorMaterial("DRE_ANGLER", 12, new int[] { 2, 4, 3, 1 }, 15);
    private static final String[] NAMES = { "anglerHat", "anglerVest", "anglerPants" };

    public ItemAnglerArmor(int type) {
        super(MATERIAL, 0, type);
        setUnlocalizedName("dre." + NAMES[type]);
        setTextureName("divinerpgextended:" + NAMES[type]);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static String name(int type) {
        return NAMES[type];
    }

    @Override
    public String getArmorTexture(ItemStack s, Entity e, int slot, String type) {
        return "divinerpgextended:textures/models/armor/angler_layer_" + (armorType == 2 ? 2 : 1) + ".png";
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.AQUA
            + StatCollector.translateToLocalFormatted("dre.tooltip.angler_piece", FishingGear.PIECE_POWER));
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("dre.tooltip.angler_set", FishingGear.SET_POWER));
    }
}
