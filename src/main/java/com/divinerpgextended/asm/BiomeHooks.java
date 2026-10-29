package com.divinerpgextended.asm;

import net.minecraft.world.biome.BiomeGenBase;

/** Вызовы из слоёв мутаций генерации обычного мира ({@link BiomeMutationTransformer}). */
public final class BiomeHooks {

    private BiomeHooks() {}

    /**
     * Биом по номеру для слоя мутаций: номера до 128 - как есть; на месте мутации (128+) - только ванильная мутация
     * (класс Minecraft или BiomeGenMutated), чужой биом мода - null, и слой оставит исходный биом.
     */
    public static BiomeGenBase mutation(int id) {
        BiomeGenBase b = BiomeGenBase.getBiome(id);
        if (b == null || id < 128) return b;
        if (b instanceof net.minecraft.world.biome.BiomeGenMutated || b.getClass().getName().startsWith("net.minecraft.")) return b;
        return null;
    }
}
