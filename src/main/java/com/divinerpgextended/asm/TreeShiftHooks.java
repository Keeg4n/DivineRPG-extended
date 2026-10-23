package com.divinerpgextended.asm;

import java.util.Random;

import net.divinerpg.dimensions.vethea.all.WorldGenConeUp;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

/**
 * Вызовы из заселения сумеречных миров ({@link TreeShiftTransformer}): та же точка со сдвигом +8 по X и Z (внутрь
 * области, которую заселяет этот чанк), высота - та же относительно поверхности, но на новом месте.
 */
public class TreeShiftHooks {

    /** Сколько заселений сумеречных миров сейчас идёт (на сервере в его потоке). */
    public static int populating;
    /** Для замера: сколько заселений началось. */
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
        if (com.divinerpgextended.world.twilight.WorldGenNests.templeIsland(w, nx, nz)) return false; // не на храме Скайтерна
        return g.generate(w, r, nx, y - w.getHeightValue(x, z) + w.getHeightValue(nx, nz), nz);
    }

    public static boolean cone(WorldGenConeUp g, World w, Random r, int x, int y, int z, int size) {
        int nx = x + 8, nz = z + 8;
        return g.generate(w, r, nx, y - w.getHeightValue(x, z) + w.getHeightValue(nx, nz), nz, size);
    }
}
