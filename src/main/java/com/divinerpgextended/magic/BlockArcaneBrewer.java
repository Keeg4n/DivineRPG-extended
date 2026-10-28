package com.divinerpgextended.magic;

import java.util.Random;

import net.minecraft.block.BlockBrewingStand;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Арканная стойка зельеварения: вид ванильной стойки в цветах Арканы, своя варка ({@link TileArcaneBrewer}). Продаёт Лорд Ваттикус. */
public class BlockArcaneBrewer extends BlockBrewingStand {

    @SideOnly(Side.CLIENT)
    private IIcon base;

    public BlockArcaneBrewer() {
        setHardness(0.5F);
        setLightLevel(0.2F);
        setBlockName("dre.arcaneBrewer");
        setBlockTextureName("divinerpgextended:arcane_brewer");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new TileArcaneBrewer();
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        if (w.getTileEntity(x, y, z) instanceof TileArcaneBrewer) p.openGui(com.divinerpgextended.DivineRPGExtended.instance, com.divinerpgextended.station.GuiHandler.BREWER, w, x, y, z);
        return true;
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return Item.getItemFromBlock(this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getItem(World w, int x, int y, int z) {
        return Item.getItemFromBlock(this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        super.registerBlockIcons(reg);
        blockIcon = reg.registerIcon("divinerpgextended:arcane_brewer");
        base = reg.registerIcon("divinerpgextended:arcane_brewer_base");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconBrewingStandBase() {
        return base;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String getItemIconName() {
        return "divinerpgextended:arcane_brewer";
    }
}
