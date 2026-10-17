package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Облако Скайтерна: площадки под ветряными тотемами. Мягкое и чуть прозрачное, по нему можно ходить; падение на облако
 * не ранит. Не выпадает при ломании.
 */
public class BlockCloud extends Block {

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
}
