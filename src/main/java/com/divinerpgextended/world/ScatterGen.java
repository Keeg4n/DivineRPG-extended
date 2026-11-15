package com.divinerpgextended.world;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.structure.ComponentScatteredFeaturePieces;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.event.terraingen.InitMapGenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Overworld pyramids and jungle temples - in Biomes O' Plenty biomes too. The vanilla generator places them only in vanilla
 * desert and jungle, and BoP displaces them (measured on the modpack: in a 10000 x 10000 square no jungles at all, yet the tomb and the lair of the
 * Ancient Dramcryx are built under them). Here is the same generator (the same sites by seed, the same structures), but the biome is by
 * the Forge dictionary:
 * <ul>
 * <li>hot sand - a desert pyramid (the tomb {@link WorldGenTomb} is above it);</li>
 * <li>jungle of any mod - a jungle temple (the lair {@link WorldGenJungleLair} is under it); if there are no jungles at all
 * near spawn - the temple in warm green biomes (savanna, warm forest, wet lands);</li>
 * <li>swamp - a witch hut, as in vanilla.</li>
 * </ul>
 * Substituted by a Forge event when the world generator is created.
 */
public class ScatterGen extends MapGenScatteredFeature {

    public static final int NONE = -1, DESERT = 0, JUNGLE = 1, SWAMP = 2;
    /** Step and minimum distance of the vanilla generator (in chunks) and its seed salt. */
    static final int MAX = 32, MIN = 8, SALT = 14357617;

    /** Whether there are jungles in the 10000 x 10000 square at spawn (computed once per world). */
    private Boolean jungles;

    public static void register() {
        MapGenStructureIO.registerStructure(Start.class, "DreTemple");
    }

    private static boolean is(BiomeGenBase b, Type t) {
        return BiomeDictionary.isBiomeOfType(b, t);
    }

    /** Hot sand (not a beach): vanilla and BoP desert. */
    public static boolean desert(BiomeGenBase b) {
        if (b == null) return false;
        if (b == BiomeGenBase.desert || b == BiomeGenBase.desertHills) return true;
        return is(b, Type.SANDY) && is(b, Type.HOT) && !is(b, Type.BEACH) && !Biomes.ocean(b) && !is(b, Type.RIVER) && !is(b, Type.MESA);
    }

    /** Warm green lands - instead of jungle, if there is none. */
    static boolean warmGreen(BiomeGenBase b) {
        if (b == null || Biomes.ocean(b) || Biomes.beach(b) || is(b, Type.RIVER) || is(b, Type.SANDY) || is(b, Type.SWAMP) || is(b, Type.MOUNTAIN)) return false;
        return b.temperature >= 0.8F && (is(b, Type.SAVANNA) || is(b, Type.FOREST) || is(b, Type.LUSH) || is(b, Type.WET) || is(b, Type.PLAINS));
    }

    /** Which structure in this biome (jungles - whether the world has jungles). */
    public static int kind(BiomeGenBase b, boolean jungles) {
        if (b == null) return NONE;
        if (Biomes.jungle(b)) return JUNGLE;
        if (desert(b)) return DESERT;
        if (b == BiomeGenBase.swampland || is(b, Type.SWAMP)) return SWAMP;
        if (!jungles && warmGreen(b)) return JUNGLE;
        return NONE;
    }

    /**
     * Whether there are enough jungles at spawn: at least {@link #MIN_TEMPLES} temple grid sites in the 10000 x 10000 square fall into
     * jungle (otherwise jungle temples also stand in warm green lands). A jungle patch that no site fell into does not count.
     */
    public static boolean hasJungles(World w) {
        net.minecraft.world.biome.WorldChunkManager wcm = w.getWorldChunkManager();
        int n = 0;
        for (int rx = -5000 / (MAX * 16); rx <= 5000 / (MAX * 16); rx++) for (int rz = -5000 / (MAX * 16); rz <= 5000 / (MAX * 16); rz++) {
            int[] s = spot(w, rx, rz);
            if (Biomes.jungle(wcm.getBiomeGenAt(s[0] * 16 + 8, s[1] * 16 + 8)) && ++n >= MIN_TEMPLES) return true;
        }
        return false;
    }

    static final int MIN_TEMPLES = 3;

    private boolean jungles() {
        if (jungles == null) jungles = hasJungles(worldObj);
        return jungles;
    }

    /** The structure chunk in region (rx, rz) of the vanilla grid (as in MapGenScatteredFeature; its own Random - the world one is not touched). */
    public static int[] spot(World w, int rx, int rz) {
        Random r = new Random(rx * 341873128712L + rz * 132897987541L + w.getWorldInfo().getSeed() + SALT);
        return new int[] { rx * MAX + r.nextInt(MAX - MIN), rz * MAX + r.nextInt(MAX - MIN) };
    }

