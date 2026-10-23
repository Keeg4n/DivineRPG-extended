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
 * Места логов обычного мира - из сида мира и карты биомов, заранее, до генерации: карта логова знает их, даже если
 * чанки ещё не созданы (раньше логова ставились случайно и карта видела только построенные).
 * <ul>
 * <li>Карцинус - по одному на регион {@link Config#carcinusSpacing}: песчаный пляж у самой воды;</li>
 * <li>ледяная пещера Снежного Глакона - по одной на регион {@link Config#snowDungeonSpacing}: ровная заснеженная суша
 * без воды на всей площади пещеры;</li>
 * <li>Древняя гробница - под ванильной пустынной пирамидой: её место игра сама выбирает из сида (та же формула, что у
 * MapGenScatteredFeature).</li>
 * </ul>
 * Место - центр чанка; логово строится, когда этот чанк заселяется.
 */
public final class LairSites {

    public static final int CARCINUS = LairData.CARCINUS, SNOW = LairData.SNOW, TOMB = LairData.TOMB;
    private static final int[] NONE = new int[0];
    /** Кэш по миру: сид + тип + регион -> место (NONE - в регионе нет места). */
    private static final Map<Long, int[]> CACHE = new HashMap<Long, int[]>();

    private LairSites() {}

    public static int spacing(int type) {
        if (type == CARCINUS) return Config.carcinusSpacing;
        if (type == SNOW) return Config.snowDungeonSpacing;
        return 32 * 16; // пирамиды: регион 32 x 32 чанка
    }

    /** Место логова типа type в регионе (rx, rz) или null. */
    public static int[] site(World w, int type, int rx, int rz) {
        int size = spacing(type);
        if (size <= 0) return null;
        long key = (w.getSeed() * 31 + type) * 0x9E3779B97F4A7C15L ^ ((long) rx << 32 | (rz & 0xFFFFFFFFL));
        synchronized (CACHE) {
            int[] c = CACHE.get(key);
            if (c != null) return c == NONE ? null : c;
        }
        int[] s = type == TOMB ? pyramid(w, rx, rz) : scan(w, type, rx, rz, size);
        synchronized (CACHE) {
            if (CACHE.size() > 20000) CACHE.clear();
            CACHE.put(key, s == null ? NONE : s);
        }
        return s;
    }

    /** Место в регионе этого чанка, если оно в этом чанке. */
    public static int[] siteInChunk(World w, int type, int chunkX, int chunkZ) {
        int size = spacing(type);
        if (size <= 0) return null;
        int bx = chunkX * 16 + 8, bz = chunkZ * 16 + 8;
        int[] s = site(w, type, Math.floorDiv(bx, size), Math.floorDiv(bz, size));
        return s != null && s[0] >> 4 == chunkX && s[1] >> 4 == chunkZ ? s : null;
    }

    /** Обход точек региона в порядке из сида; первая подходящая - место. */
    private static int[] scan(World w, int type, int rx, int rz, int size) {
        WorldChunkManager wcm = w.getWorldChunkManager();
        int step = 32, n = Math.max(1, size / step), total = n * n;
        Random r = new Random(w.getSeed() + rx * 341873128712L + rz * 132897987541L + (type == CARCINUS ? 52801 : 77213));
        int start = r.nextInt(total), stride = 7919 % total == 0 ? 1 : 7919;
        while (gcd(stride, total) != 1) stride++;
        for (int k = 0; k < total; k++) {
            int idx = (int) ((start + (long) k * stride) % total);
            int x = ((rx * size + (idx % n) * step) >> 4) * 16 + 8, z = ((rz * size + (idx / n) * step) >> 4) * 16 + 8;
            if (type == CARCINUS ? shore(wcm, x, z) : snowField(wcm, x, z)) return new int[] { x, z };
        }
        return null;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /** Песчаный пляж, а в 32 блоках от него - океан: значит, у самой воды. */
    static boolean shore(WorldChunkManager wcm, int x, int z) {
        if (!Biomes.sandBeach(wcm.getBiomeGenAt(x, z))) return false;
        int[][] d = { { 24, 0 }, { -24, 0 }, { 0, 24 }, { 0, -24 }, { 16, 16 }, { -16, -16 }, { 16, -16 }, { -16, 16 } };
        for (int[] o : d) if (Biomes.ocean(wcm.getBiomeGenAt(x + o[0], z + o[1]))) return true;
        return false;
    }

    /** Заснеженная ровная суша на всей площади пещеры (к востоку до ~170 блоков, в стороны ~60). */
    static boolean snowField(WorldChunkManager wcm, int x, int z) {
        if (!Biomes.snowyLand(wcm.getBiomeGenAt(x, z))) return false;
        for (int dx = -16; dx <= 176; dx += 32) for (int dz = -64; dz <= 64; dz += 32) {
            BiomeGenBase b = wcm.getBiomeGenAt(x + dx, z + dz);
            if (b == null || Biomes.ocean(b) || Biomes.beach(b) || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.RIVER)
                || BiomeDictionary.isBiomeOfType(b, BiomeDictionary.Type.SWAMP) || b.rootHeight < 0.0F || b.heightVariation > 0.45F) return false;
        }
        return true;
    }

    /**
     * Пустынная пирамида в регионе 32 x 32 чанка - как её ставит MapGenScatteredFeature (тот же сид и сдвиг 0..23 чанка),
     * если в этом месте пустыня или пустынные холмы. @return центр чанка пирамиды
     */
    static int[] pyramid(World w, int rx, int rz) {
        Random r = new Random(rx * 341873128712L + rz * 132897987541L + w.getWorldInfo().getSeed() + 14357617L);
        int cx = rx * 32 + r.nextInt(24), cz = rz * 32 + r.nextInt(24);
        BiomeGenBase b = w.getWorldChunkManager().getBiomeGenAt(cx * 16 + 8, cz * 16 + 8);
        return b == BiomeGenBase.desert || b == BiomeGenBase.desertHills ? new int[] { cx * 16 + 8, cz * 16 + 8 } : null;
    }

    /**
     * Ближайшее место дальше skip и не дальше radius блоков: кольца регионов от игрока, пока следующее кольцо не дальше
     * уже найденного.
     */
    public static int[] nearest(World w, int type, double px, double pz, int radius, int skip) {
        return nearest(w, type, px, pz, radius, skip, null);
    }

    /** То же, но только места, прошедшие проверку ok (например, ещё не построенные). */
    public static int[] nearest(World w, int type, double px, double pz, int radius, int skip, java.util.function.Predicate<int[]> ok) {
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
                int[] s = site(w, type, rx0 + i, rz0 + j);
                if (s == null) continue;
                double d = (s[0] - px) * (s[0] - px) + (s[1] - pz) * (s[1] - pz);
                if (d <= (double) skip * skip || d > (double) radius * radius || d >= bestD || ok != null && !ok.test(s)) continue;
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** Места в радиусе (ближние регионы первыми); дальше skip блоков от игрока. */
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
