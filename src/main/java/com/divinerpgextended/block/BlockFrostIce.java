package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Временный лёд боссов Ледяного Трона. Мета 0 - ледяной шип из волны или дорожки шипов (тает за 1.5 секунды),
 * мета 1 - стенка ледяной тюрьмы: крепкая (Ледоруб ломает мгновенно, обычной киркой долго), тает сама за 8 секунд.
 * Ничего не роняет.
 */
public class BlockFrostIce extends Block {

    public static final int SPIKE = 0, PRISON = 1;
    private IIcon spike, prison;

    public BlockFrostIce() {
        super(Material.packedIce);
        setHardness(0.5F);
        setResistance(30.0F);
        setStepSound(soundTypeGlass);
        setBlockName("dre.frostIce");
        slipperiness = 0.98F;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        spike = reg.registerIcon("ice");
        prison = reg.registerIcon("ice_packed");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return meta == PRISON ? prison : spike;
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == PRISON ? 12.0F : 0.5F;
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random rand) {
        world.setBlockToAir(x, y, z);
    }

    @Override
    public int quantityDropped(Random rand) {
        return 0;
    }

    /** Ставит временный лёд только в воздух, чтобы не портить постройки. */
    public static boolean place(World world, Block block, int x, int y, int z, int meta) {
        if (!world.isAirBlock(x, y, z)) return false;
        world.setBlock(x, y, z, block, meta, 3);
        world.scheduleBlockUpdate(x, y, z, block, meta == PRISON ? 160 : 30); // растает сам
        return true;
    }
}
