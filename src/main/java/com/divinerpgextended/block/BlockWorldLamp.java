package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Twilight world lamp - a pair to Divine's Eden lamp (the same flickering frame, the world's color). Gives full light,
 * crafted as in Divine: iron around a world shard. Addon structures are lit with it instead of glowstone.
 */
public class BlockWorldLamp extends Block {

    public final TwilightWorld world;

    public BlockWorldLamp(TwilightWorld world) {
        super(Material.glass);
        this.world = world;
        setHardness(3.0F);
        setResistance(10.0F);
        setLightLevel(1.0F);
        setStepSound(soundTypeGlass);
        setBlockName("dre.worldLamp." + world.key());
        setBlockTextureName("divinerpgextended:world_lamp_" + world.key());
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }
}
