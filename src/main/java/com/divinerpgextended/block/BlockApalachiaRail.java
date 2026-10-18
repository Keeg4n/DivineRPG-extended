package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.BlockRail;
import net.minecraft.item.Item;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Рельс Апалачии на полу: обычный ванильный рельс (повороты, наклоны, любые вагонетки) с текстурой Апалачии. Рельсы
 * Апалачии Divine сделаны на основе золотых и потому не поворачивают. Ставится предметом Рельсы Апалачии
 * ({@link com.divinerpgextended.item.ItemApalachiaRails}) и в него же ломается.
 */
public class BlockApalachiaRail extends BlockRail {

    public BlockApalachiaRail() {
        setHardness(0.7F);
        setStepSound(soundTypeMetal);
        setBlockName("dre.apalachiaRail");
        setBlockTextureName("divinerpgextended:apalachia_rail"); // и apalachia_rail_turned - поворот
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return ModItems.apalachiaRails;
    }

    @Override
    public Item getItem(World w, int x, int y, int z) {
        return ModItems.apalachiaRails;
    }
}
