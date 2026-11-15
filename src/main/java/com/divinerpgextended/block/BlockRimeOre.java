package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;

/** Rime Ore: deep in Iceika, in frozen stone. Mined with an iron pickaxe and better, smelts into a Rime Ingot. */
public class BlockRimeOre extends Block {

    public BlockRimeOre() {
        super(Material.rock);
        setHardness(3.0F);
        setResistance(5.0F);
        setStepSound(soundTypePiston);
        setBlockName("dre.rimeOre");
        setBlockTextureName("divinerpgextended:rimeOre");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        setHarvestLevel("pickaxe", 2);
    }
}
