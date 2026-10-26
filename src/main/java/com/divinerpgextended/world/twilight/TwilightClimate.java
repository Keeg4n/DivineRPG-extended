package com.divinerpgextended.world.twilight;

import net.minecraft.world.biome.BiomeGenBase;

/**
 * Температура и влажность биомов сумеречных миров (у Divine - как у равнин). Их смотрят моды и игра: рост растений,
 * погода, цвета, датчики, ритуал Гайи Blood Magic. Эдем - жаркий и сухой, Дикий лес - влажный, Скайтерн - прохладный и самый
 * влажный (облака), Мортум - холодный и мёртво-сухой. Сочетания выбраны так, чтобы ни один биом сборки (Biomes O Plenty) не
 * был ближе 0.1 - иначе ритуал Гайи берёт его.
 */
public final class TwilightClimate {

    /** Мир - {температура, влажность}. */
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
        }
    }
}
