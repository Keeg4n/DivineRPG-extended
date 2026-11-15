package com.divinerpgextended.world;

import java.util.Random;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Arcana is a maze without dead ends. In Divine every Arcana chunk is a stack of four 16x16 rooms (floors with the floor at y =
 * 8, 16, 24, 32), each from 21 random templates. Template openings are only at the middle of a side (z or x = 7..8, height 2), and
 * most have one to three sides blind; two adjacent rooms are connected only if both have an opening - so floors
 * break into islands, and sometimes it is easier to move the portal.
 * <p>
 * Each joint is decided by a maze rule (since 0.99.120; before, any opening on one side was cut through - about 80% of joints
 * were open, and people walked through Arcana in a straight line):
 * <ul>
 * <li>each chunk on each floor must open one joint - east or south (the choice is fixed, from coordinates);
 * if it cannot be cut, the other one opens. This is a "binary tree" maze: the floor is connected, but the paths wind;</li>
 * <li>Divine's native passage (an opening in both rooms) stays open with chance {@link #LOOP} - loops, so that people do not walk
 * long corridors;</li>
 * <li>the other joints are closed: an opening that looks into one is bricked up with wall masonry - no doors in the wall.</li>
 * </ul>
 * A 2x2 passage at the center of the joint: on each side - up to the first free space in the room, no deeper than 7 blocks; if on
 * some side no free space is found - we leave the joint alone. Chests, spawners and other tiles are not broken.
 */
public class ArcanaDoors implements IWorldGenerator {

