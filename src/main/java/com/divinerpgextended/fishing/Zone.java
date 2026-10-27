package com.divinerpgextended.fishing;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

/** Где ловим: от мира, биома, глубины и жидкости зависит улов и ящики. */
public enum Zone {

    OCEAN, FRESH, SNOW, ICEIKA, JUNGLE, DESERT, CAVE, HELL, ARCANA, EDEN, WILDWOOD, APALACHIA, SKYTHERN, MORTUM, DREAM;

    public String key() {
        return name().toLowerCase();
    }

    public static Zone at(World w, double px, double py, double pz, boolean lava) {
        if (lava) return HELL;
        int d = w.provider.dimensionId;
        if (d == -1) return HELL;
        if (d == ConfigurationHelper.iceika) return ICEIKA;
        // рыба Арканы - только в арканном концентрате (фонтанные залы), где бы он ни был; прочая вода Арканы - пресная
        if (com.divinerpgextended.block.BlockArcaneConcentrate.at(w, px, py, pz) || com.divinerpgextended.block.BlockArcaneConcentrate.at(w, px, py - 0.5, pz)) return ARCANA;
        if (d == ConfigurationHelper.arcana) return FRESH;
        if (d == ConfigurationHelper.eden) return EDEN;
        if (d == ConfigurationHelper.wildwood) return WILDWOOD;
        if (d == ConfigurationHelper.apalachia) return APALACHIA;
        if (d == ConfigurationHelper.skythern) return SKYTHERN;
        if (d == ConfigurationHelper.mortum) return MORTUM;
        if (d == ConfigurationHelper.vethea) return DREAM;
        int x = MathHelper.floor_double(px), y = MathHelper.floor_double(py), z = MathHelper.floor_double(pz);
        if (y < 48 && !w.canBlockSeeTheSky(x, y + 1, z)) return CAVE;
        BiomeGenBase b = w.getBiomeGenForCoords(x, z);
        if (b == null) return FRESH;
        if (com.divinerpgextended.world.Biomes.ocean(b) || com.divinerpgextended.world.Biomes.beach(b)) return OCEAN;
        if (b.getEnableSnow() || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.SNOWY)) return SNOW;
        if (BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.JUNGLE)) return JUNGLE;
        if (BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.SANDY) && !BiomeDictionary.isBiomeOfType(b,
            BiomeDictionary.Type.BEACH)) return DESERT;
        return FRESH;
    }
}
