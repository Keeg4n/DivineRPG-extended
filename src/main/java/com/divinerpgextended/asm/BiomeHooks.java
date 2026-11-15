package com.divinerpgextended.asm;

import net.minecraft.world.biome.BiomeGenBase;

/** Calls from the mutation layers of Overworld generation ({@link BiomeMutationTransformer}). */
public final class BiomeHooks {

    private BiomeHooks() {}

    /**
     * Biome by number for the mutation layer: numbers below 128 - as is; in place of a mutation (128+) - only a vanilla mutation
     * (a Minecraft or BiomeGenMutated class), another mod's biome - null, and the layer will keep the original biome.
     */
    public static BiomeGenBase mutation(int id) {
        BiomeGenBase b = BiomeGenBase.getBiome(id);
        if (b == null || id < 128) return b;
        if (b instanceof net.minecraft.world.biome.BiomeGenMutated || b.getClass().getName().startsWith("net.minecraft.")) return b;
        return null;
    }
}
