package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossDamage;
import com.divinerpgextended.entity.EntityGreatCadillion;
import com.divinerpgextended.world.CadillionWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Зов великого кадиллиона: призывает босса на алтаре его гнезда (или где угодно в его мире, если summonOnlyInLairs выключен). */
public class ItemCadillionCall extends Item {

    private static final int[] COLORS = { 0xF2D24B, 0x4FB848, 0xC45AD8, 0xD8E6F5 };

    public ItemCadillionCall() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setUnlocalizedName("dre.cadillionCall");
        setTextureName("divinerpg:baseSpawnCrystal");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item.dre.cadillionCall." + CadillionWorld.byMeta(stack.getItemDamage())
            .key();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return COLORS[CadillionWorld.byMeta(stack.getItemDamage())
            .ordinal()];
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hx, float hy, float hz) {
        if (world.isRemote) return true;
        CadillionWorld w = CadillionWorld.byMeta(stack.getItemDamage());
        if (world.provider.dimensionId != w.dimension()) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.call_wrong_world",
                new ChatComponentTranslation("dre.world." + w.key())));
            return true;
        }
        if (com.divinerpgextended.Config.summonOnlyInLairs && !player.capabilities.isCreativeMode) {
            // алтарь гнезда сам принимает Зов (клик по нему сюда не доходит); здесь - клик мимо алтаря
            player.addChatMessage(new ChatComponentTranslation("dre.chat.call_at_nest"));
            return true;
        }
        net.minecraft.entity.EntityLiving boss = com.divinerpgextended.entity.EntityGroveGuardian.create(world, w);
        boss.setLocationAndAngles(x + 0.5, y + 1, z + 0.5, player.rotationYaw, 0);
        if (!world.getCollidingBoundingBoxes(boss, boss.boundingBox)
            .isEmpty()) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.call_no_space"));
            return true;
        }
        world.spawnEntityInWorld(boss);
        if (!player.capabilities.isCreativeMode) stack.stackSize--;
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (CadillionWorld w : CadillionWorld.values()) list.add(new ItemStack(item, 1, w.ordinal()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        CadillionWorld w = CadillionWorld.byMeta(stack.getItemDamage());
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.cadillion_call",
            StatCollector.translateToLocal("dre.world." + w.key())));
        int req = BossDamage.requiredTier(Boss.cadillion(w.ordinal()));
        list.add(EnumChatFormatting.RED + StatCollector.translateToLocalFormatted("dre.tooltip.required_tier", req,
            StatCollector.translateToLocal("dre.tier." + req)));
    }
}
