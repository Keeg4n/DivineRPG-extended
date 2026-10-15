package com.divinerpgextended.station;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.divinerpgextended.DivineRPGExtended;

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
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx,
        float hy, float hz) {
        if (!world.isRemote) player.openGui(DivineRPGExtended.instance, GuiHandler.ANVIL, world, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWandererAnvil) {
            TileWandererAnvil inv = (TileWandererAnvil) te;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s != null) {
                    EntityItem e = new EntityItem(world, x + 0.5 + rand.nextFloat() * 0.3, y + 0.5,
                        z + 0.5 + rand.nextFloat() * 0.3, s.copy());
                    world.spawnEntityInWorld(e);
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
