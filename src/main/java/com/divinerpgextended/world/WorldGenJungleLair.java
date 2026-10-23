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
 * Джунгли: руда джунглей (только в джунглях, глубоко) и логово Древнего драмкрикса под ванильным храмом джунглей.
 * Храм находим в генераторе ванильных построек (рефлексией по типам полей - имена разные в разработке и в игре); логово
 * строим, когда все чанки храма уже заселены (иначе храм при достройке засыпал бы спуск). Из храма вниз ведёт обложенный
 * мхом колодец со ступенями, под ним - два яруса заросших ходов (как ледяная пещера Глакона, только джунгли: мшистый
 * камень, лианы, папоротник, кусты, спавнеры пауков и драмкриксов, сундуки), в конце - купольный зал с колоннами из
 * стволов, помостом и алтарём ({@link #ALTAR_DZ} к северу от центра зала; босс встаёт в центре).
 */
public class WorldGenJungleLair implements IWorldGenerator {

    public static final int ORE_VEINS = 3, ORE_SIZE = 5, ORE_MIN_Y = 8, ORE_MAX_Y = 36;
    public static final int COLS = 5, ROWS = 4, STEP = 11, BOSS_R = 12, ALTAR_DZ = 6;
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
            int[] a = build(world, new Random(world.getSeed() ^ t.minX * 341873128712L ^ t.minZ * 132897987541L), t);
            if (a != null) DivineRPGExtended.LOG.info("Логово джунглей под храмом " + t.minX + " " + t.minZ + ": алтарь " + a[0] + " " + a[1] + " " + a[2]);
        }
    }

    /** Джунгли - и ванильные, и других модов (Biomes O' Plenty). */
    public static boolean jungle(BiomeGenBase b) {
        return Biomes.jungle(b);
    }

    // ---------------------------------------------------------------- храмы

    private static Field scattered, structures;

    /** Рамки храмов джунглей, которые ванильный генератор уже наметил. */
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
            // другой генератор мира (моды биомов) - храмов не знаем
        }
        return out;
    }

    /** Все чанки храма уже заселены (текущий - заселяется сейчас). */
    static boolean templeReady(World w, StructureBoundingBox t, int cx, int cz) {
        for (int x = t.minX >> 4; x <= t.maxX >> 4; x++)
            for (int z = t.minZ >> 4; z <= t.maxZ >> 4; z++) {
                if (x == cx && z == cz) continue;
                if (!w.getChunkProvider().chunkExists(x, z) || !w.getChunkFromChunkCoords(x, z).isTerrainPopulated) return false;
            }
        return true;
    }

    /** Центр зала и алтарь по рамке храма: {центр x, пол зала y, центр z}. */
    public static int[] hall(StructureBoundingBox t) {
        int g = t.minY;
        int cx = (t.minX + t.maxX) / 2, cz = (t.minZ + t.maxZ) / 2;
        int yLast = g - 31 >= 14 ? g - 31 : g - 18; // как ярусы в build
        return new int[] { cx + 16 + (COLS - 1) * STEP + BOSS_R + 6, yLast - 1, cz };
    }

    static boolean isBuilt(World w, StructureBoundingBox t) {
        int[] h = hall(t);
        return w.getBlock(h[0], h[1] + 2, h[2] - ALTAR_DZ) == ModBlocks.jungleAltar;
    }

    // ---------------------------------------------------------------- логово

    private static final Block[] SHELL = { Blocks.mossy_cobblestone, Blocks.mossy_cobblestone, Blocks.cobblestone, Blocks.stonebrick, Blocks.stonebrick };

    static void shell(World w, int x, int y, int z, Random r) {
        int k = r.nextInt(SHELL.length);
        int meta = SHELL[k] == Blocks.stonebrick ? (r.nextBoolean() ? 1 : 2) : 0; // мшистый или треснувший кирпич
        w.setBlock(x, y, z, SHELL[k], meta, 2);
    }

    /** Построить логово под храмом. @return координаты алтаря или null (место не подошло) */
    public static int[] build(World world, Random rand, StructureBoundingBox t) {
        int g = t.minY; // низ храма
        int cx = (t.minX + t.maxX) / 2, cz = (t.minZ + t.maxZ) / 2;
        List<Integer> levels = new ArrayList<Integer>();
        for (int ly = g - 18; ly >= 14 && levels.size() < 2; ly -= 13) levels.add(ly);
        if (levels.isEmpty()) return null;
        int L = levels.size(), y0 = levels.get(0), yLast = levels.get(L - 1);
        int mid = ROWS / 2;
        int nx0 = cx + 16, nz0 = cz - mid * STEP;
        int[] h = hall(t);
        int bossX = h[0], bossZ = h[2], yb = h[1];
        int zMax = nz0 + (ROWS - 1) * STEP;

        int bx = cx - 6, bz = nz0 - 16;
        WorldGenLairs.Carver c = new WorldGenLairs.Carver(bx, Math.min(yb, yLast) - 8, bz, bossX + BOSS_R + 4 - bx, g + 4 - (Math.min(yb, yLast) - 8),
            Math.max(zMax, bossZ + BOSS_R) + 16 - bz, Integer.MAX_VALUE);

        double[][][][] lnode = new double[L][COLS][ROWS][];
        for (int l = 0; l < L; l++) {
            int ly = levels.get(l);
            for (int i = 0; i < COLS; i++)
                for (int j = 0; j < ROWS; j++)
                    lnode[l][i][j] = new double[] { nx0 + i * STEP + rand.nextInt(5) - 2 + 0.5, ly + 2 + rand.nextInt(3) - 1 + 0.5, nz0 + j * STEP + rand.nextInt(5) - 2 + 0.5 };
        }
        double[] start = lnode[0][0][mid] = new double[] { nx0 + 0.5, y0 + 2.5, cz + 0.5 };
        double[] last = lnode[L - 1][COLS - 1][mid] = new double[] { nx0 + (COLS - 1) * STEP + 0.5, yLast + 2.5, cz + 0.5 };

        // колодец из храма: шахта 3x3 вниз, внизу косой ход к первому ярусу
        int shaftBottom = y0 + 2;
        for (int y = shaftBottom; y <= g + 1; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.add(cx + dx, y, cz + dz);
        c.line(new double[] { cx + 0.5, shaftBottom + 1.5, cz + 0.5 }, start, rand, false, 1.8, 2.2, 1.8, 2.2);

        List<double[]> halls = new ArrayList<double[]>(), pockets = new ArrayList<double[]>();
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int l = 0; l < L; l++) {
            double[][][] ln = lnode[l];
            List<int[]> edges = WorldGenLairs.mazeEdges(rand, COLS, ROWS, 0, mid);
            for (int n = 0; n < 4; n++) {
                int i = rand.nextInt(COLS), j = rand.nextInt(ROWS);
                int[] d = dirs[rand.nextInt(4)];
                if (i + d[0] >= 0 && i + d[0] < COLS && j + d[1] >= 0 && j + d[1] < ROWS) edges.add(new int[] { i, j, i + d[0], j + d[1] });
            }
            for (int[] e : edges) c.line(ln[e[0]][e[1]], ln[e[2]][e[3]], rand, true, 2.0, 2.8, 2.0, 2.6);
            for (int n = 0; n < 8 && halls.size() < 2 * (l + 1); n++) {
                double[] p = ln[1 + rand.nextInt(COLS - 2)][rand.nextInt(ROWS)];
                if (halls.contains(p)) continue;
                c.blob(p[0], p[1] + 0.8, p[2], 5.0 + rand.nextDouble() * 1.5, 3.4);
                halls.add(p);
            }
            for (int n = 0; n < 5; n++) {
                double[] p = ln[rand.nextInt(COLS - 1)][rand.nextInt(ROWS)];
                double a = rand.nextDouble() * Math.PI * 2, len = 5 + rand.nextInt(4);
                double[] q = { p[0] + Math.cos(a) * len, p[1], p[2] + Math.sin(a) * len };
                c.line(p, q, rand, true, 1.6, 2.2, 1.8, 2.2);
                c.blob(q[0], q[1] + 0.5, q[2], 2.6, 2.0);
                pockets.add(q);
            }
            if (l + 1 < L) for (int n = 0; n < 2; n++) {
                int i = rand.nextInt(COLS - 2), j = rand.nextInt(ROWS);
                c.line(ln[i][j], lnode[l + 1][i + 2][j], rand, true, 2.0, 2.6, 2.0, 2.4);
            }
        }

        // купольный зал и ход к нему
        for (int dx = -BOSS_R; dx <= BOSS_R; dx++)
            for (int dz = -BOSS_R; dz <= BOSS_R; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz) / BOSS_R;
                if (r >= 1.0) continue;
                int hh = (int) (2 + 9 * Math.sqrt(1 - r * r));
                for (int y = yb + 1; y <= yb + hh; y++) c.add(bossX + dx, y, bossZ + dz);
            }
        c.line(last, new double[] { bossX - BOSS_R + 1.5, yb + 2.5, bossZ + 0.5 }, rand, false, 2.0, 2.6, 2.0, 2.4);

        // вырезать (храм над колодцем не трогаем выше его пола) и обложить мшистым камнем
        for (int[] p : c.cells) WorldGenLairs.set(world, p[0], p[1], p[2], Blocks.air);
        for (int[] p : c.cells)
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int x = p[0] + dx, y = p[1] + dy, z = p[2] + dz;
                if (y >= g || c.has(x, y, z)) continue;
                Block b = world.getBlock(x, y, z);
                if (b == Blocks.bedrock || b == Blocks.mossy_cobblestone || b == Blocks.stonebrick || b == Blocks.cobblestone) continue;
                shell(world, x, y, z, rand);
            }

        // ступени по стенке колодца: спираль из мшистого кирпича
        for (int y = shaftBottom; y <= g; y++) {
            int k = (y - shaftBottom) % 8;
            int[][] ring = { { -1, -1 }, { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 } };
            world.setBlock(cx + ring[k][0], y, cz + ring[k][1], Blocks.stonebrick, 1, 2);
        }

        // пол: трава с папоротником и кустами; стены - лианы
        for (int[] p : c.cells) {
            int x = p[0], y = p[1], z = p[2];
            if (y >= g - 1) continue;
            if (!c.has(x, y - 1, z)) {
                int roll = rand.nextInt(100);
                if (roll < 35) world.setBlock(x, y - 1, z, Blocks.grass, 0, 2);
                if (roll < 14) world.setBlock(x, y, z, Blocks.tallgrass, 2, 2);
                else if (roll < 17) world.setBlock(x, y, z, Blocks.leaves, 3 | 4, 2);
            }
            if (rand.nextInt(7) == 0 && world.isAirBlock(x, y, z)) {
                int meta = !c.has(x, y, z + 1) ? 1 : !c.has(x - 1, y, z) ? 2 : !c.has(x, y, z - 1) ? 4 : !c.has(x + 1, y, z) ? 8 : 0;
                if (meta != 0) world.setBlock(x, y, z, Blocks.vine, meta, 2);
            }
        }

        // светокамень в своде ходов
        List<int[]> lamps = new ArrayList<int[]>();
        for (int[] p : c.path) {
            if (rand.nextInt(7) != 0 || WorldGenLairs.near(lamps, p, 10)) continue;
            int y = p[1];
            while (c.has(p[0], y + 1, p[2]) && y - p[1] < 5) y++;
            if (c.has(p[0], y + 1, p[2]) || y + 1 >= g) continue;
            world.setBlock(p[0], y + 1, p[2], Blocks.glowstone, 0, 2);
            lamps.add(p);
        }

        // сундуки в концах карманов
        for (double[] q : pockets) {
            if (rand.nextInt(3) == 0) continue;
            int x = MathHelper.floor_double(q[0]), z = MathHelper.floor_double(q[2]), y = MathHelper.floor_double(q[1]);
            if (!c.has(x, y, z)) continue;
            while (c.has(x, y - 1, z)) y--;
            LairLoot.chest(world, rand, x, y, z, false);
        }

        // спавнеры пауков и драмкриксов джунглей в полу
        String spider = (String) EntityList.classToStringMapping.get(net.divinerpg.entities.vanilla.EntityJungleSpider.class);
        String dram = (String) EntityList.classToStringMapping.get(net.divinerpg.entities.vanilla.EntityJungleDramcryx.class);
        List<int[]> path = new ArrayList<int[]>(c.path);
        Collections.shuffle(path, rand);
        List<int[]> spawners = new ArrayList<int[]>();
        for (int[] p : path) {
            if (spawners.size() >= 4 * L || spider == null || dram == null) break;
            if (p[0] < nx0 + 6 || p[0] > bossX - BOSS_R - 3 || WorldGenLairs.near(spawners, p, 12)) continue;
            int y = p[1];
            while (c.has(p[0], y - 1, p[2]) && p[1] - y < 5) y--;
            if (c.has(p[0], y - 1, p[2])) continue;
            WorldGenLairs.spawner(world, p[0], y - 1, p[2], spawners.size() % 2 == 1 ? dram : spider);
            spawners.add(p);
        }

        // зал: колонны-стволы с кронами под сводом, помост, алтарь, светильники
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + 0.2;
            int px = bossX + (int) Math.round(Math.cos(a) * (BOSS_R - 3.5)), pz = bossZ + (int) Math.round(Math.sin(a) * (BOSS_R - 3.5));
            int top = yb + 1;
            while (c.has(px, top + 1, pz) && top < yb + 12) top++;
            for (int y = yb + 1; y <= top; y++) world.setBlock(px, y, pz, Blocks.log, 3, 2);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0) && c.has(px + dx, top, pz + dz)) world.setBlock(px + dx, top, pz + dz, Blocks.leaves, 3 | 4, 2);
        }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
            if (!corner) world.setBlock(bossX + dx, yb, bossZ + dz, Blocks.stonebrick, (dx + dz) % 3 == 0 ? 3 : 1, 2);
        }
        for (int dx = -1; dx <= 1; dx++) world.setBlock(bossX + dx, yb + 1, bossZ - ALTAR_DZ, Blocks.stonebrick, 3, 2);
        world.setBlock(bossX, yb + 2, bossZ - ALTAR_DZ, ModBlocks.jungleAltar, 0, 2);
        for (int[] l : new int[][] { { -5, -5 }, { -5, 5 }, { 5, -5 }, { 5, 5 } }) {
            int y = yb + 1;
            while (c.has(bossX + l[0], y + 1, bossZ + l[1])) y++;
            world.setBlock(bossX + l[0], y + 1, bossZ + l[1], Blocks.glowstone, 0, 2);
        }
        built++;
        return new int[] { bossX, yb + 2, bossZ - ALTAR_DZ };
    }
}
