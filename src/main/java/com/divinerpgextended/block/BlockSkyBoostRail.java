package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Arcana overhead rails: like Apalachia overhead rails, but they accelerate the Apalachia Cart (like golden ones, only without
 * redstone). Placed by the Arcana Rails item on a wall and ceiling, on the floor - Divine arcana rails.
 */
public class BlockSkyBoostRail extends BlockSkyRail {

    public BlockSkyBoostRail() {
        setBlockName("dre.skyBoostRail");
        setBlockTextureName("divinerpg:arcaniteRails");
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return ModItems.arcanaRails;
    }

    @Override
    public Item getItem(World w, int x, int y, int z) {
        return ModItems.arcanaRails;
    }
}
