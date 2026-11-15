package com.divinerpgextended.world;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;

/**
 * Which biome this is - by Forge biome dictionary types, not by vanilla objects: this also recognizes biomes of other mods
 * (Biomes O' Plenty overrides vanilla ones and adds its own beaches, snows, jungles and Nether biomes).
 */
public final class Biomes {

    private Biomes() {}

    private static boolean is(BiomeGenBase b, Type t) {
        return b != null && BiomeDictionary.isBiomeOfType(b, t);
    }

    public static boolean ocean(BiomeGenBase b) {
        return b == BiomeGenBase.ocean || b == BiomeGenBase.deepOcean || b == BiomeGenBase.frozenOcean || is(b, Type.OCEAN);
    }

    /** A sandy beach (stone and shingle ones do not count): for the Carcinus lair. */
    public static boolean sandBeach(BiomeGenBase b) {
        if (b == BiomeGenBase.beach || b == BiomeGenBase.coldBeach) return true;
        return is(b, Type.BEACH) && b.topBlock == Blocks.sand;
    }

    /** Any beach (for fishing - the sea zone). */
    public static boolean beach(BiomeGenBase b) {
        return b == BiomeGenBase.beach || b == BiomeGenBase.coldBeach || b == BiomeGenBase.stoneBeach || is(b, Type.BEACH);
    }

    /** Snowy land: for the Snow Glacon cave. */
    public static boolean snowyLand(BiomeGenBase b) {
        if (b == null || ocean(b) || beach(b) || is(b, Type.RIVER) || is(b, Type.NETHER) || is(b, Type.END)) return false;
        return b.getEnableSnow() || is(b, Type.SNOWY);
    }

    /**
     * Snow lies on the ground: snowy land where at the surface (y = 72) it snows rather than rains. Biomes O' Plenty marks
     * cold biomes without snow (Dead Forest, tundra) as snowy too - the snow there is only on the peaks.
     */
    public static boolean snowsAt(BiomeGenBase b, int x, int z) {
        return snowyLand(b) && b.getFloatTemperature(x, 72, z) < 0.15F;
    }

    /**
     * Cold land: snow lies on the ground or it is cold (temperature at the surface below 0.3 - taiga, mountains, BoP cold forests). For the Snow
     * Glacon cave - with the BoP modpack there may be no snowy biomes near spawn at all; then the igloo is covered with snow itself.
     */
    public static boolean coldLand(BiomeGenBase b, int x, int z) {
        if (b == null || ocean(b) || beach(b) || is(b, Type.RIVER) || is(b, Type.NETHER) || is(b, Type.END) || is(b, Type.SWAMP)) return false;
        return snowsAt(b, x, z) || b.getFloatTemperature(x, 72, z) < 0.3F || is(b, Type.SNOWY) || is(b, Type.COLD);
    }

    public static boolean jungle(BiomeGenBase b) {
        return b instanceof net.minecraft.world.biome.BiomeGenJungle || is(b, Type.JUNGLE);
    }

    public static boolean nether(BiomeGenBase b) {
        return b instanceof net.minecraft.world.biome.BiomeGenHell || is(b, Type.NETHER);
    }

    public static boolean end(BiomeGenBase b) {
        return b instanceof net.minecraft.world.biome.BiomeGenEnd || is(b, Type.END);
    }
}
