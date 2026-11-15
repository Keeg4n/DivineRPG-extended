package com.divinerpgextended.block;

import java.util.ArrayList;
import java.util.List;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Apalachia Cart path along the rails (overhead and floor - addon and old Divine): rail neighbors, inner corners-ramps
 * (floor - wall, wall - ceiling), wheel contact point, normal and the joint with the neighbor. The cart rides along a polyline
 * "middle of the rail - joint - middle of the next", on a ramp - along the block diagonal, like a vanilla one on a rail one block higher.
 */
public final class SkyTrack {

    /** Rail height above the support face. */
    public static final double LIFT = 1 / 16.0;
    private static final ForgeDirection NONE = ForgeDirection.UNKNOWN;

    private SkyTrack() {}

    /** Floor rail: any (Apalachia, Arcana, vanilla and other mods') - the Apalachia Cart rides on ordinary rails too. */
    static boolean floor(Block b) {
        return b instanceof net.minecraft.block.BlockRailBase;
    }

    /**
     * What a floor rail at the foot of a wall with an overhead rail turns into (a ramp): Apalachia Rails into an Apalachia
     * overhead one, Arcana rails into an Arcana overhead one; the others do not turn (otherwise a vanilla rail would break into an Apalachia rail).
     */
    public static Block rampOf(Block b) {
        if (b == TwilightBlocks.apalachiaRails || b == com.divinerpgextended.registry.ModBlocks.apalachiaRail) return com.divinerpgextended.registry.ModBlocks.skyRail;
        if (b == net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails) return com.divinerpgextended.registry.ModBlocks.skyBoostRail;
        return null;
    }

    /** Whether the rail accelerates the Apalachia Cart: Arcana rails (floor and overhead) and powered golden ones. */
    public static boolean boost(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b instanceof BlockSkyBoostRail || b == net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails
            || b == net.minecraft.init.Blocks.golden_rail && (w.getBlockMetadata(x, y, z) & 8) != 0;
    }

    /** Floor rail shape (0-9, like vanilla): without the "powered" bit for Divine rails. */
    private static int shapeMeta(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b instanceof net.minecraft.block.BlockRailBase ? ((net.minecraft.block.BlockRailBase) b).getBasicRailMetadata(w, null, x, y, z) : 0;
    }

