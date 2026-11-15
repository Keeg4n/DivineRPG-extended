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
 * Temporary ice of the Frozen Throne bosses. Meta 0 is an ice spike from a wave or a spike trail (melts in 1.5 seconds),
 * meta 1 is a wall of the ice prison: sturdy (the Ice Pick breaks it instantly, an ordinary pickaxe slowly), melts by itself in 8 seconds.
 * Drops nothing.
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

    /** Places temporary ice only into air, so as not to spoil structures. */
    public static boolean place(World world, Block block, int x, int y, int z, int meta) {
        if (!world.isAirBlock(x, y, z)) return false;
        world.setBlock(x, y, z, block, meta, 3);
        world.scheduleBlockUpdate(x, y, z, block, meta == PRISON ? 160 : 30); // will melt by itself
        return true;
    }
}
