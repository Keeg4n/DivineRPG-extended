package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraftforge.common.BiomeDictionary;

import com.divinerpgextended.Config;

/**
 * Overworld lair sites - from the world seed and biome map, in advance, before generation: the lair map knows them even if
 * chunks are not yet created (before, lairs were placed randomly and the map saw only the built ones).
 * <ul>
 * <li>Carcinus - one per region {@link Config#carcinusSpacing}: a sandy beach right at the water;</li>
 * <li>the Snow Glacon ice cave - one per region {@link Config#snowDungeonSpacing}: flat snowy land
 * without water across the whole cave area;</li>
 * <li>the Ancient Tomb - under a vanilla desert pyramid: the game itself picks its place from the seed (the same formula as
 * MapGenScatteredFeature).</li>
 * </ul>
 * The site is a chunk center; the lair is built when this chunk is populated.
 */
public final class LairSites {

    public static final int CARCINUS = LairData.CARCINUS, SNOW = LairData.SNOW, TOMB = LairData.TOMB;
    private static final int[] NONE = new int[0];
    /** The "ran out of time" answer: the search hit the allotted time, finish counting next tick (what was counted is in the cache). */
    public static final int[] UNFINISHED = new int[0];
    /** Per-world cache: seed + type + region -> site (NONE - the region has no site). */
    private static final Map<Long, int[]> CACHE = new HashMap<Long, int[]>();

    private LairSites() {}

    public static int spacing(int type) {
        if (type == CARCINUS) return Config.carcinusSpacing;
        if (type == SNOW) return Config.snowDungeonSpacing;
        return 32 * 16; // pyramids: a 32 x 32 chunk region
    }

    private static long key(World w, int type, int rx, int rz) {
        return (w.getSeed() * 31 + type) * 0x9E3779B97F4A7C15L ^ ((long) rx << 32 | (rz & 0xFFFFFFFFL));
    }

