package com.divinerpgextended.world.twilight;

import java.util.Random;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Логова боссов сумеречных миров - висят над островами на своём клочке земли, место вычисляется из сида (одно на
 * квадрат nestSpacing x nestSpacing в каждом мире).
 * <ul>
 * <li>Гнездо Великого кадиллиона (Эдем, Вайлдвуд, Апалачия, Скайтерн): чаша из земли мира с корнями снизу, плетёный
 * борт из брёвен с четырьмя проходами, кольцо деревянных клыков, светящееся кольцо в полу, помост с алтарём на севере.
 * Зов кадиллиона работает только на этом алтаре; босс встаёт в центре гнезда.</li>
 * <li>Демонический круг (Мортум): каменная площадка с кольцами из демонического камня, шесть обелисков со светом.
 * Кристаллы сумеречных боссов работают только внутри круга.</li>
 * </ul>
 */
public class WorldGenNests implements IWorldGenerator {

    /** Верхний блок пола (стоишь на высоте TOP + 1). */
    public static final int TOP = 140;
    public static final int NEST_R = 26, CIRCLE_R = 22;
    /** Алтарь гнезда: от центра на ALTAR_DZ к северу, на помосте (TOP + 2). */
    public static final int ALTAR_DZ = 10;
    /** Внутри круга (по горизонтали) кристаллы Мортума работают. */
    public static final int CIRCLE_ZONE = 18;

    // ---------------------------------------------------------------- место

    public static int radius(int dim) {
        return TwilightWorld.of(dim) == TwilightWorld.MORTUM ? CIRCLE_R : NEST_R;
    }

    /** Центр логова в области (rx, rz) мира dim или null, если логова выключены. */
    public static int[] center(long seed, int dim, int rx, int rz) {
        int g = Config.nestSpacing;
        if (g <= 0) return null;
        Random r = new Random(seed ^ (rx * 341873128712L + rz * 132897987541L) ^ (dim * 0x9E3779B97F4A7C15L) ^ 0x4E35L);
        int margin = Math.min(g / 3, NEST_R + 40);
        return new int[] { rx * g + margin + r.nextInt(Math.max(1, g - 2 * margin)), TOP,
            rz * g + margin + r.nextInt(Math.max(1, g - 2 * margin)) };
    }

