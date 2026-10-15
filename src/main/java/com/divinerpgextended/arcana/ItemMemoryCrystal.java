package com.divinerpgextended.arcana;

import java.util.List;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.cold.ColdHandler;

/** Кристалл памяти: выдаётся в Аркане, ПКМ - окно встреченных торговцев. Выбросить нельзя, вне Арканы исчезает. */
public class ItemMemoryCrystal extends Item {

    public ItemMemoryCrystal() {
        setUnlocalizedName("dre.memoryCrystal");
        setTextureName("divinerpgextended:memoryCrystal");
        setMaxStackSize(1);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote || !(p instanceof EntityPlayerMP)) return s;
        if (w.provider.dimensionId != ConfigurationHelper.arcana) {
            p.addChatMessage(new ChatComponentTranslation("dre.chat.crystal_only_arcana"));
            return s;
        }
        ColdHandler.NET.sendTo(ArcanaMerchants.openMessage(p), (EntityPlayerMP) p);
        return s;
    }

    /** Брошенный кристалл сразу рассыпается: он личный. */
    @Override
    public boolean onEntityItemUpdate(EntityItem e) {
        if (!e.worldObj.isRemote) e.setDead();
        return true;
    }

    @Override
    public boolean hasEffect(ItemStack s, int pass) {
        return true;
    }

    @Override
    public EnumRarity getRarity(ItemStack s) {
        return EnumRarity.rare;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("dre.tooltip.memory_crystal1"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.memory_crystal2"));
    }
}
