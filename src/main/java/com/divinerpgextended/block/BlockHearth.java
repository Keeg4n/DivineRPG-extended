package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Hearth in Iceika merchant houses: burns forever, gives light and warmth (counts as fire for the cold), but the fire from it does not
 * spread to a wooden floor.
 */
public class BlockHearth extends Block {

    private IIcon front, side;

    public BlockHearth() {
        super(Material.rock);
        setHardness(3.5F);
        setResistance(10.0F);
        setLightLevel(0.95F);
        setStepSound(soundTypeStone);
        setBlockName("dre.hearth");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        front = reg.registerIcon("furnace_front_on");
        side = reg.registerIcon("furnace_top");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return s == 0 || s == 1 ? side : front;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        // flame at the open sides (the hearth stands in a wall, with a shelf above)
        int[][] sides = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
        for (int[] s : sides) {
            if (!world.isAirBlock(x + s[0], y, z + s[1])) continue;
            double along = 0.2 + rand.nextDouble() * 0.6;
            double px = s[0] == 0 ? x + along : x + 0.5 + s[0] * 0.52;
            double pz = s[1] == 0 ? z + along : z + 0.5 + s[1] * 0.52;
            double py = y + 0.2 + rand.nextDouble() * 0.5;
            world.spawnParticle("flame", px, py, pz, 0, 0.01, 0);
            world.spawnParticle("smoke", px, py + 0.3, pz, 0, 0.03, 0);
        }
        if (rand.nextInt(24) == 0) world.playSound(x + 0.5, y + 0.5, z + 0.5, "fire.fire", 0.6F, 0.8F, false);
    }
}