    /** Rail support face: overhead - by meta, floor - the floor; not a rail - UNKNOWN. */
    public static ForgeDirection support(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b instanceof BlockSkyRail) return BlockSkyRail.support(w.getBlockMetadata(x, y, z));
        if (floor(b)) return ForgeDirection.DOWN;
        return NONE;
    }

    /** Sloped floor rail (meta 2-5, like vanilla): where it rises; otherwise UNKNOWN. */
    public static ForgeDirection ascending(IBlockAccess w, int x, int y, int z) {
        if (!floor(w.getBlock(x, y, z))) return NONE;
        switch (shapeMeta(w, x, y, z)) {
            case 2: return ForgeDirection.EAST;
            case 3: return ForgeDirection.WEST;
            case 4: return ForgeDirection.NORTH;
            case 5: return ForgeDirection.SOUTH;
            default: return NONE;
        }
    }

    private static boolean track(IBlockAccess w, int x, int y, int z, ForgeDirection d) {
        return BlockSkyRail.track(w.getBlock(x + d.offsetX, y + d.offsetY, z + d.offsetZ));
    }

    /**
     * Inner corner: the rail stands at a wall, and above it (on the side away from the support) a rail hangs on that wall - the floor turns into
     * the wall (likewise wall - ceiling). Returns where the ramp goes (the wall side); not a corner - UNKNOWN. There is at most one rail in
     * its own plane at the corner - and that one from the opposite side (otherwise it is a fork along the wall, not a ramp).
     */
    public static ForgeDirection corner(IBlockAccess w, int x, int y, int z) {
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE || ascending(w, x, y, z) != NONE) return NONE;
        ForgeDirection a = n.getOpposite();
        ForgeDirection d = support(w, x + a.offsetX, y + a.offsetY, z + a.offsetZ);
        if (d == NONE || d == n || d == a || track(w, x, y, z, d)) return NONE;
        for (ForgeDirection o : ForgeDirection.VALID_DIRECTIONS)
            if (o != n && o != a && o != d.getOpposite() && track(w, x, y, z, o)) return NONE;
        return d;
    }

    /** Where you can go from this rail (offsets to neighboring rails, for sloped Divine ones - also one block up/down). */
    public static List<int[]> exits(IBlockAccess w, int x, int y, int z) {
        List<int[]> out = new ArrayList<int[]>();
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE) return out;
        if (floor(w.getBlock(x, y, z)) && corner(w, x, y, z) == NONE) {
            ForgeDirection asc = ascending(w, x, y, z);
            for (ForgeDirection d : shape(shapeMeta(w, x, y, z))) {
                int[][] tries = d == asc ? new int[][] { { 1 }, { 0 } } : new int[][] { { 0 }, { -1 }, { 1 } };
                for (int[] t : tries) {
                    int dy = t[0];
                    if (BlockSkyRail.track(w.getBlock(x + d.offsetX, y + dy, z + d.offsetZ))) {
                        out.add(new int[] { d.offsetX, dy, d.offsetZ });
                        break;
                    }
                }
            }
            // overhead rails nearby: the Divine rail shape does not know about them
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                if (d == ForgeDirection.DOWN || contains(out, d)) continue;
                Block b = w.getBlock(x + d.offsetX, y + d.offsetY, z + d.offsetZ);
                if (!(b instanceof BlockSkyRail)) continue;
                ForgeDirection s = BlockSkyRail.support(w.getBlockMetadata(x + d.offsetX, y + d.offsetY, z + d.offsetZ));
                if (d == ForgeDirection.UP ? s.offsetY == 0 : s == ForgeDirection.DOWN || s == d) out.add(off(d));
            }
            return out;
        }
        // an overhead rail holds its shape like a floor one: we go only where it is connected
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS)
            if (d != n && d != n.getOpposite() && track(w, x, y, z, d) && BlockSkyRail.linked(w, x, y, z, d)) out.add(off(d));
        ForgeDirection c = corner(w, x, y, z);
        if (c != NONE && BlockSkyRail.linked(w, x, y, z, c)) out.add(off(n.getOpposite()));
        // outer edge: there is no rail further in its plane, but behind the support edge on the adjacent face of the same block there is
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (d == n || d == n.getOpposite() || track(w, x, y, z, d) || !BlockSkyRail.linked(w, x, y, z, d)) continue;
            if (outerTo(w, x, y, z, d)) out.add(new int[] { d.offsetX + n.offsetX, d.offsetY + n.offsetY, d.offsetZ + n.offsetZ });
        }
        return out;
    }

    /** Behind the support edge toward d (along its plane) - a rail on the adjacent face of the same block. */
    static boolean outerTo(IBlockAccess w, int x, int y, int z, ForgeDirection d) {
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE || d == n || d == n.getOpposite()) return false;
        return support(w, x + d.offsetX + n.offsetX, y + d.offsetY + n.offsetY, z + d.offsetZ + n.offsetZ) == d.getOpposite();
    }

    /** Pair a -> b across an outer edge: direction from a along its plane (UNKNOWN - not an edge). */
    private static ForgeDirection outer(IBlockAccess w, int ax, int ay, int az, int bx, int by, int bz) {
        ForgeDirection n = support(w, ax, ay, az);
        if (n == NONE) return NONE;
        ForgeDirection d = dir(bx - ax - n.offsetX, by - ay - n.offsetY, bz - az - n.offsetZ);
        return d != NONE && outerTo(w, ax, ay, az, d) ? d : NONE;
    }

    private static boolean contains(List<int[]> l, ForgeDirection d) {
        for (int[] o : l) if (o[0] == d.offsetX && o[2] == d.offsetZ && (d.offsetY == 0 || o[1] == d.offsetY)) return true;
        return false;
    }

    private static int[] off(ForgeDirection d) {
        return new int[] { d.offsetX, d.offsetY, d.offsetZ };
    }

    /** The two directions of a vanilla rail shape by meta. */
    private static ForgeDirection[] shape(int meta) {
        ForgeDirection N = ForgeDirection.NORTH, S = ForgeDirection.SOUTH, E = ForgeDirection.EAST, W = ForgeDirection.WEST;
        switch (meta) {
            case 1: case 2: case 3: return new ForgeDirection[] { E, W };
            case 6: return new ForgeDirection[] { S, E };
            case 7: return new ForgeDirection[] { S, W };
            case 8: return new ForgeDirection[] { N, W };
            case 9: return new ForgeDirection[] { N, E };
            default: return new ForgeDirection[] { N, S };
        }
    }

    private static double[] v(ForgeDirection d, double k) {
        return new double[] { d.offsetX * k, d.offsetY * k, d.offsetZ * k };
    }

    private static double[] add(double[]... vs) {
        double[] r = new double[3];
        for (double[] a : vs) for (int i = 0; i < 3; i++) r[i] += a[i];
        return r;
    }

    private static double[] center(int x, int y, int z) {
        return new double[] { x + 0.5, y + 0.5, z + 0.5 };
    }

    /** Wheel contact point in the middle of the rail. */
    public static double[] contact(IBlockAccess w, int x, int y, int z) {
        double[] c = center(x, y, z);
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE || corner(w, x, y, z) != NONE || ascending(w, x, y, z) != NONE) return c; // ramp and slope - the middle of the block
        return add(c, v(n, 0.5 - LIFT));
    }

    /** Path normal (from the support toward the cart), unit length. */
    public static double[] normal(IBlockAccess w, int x, int y, int z) {
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE) return new double[] { 0, 1, 0 };
        ForgeDirection c = corner(w, x, y, z), asc = ascending(w, x, y, z);
        double k = Math.sqrt(0.5);
        if (c != NONE) return add(v(n, -k), v(c, -k));
        if (asc != NONE) return add(v(ForgeDirection.UP, k), v(asc, -k));
        return v(n, -1);
    }

    /** Joint point of the rail with the neighbor toward dir (the horizontal part of the offset for sloped ones). */
    public static double[] port(IBlockAccess w, int x, int y, int z, ForgeDirection dir) {
        double[] c = center(x, y, z);
        ForgeDirection n = support(w, x, y, z);
        ForgeDirection cr = corner(w, x, y, z), asc = ascending(w, x, y, z);
        if (cr != NONE) {
            if (dir == cr.getOpposite()) return add(c, v(n, 0.5), v(cr, -0.5)); // lower edge of the ramp
            if (dir == n.getOpposite()) return add(c, v(n, -0.5), v(cr, 0.5)); // upper edge - at the rail on the wall
            return add(c, v(dir, 0.5));
        }
        if (asc != NONE) {
            if (dir == asc) return add(c, v(dir, 0.5), v(ForgeDirection.UP, 0.5));
            if (dir == asc.getOpposite()) return add(c, v(dir, 0.5), v(ForgeDirection.DOWN, 0.5));
            return add(c, v(dir, 0.5));
        }
        if (n != NONE && dir != n && dir != n.getOpposite()) return add(c, v(n, 0.5 - LIFT), v(dir, 0.5));
        return add(c, v(dir, 0.5));
    }

    /** Joint of two neighboring rails (the middle between their edges - smooths out inexact pairs). */
    public static double[] joint(IBlockAccess w, int ax, int ay, int az, int bx, int by, int bz) {
        // across an outer edge - the joint is on the support edge itself (slightly outside), the cart rolls over it
        ForgeDirection od = outer(w, ax, ay, az, bx, by, bz);
        if (od != NONE) {
            ForgeDirection n = support(w, ax, ay, az);
            return add(center(ax, ay, az), v(n, 0.5 - LIFT), v(od, 0.5 + LIFT));
        }
        int dx = bx - ax, dy = by - ay, dz = bz - az;
        boolean diag = (dx != 0 || dz != 0) && dy != 0;
        ForgeDirection to = dir(dx, diag ? 0 : dy, dz);
        double[] pa = port(w, ax, ay, az, to), pb = port(w, bx, by, bz, to.getOpposite());
        return new double[] { (pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2, (pa[2] + pb[2]) / 2 };
    }

    public static ForgeDirection dir(int dx, int dy, int dz) {
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) if (d.offsetX == dx && d.offsetY == dy && d.offsetZ == dz) return d;
        return NONE;
    }
}
