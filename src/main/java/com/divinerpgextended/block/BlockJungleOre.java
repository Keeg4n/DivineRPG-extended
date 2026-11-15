package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.divinerpgextended.halite.HaliteTools;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Jungle Ore: only in the jungle, deep underground. Only a Halite Pickaxe or Multitool takes it - with others
 * it cannot be mined at all. Gives Jungle Gems (for the Ancient Dramcryx Totem).
 */
public class BlockJungleOre extends Block {

    public BlockJungleOre() {
        super(Material.rock);
        setHardness(6.0F);
        setResistance(20.0F);
        setStepSound(soundTypeStone);
        setBlockName("dre.jungleOre");
        setBlockTextureName("divinerpgextended:jungleOre");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return ModItems.jungleGem;
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, Random r) {
        return 1 + (fortune > 0 ? r.nextInt(fortune + 1) : 0);
    }

    @Override
    public int getExpDrop(net.minecraft.world.IBlockAccess w, int meta, int fortune) {
        return 4 + new Random().nextInt(5);
    }

    /** Only the Halite Pickaxe and Multitool. */
    public static class Handler {

        @SubscribeEvent
        public void onSpeed(PlayerEvent.BreakSpeed e) {
            if (e.block instanceof BlockJungleOre && !HaliteTools.minesJungle(e.entityPlayer.getHeldItem())) e.newSpeed = 0;
        }

        @SubscribeEvent
        public void onHarvest(PlayerEvent.HarvestCheck e) {
            if (e.block instanceof BlockJungleOre) e.success = HaliteTools.minesJungle(e.entityPlayer.getHeldItem());
        }
    }
}
