package com.divinerpgextended.asm;

import java.util.Random;

import net.divinerpg.dimensions.vethea.all.WorldGenConeUp;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

/**
 * Calls from twilight world population ({@link TreeShiftTransformer}): the same point shifted by +8 in X and Z (into the
 * area that this chunk populates), the height is the same relative to the surface, but at the new place.
 */
public class TreeShiftHooks {

    /** How many twilight world populations are in progress now (on the server in its thread). */
    public static int populating;
    /** For measurement: how many populations started. */
    public static int begins;

    public static void begin() {
        populating++;
        begins++;
    }

    public static void end() {
        if (populating > 0) populating--;
    }

    public static boolean tree(WorldGenerator g, World w, Random r, int x, int y, int z) {
        int nx = x + 8, nz = z + 8;
        if (com.divinerpgextended.world.twilight.WorldGenNests.templeIsland(w, nx, nz)) return false; // not on the Skythern temple
        return g.generate(w, r, nx, y - w.getHeightValue(x, z) + w.getHeightValue(nx, nz), nz);
    }

    public static boolean cone(WorldGenConeUp g, World w, Random r, int x, int y, int z, int size) {
        int nx = x + 8, nz = z + 8;
        return g.generate(w, r, nx, y - w.getHeightValue(x, z) + w.getHeightValue(nx, nz), nz, size);
    }
}
