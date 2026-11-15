package com.divinerpgextended.world;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Jungle: Jungle Ore (only in jungles, deep) and the lair of the Ancient Dramcryx under the vanilla jungle temple.
 * We find the temple in the vanilla structure generator (by reflection on field types - names differ in development and in game); the lair
 * is built when all the temple's chunks are already populated (otherwise the temple, when completed, would bury the descent). From the temple a
 * moss-lined well with a spiral staircase leads down to the abandoned underground city ({@link LairDomes.JungleCity}); the altar is on the arena at the center
 * of the city ({@link #ALTAR_DZ} north of its center; the boss stands at the center).
 */
public class WorldGenJungleLair implements IWorldGenerator {

    public static final int ORE_VEINS = 3, ORE_SIZE = 5, ORE_MIN_Y = 8, ORE_MAX_Y = 36;
    public static final int BOSS_R = 16, ALTAR_DZ = 6;
    public static int built;

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != 0) return;
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        BiomeGenBase b = world.getBiomeGenForCoords(x0 + 8, z0 + 8);
        if (jungle(b)) for (int i = 0; i < ORE_VEINS; i++)
            new WorldGenMinable(ModBlocks.jungleOre, ORE_SIZE).generate(world, rand, x0 + rand.nextInt(16), ORE_MIN_Y + rand.nextInt(ORE_MAX_Y - ORE_MIN_Y),
                z0 + rand.nextInt(16));
        for (StructureBoundingBox t : temples(gen)) {
            if (t.maxX < x0 || t.minX > x0 + 15 || t.maxZ < z0 || t.minZ > z0 + 15) continue;
            if (!templeReady(world, t, chunkX, chunkZ) || isBuilt(world, t)) continue;
            // a vanilla temple (without our own temple generator) next to another - no lair: lairs of one kind no closer than lairGap
            if (!ScatterGen.allowed(world, Math.floorDiv(t.minX >> 4, ScatterGen.MAX), Math.floorDiv(t.minZ >> 4, ScatterGen.MAX))) continue;
            int[] a = build(world, new Random(world.getSeed() ^ t.minX * 341873128712L ^ t.minZ * 132897987541L), t);
            if (a != null) DivineRPGExtended.LOG.info("Jungle lair under temple " + t.minX + " " + t.minZ + ": altar " + a[0] + " " + a[1] + " " + a[2]);
        }
    }

    /** Jungle - both vanilla and other mods' (Biomes O' Plenty). */
    public static boolean jungle(BiomeGenBase b) {
        return Biomes.jungle(b);
    }

    // ---------------------------------------------------------------- temples

    private static Field scattered, structures;

    /** Bounding boxes of jungle temples that the vanilla generator has already planned. */
    @SuppressWarnings("unchecked")
    public static List<StructureBoundingBox> temples(IChunkProvider gen) {
        List<StructureBoundingBox> out = new ArrayList<StructureBoundingBox>();
        try {
            if (scattered == null) {
                for (Class<?> c = gen.getClass(); c != null && scattered == null; c = c.getSuperclass())
                    for (Field f : c.getDeclaredFields()) if (MapGenScatteredFeature.class.isAssignableFrom(f.getType())) scattered = f;
                for (Field f : MapGenStructure.class.getDeclaredFields()) if (Map.class.isAssignableFrom(f.getType())) structures = f;
                if (scattered != null) scattered.setAccessible(true);
                if (structures != null) structures.setAccessible(true);
            }
            if (scattered == null || structures == null || !scattered.getDeclaringClass().isInstance(gen)) return out;
            Object g = scattered.get(gen);
            if (g == null) return out;
            for (Object o : ((Map<Object, Object>) structures.get(g)).values()) {
                StructureStart s = (StructureStart) o;
                for (Object c : s.getComponents()) if (((StructureComponent) c).getClass().getName().contains("JunglePyramid")) {
                    out.add(((StructureComponent) c).getBoundingBox());
                    break;
                }
            }
        } catch (Exception ex) {
            // another world generator (biome mods) - we do not know the temples
        }
        return out;
    }

    /** All the temple's chunks are already populated (the current one is being populated now). */
    static boolean templeReady(World w, StructureBoundingBox t, int cx, int cz) {
        for (int x = t.minX >> 4; x <= t.maxX >> 4; x++)
            for (int z = t.minZ >> 4; z <= t.maxZ >> 4; z++) {
                if (x == cx && z == cz) continue;
                if (!w.getChunkProvider().chunkExists(x, z) || !w.getChunkFromChunkCoords(x, z).isTerrainPopulated) return false;
            }
        return true;
    }

    /** The temple well into the city (by the temple box: the temple stands from the corner of its chunk). */
    static int[] center(StructureBoundingBox t) {
        return LairDomes.jungleWell(t.minX >> 4, t.minZ >> 4);
    }

    /** Center of the plaza arena by the temple box: {center x, floor y, center z}; the altar is ALTAR_DZ to the north, 2 above the floor. */
    public static int[] hall(StructureBoundingBox t) {
        return LairDomes.jungleCenter(t.minX >> 4, t.minZ >> 4); // the arena is at the center of the city
    }

    /** The well is already dug: at its middle under the city vault is air. */
    static boolean isBuilt(World w, StructureBoundingBox t) {
        int[] c = center(t);
        return w.isAirBlock(c[0], LairDomes.towerCeil() + 6, c[2]);
    }

    // ---------------------------------------------------------------- well

    private static final Block[] SHELL = { Blocks.mossy_cobblestone, Blocks.mossy_cobblestone, Blocks.cobblestone, Blocks.stonebrick, Blocks.stonebrick };

    static void shell(World w, int x, int y, int z, Random r) {
        int k = r.nextInt(SHELL.length);
        int meta = SHELL[k] == Blocks.stonebrick ? (r.nextBoolean() ? 1 : 2) : 0; // mossy or cracked brick
        w.setBlock(x, y, z, SHELL[k], meta, 2);
    }

    /**
     * The well from the temple into the abandoned city ({@link LairDomes.JungleCity}): a 3x3 shaft from the temple floor to the vault, steps along
     * the ring (at height y - cell RING[y & 7], as in the city tower - the staircase is continuous), walls lined with mossy stone.
     * @return coordinates of the altar on the plaza arena or null (the temple is too low above the city)
     */
    public static int[] build(World world, Random rand, StructureBoundingBox t) {
        int g = t.minY; // bottom of the temple
        int[] c = center(t);
        int cx = c[0], cz = c[2], top = LairDomes.towerCeil() + 4;
        if (g - top < 6) return null;
        for (int y = top; y <= g + 1; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = cx + dx, z = cz + dz;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                int k = LairDomes.ringIndex(dx, dz);
                if (k >= 0 && (y & 7) == k && y <= g) world.setBlock(x, y, z, Blocks.stonebrick, 1, 2);
                else world.setBlock(x, y, z, Blocks.air, 0, 2);
            } else if (y < g) shell(world, x, y, z, rand);
        }
        built++;
        int[] h = hall(t);
        return new int[] { h[0], h[1] + 2, h[2] - ALTAR_DZ };
    }
}
