package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Завеса пузырей на входе в нору краба: прозрачная воздушная плёнка. Вода через неё не течёт (как через дверь), а
 * игрок и мобы проходят насквозь. Сломать нельзя, чтобы нору не затопило.
 */
public class BlockBubbleCurtain extends Block {

    public BlockBubbleCurtain() {
        // Материал "камень" не даёт воде растечься в этот блок; столкновений при этом нет
        super(Material.rock);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightOpacity(0);
        setBlockName("dre.bubbleCurtain");
        setBlockTextureName("minecraft:ice");
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return world.getBlock(x, y, z) != this && super.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return 0xB8E0FF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int meta) {
        return 0xB8E0FF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        for (int i = 0; i < 3; i++) {
            world.spawnParticle("bubble", x + rand.nextDouble(), y + rand.nextDouble(), z + rand.nextDouble(), 0,
                0.05, 0);
        }
    }
}
