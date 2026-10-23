package com.divinerpgextended.asm;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

/** Вызовы генераторов из заселения Iceika ({@link IceikaThroneTransformer}): внутри Ледяного Трона - не ставить. */
public class IceikaThroneHooks {

    /** Для замера и автотеста: сколько вызовов пропущено. */
    public static int skipped;

    public static boolean set(World w, int x, int y, int z, net.minecraft.block.Block b, int meta, int flags) {
        if (com.divinerpgextended.world.WorldGenThrone.shields(w, x, y, z)) {
            skipped++;
            return false;
        }
        return w.setBlock(x, y, z, b, meta, flags);
    }

    public static boolean gen(WorldGenerator g, World w, Random r, int x, int y, int z) {
        if (com.divinerpgextended.world.WorldGenThrone.shields(w, x, y, z)) {
            skipped++;
            return false;
        }
        return g.generate(w, r, x, y, z);
    }
}
