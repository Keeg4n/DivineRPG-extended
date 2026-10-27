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
 * Ловушка Древней гробницы: выглядит как гладкий песчаник пола, но если на неё наступит игрок, через треть секунды
 * она и соседние такие же плиты крошатся - под ними яма. Ломается как песчаник и роняет песчаник.
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
        world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(Blocks.sandstone)); // крошки песчаника
        world.setBlockToAir(x, y, z);
    }
}
