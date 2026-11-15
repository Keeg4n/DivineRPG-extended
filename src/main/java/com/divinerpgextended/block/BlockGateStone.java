package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.divinerpgextended.world.CadillionWorld;

/**
 * Gate Stone: the portal frame to the next twilight world (the Eden Gate Stone - to Wildwood and so on). Assembled from
 * blocks of the world's stones around the guardian heart - dropped by the Great Cadillion of that world.
 */
public class BlockGateStone extends Block {

    public final CadillionWorld world;

    public BlockGateStone(CadillionWorld world) {
        super(Material.rock);
        this.world = world;
        setHardness(3.0F);
        setResistance(30.0F);
        setLightLevel(0.4F);
        setStepSound(soundTypeStone);
        setBlockName("dre.gateStone." + world.key());
        setBlockTextureName("divinerpgextended:gate_stone_" + world.key());
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }
}
