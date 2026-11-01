package com.divinerpgextended.asm;

import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.event.ForgeEventFactory;

/** Вызывается из WorldServer.spawnRandomCreature ({@link SpawnTransformer}). */
public final class SpawnHooks {

    private SpawnHooks() {}

    /** Сколько раз игра выбирала вид моба через хук (для самопроверки: подмена в WorldServer работает). */
    public static long calls;

    /** Как ForgeEventFactory.getPotentialSpawns, но без видов выше ранга игроков рядом. */
    public static List<BiomeGenBase.SpawnListEntry> potentialSpawns(WorldServer w, EnumCreatureType type, int x, int y, int z,
        List<BiomeGenBase.SpawnListEntry> list) {
        calls++;
        List<BiomeGenBase.SpawnListEntry> l = ForgeEventFactory.getPotentialSpawns(w, type, x, y, z, list);
        return com.divinerpgextended.boss.ProgressSpawns.candidates(w, x, z, l);
    }

    /** Существа при генерации чанка (SpawnerAnimals.performWorldGenSpawning): без видов выше ранга игроков рядом. */
    public static List<BiomeGenBase.SpawnListEntry> worldGenSpawns(List<BiomeGenBase.SpawnListEntry> list, net.minecraft.world.World w, int x, int z) {
        return com.divinerpgextended.boss.ProgressSpawns.candidates(w, x, z, list);
    }
}
