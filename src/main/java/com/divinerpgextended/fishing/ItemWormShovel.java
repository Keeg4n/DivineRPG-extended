package com.divinerpgextended.fishing;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Червекопалка: железная лопата, с которой из земли и травы иногда выкапываются черви (другие лопаты червей не дают). */
public class ItemWormShovel extends ItemSpade {

    public ItemWormShovel() {
        super(ToolMaterial.IRON);
        setUnlocalizedName("dre.wormShovel");
        setTextureName("divinerpgextended:wormShovel");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.worm_shovel"));
    }

    public static class Handler {

        @SubscribeEvent
        public void onDrops(BlockEvent.HarvestDropsEvent e) {
            EntityPlayer p = e.harvester;
            if (p == null || e.world.isRemote || e.isSilkTouching) return;
            ItemStack held = p.getCurrentEquippedItem();
            if (held == null || !(held.getItem() instanceof ItemWormShovel)) return;
            Block b = e.block;
            if (b.getMaterial() != Material.ground && b.getMaterial() != Material.grass) return;
            double chance = e.world.isRaining() ? Config.wormRainChance : Config.wormChance;
            if (e.world.rand.nextDouble() < chance) e.drops.add(new ItemStack(ModItems.worm, 1 + (e.world.rand.nextInt(4) == 0 ? 1 : 0)));
        }
    }
}
