package com.divinerpgextended.world.twilight;

import net.minecraft.world.biome.BiomeGenBase;

/**
 * Temperature and humidity of twilight dimension biomes (in Divine - like plains). Mods and the game look at them: plant growth,
 * weather, colors, sensors, the Blood Magic Gaia ritual. Eden is hot and dry, Wildwood is humid, Skythern is cool and the most
 * humid (clouds), Mortum is cold and dead-dry. The combinations are chosen so that no biome of the modpack (Biomes O Plenty) is
 * closer than 0.1 - otherwise the Gaia ritual takes it.
 */
public final class TwilightClimate {

    /** World - {temperature, humidity}. */
    static final Object[][] CLIMATE = { { "BiomeGenEden", 1.7F, 0.1F }, { "BiomeGenWildwood", 0.7F, 1.7F }, { "BiomeGenApalachia", 1.1F, 1.2F },
        { "BiomeGenSkythern", 0.5F, 1.9F }, { "BiomeGenMortum", 0.2F, 0.1F } };

    private TwilightClimate() {}

    public static void apply() {
        for (BiomeGenBase b : BiomeGenBase.getBiomeGenArray()) {
            if (b == null || !b.getClass().getName().startsWith("net.divinerpg.dimensions.twilight.")) continue;
            for (Object[] c : CLIMATE) if (b.getClass().getSimpleName().equals(c[0])) {
                b.temperature = (Float) c[1];
                b.rainfall = (Float) c[2];
            }
            // Eden water: Divine has an almost black multiplier; the texture there is its own, golden (WaterTransformer) - the multiplier is neutral
            if (b.getClass().getSimpleName().equals("BiomeGenEden")) b.waterColorMultiplier = 0xFFFFFF;
        }
    }
}
