package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Skythern Cloud: platforms under Wind Totems. Soft and slightly transparent, it can be walked on; a fall onto the cloud
 * does not hurt. Does not drop when broken. Meta {@link #TEMP} is the Cloud Staff cloud: melts after {@link #TEMP_TICKS} ticks.
 */
public class BlockCloud extends Block {

    public static final int TEMP = 1, TEMP_TICKS = 600;

    public BlockCloud() {
        super(Material.cloth);
        setHardness(0.4F);
        setStepSound(soundTypeCloth);
        setLightOpacity(1);
        setBlockName("dre.cloud");
        setBlockTextureName("divinerpgextended:cloud");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess w, int x, int y, int z, int side) {
        return w.getBlock(x, y, z) != this && super.shouldSideBeRendered(w, x, y, z, side);
    }

    @Override
    public void onFallenUpon(net.minecraft.world.World w, int x, int y, int z, net.minecraft.entity.Entity e, float dist) {
        e.fallDistance = 0;
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return null;
    }

    /** Silk Touch does not take a temporary cloud: a cloud of the Cloud Staff came out as a permanent block - 9 free blocks every 40 s. */
    @Override
    public boolean canSilkHarvest(net.minecraft.world.World w, net.minecraft.entity.player.EntityPlayer p, int x, int y, int z, int meta) {
        return meta != TEMP;
    }

    @Override
    public void updateTick(net.minecraft.world.World w, int x, int y, int z, Random r) {
        if (w.getBlockMetadata(x, y, z) == TEMP) w.setBlockToAir(x, y, z);
    }
}
