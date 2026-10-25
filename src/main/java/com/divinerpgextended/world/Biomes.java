package com.divinerpgextended.world;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;

/**
 * Какой это биом - по типам словаря биомов Forge, а не по ванильным объектам: так узнаются и биомы других модов
 * (Biomes O' Plenty переопределяет ванильные и добавляет свои пляжи, снега, джунгли и биомы Ада).
 */
public final class Biomes {

    private Biomes() {}

    private static boolean is(BiomeGenBase b, Type t) {
        return b != null && BiomeDictionary.isBiomeOfType(b, t);
    }

    public static boolean ocean(BiomeGenBase b) {
        return b == BiomeGenBase.ocean || b == BiomeGenBase.deepOcean || b == BiomeGenBase.frozenOcean || is(b, Type.OCEAN);
    }

    /** Песчаный пляж (каменный и галечный - нет): под логово Карцинуса. */
    public static boolean sandBeach(BiomeGenBase b) {
        if (b == BiomeGenBase.beach || b == BiomeGenBase.coldBeach) return true;
        return is(b, Type.BEACH) && b.topBlock == Blocks.sand;
    }

    /** Любой пляж (для рыбалки - морская зона). */
    public static boolean beach(BiomeGenBase b) {
        return b == BiomeGenBase.beach || b == BiomeGenBase.coldBeach || b == BiomeGenBase.stoneBeach || is(b, Type.BEACH);
    }

    /** Заснеженная суша: под пещеру Снежного Глакона. */
    public static boolean snowyLand(BiomeGenBase b) {
        if (b == null || ocean(b) || beach(b) || is(b, Type.RIVER) || is(b, Type.NETHER) || is(b, Type.END)) return false;
        return b.getEnableSnow() || is(b, Type.SNOWY);
    }

    /**
     * Снег лежит на земле: заснеженная суша, где у поверхности (y = 72) снег, а не дождь. Biomes O' Plenty помечает
     * снежными и холодные биомы без снега (Dead Forest, тундра) - снег там только на вершинах.
     */
    public static boolean snowsAt(BiomeGenBase b, int x, int z) {
        return snowyLand(b) && b.getFloatTemperature(x, 72, z) < 0.15F;
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