    private static final java.util.Map<World, Boolean> JUNGLES = new java.util.WeakHashMap<World, Boolean>();

    /** Whether the world has jungles (by setting and measurement at spawn, once per world). */
    public static boolean jungles(World w) {
        Boolean has = JUNGLES.get(w);
        if (has == null) JUNGLES.put(w, has = !com.divinerpgextended.Config.scatterInModBiomes || hasJungles(w));
        return has;
    }

    /** The structure kind in region (rx, rz). */
    public static int kindAt(World w, int rx, int rz) {
        int[] s = spot(w, rx, rz);
        return kind(w.getWorldChunkManager().getBiomeGenAt(s[0] * 16 + 8, s[1] * 16 + 8), jungles(w));
    }

    /**
     * A pyramid or jungle temple (with a lair under them) in region (rx, rz) is allowed: no same kind closer than
     * {@link com.divinerpgextended.Config#lairGap} blocks with a higher region priority. Witch huts are not limited.
     */
    public static boolean allowed(World w, int rx, int rz) {
        long key = (w.getSeed() * 31 + rx) * 0x9E3779B97F4A7C15L ^ rz * 0xC2B2AE3D27D4EB4FL ^ com.divinerpgextended.Config.lairGap;
        synchronized (ALLOWED) {
            Boolean c = ALLOWED.get(key);
            if (c != null) return c;
        }
        boolean ok = allowedNow(w, rx, rz);
        synchronized (ALLOWED) {
            if (ALLOWED.size() > 20000) ALLOWED.clear();
            ALLOWED.put(key, ok);
        }
        return ok;
    }

    private static final java.util.Map<Long, Boolean> ALLOWED = new java.util.HashMap<Long, Boolean>();

    private static boolean allowedNow(World w, int rx, int rz) {
        int gap = com.divinerpgextended.Config.lairGap, k = kindAt(w, rx, rz);
        if (gap <= 0 || k != DESERT && k != JUNGLE) return true;
        int[] s = spot(w, rx, rz);
        long me = priority(w, rx, rz);
        int n = (gap + MAX * 16 - 1) / (MAX * 16);
        for (int i = -n; i <= n; i++) for (int j = -n; j <= n; j++) {
            if (i == 0 && j == 0) continue;
            int[] o = spot(w, rx + i, rz + j);
            long dx = (o[0] - s[0]) * 16L, dz = (o[1] - s[1]) * 16L;
            if (dx * dx + dz * dz >= (long) gap * gap || kindAt(w, rx + i, rz + j) != k) continue;
            if (priority(w, rx + i, rz + j) > me) return false;
        }
        return true;
    }

    static long priority(World w, int rx, int rz) {
        long h = w.getSeed() ^ rx * 0x9E3779B97F4A7C15L ^ rz * 0xC2B2AE3D27D4EB4FL ^ 0x7E3A1L;
        h ^= h >>> 31;
        return h * 0xBF58476D1CE4E5B9L;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        int rx = Math.floorDiv(cx, MAX), rz = Math.floorDiv(cz, MAX);
        int[] s = spot(worldObj, rx, rz);
        if (s[0] != cx || s[1] != cz) return false;
        return kind(worldObj.getWorldChunkManager().getBiomeGenAt(cx * 16 + 8, cz * 16 + 8), jungles()) != NONE && allowed(worldObj, rx, rz);
    }

    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        return new Start(worldObj, rand, cx, cz, kind(worldObj.getBiomeGenForCoords(cx * 16 + 8, cz * 16 + 8), jungles()));
    }

    /** The structure: the same vanilla parts as MapGenScatteredFeature.Start, but by the kind from {@link #kind}. */
    public static class Start extends StructureStart {

        public Start() {}

        @SuppressWarnings("unchecked")
        public Start(World w, Random r, int cx, int cz, int kind) {
            super(cx, cz);
            if (kind == JUNGLE) components.add(new ComponentScatteredFeaturePieces.JunglePyramid(r, cx * 16, cz * 16));
            else if (kind == SWAMP) components.add(new ComponentScatteredFeaturePieces.SwampHut(r, cx * 16, cz * 16));
            else components.add(new ComponentScatteredFeaturePieces.DesertPyramid(r, cx * 16, cz * 16));
            updateBoundingBox();
        }
    }

    /** Substitution of the overworld structure generator. */
    public static class Init {

        @SubscribeEvent
        public void onInit(InitMapGenEvent e) {
            if (e.type == InitMapGenEvent.EventType.SCATTERED_FEATURE && com.divinerpgextended.Config.scatterInModBiomes) e.newGen = new ScatterGen();
        }
    }
}