    /** For the selftest: forget the counted sites. */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        synchronized (PROGRESS) {
            PROGRESS.clear();
        }
        synchronized (RAW) {
            RAW.clear();
        }
    }

    /** The site of a lair of type type in region (rx, rz) or null. */
    public static int[] site(World w, int type, int rx, int rz) {
        return site(w, type, rx, rz, Long.MAX_VALUE);
    }

    /** How far the pass over uncounted regions got: region key -> {point number, point check step}. */
    private static final Map<Long, int[]> PROGRESS = new HashMap<Long, int[]>();

    /** The same, but no longer than deadline: ran out of time - {@link #UNFINISHED}, the region pass continues from the same point. */
    static int[] site(World w, int type, int rx, int rz, long deadline) {
        int size = spacing(type);
        if (size <= 0) return null;
        long key = key(w, type, rx, rz);
        synchronized (CACHE) {
            int[] c = CACHE.get(key);
            if (c != null) return c == NONE ? null : c;
        }
        int[] s = raw(w, type, rx, rz, deadline);
        if (s == UNFINISHED) return s;
        // lairs of one kind no closer than Config.lairGap: of two close ones the one whose region has higher priority remains
        if (s != null && (type == CARCINUS || type == SNOW) && com.divinerpgextended.Config.lairGap > 0) {
            int g = com.divinerpgextended.Config.lairGap, n = (g + size - 1) / size;
            for (int i = -n; i <= n && s != null; i++) for (int k = -n; k <= n && s != null; k++) {
                if (i == 0 && k == 0) continue;
                int[] o = raw(w, type, rx + i, rz + k, deadline);
                if (o == UNFINISHED) return o;
                if (o == null || (long) (o[0] - s[0]) * (o[0] - s[0]) + (long) (o[1] - s[1]) * (o[1] - s[1]) >= (long) g * g) continue;
                if (priority(w, rx + i, rz + k) > priority(w, rx, rz)) s = null;
            }
        }
        synchronized (CACHE) {
            if (CACHE.size() > 20000) CACHE.clear();
            CACHE.put(key, s == null ? NONE : s);
        }
        return s;
    }

    /** The region's site before checking neighbors. */
    private static final Map<Long, int[]> RAW = new HashMap<Long, int[]>();

    private static int[] raw(World w, int type, int rx, int rz, long deadline) {
        if (type == TOMB) return pyramid(w, rx, rz);
        long key = key(w, type, rx, rz) ^ 0x5DEECE66DL;
        synchronized (RAW) {
            int[] c = RAW.get(key);
            if (c != null) return c == NONE ? null : c;
        }
        int[] s = scan(w, type, rx, rz, spacing(type), key, deadline);
        if (s == UNFINISHED) return s;
        synchronized (RAW) {
            if (RAW.size() > 20000) RAW.clear();
            RAW.put(key, s == null ? NONE : s);
        }
        return s;
    }

    private static long priority(World w, int rx, int rz) {
        long h = w.getSeed() ^ rx * 0x9E3779B97F4A7C15L ^ rz * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        return h * 0xBF58476D1CE4E5B9L;
    }

    /**
     * A seed site where there will be no lair: its chunk is already populated (created earlier - before this version, without the addon, or before settings
     * changed), there is no lair nearby, and the tomb is not awaiting construction. Such a site is no good for the map; it is remembered as failed.
     */
    public static boolean stale(World w, int type, int x, int z) {
        if (!(w instanceof net.minecraft.world.WorldServer)) return false;
        LairData d = LairData.get(w);
        if (d.near(type, x, z, 96) || type == TOMB && WorldGenTomb.pendingNear(w, x, z, 96)) return false;
        if (!settled((net.minecraft.world.WorldServer) w, x >> 4, z >> 4)) return false;
        d.fail(type, x, z);
        return true;
    }

    /** The chunk is populated: loaded and populated, or on disk along with the neighbors without which population cannot happen (+x, +z). */
    static boolean settled(net.minecraft.world.WorldServer w, int cx, int cz) {
        net.minecraft.world.gen.ChunkProviderServer p = w.theChunkProviderServer;
        if (p.chunkExists(cx, cz)) return w.getChunkFromChunkCoords(cx, cz).isTerrainPopulated;
        if (!(p.currentChunkLoader instanceof net.minecraft.world.chunk.storage.AnvilChunkLoader)) return false;
        net.minecraft.world.chunk.storage.AnvilChunkLoader l = (net.minecraft.world.chunk.storage.AnvilChunkLoader) p.currentChunkLoader;
        for (int i = 0; i <= 1; i++) for (int k = 0; k <= 1; k++) if (!p.chunkExists(cx + i, cz + k) && !l.chunkExists(w, cx + i, cz + k)) return false;
        return true;
    }

    /** The site in this chunk's region, if it is in this chunk. */
    public static int[] siteInChunk(World w, int type, int chunkX, int chunkZ) {
        int size = spacing(type);
        if (size <= 0) return null;
        int bx = chunkX * 16 + 8, bz = chunkZ * 16 + 8;
        int[] s = site(w, type, Math.floorDiv(bx, size), Math.floorDiv(bz, size));
        return s != null && s[0] >> 4 == chunkX && s[1] >> 4 == chunkZ ? s : null;
    }

    /** A pass over the region's points in seed order; the first suitable one is the site. Ran out of time before deadline - UNFINISHED. */
    private static int[] scan(World w, int type, int rx, int rz, int size, long key, long deadline) {
        WorldChunkManager wcm = w.getWorldChunkManager();
        int step = 32, n = Math.max(1, size / step), total = n * n;
        Random r = new Random(w.getSeed() + rx * 341873128712L + rz * 132897987541L + (type == CARCINUS ? 52801 : 77213));
        int start = r.nextInt(total), stride = 7919 % total == 0 ? 1 : 7919;
        while (gcd(stride, total) != 1) stride++;
        int[] from;
        synchronized (PROGRESS) {
            from = PROGRESS.remove(key);
        }
        int m = from == null ? 0 : from[1];
        for (int k = from == null ? 0 : from[0]; k < total; k++, m = 0) {
            if (deadline != Long.MAX_VALUE && System.nanoTime() > deadline) return pause(key, k, m);
            int idx = (int) ((start + (long) k * stride) % total);
            int x = ((rx * size + (idx % n) * step) >> 4) * 16 + 8, z = ((rz * size + (idx / n) * step) >> 4) * 16 + 8;
            if (type == CARCINUS) {
                if (shore(wcm, x, z)) return new int[] { x, z };
                continue;
            }
            // a snowy field is checked at 35 points of the cave area - and can be interrupted midway
            int res = snowField(wcm, x, z, m, deadline);
            if (res == SNOW_YES) return new int[] { x, z };
            if (res >= 0) return pause(key, k, res);
        }
        return null;
    }

    private static int[] pause(long key, int k, int m) {
        synchronized (PROGRESS) {
            if (PROGRESS.size() > 5000) PROGRESS.clear();
            PROGRESS.put(key, new int[] { k, m });
        }
        return UNFINISHED;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /** A sandy beach, and 32 blocks from it an ocean: so, right at the water. */
    static boolean shore(WorldChunkManager wcm, int x, int z) {
        if (!Biomes.sandBeach(wcm.getBiomeGenAt(x, z))) return false;
        int[][] d = { { 24, 0 }, { -24, 0 }, { 0, 24 }, { 0, -24 }, { 16, 16 }, { -16, -16 }, { 16, -16 }, { -16, 16 } };
        for (int[] o : d) if (Biomes.ocean(wcm.getBiomeGenAt(x + o[0], z + o[1]))) return true;
        return false;
    }

    /** Snowy flat land across the whole cave area (to the east up to ~170 blocks, to the sides ~60). */
    static boolean snowField(WorldChunkManager wcm, int x, int z) {
        return snowField(wcm, x, z, 0, Long.MAX_VALUE) == SNOW_YES;
    }

    private static final int SNOW_YES = -2, SNOW_NO = -1;

    /**
     * Field check from step m (0 - center, then 35 points of the area): SNOW_YES, SNOW_NO or the step to resume from
     * if time ran out.
     */
    private static int snowField(WorldChunkManager wcm, int x, int z, int m, long deadline) {
        if (m == 0 && !Biomes.coldLand(wcm.getBiomeGenAt(x, z), x, z)) return SNOW_NO;
        int i = 0, bare = 0;
        for (int dx = -16; dx <= 176; dx += 32) for (int dz = -64; dz <= 64; dz += 32) {
            if (++i <= m) continue;
            if (deadline != Long.MAX_VALUE && System.nanoTime() > deadline) return i - 1;
            BiomeGenBase b = wcm.getBiomeGenAt(x + dx, z + dz);
            if (b == null || Biomes.ocean(b) || Biomes.beach(b) || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.RIVER)
                || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.SWAMP) || b.rootHeight < 0.0F || b.heightVariation > 0.45F) return SNOW_NO;
            // above the cave is a snow field: no more than a quarter snowless points (resuming after a pause counts anew)
            if (!Biomes.coldLand(b, x + dx, z + dz) && ++bare > 8) return SNOW_NO;
        }
        return SNOW_YES;
    }

    /**
     * A desert pyramid in a 32 x 32 chunk region - as MapGenScatteredFeature places it (the same seed and a 0..23 chunk shift),
     * if there is desert at that spot (with {@link ScatterGen} - any hot sand, BoP too). @return the pyramid's chunk center
     */
    static int[] pyramid(World w, int rx, int rz) {
        Random r = new Random(rx * 341873128712L + rz * 132897987541L + w.getWorldInfo().getSeed() + 14357617L);
        int cx = rx * 32 + r.nextInt(24), cz = rz * 32 + r.nextInt(24);
        BiomeGenBase b = w.getWorldChunkManager().getBiomeGenAt(cx * 16 + 8, cz * 16 + 8);
        boolean desert = com.divinerpgextended.Config.scatterInModBiomes ? ScatterGen.desert(b) && !Biomes.jungle(b)
            : b == BiomeGenBase.desert || b == BiomeGenBase.desertHills;
        return desert && ScatterGen.allowed(w, rx, rz) ? new int[] { cx * 16 + 8, cz * 16 + 8 } : null;
    }

    /**
     * The nearest site farther than skip and no farther than radius blocks: rings of regions from the player, until the next ring is no farther
     * than the one already found.
     */
    public static int[] nearest(World w, int type, double px, double pz, int radius, int skip) {
        return nearest(w, type, px, pz, radius, skip, null);
    }

    /** The same, but only sites that passed the check ok (for example, not yet built). */
    public static int[] nearest(World w, int type, double px, double pz, int radius, int skip, java.util.function.Predicate<int[]> ok) {
        return nearest(w, type, px, pz, radius, skip, ok, Long.MAX_VALUE);
    }

    /**
     * The same, but no longer than until deadline (System.nanoTime): ran out of time - {@link #UNFINISHED}, counted regions stay in
     * the cache, and the next call resumes from where it stopped. This way the map does not hang the game on a distant search.
     */
    public static int[] nearest(World w, int type, double px, double pz, int radius, int skip, java.util.function.Predicate<int[]> ok, long deadline) {
        int size = spacing(type);
        if (size <= 0) return null;
        int rx0 = Math.floorDiv((int) Math.floor(px), size), rz0 = Math.floorDiv((int) Math.floor(pz), size), rings = radius / size + 1;
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int ring = 0; ring <= rings; ring++) {
            double near = Math.max(0, (ring - 1) * (double) size);
            if (best != null && near * near > bestD) break;
            for (int i = -ring; i <= ring; i++) for (int j = -ring; j <= ring; j++) {
                if (Math.abs(i) != ring && Math.abs(j) != ring) continue;
                int[] s = site(w, type, rx0 + i, rz0 + j, deadline);
                if (s == UNFINISHED) return UNFINISHED;
                if (s == null) continue;
                double d = (s[0] - px) * (s[0] - px) + (s[1] - pz) * (s[1] - pz);
                if (d <= (double) skip * skip || d > (double) radius * radius || d >= bestD || ok != null && !ok.test(s)) continue;
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** Sites within the radius (nearest regions first); farther than skip blocks from the player. */
    public static List<int[]> around(World w, int type, double px, double pz, int radius) {
        List<int[]> out = new ArrayList<int[]>();
        int size = spacing(type);
        if (size <= 0) return out;
        int rx0 = Math.floorDiv((int) px, size), rz0 = Math.floorDiv((int) pz, size), rings = radius / size + 1;
        for (int ring = 0; ring <= rings; ring++) {
            for (int i = -ring; i <= ring; i++) for (int j = -ring; j <= ring; j++) {
                if (Math.abs(i) != ring && Math.abs(j) != ring) continue;
                int[] s = site(w, type, rx0 + i, rz0 + j);
                if (s != null) out.add(s);
            }
        }
        return out;
    }
}
