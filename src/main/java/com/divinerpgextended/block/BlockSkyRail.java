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
 * Навесные рельсы Апалачии: ставятся рельсами Апалачии ({@link com.divinerpgextended.item.ItemApalachiaRails}) на стену или потолок, на пол - обычные
 * рельсы Divine; у стены пол переходит в стену горкой ({@link SkyTrack#corner}) (мета 0-5 - грань опоры: 0 снизу, 1 сверху, 2-5 -
 * север, юг, запад, восток; бит 8 - поперёк). По ним ездит вагонетка Апалачии ({@link com.divinerpgextended.entity.EntitySkyCart})
 * и сама поворачивает на стыках, так что можно замкнуть кольцо пол - стена - потолок - стена.
 */
public class BlockSkyRail extends Block {

    public static int renderId = -1;
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public static net.minecraft.util.IIcon turnedIcon;

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister reg) {
        super.registerBlockIcons(reg);
        turnedIcon = reg.registerIcon("divinerpgextended:sky_rail_turned");
    }

    /**
     * Куда рельс соединён (направления в плоскости его грани): соседние рельсы в плоскости и, если сосед стоит на
     * перпендикулярной грани (рельс на полу, а над ним - на стене), - в сторону опоры того соседа.
     */
    public static ForgeDirection[] links(IBlockAccess w, int x, int y, int z) {
        ForgeDirection n = support(w.getBlockMetadata(x, y, z));
        java.util.List<ForgeDirection> out = new java.util.ArrayList<ForgeDirection>();
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (d == n || d == n.getOpposite()) continue;
            if (track(w.getBlock(x + d.offsetX, y + d.offsetY, z + d.offsetZ)) && !out.contains(d)) out.add(d);
        }
        ForgeDirection away = n.getOpposite();
        int ax = x + away.offsetX, ay = y + away.offsetY, az = z + away.offsetZ;
        if (w.getBlock(ax, ay, az) instanceof BlockSkyRail) {
            ForgeDirection s2 = support(w.getBlockMetadata(ax, ay, az));
            if (s2 != n && s2 != away && !out.contains(s2)) out.add(s2);
        }
        // если прямой ряд, первым - тот, что дал ось; для поворота - два перпендикулярных
        if (out.size() > 2) {
            for (int i = 0; i < out.size(); i++) for (int j = i + 1; j < out.size(); j++) if (out.get(i) == out.get(j).getOpposite())
                return new ForgeDirection[] { out.get(i), out.get(j) };
        }
        return out.toArray(new ForgeDirection[out.size()]);
    }

    /** Ось по метаданным, когда соседей нет: бит "поперёк" и грань опоры. */
    public static ForgeDirection defaultAxis(int meta) {
        ForgeDirection n = support(meta);
        boolean ac = across(meta);
        if (n == ForgeDirection.DOWN || n == ForgeDirection.UP) return ac ? ForgeDirection.EAST : ForgeDirection.SOUTH;
        if (!ac) return ForgeDirection.UP;
        return n == ForgeDirection.NORTH || n == ForgeDirection.SOUTH ? ForgeDirection.EAST : ForgeDirection.SOUTH;
    }

    public BlockSkyRail() {
        super(Material.circuits);
        setHardness(0.7F);
        setStepSound(soundTypeMetal);
        setBlockName("dre.skyRail");
        setBlockTextureName("divinerpg:apalachiaRails");
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

    /** Рельс на стене над напольным рельсом у её подножия: тот становится горкой (навесным рельсом на полу). */
    @Override
    public void onBlockAdded(World w, int x, int y, int z) {
        if (w.isRemote || support(w.getBlockMetadata(x, y, z)).offsetY != 0) return;
        Block ramp = SkyTrack.rampOf(w.getBlock(x, y - 1, z));
        if (ramp != null && SkyTrack.ascending(w, x, y - 1, z) == ForgeDirection.UNKNOWN) w.setBlock(x, y - 1, z, ramp, 0, 3);
    }

    @Override
    public net.minecraft.item.Item getItem(World w, int x, int y, int z) {
        return com.divinerpgextended.registry.ModItems.apalachiaRails;
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

    /** Ломается в рельсы Апалачии: из них же и ставится. */
    @Override
    public net.minecraft.item.Item getItemDropped(int meta, java.util.Random r, int fortune) {
        return com.divinerpgextended.registry.ModItems.apalachiaRails;
    }

    /** Путь вагонетки Апалачии: навесной рельс или любой напольный. */
    public static boolean track(Block b) {
        return b instanceof BlockSkyRail || SkyTrack.floor(b);
    }
}
