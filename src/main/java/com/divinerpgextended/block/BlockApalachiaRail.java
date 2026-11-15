package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.BlockRail;
import net.minecraft.item.Item;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Apalachia Rail on the floor: an ordinary vanilla rail (turns, slopes, any minecarts) with the Apalachia texture. Divine's Apalachia
 * Rails are made on a golden basis and so do not turn. Placed by the Apalachia Rails item
 * ({@link com.divinerpgextended.item.ItemApalachiaRails}) and breaks back into it.
 */
public class BlockApalachiaRail extends BlockRail {

    public BlockApalachiaRail() {
        setHardness(0.7F);
        setStepSound(soundTypeMetal);
        setBlockName("dre.apalachiaRail");
        setBlockTextureName("divinerpgextended:apalachia_rail"); // and apalachia_rail_turned - the turn
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
