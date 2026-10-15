package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.divinerpg.entities.vanilla.EntityFrost;
import net.divinerpg.entities.vanilla.EntityGlacon;
import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Генерация логов ранних боссов в обычном мире:
 * <ul>
 * <li>логово Карцинуса у песчаного берега (материка или острова): воронка у кромки воды, длинный косой лаз в
 * затопленный лабиринт нор под берегом и дном, за завесой пузырей - сухой грот с алтарём Карцинуса;</li>
 * <li>ледяная пещера под заснеженным биомом: вход через иглу, петляющие ходы с Глаконами и Фростами, зал с
 * алтарём Снежного Глакона.</li>
 * </ul>
 */
public class WorldGenLairs implements IWorldGenerator {

    /** Лабиринт нор: сетка узлов N x N с шагом STEP (около 42 блоков в поперечнике). */
    private static final int N = 11, STEP = 7;

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != 0) return;
        int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        if (biome == null) return;
        boolean ocean = biome == BiomeGenBase.ocean || biome == BiomeGenBase.deepOcean
            || biome == BiomeGenBase.frozenOcean;
        boolean beach = biome == BiomeGenBase.beach || biome == BiomeGenBase.coldBeach;
        if (beach && Config.carcinusShoreRarity > 0 && rand.nextInt(Config.carcinusShoreRarity) == 0) {
            if (crabLair(world, rand, x, z)) DivineRPGExtended.LOG.info("Логово Карцинуса: " + x + " " + z);
        } else if (!ocean && biome.getEnableSnow() && Config.snowDungeonRarity > 0
            && rand.nextInt(Config.snowDungeonRarity) == 0) {
                if (snowDungeon(world, rand, x, z)) DivineRPGExtended.LOG.info("Снежное подземелье: " + x + " " + z);
            }
    }

    // ---------------------------------------------------------------- логово Карцинуса

    private static boolean isWater(World w, int x, int y, int z) {
        return w.getBlock(x, y, z)
            .getMaterial() == Material.water;
    }

    /** Верхний твёрдый блок дна под водой. */
    private static int seabed(World w, int x, int z) {
        int y = 62;
        while (y > 5 && isWater(w, x, y, z)) y--;
        return y;
    }

    /** Соседи на расстоянии 3: где-то рядом с точкой входа должна быть суша. */
    private static final int[][] SHORE = { { 3, 0 }, { -3, 0 }, { 0, 3 }, { 0, -3 }, { 2, 2 }, { -2, -2 }, { 2, -2 },
        { -2, 2 } };

    /**
     * Крабья нора у берега: у самой кромки воды неровная воронка, от неё наискось вниз уходит длинный лаз в
     * затопленный лабиринт нор на высоте 30-40 под берегом и морским дном.
     * Ходы кривые, разной ширины, с петлями, тупиковыми карманами и расширениями - будто их рыл сам краб. Стены,
     * пол и потолок - блоки норы: если их сломать, проход засыпает песком. Под полом спрятаны гнёзда норных крабов, на
     * потолке светятся лампы кракена и аква-лампы. На востоке за завесой пузырей - сухой грот с алтарём призыва Карцинуса.
     */
    private boolean crabLair(World world, Random rand, int cx, int cz) {
        // вход: вода на уровне моря у самой суши (кромка пляжа)
        int ex = 0, ez = 0;
        boolean found = false;
        for (int t = 0; t < 64 && !found; t++) {
            int x = cx + rand.nextInt(32) - 16, z = cz + rand.nextInt(32) - 16;
            if (!isWater(world, x, 62, z) || seabed(world, x, z) < 55) continue;
            for (int[] d : SHORE) {
                if (!isWater(world, x + d[0], 62, z + d[1]) && world.getBlock(x + d[0], 62, z + d[1])
                    .getMaterial()
                    .isSolid()) {
                    found = true;
                    ex = x;
                    ez = z;
                    break;
                }
            }
        }
        if (!found) return false;

        int span = (N - 1) * STEP;
        int ym = 34; // средняя высота ходов: пол около 31
        int x0 = ex + 20, z0 = ez - (N / 2) * STEP; // узел (0, 0); вход в 20 блоках западнее первого узла
        int gx0 = x0 + span + 5, gcx = gx0 + 6, gcz = z0 + (N / 2) * STEP; // стена грота и его центр
        int ax0 = ex - 10, ax1 = gcx + 8, az0 = z0 - 8, az1 = z0 + span + 8;

        // над ходами нужна толща суши или морского дна
        for (int x = ax0 + 6; x <= ax1; x += 4) {
            for (int z = az0; z <= az1 + 3; z += 4) {
                if (seabed(world, x, z) < ym + 10) return false;
            }
        }

        Carver c = new Carver(ax0, ym - 8, az0, ax1 - ax0 + 1, 72 - (ym - 8), az1 - az0 + 1, gx0 - 1);

        // узлы со сдвигом, чтобы ходы не шли по линейке
        double[][][] node = new double[N][N][];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                node[i][j] = new double[] { x0 + i * STEP + rand.nextInt(3) - 1 + 0.5, ym + rand.nextInt(5) - 2 + 0.5,
                    z0 + j * STEP + rand.nextInt(3) - 1 + 0.5 };
            }
        }
        double[] exit = node[N - 1][N / 2] = new double[] { x0 + span + 0.5, ym + 0.5, gcz + 0.5 };
        double[] start = node[0][N / 2];

        // остов лабиринта "рекурсивным возвратом" + несколько петель, чтобы было где запутаться
        List<int[]> edges = mazeEdges(rand, N, N, 0, N / 2);
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int n = 0; n < 6; n++) {
            int i = rand.nextInt(N), j = rand.nextInt(N);
            int[] d = dirs[rand.nextInt(4)];
            if (i + d[0] >= 0 && i + d[0] < N && j + d[1] >= 0 && j + d[1] < N) {
                edges.add(new int[] { i, j, i + d[0], j + d[1] });
            }
        }
        for (int[] e : edges) c.line(node[e[0]][e[1]], node[e[2]][e[3]], rand, true);

        // расширения-ямы и тупиковые карманы (в части карманов - сундуки)
        for (int n = 0; n < 7; n++) {
            double[] p = node[1 + rand.nextInt(N - 2)][rand.nextInt(N)];
            c.blob(p[0], p[1] + 0.5, p[2], 2.8 + rand.nextDouble(), 1.9);
        }
        List<double[]> pockets = new ArrayList<double[]>();
        for (int n = 0; n < 14; n++) {
            double[] p = node[rand.nextInt(N - 1)][rand.nextInt(N)];
            double a = rand.nextDouble() * Math.PI * 2, len = 4 + rand.nextInt(4);
            double[] q = { p[0] + Math.cos(a) * len, p[1] + rand.nextInt(3) - 1, p[2] + Math.sin(a) * len };
            c.line(p, q, rand, true);
            c.blob(q[0], q[1], q[2], 2.0, 1.4);
            pockets.add(q);
        }

        // ход к гроту: заканчивается у восточной стены, дальше завеса пузырей
        c.line(exit, new double[] { gx0 - 0.5, ym + 0.5, gcz + 0.5 }, rand, false);
        for (int y = ym - 1; y <= ym + 1; y++) c.add(gx0 - 1, y, gcz);

        // косой лаз от воронки на дне к первому узлу
        int ey = seabed(world, ex, ez);
        c.line(new double[] { ex + 0.5, ey + 1.5, ez + 0.5 }, start, rand, false);

        // заливаем ходы водой и обкладываем блоками норы (над дном - открытая вода, её не трогаем)
        for (int[] p : c.cells) set(world, p[0], p[1], p[2], Blocks.water);
        for (int[] p : c.cells) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int x = p[0] + dx, y = p[1] + dy, z = p[2] + dz;
                if (c.has(x, y, z)) continue;
                Block b = world.getBlock(x, y, z);
                if (b.getMaterial() == Material.water || b == Blocks.bedrock || b == ModBlocks.burrow) continue;
                boolean sandy = c.has(x, y + 1, z) || c.has(x, y - 1, z);
                if (rand.nextInt(6) == 0) sandy = !sandy;
                burrow(world, x, y, z, sandy ? 1 : 0);
            }
        }

        // воронка вокруг лаза и неровный вал из песка и песчаника
        double r0 = 3.2 + rand.nextDouble() * 1.5;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int x = ex + dx, z = ez + dz, top = seabed(world, x, z);
                if (!isWater(world, x, 62, z)) continue; // только под водой, не на пляже
                double dist = Math.sqrt(dx * dx + dz * dz), r = r0 + (rand.nextDouble() - 0.5) * 1.6;
                if (dist < r) {
                    int depth = dist < r * 0.5 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        if (world.getBlock(x, top - d, z) != Blocks.bedrock) set(world, x, top - d, z, Blocks.water);
                    }
                } else if (dist < r + 2.5 && rand.nextInt(100) < 60) {
                    int h = rand.nextInt(3) == 0 ? 2 : 1;
                    for (int d = 1; d <= h; d++) {
                        set(world, x, top + d, z, rand.nextBoolean() ? Blocks.sand : Blocks.sandstone);
                    }
                }
            }
        }

        // лампы кракена и аква-лампы в потолке
        List<int[]> lamps = new ArrayList<int[]>();
        for (int[] p : c.path) {
            if (rand.nextInt(6) != 0 || near(lamps, p, 7)) continue;
            int y = p[1];
            while (c.has(p[0], y + 1, p[2]) && y - p[1] < 4) y++;
            if (world.getBlock(p[0], y + 1, p[2]) != ModBlocks.burrow) continue;
            lamp(world, rand, p[0], y + 1, p[2]);
            lamps.add(p);
        }

        // гнёзда акул под полом по всему лабиринту, но не у самого входа
        List<int[]> path = new ArrayList<int[]>(c.path);
        Collections.shuffle(path, rand);
        List<int[]> nests = new ArrayList<int[]>();
        for (int[] p : path) {
            if (nests.size() >= 10) break;
            double sdx = p[0] - start[0], sdz = p[2] - start[2];
            if (sdx * sdx + sdz * sdz < 100 || p[0] > gx0 - 5 || near(nests, p, 9)) continue;
            int y = p[1];
            while (c.has(p[0], y - 1, p[2]) && p[1] - y < 4) y--;
            if (world.getBlock(p[0], y - 1, p[2]) != ModBlocks.burrow) continue;
            world.setBlock(p[0], y - 1, p[2], ModBlocks.sharkNest, 0, 2);
            nests.add(p);
        }

        // сундуки в концах тупиковых карманов, на полу
        for (int n = 0; n < 7 && n < pockets.size(); n++) {
            double[] q = pockets.get(n);
            int x = MathHelper.floor_double(q[0]), z = MathHelper.floor_double(q[2]), y = MathHelper.floor_double(q[1]);
            if (!c.has(x, y, z)) continue;
            while (c.has(x, y - 1, z)) y--;
            LairLoot.chest(world, rand, x, y, z, false);
        }

        // сухой грот краба за восточной стеной: купол радиусом 6, пол вровень с ходами
        int yd = ym - 2;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                for (int y = yd; y <= yd + 7; y++) {
                    if (inDen(dx, y - yd, dz)) continue;
                    boolean nearDen = false;
                    for (int[] n : NEIGHBORS) {
                        if (inDen(dx + n[0], y - yd + n[1], dz + n[2])) nearDen = true;
                    }
                    if (nearDen) burrow(world, gcx + dx, y, gcz + dz, y == yd ? 1 : 0);
                }
                for (int y = yd + 1; y <= yd + 6; y++) {
                    if (inDen(dx, y - yd, dz)) set(world, gcx + dx, y, gcz + dz, Blocks.air);
                }
            }
        }
        for (int[] g : new int[][] { { -2, -2 }, { -2, 2 }, { 2, -2 }, { 2, 2 } }) {
            lamp(world, rand, gcx + g[0], yd + 7, gcz + g[1]);
        }
        // завеса пузырей держит воду: вода лабиринта стоит прямо перед ней, а пройти можно насквозь
        set(world, gx0, yd + 1, gcz, ModBlocks.bubbleCurtain);
        set(world, gx0, yd + 2, gcz, ModBlocks.bubbleCurtain);
        set(world, gcx, yd + 1, gcz, ModBlocks.carcinusAltar); // призыв: крабовые клешни, их роняют норные крабы

        LairData.get(world)
            .add(LairData.CARCINUS, ex, ey + 1, ez); // воронка на дне
        return true;
    }

    private static boolean near(List<int[]> points, int[] p, int dist) {
        for (int[] q : points) {
            int dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
            if (dx * dx + dy * dy + dz * dz < dist * dist) return true;
        }
        return false;
    }

    /** Лампа кракена или аква-лампа Divine. */
    private static void lamp(World world, Random rand, int x, int y, int z) {
        if (rand.nextBoolean()) world.setBlock(x, y, z, VanillaBlocks.lamp2, 2, 2);
        else world.setBlock(x, y, z, VanillaBlocks.lamp1, 0, 2);
    }

    private static final int[][] NEIGHBORS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    /** Пустота норы краба: круглый зал радиусом 6, высота 4 и купол сверху (dy - от пола). */
    private static boolean inDen(int dx, int dy, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz) / 6.0;
        if (r >= 1.0 || dy < 1) return false;
        if (dy <= 4) return true;
        if (dy == 5) return r < 0.8;
        if (dy == 6) return r < 0.5;
        return false;
    }

    private static void burrow(World world, int x, int y, int z, int meta) {
        world.setBlock(x, y, z, ModBlocks.burrow, meta, 2);
    }

    /** Остов лабиринта "рекурсивным возвратом" по сетке узлов cols x rows от (si, sj): рёбра {i1, j1, i2, j2}. */
    private static List<int[]> mazeEdges(Random rand, int cols, int rows, int si, int sj) {
        List<int[]> edges = new ArrayList<int[]>();
        boolean[][] seen = new boolean[cols][rows];
        List<int[]> stack = new ArrayList<int[]>();
        stack.add(new int[] { si, sj });
        seen[si][sj] = true;
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!stack.isEmpty()) {
            int[] c = stack.get(stack.size() - 1);
            List<int[]> next = new ArrayList<int[]>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < cols && nz < rows && !seen[nx][nz]) next.add(new int[] { nx, nz });
            }
            if (next.isEmpty()) {
                stack.remove(stack.size() - 1);
                continue;
            }
            int[] n = next.get(rand.nextInt(next.size()));
            seen[n[0]][n[1]] = true;
            edges.add(new int[] { c[0], c[1], n[0], n[1] });
            stack.add(n);
        }
        return edges;
    }

    /** Вырезает кривые ходы в объёме: набор "выкопанных" блоков и точки по оси ходов. */
    static final class Carver {

        final int bx, by, bz, sx, sy, sz, maxX;
        final boolean[] grid;
        final List<int[]> cells = new ArrayList<int[]>();
        final List<int[]> path = new ArrayList<int[]>();

        Carver(int bx, int by, int bz, int sx, int sy, int sz, int maxX) {
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.maxX = maxX;
            grid = new boolean[sx * sy * sz];
        }

        private int idx(int x, int y, int z) {
            x -= bx;
            y -= by;
            z -= bz;
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return -1;
            return (x * sy + y) * sz + z;
        }

        boolean has(int x, int y, int z) {
            int i = idx(x, y, z);
            return i >= 0 && grid[i];
        }

        void add(int x, int y, int z) {
            if (x > maxX || y < 6) return;
            int i = idx(x, y, z);
            if (i < 0 || grid[i]) return;
            grid[i] = true;
            cells.add(new int[] { x, y, z });
        }

        /** Эллипсоид с горизонтальным радиусом rh и вертикальным rv. */
        void blob(double px, double py, double pz, double rh, double rv) {
            for (int x = (int) Math.floor(px - rh); x <= (int) Math.ceil(px + rh); x++) {
                for (int y = (int) Math.floor(py - rv); y <= (int) Math.ceil(py + rv); y++) {
                    for (int z = (int) Math.floor(pz - rh); z <= (int) Math.ceil(pz + rh); z++) {
                        double dx = (x + 0.5 - px) / rh, dy = (y + 0.5 - py) / rv, dz = (z + 0.5 - pz) / rh;
                        if (dx * dx + dy * dy + dz * dz <= 1.0) add(x, y, z);
                    }
                }
            }
        }

        /** Кривой ход от a к b: изгиб через сдвинутую середину, ширина и высота гуляют по дороге. */
        void line(double[] a, double[] b, Random rand, boolean record) {
            line(a, b, rand, record, 1.3, 2.3, 1.1, 1.7);
        }

        /** То же с заданными пределами горизонтального (rh) и вертикального (rv) радиуса. */
        void line(double[] a, double[] b, Random rand, boolean record, double rhMin, double rhMax, double rvMin,
            double rvMax) {
            double mx = (a[0] + b[0]) / 2 + (rand.nextDouble() - 0.5) * 5;
            double my = (a[1] + b[1]) / 2 + (rand.nextDouble() - 0.5) * 2;
            double mz = (a[2] + b[2]) / 2 + (rand.nextDouble() - 0.5) * 5;
            double len = Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1])
                + (a[2] - b[2]) * (a[2] - b[2]));
            int steps = (int) (len * 2) + 2;
            double rh = rhMin + (rhMax - rhMin) * (0.2 + rand.nextDouble() * 0.5);
            double rv = rvMin + (rvMax - rvMin) * (0.2 + rand.nextDouble() * 0.5);
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps, u = 1 - t;
                double px = u * u * a[0] + 2 * u * t * mx + t * t * b[0];
                double py = u * u * a[1] + 2 * u * t * my + t * t * b[1];
                double pz = u * u * a[2] + 2 * u * t * mz + t * t * b[2];
                rh = Math.max(rhMin, Math.min(rhMax, rh + (rand.nextDouble() - 0.5) * (rhMax - rhMin) * 0.35));
                rv = Math.max(rvMin, Math.min(rvMax, rv + (rand.nextDouble() - 0.5) * (rvMax - rvMin) * 0.4));
                blob(px, py, pz, rh, rv);
                if (record && s % 2 == 0) path.add(new int[] { (int) Math.floor(px), (int) Math.floor(py),
                    (int) Math.floor(pz) });
            }
        }
    }

    // ---------------------------------------------------------------- ледяная пещера

    /** Пещера: сетка узлов COLS x ROWS с шагом STEP, за последним столбцом - зал босса. */
    private static final int CAVE_COLS = 6, CAVE_ROWS = 5, CAVE_STEP = 11, BOSS_R = 10, LEVEL_GAP = 13;

    /**
     * Ледяная пещера Снежного Глакона. На поверхности иглу, из него петляет косой спуск в пещеру из пакового льда и
     * мёрзлого камня: широкие кривые ходы, залы с колоннами-"песочными часами", сосульки, снег на полу, редкие
     * ледяные лампы. Спавнеры Глаконов и Фростов вмёрзли в пол под снегом. В конце - купольный зал с алтарём
     * Глакона на помосте у светящейся рунной стены.
     */
    private boolean snowDungeon(World world, Random rand, int cx, int cz) {
        int surface = world.getTopSolidOrLiquidBlock(cx, cz);
        if (surface < 62 || world.getBlock(cx, surface - 1, cz)
            .getMaterial()
            .isLiquid()) return false;
        int g = surface - 1; // земля, на которой стоит иглу
        // этажи пещеры: первый на 20 блоков ниже поверхности, дальше каждые 13 блоков, пока не ниже ~30 (до 3 этажей)
        List<Integer> levels = new ArrayList<Integer>();
        for (int ly = g - 20; ly >= 32 && levels.size() < 3; ly -= LEVEL_GAP) levels.add(ly);
        if (levels.isEmpty()) return false;
        int y0 = levels.get(0), yLast = levels.get(levels.size() - 1);
        int mid = CAVE_ROWS / 2;
        int x0 = cx + 28, z0 = cz + 10 - mid * CAVE_STEP; // узел (0, 0)
        int bossX = x0 + (CAVE_COLS - 1) * CAVE_STEP + BOSS_R + 7, bossZ = cz + 10, yb = yLast - 1;
        int zMax = z0 + (CAVE_ROWS - 1) * CAVE_STEP;

        // над всей пещерой должна быть толща земли, а не вода
        for (int x = cx - 6; x <= bossX + BOSS_R + 2; x += 6) {
            for (int z = z0 - 10; z <= zMax + 10; z += 6) {
                int top = world.getTopSolidOrLiquidBlock(x, z);
                if (top < y0 + 16 || world.getBlock(x, top - 1, z)
                    .getMaterial()
                    .isLiquid()) return false;
            }
        }

        int bx = cx - 10, bz = z0 - 16;
        Carver c = new Carver(bx, yLast - 8, bz, bossX + BOSS_R + 4 - bx, g + 6 - (yLast - 8), zMax + 16 - bz,
            Integer.MAX_VALUE);

        int L = levels.size();
        double[][][][] lnode = new double[L][CAVE_COLS][CAVE_ROWS][];
        for (int l = 0; l < L; l++) {
            int ly = levels.get(l);
            for (int i = 0; i < CAVE_COLS; i++) {
                for (int j = 0; j < CAVE_ROWS; j++) {
                    lnode[l][i][j] = new double[] { x0 + i * CAVE_STEP + rand.nextInt(5) - 2 + 0.5,
                        ly + 2 + rand.nextInt(3) - 1 + 0.5, z0 + j * CAVE_STEP + rand.nextInt(5) - 2 + 0.5 };
                }
            }
        }
        double[][][] node = lnode[0];
        double[] start = node[0][mid] = new double[] { x0 + 0.5, y0 + 2.5, bossZ + 0.5 };
        double[] last = lnode[L - 1][CAVE_COLS - 1][mid] = new double[] { x0 + (CAVE_COLS - 1) * CAVE_STEP + 0.5,
            yLast + 2.5, bossZ + 0.5 };

        // спуск из иглу: сначала яма в полу, дальше косой ход с поворотами
        double[][] descent = { { cx + 0.5, g + 1.5, cz + 0.5 }, { cx + 4.5, g - 2.5, cz + 0.5 },
            { cx + 11.5, g - 8.5, cz - 5.5 }, { cx + 20.5, g - 14.5, cz + 2.5 }, start };
        c.line(descent[0], descent[1], rand, false, 1.6, 2.0, 1.8, 2.2);
        for (int n = 1; n < descent.length - 1; n++) {
            c.line(descent[n], descent[n + 1], rand, true, 2.0, 2.6, 2.0, 2.4);
        }

        // каждый этаж: ходы (дерево + петли), залы с колоннами, тупиковые карманы с сундуками
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        List<double[]> halls = new ArrayList<double[]>(), pockets = new ArrayList<double[]>();
        for (int l = 0; l < L; l++) {
            double[][][] ln = lnode[l];
            List<int[]> edges = mazeEdges(rand, CAVE_COLS, CAVE_ROWS, 0, mid);
            for (int n = 0; n < 5; n++) {
                int i = rand.nextInt(CAVE_COLS), j = rand.nextInt(CAVE_ROWS);
                int[] d = dirs[rand.nextInt(4)];
                if (i + d[0] >= 0 && i + d[0] < CAVE_COLS && j + d[1] >= 0 && j + d[1] < CAVE_ROWS) {
                    edges.add(new int[] { i, j, i + d[0], j + d[1] });
                }
            }
            for (int[] e : edges) c.line(ln[e[0]][e[1]], ln[e[2]][e[3]], rand, true, 2.0, 2.8, 2.0, 2.6);
            int hallsHere = 0;
            for (int n = 0; n < 8 && hallsHere < 2; n++) {
                double[] p = ln[1 + rand.nextInt(CAVE_COLS - 2)][rand.nextInt(CAVE_ROWS)];
                if (halls.contains(p)) continue;
                c.blob(p[0], p[1] + 0.8, p[2], 5.0 + rand.nextDouble() * 1.5, 3.4);
                halls.add(p);
                hallsHere++;
            }
            for (int n = 0; n < 6; n++) {
                double[] p = ln[rand.nextInt(CAVE_COLS - 1)][rand.nextInt(CAVE_ROWS)];
                double a = rand.nextDouble() * Math.PI * 2, len = 5 + rand.nextInt(4);
                double[] q = { p[0] + Math.cos(a) * len, p[1], p[2] + Math.sin(a) * len };
                c.line(p, q, rand, true, 1.6, 2.2, 1.8, 2.2);
                c.blob(q[0], q[1] + 0.5, q[2], 2.6, 2.0);
                pockets.add(q);
            }
            // два пологих спуска на этаж ниже (сдвиг на два узла - уклон около 1:2)
            if (l + 1 < L) {
                for (int n = 0; n < 2; n++) {
                    int i = rand.nextInt(CAVE_COLS - 2), j = rand.nextInt(CAVE_ROWS);
                    c.line(ln[i][j], lnode[l + 1][i + 2][j], rand, true, 2.0, 2.6, 2.0, 2.4);
                }
            }
        }

        // купольный зал босса и ход к нему
        for (int dx = -BOSS_R; dx <= BOSS_R; dx++) {
            for (int dz = -BOSS_R; dz <= BOSS_R; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz) / BOSS_R;
                if (r >= 1.0) continue;
                int h = (int) (1 + 7 * Math.sqrt(1 - r * r));
                for (int y = yb + 1; y <= yb + h; y++) c.add(bossX + dx, y, bossZ + dz);
            }
        }
        c.line(last, new double[] { bossX - BOSS_R + 1.5, yb + 2.5, bossZ + 0.5 }, rand, false, 2.0, 2.6, 2.0, 2.4);

        // вырезаем и обкладываем льдом и мёрзлым камнем (у поверхности - не трогаем, там иглу)
        for (int[] p : c.cells) set(world, p[0], p[1], p[2], Blocks.air);
        for (int[] p : c.cells) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int x = p[0] + dx, y = p[1] + dy, z = p[2] + dz;
                if (y >= g || c.has(x, y, z)) continue;
                Block b = world.getBlock(x, y, z);
                if (b == Blocks.bedrock || b == Blocks.packed_ice || b == IceikaBlocks.frozenStone) continue;
                set(world, x, y, z, iceShell(x, y, z, c.has(x, y + 1, z)));
            }
        }

        igloo(world, c, cx, g, cz);

        // снег на полу и сосульки с потолка
        for (int[] p : c.cells) {
            int x = p[0], y = p[1], z = p[2];
            if (y >= g - 1) continue;
            if (!c.has(x, y - 1, z) && rand.nextInt(100) < 55) world.setBlock(x, y, z, Blocks.snow_layer,
                rand.nextInt(2), 2);
            if (!c.has(x, y + 1, z) && rand.nextInt(14) == 0) {
                int len = 1 + rand.nextInt(3);
                boolean room = true;
                for (int k = 0; k <= len + 2; k++) if (!c.has(x, y - k, z)) room = false;
                if (room) for (int k = 0; k < len; k++) set(world, x, y - k, z, Blocks.packed_ice);
            }
        }

        // ледяные лампы в потолке
        List<int[]> lamps = new ArrayList<int[]>();
        for (int[] p : c.path) {
            if (rand.nextInt(7) != 0 || near(lamps, p, 10)) continue;
            int y = p[1];
            while (c.has(p[0], y + 1, p[2]) && y - p[1] < 5) y++;
            if (c.has(p[0], y + 1, p[2]) || y + 1 >= g) continue;
            world.setBlock(p[0], y + 1, p[2], VanillaBlocks.lamp2, 0, 2);
            lamps.add(p);
        }

        // морозные сундуки в концах карманов
        for (int n = 0; n < pockets.size(); n++) {
            if (rand.nextInt(4) == 0) continue; // не в каждом кармане
            double[] q = pockets.get(n);
            int x = MathHelper.floor_double(q[0]), z = MathHelper.floor_double(q[2]), y = MathHelper.floor_double(q[1]);
            if (!c.has(x, y, z)) continue;
            while (c.has(x, y - 1, z)) y--;
            LairLoot.chest(world, rand, x, y, z, true);
        }

        // спавнеры Глаконов и Фростов вмёрзли в пол, сверху снег
        String glacon = (String) EntityList.classToStringMapping.get(EntityGlacon.class);
        String frost = (String) EntityList.classToStringMapping.get(EntityFrost.class);
        List<int[]> path = new ArrayList<int[]>(c.path);
        Collections.shuffle(path, rand);
        List<int[]> spawners = new ArrayList<int[]>();
        for (int[] p : path) {
            if (spawners.size() >= 5 * L || glacon == null || frost == null) break;
            if (p[0] < x0 + 6 || p[0] > bossX - BOSS_R - 3 || near(spawners, p, 12)) continue;
            int y = p[1];
            while (c.has(p[0], y - 1, p[2]) && p[1] - y < 5) y--;
            if (c.has(p[0], y - 1, p[2])) continue;
            spawner(world, p[0], y - 1, p[2], spawners.size() % 3 == 2 ? frost : glacon);
            world.setBlock(p[0], y, p[2], Blocks.snow_layer, 0, 2);
            spawners.add(p);
        }

        // колонны в залах и кольцом в зале босса
        for (double[] h : halls) {
            int n = 2 + rand.nextInt(2);
            for (int k = 0; k < n; k++) {
                double a = rand.nextDouble() * Math.PI * 2, r = 2.0 + rand.nextDouble() * 1.8;
                pillar(world, c, (int) Math.floor(h[0] + Math.cos(a) * r), (int) h[1],
                    (int) Math.floor(h[2] + Math.sin(a) * r), rand);
            }
        }
        double a0 = rand.nextDouble() * Math.PI;
        for (int k = 0; k < 6; k++) {
            double a = a0 + k * Math.PI / 3;
            pillar(world, c, bossX + (int) Math.round(Math.cos(a) * 6.5), yb + 3,
                bossZ + (int) Math.round(Math.sin(a) * 6.5), rand);
        }
        for (int[] l : new int[][] { { -4, -4 }, { -4, 4 }, { 3, -4 }, { 3, 4 } }) {
            int y = yb + 1;
            while (c.has(bossX + l[0], y + 1, bossZ + l[1])) y++;
            world.setBlock(bossX + l[0], y + 1, bossZ + l[1], VanillaBlocks.lamp2, 0, 2);
        }

        // ступенчатый помост в центре зала: 5x5 без углов, сверху 3x3 и алтарь; по углам ледяные фонари
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            if (!corner) set(world, bossX + dx, yb + 1, bossZ + dz, IceikaBlocks.snowBricks);
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) set(world, bossX + dx, yb + 2, bossZ + dz, IceikaBlocks.icyBricks);
        }
        for (int[] k : new int[][] { { -3, -3 }, { -3, 3 }, { 3, -3 }, { 3, 3 } }) {
            set(world, bossX + k[0], yb + 1, bossZ + k[1], Blocks.packed_ice);
            set(world, bossX + k[0], yb + 2, bossZ + k[1], Blocks.packed_ice);
            world.setBlock(bossX + k[0], yb + 3, bossZ + k[1], VanillaBlocks.lamp2, 0, 2);
        }
        set(world, bossX, yb + 3, bossZ, ModBlocks.glaconAltar);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = yb + 4; y <= yb + 7; y++) {
            if (c.has(bossX + dx, y, bossZ + dz)) set(world, bossX + dx, y, bossZ + dz, Blocks.air); // без сосулек над боссом
        }
        // светящаяся рунная плита в восточной стене
        int ax = bossX + 6;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 0; dy <= 3; dy++) {
                int y = yb + 2 + dy, x = ax + 1;
                while (c.has(x, y, bossZ + dz)) x++;
                boolean frame = Math.abs(dz) == 2 || dy == 0 || dy == 3;
                set(world, x, y, bossZ + dz, frame ? IceikaBlocks.icyBricks : Blocks.glowstone);
            }
        }

        LairData.get(world)
            .add(LairData.SNOW, cx - 7, g + 1, cz); // вход в иглу
        return true;
    }

    /** Стена пещеры: пятна пакового льда и мёрзлого камня; пол чаще каменный. */
    private static Block iceShell(int x, int y, int z, boolean floor) {
        int h = ((x >> 1) * 734287) ^ ((y >> 1) * 912271) ^ ((z >> 1) * 438289);
        int v = (h >>> 8) % 10;
        if (floor) return v < 6 ? IceikaBlocks.frozenStone : Blocks.packed_ice;
        return v < 7 ? Blocks.packed_ice : IceikaBlocks.frozenStone;
    }

    /** Колонна от пола до потолка: толстая у основания и у свода, тонкая посередине. */
    private static void pillar(World world, Carver c, int x, int yMid, int z, Random rand) {
        if (!c.has(x, yMid, z)) return;
        int lo = yMid, hi = yMid;
        while (c.has(x, lo - 1, z)) lo--;
        while (c.has(x, hi + 1, z)) hi++;
        int h = hi - lo + 1;
        if (h < 3) return;
        for (int y = lo; y <= hi; y++) {
            double t = (y - lo) / (double) (h - 1), r = 0.9 + 1.5 * Math.abs(t - 0.5) * 2;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > r * r || !c.has(x + dx, y, z + dz)) continue;
                set(world, x + dx, y, z + dz, rand.nextInt(4) == 0 ? IceikaBlocks.frozenStone : Blocks.packed_ice);
            }
        }
    }

    /** Иглу из снежных блоков с входом-туннелем на запад; в полу - яма спуска в пещеру. */
    private static void igloo(World world, Carver c, int cx, int g, int cz) {
        for (int dx = -8; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int x = cx + dx, z = cz + dz;
                double r = Math.sqrt(dx * dx + dz * dz);
                boolean tunnel = dx <= -4 && Math.abs(dz) <= 1;
                if (r > 5.5 && !(tunnel && dx >= -8)) continue;
                // фундамент: пол и всё пустое под ним до земли
                for (int y = g; y > g - 6; y--) {
                    if (c.has(x, y, z)) break;
                    if (y < g && world.getBlock(x, y, z)
                        .getMaterial()
                        .isSolid()) break;
                    set(world, x, y, z, Blocks.snow);
                }
                // купол: снаружи 5, внутри 4
                for (int dy = 1; dy <= 7; dy++) {
                    if (c.has(x, g + dy, z)) continue;
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy);
                    Block b = d < 4.0 ? Blocks.air : d < 5.0 ? Blocks.snow : Blocks.air;
                    if (tunnel && r >= 3.5) {
                        // туннель входа: 1 x 2 внутри, стены и крыша из снега
                        boolean inside = dz == 0 && dy <= 2;
                        b = inside ? Blocks.air : dy <= 3 && dx >= -7 ? Blocks.snow : Blocks.air;
                    }
                    set(world, x, g + dy, z, b);
                }
            }
        }
    }

    // ---------------------------------------------------------------- общее

    private static void set(World world, int x, int y, int z, Block b) {
        world.setBlock(x, y, z, b, 0, 2);
    }

    private static void spawner(World world, int x, int y, int z, String entity) {
        world.setBlock(x, y, z, Blocks.mob_spawner, 0, 2);
        TileEntityMobSpawner te = (TileEntityMobSpawner) world.getTileEntity(x, y, z);
        if (te != null) te.func_145881_a()
            .setEntityName(entity);
    }
}
