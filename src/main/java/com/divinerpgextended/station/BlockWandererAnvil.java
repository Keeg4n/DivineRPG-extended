package com.divinerpgextended.station;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.divinerpgextended.DivineRPGExtended;

/**
 * Wanderer Anvil. Drawn with a model ({@link com.divinerpgextended.client.RenderWandererAnvil}), not a cube; meta 0-3 -
 * rotation: facing the player who placed it. Does not block light.
 */
public class BlockWandererAnvil extends BlockContainer {

    private final Random rand = new Random();

    public BlockWandererAnvil() {
        super(Material.anvil);
        setHardness(5.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeAnvil);
        setBlockName("dre.wandererAnvil");
        setBlockTextureName("minecraft:anvil_base");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileWandererAnvil();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z, (MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) + 2) & 3, 2);
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
    public int getRenderType() {
        return -1; // model only
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx,
        float hy, float hz) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWandererAnvil) ((TileWandererAnvil) te).releaseLegacy(player); // items that lay in the block from old versions
        player.openGui(DivineRPGExtended.instance, GuiHandler.ANVIL, world, x, y, z);
        return true;
    }

    /** The block is empty; only items left in it from old versions drop. Open windows close by themselves (the block is gone). */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWandererAnvil) {
            for (ItemStack s : ((TileWandererAnvil) te).takeLegacy()) {
                EntityItem e = new EntityItem(world, x + 0.5 + rand.nextFloat() * 0.3, y + 0.5, z + 0.5 + rand.nextFloat() * 0.3, s);
                world.spawnEntityInWorld(e);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
