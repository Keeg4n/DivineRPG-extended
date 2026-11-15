package com.divinerpgextended.asm;

import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.event.ForgeEventFactory;

/** Called from WorldServer.spawnRandomCreature ({@link SpawnTransformer}). */
public final class SpawnHooks {

    private SpawnHooks() {}

    /** How many times the game chose a mob kind through the hook (for the selftest: the substitution in WorldServer works). */
    public static long calls;

    /** Like ForgeEventFactory.getPotentialSpawns, but without kinds above the rank of nearby players. */
    public static List<BiomeGenBase.SpawnListEntry> potentialSpawns(WorldServer w, EnumCreatureType type, int x, int y, int z,
        List<BiomeGenBase.SpawnListEntry> list) {
        calls++;
        List<BiomeGenBase.SpawnListEntry> l = ForgeEventFactory.getPotentialSpawns(w, type, x, y, z, list);
        return com.divinerpgextended.boss.ProgressSpawns.candidates(w, x, z, l);
    }

    /** Creatures at chunk generation (SpawnerAnimals.performWorldGenSpawning): without kinds above the rank of nearby players. */
    public static List<BiomeGenBase.SpawnListEntry> worldGenSpawns(List<BiomeGenBase.SpawnListEntry> list, net.minecraft.world.World w, int x, int z) {
        return com.divinerpgextended.boss.ProgressSpawns.candidates(w, x, z, list);
    }
}
