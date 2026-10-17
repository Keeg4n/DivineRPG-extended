package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Рельсы Апалачии: крепятся к любой грани - полу, стене, потолку (мета 0-5 - грань опоры: 0 снизу, 1 сверху, 2-5 -
 * север, юг, запад, восток; бит 8 - поперёк). По ним ездит вагонетка Апалачии ({@link com.divinerpgextended.entity.EntitySkyCart})
 * и сама поворачивает на стыках, так что можно замкнуть кольцо пол - стена - потолок - стена.
 */
public class BlockSkyRail extends Block {

    public static int renderId = -1;

    public BlockSkyRail() {
        super(Material.circuits);
        setHardness(0.7F);
        setStepSound(soundTypeMetal);
        setBlockName("dre.skyRail");
        setBlockTextureName("divinerpg:apalachiaRails");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    /** Грань опоры (ForgeDirection, куда смотрит опора от рельса). */
    public static ForgeDirection support(int meta) {
        return ForgeDirection.getOrientation(meta & 7);
    }

    public static boolean across(int meta) {
        return (meta & 8) != 0;
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
        return renderId;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World w, int x, int y, int z) {
        return null;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess w, int x, int y, int z) {
        float t = 0.125F;
        switch (support(w.getBlockMetadata(x, y, z))) {
            case UP: setBlockBounds(0, 1 - t, 0, 1, 1, 1); break;
            case NORTH: setBlockBounds(0, 0, 0, 1, 1, t); break;
            case SOUTH: setBlockBounds(0, 0, 1 - t, 1, 1, 1); break;
            case WEST: setBlockBounds(0, 0, 0, t, 1, 1); break;
            case EAST: setBlockBounds(1 - t, 0, 0, 1, 1, 1); break;
            default: setBlockBounds(0, 0, 0, 1, t, 1);
        }
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, 0, 0, 1, 0.125F, 1);
    }

    private static boolean solid(World w, int x, int y, int z, ForgeDirection d) {
        return w.isSideSolid(x + d.offsetX, y + d.offsetY, z + d.offsetZ, d.getOpposite(), false);
    }

    @Override
    public boolean canPlaceBlockOnSide(World w, int x, int y, int z, int side) {
        return solid(w, x, y, z, ForgeDirection.getOrientation(side).getOpposite());
    }

    @Override
    public int onBlockPlaced(World w, int x, int y, int z, int side, float hx, float hy, float hz, int meta) {
        return ForgeDirection.getOrientation(side).getOpposite().ordinal();
    }

    /** Направление по взгляду игрока: на полу и потолке - вдоль или поперёк, на стене - вертикально или вдоль стены. */
    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase p, ItemStack s) {
        int meta = w.getBlockMetadata(x, y, z) & 7;
        ForgeDirection sup = support(meta);
        boolean acrossBit;
        if (sup == ForgeDirection.DOWN || sup == ForgeDirection.UP) {
            int facing = MathHelper.floor_double(p.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
            acrossBit = facing == 1 || facing == 3; // смотрит вдоль X
        } else {
            acrossBit = Math.abs(p.rotationPitch) < 35; // смотрит прямо - рельс вдоль стены, вверх-вниз - вертикальный
        }
        w.setBlockMetadataWithNotify(x, y, z, meta | (acrossBit ? 8 : 0), 2);
    }

    @Override
    public void onNeighborBlockChange(World w, int x, int y, int z, Block b) {
        if (!solid(w, x, y, z, support(w.getBlockMetadata(x, y, z)))) {
            dropBlockAsItem(w, x, y, z, 0, 0);
            w.setBlockToAir(x, y, z);
        }
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }
}