    /** Ближайшее к точке логово этого мира (своя и соседние области). */
    public static int[] nearest(long seed, int dim, double x, double z) {
        int g = Config.nestSpacing;
        if (g <= 0) return null;
        int rx = (int) Math.floor(x / g), rz = (int) Math.floor(z / g);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] c = center(seed, dim, rx + i, rz + k);
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** Точка внутри Демонического круга Мортума (там работают кристаллы). */
    public static boolean inCircle(World w, double x, double y, double z) {
        if (TwilightWorld.of(w.provider.dimensionId) != TwilightWorld.MORTUM) return false;
        int[] c = nearest(w.getSeed(), w.provider.dimensionId, x, z);
        if (c == null) return false;
        double dx = x - c[0] - 0.5, dz = z - c[2] - 0.5;
        return dx * dx + dz * dz <= CIRCLE_ZONE * CIRCLE_ZONE && Math.abs(y - (TOP + 1)) <= 8;
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        TwilightWorld tw = TwilightWorld.of(world.provider.dimensionId);
        if (tw == null || Config.nestSpacing <= 0) return;
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8; // область заселения этого чанка
        int[] c = nearest(world.getSeed(), world.provider.dimensionId, x0 + 8, z0 + 8);
        int r = radius(world.provider.dimensionId);
        if (c == null || Math.abs(c[0] - (x0 + 8)) > r + 16 || Math.abs(c[2] - (z0 + 8)) > r + 16) return;
        ColumnStructure s = shape(tw);
        s.buildSlice(world, c[0], c[2], x0, z0);
        s.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, TOP + 1, TOP + 24);
    }

    public static ColumnStructure shape(TwilightWorld tw) {
        return tw == TwilightWorld.MORTUM ? new Circle() : new Nest(tw);
    }

    // ---------------------------------------------------------------- общее

    static final int STONE = 1, DIRT = 2, GRASS = 3, LOG = 4, LEAVES = 5, PLANKS = 6, GEM = 7, GLOW = 8, ALTAR = 9;

    static Block palette(TwilightWorld w, int code) {
        switch (code) {
            case STONE: return TwilightBlocks.twilightStone;
            case DIRT: return w.dirt();
            case GRASS: return w.grass();
            case LOG: return w.log();
            case LEAVES: return w.leaves();
            case PLANKS: return w.planks();
            case GEM: return w.gemBlock();
            case GLOW: return Blocks.glowstone;
            case ALTAR: return ModBlocks.nestAltar;
            default: return Blocks.air;
        }
    }

    /** Чаша под полом: земля мира сверху, камень ниже, глубже к центру; редкие свисающие корни. */
    static void bowl(int dx, int dz, double r, double R, int[] col, boolean roots) {
        int bottom = TOP - 2 - (int) (13 * Math.sqrt(Math.max(0, 1 - (r / R) * (r / R))));
        for (int y = bottom; y <= TOP - 3; y++) ColumnStructure.put(col, y, STONE, 0);
        ColumnStructure.put(col, TOP - 2, DIRT, 0);
        ColumnStructure.put(col, TOP - 1, DIRT, 0);
        if (roots && r < R - 4 && ColumnStructure.hash(dx, dz) % 17 == 0) {
            int len = 2 + ColumnStructure.hash(dz, dx) % 7;
            for (int y = bottom - len; y < bottom; y++) ColumnStructure.put(col, y, LOG, 0);
        }
    }

    /** Ближайший центр предмета кольца (радиус ring, count штук) - расстояние до него. */
    static double ringDist(double dx, double dz, double ring, int count, double phase) {
        double a = Math.atan2(dz, dx) - phase, step = Math.PI * 2 / count;
        double k = Math.round(a / step);
        double ca = k * step + phase;
        double px = Math.cos(ca) * ring, pz = Math.sin(ca) * ring;
        return Math.sqrt((dx - px) * (dx - px) + (dz - pz) * (dz - pz));
    }

    /** Проход в борту: четыре стороны света, по ~14 градусов. */
    static boolean gate(int dx, int dz) {
        double ang = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 90;
        return ang < 7 || ang > 83;
    }

    // ---------------------------------------------------------------- гнездо

    static class Nest extends ColumnStructure {

        private final TwilightWorld w;
        /** Ветки, торчащие из вала наружу и вверх. */
        private static final int TWIGS = 14;

        Nest(TwilightWorld w) {
            this.w = w;
        }

        @Override
        protected Block block(int code, int meta) {
            return palette(w, code);
        }

        @Override
        protected boolean lit(Block b) {
            return b == Blocks.glowstone || b == ModBlocks.nestAltar;
        }

        /** Плавный шум 0..1 по ячейкам cell (билинейный по хэшам углов). */
        static double noise(int x, int z, int cell, int salt) {
            int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
            double fx = (x - gx * cell) / (double) cell, fz = (z - gz * cell) / (double) cell;
            fx = fx * fx * (3 - 2 * fx);
            fz = fz * fz * (3 - 2 * fz);
            double a = hash(gx + salt, gz) % 1000 / 1000.0, b = hash(gx + 1 + salt, gz) % 1000 / 1000.0;
            double c = hash(gx + salt, gz + 1) % 1000 / 1000.0, d = hash(gx + 1 + salt, gz + 1) % 1000 / 1000.0;
            return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
        }

        /** Бревно вдоль касательной к кругу: мета 4 - вдоль X, 8 - вдоль Z. */
        private static int tangent(int dx, int dz) {
            return Math.abs(dz) > Math.abs(dx) ? 4 : 8;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            double rOut = NEST_R - 1 + noise(dx, dz, 7, 11) * 3, rIn = rOut - 6;
            boolean any = false;
            if (r <= rOut) {
                any = true;
                // низ - неровный остров: земля под полом, глубже камень с землёй, каменные сосульки и редкие корни
                // к краю остров сходит на нет: и толщина, и шум затухают (без отвесной стенки)
                double edge = 1 - r / rOut;
                double depth = 16 * (1 - Math.pow(r / rOut, 1.3)) + noise(dx, dz, 5, 23) * 6 * Math.sqrt(edge);
                int bottom = TOP - 1 - (int) depth;
                for (int y = bottom; y < TOP; y++) {
                    boolean dirt = y >= TOP - 2 || hash(dx * 31 + y, dz) % 10 < 3;
                    put(col, y, dirt ? DIRT : STONE, 0);
                }
                int h = hash(dx, dz);
                if (r < rOut - 5 && h % 29 == 0) {
                    int len = 1 + h / 29 % 4;
                    for (int y = bottom - len; y < bottom; y++) put(col, y, STONE, 0);
                } else if (r < rOut - 6 && h % 53 == 0) {
                    int len = 3 + h / 53 % 6;
                    for (int y = bottom - len; y < bottom; y++) put(col, y, LOG, 0);
                }
                for (int y = TOP + 1; y <= TOP + 24; y++) put(col, y, AIR, 0);
                if (r < rIn) {
                    // пол: трава, подстилка из листвы, кладка яиц, помост с алтарём, немного света
                    put(col, TOP, GRASS, 0);
                    if (hash(dz, dx) % 7 == 0 && r > 4) put(col, TOP + 1, LEAVES, 0);
                    if (ringDist(dx, dz, 9, 6, Math.PI / 6) < 0.5) put(col, TOP, GLOW, 0);
                    // кладка: пять "яиц" у центра
                    double egg = ringDist(dx, dz, 3, 5, 0.3);
                    if (egg < 0.8) {
                        put(col, TOP + 1, GEM, 0);
                        put(col, TOP + 2, GEM, 0);
                    } else if (egg < 1.6) {
                        put(col, TOP + 1, GEM, 0);
                    }
                    if (r < 1.2) put(col, TOP, GLOW, 0);
                } else {
                    // вал, сплетённый из лежащих брёвен, листвы и досок; выше всего посередине
                    double t = (r - rIn) / (rOut - rIn);
                    double ang = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 90;
                    boolean gate = ang < 5 || ang > 85;
                    int lip = gate ? 1 : (int) Math.round(5.5 * Math.sin(Math.PI * Math.pow(t, 0.8)) + noise(dx, dz, 3, 41) * 2 - 0.5);
                    put(col, TOP, LOG, tangent(dx, dz));
                    for (int y = TOP + 1; y <= TOP + lip; y++) {
                        int k = hash(dx * 7 + y, dz * 13 - y) % 20;
                        if (k < 11) put(col, y, LOG, tangent(dx, dz));
                        else if (k < 17) put(col, y, LEAVES, 0);
                        else put(col, y, PLANKS, 0);
                    }
                    if (!gate && lip > 0 && hash(dz, dx + 5) % 3 == 0) put(col, TOP + lip + 1, LEAVES, 0);
                    if (gate) put(col, TOP, PLANKS, 0);
                }
                // помост с алтарём на севере
                double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ) * (dz + ALTAR_DZ));
                if (ad <= 2.5) put(col, TOP + 1, GEM, 0);
                if (ad <= 1.5) put(col, TOP + 1, PLANKS, 0);
                if (dx == 0 && dz == -ALTAR_DZ) put(col, TOP + 2, ALTAR, 0);
            }
            // ветки: из вала наружу и вверх, на концах листва
            for (int i = 0; i < TWIGS; i++) {
                double a = Math.PI * 2 * i / TWIGS + (hash(i, 77) % 100) / 100.0 * 0.35;
                double ca = Math.cos(a), sa = Math.sin(a);
                double bx = ca * (NEST_R - 3), bz = sa * (NEST_R - 3);
                double along = (dx - bx) * ca + (dz - bz) * sa, across = Math.abs(-(dx - bx) * sa + (dz - bz) * ca);
                int len = 6 + hash(i, 13) % 5;
                if (along < 0 || along > len || across > 0.75) continue;
                any = true;
                int y = TOP + 3 + (int) Math.round(along * (0.45 + hash(i, 5) % 30 / 100.0));
                put(col, y, LOG, Math.abs(ca) > Math.abs(sa) ? 4 : 8);
                if (along > len - 1.5) {
                    put(col, y + 1, LEAVES, 0);
                }
            }
            return any;
        }
    }

    // ---------------------------------------------------------------- Демонический круг

    static class Circle extends ColumnStructure {

        @Override
        protected Block block(int code, int meta) {
            return palette(TwilightWorld.MORTUM, code);
        }

        @Override
        protected boolean lit(Block b) {
            return b == Blocks.glowstone;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz), R = CIRCLE_R;
            if (r > R + 0.5) return false;
            bowl(dx, dz, r, R, col, false);
            int ri = (int) r;
            put(col, TOP, ri % 4 == 0 || r < 2.5 ? GEM : STONE, 0);
            for (int y = TOP + 1; y <= TOP + 24; y++) put(col, y, AIR, 0);
            if (r >= R - 1.5 && !gate(dx, dz)) {
                put(col, TOP + 1, STONE, 0);
                if (hash(dx, dz) % 3 != 0) put(col, TOP + 2, STONE, 0);
            }
            // шесть обелисков со светом на вершине
            double ob = ringDist(dx, dz, 16, 6, 0);
            if (ob < 1.6) {
                int h = ob < 0.9 ? 10 : 7;
                for (int y = TOP + 1; y <= TOP + h; y++) put(col, y, GEM, 0);
                if (ob < 0.9) put(col, TOP + 11, GLOW, 0);
            }
            // внутреннее кольцо света
            if (ringDist(dx, dz, 6, 12, 0) < 0.5) put(col, TOP, GLOW, 0);
            return true;
        }
    }
}
