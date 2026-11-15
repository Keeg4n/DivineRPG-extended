package com.divinerpgextended.asm;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * World calls from Vethea generation ({@link VetheaGenTransformer}): a block in a not yet created chunk is not placed and is read as
 * air (otherwise the chunk is created and populated in a chain), blocks are placed without notifying neighbors (flag 2: sent to the client,
 * light is recomputed).
 */
public final class VetheaGenHooks {

    /** For measurements: how many accesses were cut off. */
    public static long skipped;

    private VetheaGenHooks() {}

    static boolean exists(World w, int x, int z) {
        return w.getChunkProvider().chunkExists(x >> 4, z >> 4);
    }

    public static boolean set(World w, int x, int y, int z, Block b) {
        return set(w, x, y, z, b, 0, 3);
    }

    public static boolean set(World w, int x, int y, int z, Block b, int meta, int flags) {
        if (!exists(w, x, z)) {
            skipped++;
            return false;
        }
        return w.setBlock(x, y, z, b, meta, flags & ~1);
    }

    /** Structure: placed whole (also in neighboring chunks, as before), but without notifying neighbors. */
    public static boolean build(World w, int x, int y, int z, Block b) {
        return w.setBlock(x, y, z, b, 0, 2);
    }

    public static boolean build(World w, int x, int y, int z, Block b, int meta, int flags) {
        return w.setBlock(x, y, z, b, meta, flags & ~1);
    }

    public static Block get(World w, int x, int y, int z) {
        if (!exists(w, x, z)) {
            skipped++;
            return Blocks.air;
        }
        return w.getBlock(x, y, z);
    }

    public static boolean air(World w, int x, int y, int z) {
        return get(w, x, y, z) == Blocks.air || w.isAirBlock(x, y, z);
    }

    public static int meta(World w, int x, int y, int z) {
        return exists(w, x, z) ? w.getBlockMetadata(x, y, z) : 0;
    }
}
