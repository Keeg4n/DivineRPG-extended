package com.divinerpgextended.easter;

import java.util.Random;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Sofa with an RPG (DivineRPG - "Divan RPG"): an Easter egg. Two blocks, like a bed: metadata is the direction (0-3, where the sitter looks)
 * and the {@link #SECOND} bit for the second half. The model with the RPG-7 on the cushions is drawn by the first half ({@link TileSofa}). Right-click -
 * sit down. Stands in a special house of the underground city under the Jungle Temple.
 */
public class BlockSofa extends BlockContainer {

    public static final int SECOND = 4;
    /** Offset to the second half by direction: to the right of the sitter. */
    static final int[][] SIDE = { { -1, 0 }, { 0, -1 }, { 1, 0 }, { 0, 1 } };

    public BlockSofa() {
        super(Material.cloth);
        setHardness(1.0F);
        setStepSound(soundTypeCloth);
        setBlockName("dre.sofa");
        setBlockTextureName("minecraft:wool_colored_red");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        setBlockBounds(0, 0, 0, 1, 0.5625F, 1);
    }

    /** Direction (where the sitter looks) by the rotation angle of the player placing the sofa: facing the player. */
    public static int facing(float yaw) {
        return (MathHelper.floor_double(yaw * 4.0F / 360.0F + 0.5D) + 2) & 3;
    }

    /** Offset to the second half for direction f. */
    public static int[] side(int f) {
        return SIDE[f & 3];
    }

    public static int[] other(int x, int z, int meta) {
        int f = meta & 3, s = (meta & SECOND) != 0 ? -1 : 1;
        return new int[] { x + SIDE[f][0] * s, z + SIDE[f][1] * s };
    }

    /** Place the sofa (two halves); false - does not fit. */
    public static boolean place(World w, int x, int y, int z, int facing) {
        int ox = x + SIDE[facing][0], oz = z + SIDE[facing][1];
        if (!w.isAirBlock(x, y, z) || !w.isAirBlock(ox, y, oz)) return false;
        w.setBlock(x, y, z, com.divinerpgextended.registry.ModBlocks.sofa, facing, 3);
        w.setBlock(ox, y, oz, com.divinerpgextended.registry.ModBlocks.sofa, facing | SECOND, 3);
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return (meta & SECOND) != 0 ? null : new TileSofa();
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return (meta & SECOND) == 0;
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        if (p.ridingEntity != null || p.isSneaking()) return false;
        EntitySeat seat = new EntitySeat(w, x + 0.5, y + 0.3, z + 0.5);
        w.spawnEntityInWorld(seat);
        p.mountEntity(seat);
        return true;
    }

    @Override
    public void onNeighborBlockChange(World w, int x, int y, int z, net.minecraft.block.Block b) {
        int[] o = other(x, z, w.getBlockMetadata(x, y, z));
        if (w.getBlock(o[0], y, o[1]) != this) w.setBlockToAir(x, y, z); // the second half is broken - and this one too
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return (meta & SECOND) != 0 ? null : Item.getItemFromBlock(this);
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
        return -1; // the model only
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess w, int x, int y, int z) {
        setBlockBounds(0, 0, 0, 1, 0.5625F, 1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon("minecraft:wool_colored_red"); // breaking particles
    }

    /** The first half of the sofa: the model is drawn on it. */
    public static class TileSofa extends TileEntity {

        @Override
        public boolean canUpdate() {
            return false;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public net.minecraft.util.AxisAlignedBB getRenderBoundingBox() {
            return net.minecraft.util.AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord, zCoord - 1, xCoord + 2, yCoord + 2, zCoord + 2);
        }
    }

    /** The sofa item: places both halves. */
    public static class ItemSofa extends net.minecraft.item.ItemBlock {

        public ItemSofa(net.minecraft.block.Block b) {
            super(b);
            setTextureName("divinerpgextended:easter/sofa");
        }

        /** The inventory icon is its own (the block has only a model). */
        @Override
        @SideOnly(Side.CLIENT)
        public int getSpriteNumber() {
            return 1;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void registerIcons(IIconRegister reg) {
            itemIcon = reg.registerIcon(getIconString());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public net.minecraft.util.IIcon getIconFromDamage(int meta) {
            return itemIcon;
        }

        @Override
        public boolean onItemUse(net.minecraft.item.ItemStack s, EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
            if (side != 1) return false;
            if (w.isRemote) return true;
            if (!place(w, x, y + 1, z, facing(p.rotationYaw))) return false;
            if (!p.capabilities.isCreativeMode) s.stackSize--;
            w.playSoundEffect(x + 0.5, y + 1.5, z + 0.5, "step.cloth", 1F, 0.8F);
            return true;
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void addInformation(net.minecraft.item.ItemStack s, EntityPlayer p, java.util.List list, boolean adv) {
            list.add(net.minecraft.util.EnumChatFormatting.GRAY + net.minecraft.util.StatCollector.translateToLocal("dre.tooltip.sofa"));
        }
    }
}
