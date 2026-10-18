package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Навесные рельсы арканы: как навесные рельсы Апалачии, но разгоняют вагонетку Апалачии (как золотые, только без
 * редстоуна). Ставятся предметом Рельсы арканы на стену и потолок, на пол - рельсы арканы Divine.
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
