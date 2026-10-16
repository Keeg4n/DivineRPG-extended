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

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz), R = NEST_R;
            if (r > R + 0.5) return false;
            bowl(dx, dz, r, R, col, true);
            boolean rim = r >= R - 3;
            put(col, TOP, rim ? LOG : GRASS, 0);
            for (int y = TOP + 1; y <= TOP + 24; y++) put(col, y, AIR, 0);
            if (rim && !gate(dx, dz)) {
                int h = 3 + hash(dx, dz) % 3;
                for (int y = TOP + 1; y <= TOP + h; y++) put(col, y, LOG, 0);
                if (hash(dz, dx) % 2 == 0) put(col, TOP + h + 1, LEAVES, 0);
            } else if (rim) {
                put(col, TOP, PLANKS, 0); // порог прохода
            }
            // кольцо деревянных клыков с кронами
            double fang = ringDist(dx, dz, 15, 8, Math.PI / 8);
            if (fang < 0.9) {
                for (int y = TOP + 1; y <= TOP + 7; y++) put(col, y, LOG, 0);
                put(col, TOP + 8, LEAVES, 0);
            } else if (fang < 1.9) {
                put(col, TOP + 7, LEAVES, 0);
            }
            // светящееся кольцо в полу
            if (ringDist(dx, dz, 9, 8, 0) < 0.5) put(col, TOP, GLOW, 0);
            if (r < 1.5) put(col, TOP, GEM, 0);
            // помост с алтарём на севере
            double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ) * (dz + ALTAR_DZ));
            if (ad <= 2.5) put(col, TOP + 1, GEM, 0);
            if (dx == 0 && dz == -ALTAR_DZ) put(col, TOP + 2, ALTAR, 0);
            return true;
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
