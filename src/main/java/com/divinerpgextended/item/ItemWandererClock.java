package com.divinerpgextended.item;

import java.util.List;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * Часы Странника I-V: зажигают порталы сумеречных миров, как сумеречные часы Divine, но только до своего уровня.
 * I - Eden, II - Wildwood, III - Apalachia, IV - Skythern, V - Mortum. Уровень растёт шестернями времени.
 */
public class ItemWandererClock extends Item {

    public static final int MAX_TIER = 5;
    private static final String[] TARGET = { "eden", "wildwood", "apalachia", "skythern", "mortum" };

    public ItemWandererClock() {
        setMaxStackSize(1);
        setUnlocalizedName("dre.wandererClock");
        setTextureName("divinerpg:twilightClock");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static int tier(ItemStack stack) {
        return stack.getTagCompound() == null ? 1 : Math.max(1, stack.getTagCompound()
            .getInteger("tier"));
    }

    public static ItemStack withTier(Item item, int tier) {
        ItemStack s = new ItemStack(item);
        s.setTagCompound(new NBTTagCompound());
        s.getTagCompound()
            .setInteger("tier", tier);
        return s;
    }

    /** Какой уровень часов нужен, чтобы поджечь портал из этого блока (0 - не портальный блок). */
    private static int requiredTier(Block block) {
        if (block == VanillaBlocks.divineRock) return 1;
        if (block == TwilightBlocks.edenBlock) return 2;
        if (block == TwilightBlocks.wildwoodBlock) return 3;
        if (block == TwilightBlocks.apalachiaBlock) return 4;
        if (block == TwilightBlocks.skythernBlock) return 5;
        return 0;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hx, float hy, float hz) {
        if (side != 1 || !player.canPlayerEdit(x, y, z, side, stack) || !player.canPlayerEdit(x, y + 1, z, side, stack))
            return false;
        int need = requiredTier(world.getBlock(x, y, z));
        if (need == 0 || !world.isAirBlock(x, y + 1, z)) return false;
        if (tier(stack) < need) {
            if (!world.isRemote) {
                player.addChatMessage(new ChatComponentTranslation("dre.chat.clock_too_weak", need,
                    new ChatComponentTranslation("dre.world." + TARGET[need - 1])));
            }
            return true;
        }
        world.setBlock(x, y + 1, z, TwilightBlocks.blueFire);
        return true;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int t = 1; t <= MAX_TIER; t++) list.add(withTier(item, t));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        int t = tier(stack);
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("dre.tooltip.clock_tier", t,
            StatCollector.translateToLocal("dre.world." + TARGET[t - 1])));
        if (t < MAX_TIER) {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.clock_next",
                StatCollector.translateToLocal("item.dre.timeGear." + TARGET[t - 1] + ".name")));
        }
    }
}
