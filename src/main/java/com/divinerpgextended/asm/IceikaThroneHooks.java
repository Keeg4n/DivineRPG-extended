package com.divinerpgextended.asm;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

/** Generator calls from Iceika population ({@link IceikaThroneTransformer}): inside the Frozen Throne - do not place. */
public class IceikaThroneHooks {

    /** For measurement and the selftest: how many calls were skipped. */
    public static int skipped;

    public static boolean set(World w, int x, int y, int z, net.minecraft.block.Block b, int meta, int flags) {
        if (com.divinerpgextended.world.WorldGenThrone.shields(w, x, y, z)) {
            skipped++;
            return false;
        }
        return w.setBlock(x, y, z, b, meta, flags);
    }

    public static boolean gen(WorldGenerator g, World w, Random r, int x, int y, int z) {
        if (com.divinerpgextended.world.WorldGenThrone.shields(w, x, y, z) || g instanceof net.minecraft.world.gen.feature.WorldGenLakes && lakeShielded(w, x, y, z)) {
            skipped++;
            return false;
        }
        return g.generate(w, r, x, y, z);
    }

    /**
     * WorldGenLakes does not dig where it is called: it shifts to the corner (x - 8, z - 8), drops through air to the first
     * solid block and digs 4 blocks below it. A lake called high in the air passed the check at its own point, then landed on the
     * ground right above the outer part of the dome and dug down to the vault. Check the point where it really digs.
     */
    static boolean lakeShielded(World w, int x, int y, int z) {
        int ly = y;
        while (ly > 5 && w.isAirBlock(x - 8, ly, z - 8)) ly--;
        return com.divinerpgextended.world.WorldGenThrone.shields(w, x, ly - 4, z);
    }
}
