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
 * Apalachia overhead rails: placed with Apalachia Rails ({@link com.divinerpgextended.item.ItemApalachiaRails}) on a wall or ceiling, on the floor - ordinary
 * Divine rails; at a wall the floor turns into the wall with a ramp ({@link SkyTrack#corner}) (meta 0-5 - the support face: 0 bottom, 1 top, 2-5 -
 * north, south, west, east; bit 8 - across). The Apalachia Cart ({@link com.divinerpgextended.entity.EntitySkyCart}) rides on them
 * and turns by itself at joints, so you can close a loop floor - wall - ceiling - wall.
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
     * Where the rail is connected (directions in the plane of its face): neighboring rails in the plane and, if the neighbor stands on a
     * perpendicular face (a rail on the floor with one on a wall above it), toward that neighbor's support.
     */
    public static ForgeDirection[] links(IBlockAccess w, int x, int y, int z) {
        TileSkyRail t = tile(w, x, y, z);
        if (t != null && t.hasShape()) return t.shape();
        return dynamicLinks(w, x, y, z);
    }

    private static TileSkyRail tile(IBlockAccess w, int x, int y, int z) {
        net.minecraft.tileentity.TileEntity te = w.getTileEntity(x, y, z);
        return te instanceof TileSkyRail ? (TileSkyRail) te : null;
    }

    /** Whether it is connected toward d: if there is a shape - only its two directions; if not (an old rail) - everything nearby. */
    public static boolean linked(IBlockAccess w, int x, int y, int z, ForgeDirection d) {
        TileSkyRail t = tile(w, x, y, z);
        if (t == null || !t.hasShape()) return true;
        ForgeDirection[] s = t.shape();
        return s[0] == d || s[1] == d;
    }

    /** Where it can connect: neighbors in the plane, a rail on the adjacent face behind the edge, a rail on a wall above an inner corner. */
    static java.util.List<ForgeDirection> candidates(IBlockAccess w, int x, int y, int z) {
        ForgeDirection n = support(w.getBlockMetadata(x, y, z));
        java.util.List<ForgeDirection> out = new java.util.ArrayList<ForgeDirection>();
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (d == n || d == n.getOpposite()) continue;
            if (track(w.getBlock(x + d.offsetX, y + d.offsetY, z + d.offsetZ)) || SkyTrack.outerTo(w, x, y, z, d)) out.add(d);
        }
        ForgeDirection away = n.getOpposite();
        int ax = x + away.offsetX, ay = y + away.offsetY, az = z + away.offsetZ;
        if (w.getBlock(ax, ay, az) instanceof BlockSkyRail) {
            ForgeDirection s2 = support(w.getBlockMetadata(ax, ay, az));
            if (s2 != n && s2 != away && !out.contains(s2)) out.add(s2);
        }
        return out;
    }

    /** Shape for a new rail, like a floor one: straight between two opposite neighbors, otherwise a turn between two, otherwise straight. */
    static ForgeDirection[] pick(java.util.List<ForgeDirection> c, ForgeDirection axis) {
        for (ForgeDirection d : c) if (c.contains(d.getOpposite())) return new ForgeDirection[] { d, d.getOpposite() };
        if (c.size() >= 2) return new ForgeDirection[] { c.get(0), c.get(1) };
        if (c.size() == 1) return new ForgeDirection[] { c.get(0), c.get(0).getOpposite() };
        return new ForgeDirection[] { axis, axis.getOpposite() };
    }

    /**
     * A rail was placed nearby: as with the vanilla one - a lone rail (both ends free) turns straight toward the new neighbor,
     * a rail with one neighbor turns its free end toward the new one; occupied ends hold, a removed neighbor does not change the shape.
     */
    static void relink(World w, int x, int y, int z, TileSkyRail t, java.util.List<ForgeDirection> c) {
        ForgeDirection[] s = t.shape();
        boolean free0 = !c.contains(s[0]), free1 = !c.contains(s[1]);
        if (!free0 && !free1) return;
        ForgeDirection keep = free0 ? s[1] : s[0];
        ForgeDirection best = null;
        for (ForgeDirection d : c) {
            if (d == s[0] || d == s[1]) continue;
            if (best == null || d == keep.getOpposite()) best = d; // straight is better than a turn
        }
        if (best == null) return;
        if (free0 && free1) t.set(best, best.getOpposite());
        else t.set(keep, best);
    }

    /** Switch: at a fork the signal throws one end to the opposite branch (straight - turn - straight). */
    static void toggle(TileSkyRail t, java.util.List<ForgeDirection> c) {
        ForgeDirection[] s = t.shape();
        for (int i = 0; i < 2; i++) {
            ForgeDirection end = s[i], other = s[1 - i];
            if (other == end.getOpposite()) { // straight: one end goes to a side branch
                for (ForgeDirection d : c) if (d != end && d != other && d != end.getOpposite() && d.getOpposite() != other) {
                    t.set(other, d);
                    return;
                }
                continue;
            }
            if (c.contains(end.getOpposite()) && end.getOpposite() != other) { // turn: the end goes to the other side
                t.set(other, end.getOpposite());
                return;
            }
        }
    }

    /** Connections by neighbors (old rails without a saved shape). */
    static ForgeDirection[] dynamicLinks(IBlockAccess w, int x, int y, int z) {
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
        // if it is a straight row, the one that gave the axis goes first; for a turn - two perpendicular ones
        if (out.size() > 2) {
            for (int i = 0; i < out.size(); i++) for (int j = i + 1; j < out.size(); j++) if (out.get(i) == out.get(j).getOpposite())
                return new ForgeDirection[] { out.get(i), out.get(j) };
        }
        return out.toArray(new ForgeDirection[out.size()]);
    }

    /** Axis by metadata when there are no neighbors: the "across" bit and the support face. */
    public static ForgeDirection defaultAxis(int meta) {
        ForgeDirection n = support(meta);
        boolean ac = across(meta);
        if (n == ForgeDirection.DOWN || n == ForgeDirection.UP) return ac ? ForgeDirection.EAST : ForgeDirection.SOUTH;
        if (!ac) return ForgeDirection.UP;
        return n == ForgeDirection.NORTH || n == ForgeDirection.SOUTH ? ForgeDirection.EAST : ForgeDirection.SOUTH;
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public net.minecraft.tileentity.TileEntity createTileEntity(World w, int meta) {
        return new TileSkyRail();
    }

    public BlockSkyRail() {
        super(Material.circuits);
        setHardness(0.7F);
        setStepSound(soundTypeMetal);
        setBlockName("dre.skyRail");
        setBlockTextureName("divinerpg:apalachiaRails");
    }

    /** Support face (ForgeDirection, where the support looks from the rail). */
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

    /** Direction by the player's view: on the floor and ceiling - along or across, on a wall - vertical or along the wall. */
    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase p, ItemStack s) {
        int meta = w.getBlockMetadata(x, y, z) & 7;
        ForgeDirection sup = support(meta);
        boolean acrossBit;
        if (sup == ForgeDirection.DOWN || sup == ForgeDirection.UP) {
            int facing = MathHelper.floor_double(p.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
            acrossBit = facing == 1 || facing == 3; // looks along X
        } else {
            acrossBit = Math.abs(p.rotationPitch) < 35; // looks straight - the rail runs along the wall, up-down - vertical
        }
        w.setBlockMetadataWithNotify(x, y, z, meta | (acrossBit ? 8 : 0), 2);
        TileSkyRail t = tile(w, x, y, z);
        if (t != null) {
            ForgeDirection[] f = pick(candidates(w, x, y, z), defaultAxis(w.getBlockMetadata(x, y, z)));
            t.set(f[0], f[1]);
            t.powered = w.isBlockIndirectlyGettingPowered(x, y, z);
        }
    }

    /** A rail on a wall above a floor rail at its foot: the latter becomes a ramp (an overhead rail on the floor). */
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
            return;
        }
        TileSkyRail t = tile(w, x, y, z);
        if (w.isRemote || t == null) return;
        java.util.List<ForgeDirection> c = candidates(w, x, y, z);
        if (!t.hasShape()) {
            ForgeDirection[] f = pick(c, defaultAxis(w.getBlockMetadata(x, y, z)));
            t.set(f[0], f[1]);
        } else relink(w, x, y, z, t, c);
        boolean p = w.isBlockIndirectlyGettingPowered(x, y, z);
        if (p != t.powered) {
            t.powered = p;
            t.markDirty();
            if (c.size() >= 3) toggle(t, c);
        }
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    /** Breaks into Apalachia Rails: it is placed from them too. */
    @Override
    public net.minecraft.item.Item getItemDropped(int meta, java.util.Random r, int fortune) {
        return com.divinerpgextended.registry.ModItems.apalachiaRails;
    }

    /** Apalachia Cart track: an overhead rail or any floor one. */
    public static boolean track(Block b) {
        return b instanceof BlockSkyRail || SkyTrack.floor(b);
    }

    /** Remember the current shape of an old rail (one without a saved shape): from then on it holds the turn and works as a switch, like new ones. */
    public static boolean adopt(World w, int x, int y, int z) {
        if (!(w.getBlock(x, y, z) instanceof BlockSkyRail)) return false;
        TileSkyRail t = tile(w, x, y, z);
        if (t == null || t.hasShape()) return false;
        ForgeDirection[] l = dynamicLinks(w, x, y, z);
        ForgeDirection a = l.length > 0 ? l[0] : defaultAxis(w.getBlockMetadata(x, y, z));
        t.set(a, l.length > 1 ? l[1] : a.getOpposite());
        return true;
    }

    /**
     * Rails placed before 0.99.13 (without a saved shape) remember their current shape on chunk load - on the
     * next tick, when the neighboring chunks are already in place.
     */
    public static class Adopt {

        private final java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<Object[]>();

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onLoad(net.minecraftforge.event.world.ChunkEvent.Load e) {
            if (e.world.isRemote) return;
            net.minecraft.world.chunk.Chunk c = e.getChunk();
            for (net.minecraft.world.chunk.storage.ExtendedBlockStorage s : c.getBlockStorageArray()) {
                if (s == null || s.isEmpty()) continue;
                for (int ly = 0; ly < 16; ly++) for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++)
                    if (s.getBlockByExtId(lx, ly, lz) instanceof BlockSkyRail)
                        queue.add(new Object[] { e.world, (c.xPosition << 4) + lx, s.getYLocation() + ly, (c.zPosition << 4) + lz });
            }
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onTick(cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent e) {
            if (e.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;
            for (int n = 0; n < 256 && !queue.isEmpty(); n++) {
                Object[] q = queue.poll();
                World w = (World) q[0];
                int x = (Integer) q[1], y = (Integer) q[2], z = (Integer) q[3];
                // links look at neighbors: without loaded neighboring chunks we do not touch (once loaded - the rail is queued again)
                if (w.checkChunksExist(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1)) adopt(w, x, y, z);
            }
        }
    }
}
