package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Ancient Tomb trap: looks like smooth sandstone of the floor, but if a player steps on it, after a third of a second
 * it and the neighboring such slabs crumble - a pit below them. Breaks like sandstone and drops sandstone.
 */
public class BlockTrapFloor extends Block {

    private IIcon top, side;

    public BlockTrapFloor() {
        super(Material.rock);
        setHardness(0.8F);
        setStepSound(soundTypePiston);
        setBlockName("dre.trapFloor");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        top = reg.registerIcon("sandstone_top");
        side = reg.registerIcon("sandstone_smooth");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return s == 0 || s == 1 ? top : side;
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return Item.getItemFromBlock(Blocks.sandstone);
    }

    /** Under Silk Touch - sandstone too, not the trap itself. */
    @Override
    public boolean canSilkHarvest(World w, EntityPlayer p, int x, int y, int z, int meta) {
        return false;
    }

    @Override
    public void onEntityWalking(World world, int x, int y, int z, Entity entity) {
        if (world.isRemote || !(entity instanceof EntityPlayer) || ((EntityPlayer) entity).capabilities.isCreativeMode) return;
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "dig.sand", 1.0F, 0.5F);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (world.getBlock(x + dx, y, z + dz) == this) world.scheduleBlockUpdate(x + dx, y, z + dz, this, 6);
        }
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random rand) {
        world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(Blocks.sandstone)); // sandstone crumbs
        world.setBlockToAir(x, y, z);
    }
}
