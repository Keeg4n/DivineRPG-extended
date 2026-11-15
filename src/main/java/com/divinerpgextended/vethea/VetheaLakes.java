package com.divinerpgextended.vethea;

import java.util.Random;

import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Lakes on Vethea floors - dream fishing: oval water in the floor's grass, up to 3 deep (within the top 4 blocks of the
 * slab, which can be dug - we do not touch the lower part of the slab, {@link VetheaFloors}).
 * <p>
 * Ceiling lakes: at the bottom of the slab above the floor a bowl of Dream Water is cut out that flows upward: the surface faces down,
 * flush with the ceiling. Swim up to it - you hang upside down, feet in the water, and fish
 * ({@link com.divinerpgextended.block.BlockDreamWater}).
 */
public class VetheaLakes implements IWorldGenerator {

    /** Floor surfaces. */
    public static final int[] FLOORS = { 16, 64, 112, 160 };

    /** For measurements: how many nanoseconds went to lakes and for how many chunks. */
    public static long nanos, chunks;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World w, IChunkProvider gen, IChunkProvider prov) {
        if (w.provider.dimensionId != ConfigurationHelper.vethea || Config.vetheaLakeRarity <= 0) return;
        long t0 = System.nanoTime();
        try {
            lakes(chunkX, chunkZ, w);
        } finally {
            nanos += System.nanoTime() - t0;
            chunks++;
        }
    }

    private void lakes(int chunkX, int chunkZ, World w) {
        Random r = new Random(w.getSeed() ^ chunkX * 341873128712L ^ chunkZ * 132897987541L ^ 0x1A4EL);
        for (int f : FLOORS) {
            if (r.nextInt(Config.vetheaLakeRarity) != 0) continue;
            lake(w, r, chunkX * 16 + 8 + r.nextInt(4), chunkZ * 16 + 8 + r.nextInt(4), f);
        }
        if (Config.vetheaHangingLakeRarity <= 0) return;
        for (int f : FLOORS) {
            if (r.nextInt(Config.vetheaHangingLakeRarity) != 0) continue;
            hanging(w, r, chunkX * 16 + 16 + r.nextInt(4), chunkZ * 16 + 16 + r.nextInt(4), f);
        }
    }

    /** The lowest ceiling height above the grass where we place a ceiling lake. */
    static final int MIN_CEILING = 10;

    /** The lowest solid ceiling block above (x, y, z) not higher than limit, or -1. */
    static int ceiling(World w, int x, int y, int z, int limit) {
        for (int h = y; h <= limit; h++) if (!w.isAirBlock(x, h, z)) return h;
        return -1;
    }

    /**
     * A ceiling lake centered at (x, z) on floor floor: an oval bowl is cut at the bottom of the slab above the floor, the surface flush with
     * the ceiling; false - the place did not fit (the ceiling is uneven or low).
     */
    public static boolean hanging(World w, Random r, int x, int z, int floor) {
        int g = ground(w, x, z, floor);
        if (g < 0) return false;
        int c = ceiling(w, x, g + 3, z, g + 45);
        if (c < 0 || c - g < MIN_CEILING) return false;
        int rx = 5 + r.nextInt(2), rz = 4 + r.nextInt(2);
        for (int dx = -rx - 1; dx <= rx + 1; dx++) for (int dz = -rz - 1; dz <= rz + 1; dz++) {
            double d = Math.pow(dx / (rx + 1.0), 2) + Math.pow(dz / (rz + 1.0), 2);
            if (d >= 1) continue;
            // around and above the bowl is a solid slab: the water will not flow up or sideways
            if (!w.isAirBlock(x + dx, c - 1, z + dz) || w.isAirBlock(x + dx, c, z + dz)) return false;
            for (int y = c; y <= c + 4; y++) if (w.isAirBlock(x + dx, y, z + dz) || w.getBlock(x + dx, y, z + dz).getMaterial().isLiquid()) return false;
        }
        net.minecraft.block.Block water = com.divinerpgextended.registry.ModBlocks.dreamWater;
        for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) {
            double d = Math.pow(dx / (double) rx, 2) + Math.pow(dz / (double) rz, 2);
            if (d >= 1) continue;
            int depth = d < 0.3 ? 3 : d < 0.7 ? 2 : 1;
            for (int y = c; y < c + depth; y++) w.setBlock(x + dx, y, z + dz, water, 0, 2);
        }
        return true;
    }

    /** Floor grass in the column near height floor (the top block with air above it) or -1. */
    public static int ground(World w, int x, int z, int floor) {
        for (int y = floor + 4; y >= floor - 4; y--)
            if (w.getBlock(x, y, z) == VetheaBlocks.dreamGrass && w.isAirBlock(x, y + 1, z) && w.isAirBlock(x, y + 3, z)) return y;
        return -1;
    }

    /** A 12 x 12 lake with its corner at (x, z) on floor floor; false - the place did not fit. */
    public static boolean lake(World w, Random r, int x, int z, int floor) {
        int lo = 999, hi = -1;
        for (int i = 0; i < 12; i++) for (int k = 0; k < 12; k++) {
            int y = ground(w, x + i, z + k, floor);
            if (y < 0) return false;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        if (hi - lo > 1) return false;
        int rx = 3 + r.nextInt(2), rz = 3 + r.nextInt(2);
        for (int i = 0; i < 12; i++) for (int k = 0; k < 12; k++) {
            double d = Math.pow((i - 5.5) / rx, 2) + Math.pow((k - 5.5) / rz, 2);
            if (d >= 1) continue;
            int top = ground(w, x + i, z + k, floor), depth = d < 0.35 ? 3 : d < 0.75 ? 2 : 1;
            for (int y = lo + 1; y <= top; y++) w.setBlock(x + i, y, z + k, Blocks.air, 0, 2);
            for (int y = lo - depth + 1; y <= lo; y++) w.setBlock(x + i, y, z + k, Blocks.water, 0, 2);
            w.setBlock(x + i, lo - depth, z + k, VetheaBlocks.dreamDirt, 0, 2);
        }
        return true;
    }
}