    private static final int[] LEVELS = { 8, 16, 24, 32 };
    private static final int MAX_DEPTH = 7;
    /** Share of Divine's native passages that stay open beyond the mandatory ones (maze loops). */
    public static final float LOOP = 0.3F;

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (!Config.arcanaNoDeadEnds || world.provider.dimensionId != ConfigurationHelper.arcana) return;
        // joints not only of this chunk but of its neighbors: populating a chunk (Divine's boss halls) rebuilds neighboring rooms too, and
        // an already decided joint would get an opening into the wall again. Decisions are fixed (from coordinates), a repeat pass does not change them
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) junctions(world, chunkX + dx, chunkZ + dz);
    }

    /** The east and south joints of the chunk on all floors - if both neighbors already exist (we do not create chunks). */
    private static void junctions(World world, int chunkX, int chunkZ) {
        IChunkProvider cp = world.getChunkProvider();
        if (!cp.chunkExists(chunkX, chunkZ) || !cp.chunkExists(chunkX + 1, chunkZ) || !cp.chunkExists(chunkX, chunkZ + 1)) return;
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        for (int y0 : LEVELS) {
            // the east joint with chunk (x+1) and the south with (z+1): each joint is handled by exactly one chunk;
            // one of them is mandatory (floor connectivity), the choice is fixed from coordinates; if it was not cut, the second is mandatory
            int h = (chunkX * 73856093) ^ (chunkZ * 19349663) ^ (y0 * 83492791);
            boolean east = (h >>> 7 & 1) == 1;
            boolean loopE = (h >>> 9 & 1023) < LOOP * 1024, loopS = (h >>> 19 & 1023) < LOOP * 1024;
            if (east) door(world, x0 + 7, y0, z0 + 15, 0, 1, !door(world, x0 + 15, y0, z0 + 7, 1, 0, true, loopE), loopS);
            else door(world, x0 + 15, y0, z0 + 7, 1, 0, !door(world, x0 + 7, y0, z0 + 15, 0, 1, true, loopS), loopE);
        }
    }

    /** Whether the joint is open (the 2x2 section is free on both sides of the border) - for the selftest. */
    public static boolean open(World w, int x, int y0, int z, int ax, int az) {
        return free(w, x, y0, z, ax, az) && free(w, x + ax, y0, z + az, ax, az);
    }

    /** An opening on only one side of the joint (a door into a wall) - for the selftest. */
    public static boolean blind(World w, int x, int y0, int z, int ax, int az) {
        return free(w, x, y0, z, ax, az) != free(w, x + ax, y0, z + az, ax, az);
    }

    private static boolean blocks(World w, int x, int y, int z) {
        return w.getBlock(x, y, z)
            .getMaterial()
            .blocksMovement();
    }

    /** Whether the 2x2 passage section is free at the point (offset along the joint - along the cross axis). */
    private static boolean free(World w, int x, int y0, int z, int ax, int az) {
        int px = az, pz = ax; // across the passage direction
        for (int s = 0; s <= 1; s++) for (int y = y0 + 1; y <= y0 + 2; y++) {
            if (blocks(w, x + px * s, y, z + pz * s)) return false;
        }
        return true;
    }

    /**
     * The joint between cell (x, z) and the next one along axis (ax, az). x, z - the last row of this room, z (or x) - the left of
     * the two opening cells. force - mandatory joint (cut through), loop - keep the native passage. @return whether the joint is open
     */
    private static boolean door(World w, int x, int y0, int z, int ax, int az, boolean force, boolean loop) {
        int bx = x + ax, bz = z + az; // the first row of the neighboring room
        boolean fa = free(w, x, y0, z, ax, az), fb = free(w, bx, y0, bz, ax, az);
        if (!force && !(fa && fb && loop)) { // the joint is closed: openings that look into it get bricked up
            if (fa) seal(w, x, y0, z, ax, az);
            if (fb) seal(w, bx, y0, bz, ax, az);
            return false;
        }
        if (fa && fb) return true; // already open
        int dA = depth(w, x, y0, z, -ax, -az), dB = depth(w, bx, y0, bz, ax, az);
        if (dA < 0 || dB < 0) { // cannot cut through on some side - the joint is closed, brick up the opening
            if (fa) seal(w, x, y0, z, ax, az);
            if (fb) seal(w, bx, y0, bz, ax, az);
            return false;
        }
        carve(w, x, y0, z, -ax, -az, dA, ax, az);
        carve(w, bx, y0, bz, ax, az, dB, ax, az);
        return true;
    }

    /** Brick up the 2x2 section in row (x, z) with wall masonry matching the opening; tiles are not touched. */
    private static void seal(World w, int x, int y0, int z, int ax, int az) {
        int px = az, pz = ax; // along the wall
        Block wall = null;
        int meta = 0;
        for (int[] at : new int[][] { { x - px, z - pz }, { x + 2 * px, z + 2 * pz } }) {
            Block b = w.getBlock(at[0], y0 + 1, at[1]);
            int m = w.getBlockMetadata(at[0], y0 + 1, at[1]);
            if (b.getMaterial().blocksMovement() && b.isOpaqueCube() && !b.hasTileEntity(m) && b != Blocks.bedrock) {
                wall = b;
                meta = m;
                break;
            }
        }
        if (wall == null) return;
        for (int s = 0; s <= 1; s++) for (int y = y0 + 1; y <= y0 + 2; y++) {
            int cx = x + px * s, cz = z + pz * s;
            if (!blocks(w, cx, y, cz)) w.setBlock(cx, y, cz, wall, meta, 2);
        }
    }

    /** How many rows from the border inward (dx, dz) must be cut to reach a free section; -1 - not found. */
    private static int depth(World w, int x, int y0, int z, int dx, int dz) {
        for (int d = 0; d <= MAX_DEPTH; d++) {
            if (free(w, x + dx * d, y0, z + dz * d, Math.abs(dx), Math.abs(dz))) return d;
        }
        return -1;
    }

    /** Cut n rows from the border inward (dx, dz), a 2x2 section; tiles (chests, spawners) are not touched. */
    private static void carve(World w, int x, int y0, int z, int dx, int dz, int n, int ax, int az) {
        int px = az, pz = ax;
        for (int d = 0; d < n; d++) {
            for (int s = 0; s <= 1; s++) for (int y = y0 + 1; y <= y0 + 2; y++) {
                int cx = x + dx * d + px * s, cz = z + dz * d + pz * s;
                Block b = w.getBlock(cx, y, cz);
                if (!b.getMaterial()
                    .blocksMovement() || b.hasTileEntity(w.getBlockMetadata(cx, y, cz)) || b == Blocks.bedrock || b instanceof net.minecraft.block.BlockDoor
                    || b.getClass().getSimpleName().contains("Door")) continue; // Arcana doors (with keys) are not broken
                w.setBlock(cx, y, cz, Blocks.air, 0, 2);
            }
        }
    }
}
