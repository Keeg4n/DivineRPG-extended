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
 * <li>Заповедная роща Великого кадиллиона (Эдем, Вайлдвуд, Апалачия, Скайтерн): остров с шестью гигантскими деревьями
 * мира, под сводом крон - поляна с колоннадой древнего святилища, родником и помостом с алтарём на севере. Зов
 * кадиллиона работает только на этом алтаре; босс встаёт в центре поляны.</li>
 * <li>Демонический круг (Мортум): каменная площадка с кольцами из демонического камня, шесть обелисков со светом.
 * Кристаллы сумеречных боссов работают только внутри круга.</li>
 * </ul>
 */
public class WorldGenNests implements IWorldGenerator {

    /** Верхний блок пола (стоишь на высоте TOP + 1). */
    public static final int TOP = 140;
    public static final int NEST_R = 34, CIRCLE_R = 22;
    /** Кроны рощи выходят за край острова. */
    static final int CANOPY_REACH = 12;
    /** Алтарь гнезда: от центра на ALTAR_DZ к северу, на помосте (TOP + 2). */
    public static final int ALTAR_DZ = 10;
    /** Внутри круга (по горизонтали) кристаллы Мортума работают. */
    public static final int CIRCLE_ZONE = 18;

    // ---------------------------------------------------------------- место

    public static int radius(int dim) {
        return TwilightWorld.of(dim) == TwilightWorld.MORTUM ? CIRCLE_R : NEST_R + CANOPY_REACH;
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
        ColumnStructure s = shape(tw, c);
        s.buildSlice(world, c[0], c[2], x0, z0);
        s.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, TOP + 1, TOP + 24);
    }

    public static ColumnStructure shape(TwilightWorld tw, int[] c) {
        if (tw == TwilightWorld.SKYTHERN) return new Temple(c[0], c[2]);
        if (tw == TwilightWorld.WILDWOOD) return new Henge(c[0], c[2]);
        if (tw == TwilightWorld.APALACHIA) return new Colosseum(c[0], c[2]);
        return tw == TwilightWorld.MORTUM ? new Circle() : new Nest(tw, c[0], c[2]);
    }

    // ---------------------------------------------------------------- общее

    static final int STONE = 1, DIRT = 2, GRASS = 3, LOG = 4, LEAVES = 5, PLANKS = 6, GEM = 7, GLOW = 8, ALTAR = 9, WATER = 10, ORE = 11;

    static Block palette(TwilightWorld w, int code) {
        switch (code) {
            case STONE: return TwilightBlocks.twilightStone;
            case DIRT: return w.dirt();
            case GRASS: return w.grass();
            case LOG: return w.log();
            case LEAVES: return w.leaves();
            case PLANKS: return w.planks();
            case GEM: return w.gemBlock();
            case GLOW: return w.lamp();
            case ALTAR: return ModBlocks.nestAltar;
            case ORE: return w.ore();
            default: return Blocks.air;
        }
    }

    /** Лампа любого сумеречного мира (для пересчёта света вокруг). */
    static boolean isLamp(Block b) {
        for (TwilightWorld w : TwilightWorld.values()) if (w.isLamp(b)) return true;
        return false;
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

    /**
     * Заповедная роща Великого кадиллиона: остров с шестью гигантскими деревьями мира по кругу (видно издалека), под
     * сводом их крон - поляна с обломками колоннады древнего святилища, обвитыми корнями, родником и помостом с
     * алтарём. Под островом свисают корни деревьев.
     */
    static class Nest extends ColumnStructure {

        private final TwilightWorld w;
        private final int salt;
        /** С какой стороны кусок оторван от земли (отвесный скол). */
        private final double torn;
        private static final int TREES = 6, TRUNK_RING = 24, CANOPY_R = 13, CANOPY_H = 6;
        private final double[] tx = new double[TREES], tz = new double[TREES];
        private final int[] th = new int[TREES];

        Nest(TwilightWorld w, int cx, int cz) {
            this.w = w;
            salt = hash(cx, cz) % 1000;
            torn = salt / 1000.0 * Math.PI * 2;
            for (int i = 0; i < TREES; i++) {
                double a = Math.PI * 2 * i / TREES + (hash(i, salt) % 100) / 100.0 * 0.5;
                double rr = TRUNK_RING + hash(salt, i) % 4 - 1;
                tx[i] = Math.cos(a) * rr;
                tz[i] = Math.sin(a) * rr;
                th[i] = 36 + hash(i * 7, salt) % 15;
            }
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? w.lampMeta() : meta;
        }

        @Override
        protected Block block(int code, int meta) {
            if (code == WATER) return Blocks.water;
            return palette(w, code);
        }

        @Override
        protected boolean lit(Block b) {
            return isLamp(b) || b == ModBlocks.nestAltar;
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

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            double rOut = NEST_R - 1 + noise(dx, dz, 7, 11 + salt) * 3;
            boolean any = false;
            if (r <= rOut) {
                any = true;
                // низ - кусок земли, вырванный из мира: глубокий рваный камень с прожилками земли и вкраплениями руды;
                // с одной стороны отвесный скол (оторвано сбоку), с остальных сужается; длинные сосульки и наплывы
                double t = r / rOut, ang = Math.atan2(dz, dx);
                double side = Math.max(0, Math.cos(ang - torn));
                side = side * side;
                double base = 30 * (1 - Math.pow(t, 1.5)), cliff = 27 * (1 - Math.pow(t, 5));
                double depth = base + (cliff - base) * side + noise(dx, dz, 6, 23 + salt) * 9 * Math.sqrt(1 - t)
                    + noise(dx, dz, 2, 37 + salt) * 3;
                int bottom = TOP - 1 - (int) depth;
                for (int y = bottom; y < TOP; y++) {
                    int k = hash(dx * 31 + y, dz - y * 7 + salt) % 100;
                    int code = y >= TOP - 3 ? DIRT : k < 18 ? DIRT : k < 21 ? ORE : STONE;
                    put(col, y, code, 0);
                }
                put(col, TOP, GRASS, 0);
                for (int y = TOP + 1; y <= TOP + 60; y++) put(col, y, AIR, 0);
                int h = hash(dx + salt, dz);
                double drip = noise(dx, dz, 3, 99 + salt);
                if (drip > 0.74 && t < 0.9) {
                    int len = (int) ((drip - 0.74) * 50) + 2; // широкие наплывы
                    for (int y = bottom - len; y < bottom; y++) put(col, y, STONE, 0);
                } else if (t < 0.92 && h % 11 == 0) {
                    int len = 2 + h / 11 % 9; // сосульки
                    for (int y = bottom - len; y < bottom; y++) put(col, y, STONE, 0);
                }
                // поляна: каменное кольцо-дорожка, колоннада, родник, помост с алтарём
                if (r > 11.5 && r < 13.5 && hash(dz, dx) % 4 != 0) put(col, TOP, STONE, 0);
                double colDist = ringDist(dx, dz, 15, 8, Math.PI / 8 + salt % 7 * 0.1);
                if (colDist < 1.0) {
                    int k = (int) Math.round((Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2 / 8)) % 8;
                    int ch = 3 + hash(k, salt) % 8; // обломаны по-разному
                    for (int y = TOP + 1; y <= TOP + ch; y++) put(col, y, STONE, 0);
                    if (ch >= 8) put(col, TOP + ch + 1, GEM, 0);
                } else if (colDist < 1.9) {
                    int k = (int) Math.round((Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2 / 8)) % 8;
                    // корни обвивают колонну с одной стороны
                    if (hash(dx, dz + salt) % 3 == 0) for (int y = TOP + 1; y <= TOP + 2 + hash(k, 3) % 4; y++) put(col, y, LOG, 0);
                    else if (hash(dz, dx + salt) % 5 == 0) put(col, TOP + 1, STONE, 0); // обломок
                }
                double sp = Math.sqrt(dx * dx + (dz - 7) * (dz - 7));
                if (sp < 2.3) put(col, TOP, WATER, 0);
                else if (sp < 3.3) put(col, TOP, STONE, 0);
                if (sp < 3.3) put(col, TOP - 1, STONE, 0);
                double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ) * (dz + ALTAR_DZ));
                if (ad <= 2.5) put(col, TOP + 1, GEM, 0);
                if (dx == 0 && dz == -ALTAR_DZ) put(col, TOP + 2, ALTAR, 0);
                if (r < 1.2) put(col, TOP, GLOW, 0);
                if (r > 6 && r < 30 && hash(dx * 3, dz * 5 + salt) % 97 == 0) put(col, TOP, GLOW, 0);
            }
            // деревья: стволы клонятся к поляне, у основания корни-подпорки, под островом - корни вниз;
            // кроны широкие и плоские, сдвинуты к центру - смыкаются в рваный свод над поляной
            for (int i = 0; i < TREES; i++) {
                int top = TOP + th[i];
                double len = Math.sqrt(tx[i] * tx[i] + tz[i] * tz[i]), ix = -tx[i] / len, iz = -tz[i] / len;
                double baseD = Math.sqrt((dx - tx[i]) * (dx - tx[i]) + (dz - tz[i]) * (dz - tz[i]));
                if (baseD > CANOPY_R + 10) continue;
                for (int y = TOP - 30 - 18; y <= top + CANOPY_H + 4; y++) {
                    double lean = Math.max(0, y - TOP) / (double) th[i] * 5; // к вершине ствол уходит на 5 блоков к центру
                    double cx = tx[i] + ix * lean, cz = tz[i] + iz * lean;
                    double ddx = dx - cx, ddz = dz - cz, d = Math.sqrt(ddx * ddx + ddz * ddz);
                    if (y <= top + 2) {
                        double rad = 2.8;
                        if (y > TOP && y <= TOP + 5) {
                            // корни-подпорки по диагоналям, ниже - шире
                            double ang = Math.atan2(ddz, ddx);
                            double ridge = Math.abs(Math.cos(2 * (ang - salt % 10 * 0.1)));
                            rad += (TOP + 6 - y) * 0.9 * ridge * ridge;
                        }
                        if (y <= TOP && y >= TOP - 30 && d <= 2.8) { put(col, y, LOG, 0); any = true; continue; }
                        if (y < TOP - 30) {
                            // корень вниз сужается к концу
                            int root = 8 + hash(i, salt) % 10; // вырванные корни торчат из-под камня
                            double rr = 1.9 * (1 - (TOP - 30 - y) / (double) (root + 2));
                            if (y >= TOP - 30 - root && d <= Math.max(0.6, rr)) { put(col, y, LOG, 0); any = true; }
                            continue;
                        }
                        if (y > TOP && d <= rad) { put(col, y, LOG, 0); any = true; continue; }
                    }
                    // ветки на двух уровнях, наклонены вверх, по четырём сторонам
                    for (int lvl = 0; lvl < 2; lvl++) {
                        int by = top - 6 + lvl * 4;
                        for (int k = 0; k < 4; k++) {
                            double ba = Math.PI / 2 * k + lvl * Math.PI / 4 + salt % 10 * 0.2;
                            double bx = Math.cos(ba), bz = Math.sin(ba);
                            double along = ddx * bx + ddz * bz, across = Math.abs(-ddx * bz + ddz * bx);
                            if (along >= 2 && along <= 8 && across < 0.7 && y == by + (int) (along * 0.4)) {
                                put(col, y, LOG, Math.abs(bx) > Math.abs(bz) ? 4 : 8);
                                any = true;
                            }
                        }
                    }
                    if (y < top - CANOPY_H + 2 || col[y * 2] == LOG) continue;
                    // крона: основной плоский ярус, сдвинутый к центру рощи, и малый верхний
                    double mx = dx - (cx + ix * 4), mz = dz - (cz + iz * 4);
                    double e1 = (mx * mx + mz * mz) / (double) (CANOPY_R * CANOPY_R) + ((y - top) / 5.0) * ((y - top) / 5.0);
                    double e2 = (ddx * ddx + ddz * ddz) / 36.0 + ((y - top - 6) / 4.0) * ((y - top - 6) / 4.0);
                    double e = Math.min(e1, e2);
                    if (e > 1) continue;
                    if (e > 0.7 && hash(dx * 7 + y, dz * 3 - y + salt) % 4 == 0) continue; // рваный край
                    put(col, y, hash(dx + y * 13, dz + salt) % 71 == 0 ? GLOW : LEAVES, 0);
                    any = true;
                }
            }
            return any;
        }
    }

    // ---------------------------------------------------------------- общее для арен

    /** Каменный клин-основание острова арены и пустота над ним. */
    static void islandBase(int dx, int dz, double r, double rOut, int salt, int[] col) {
        double t = r / rOut;
        int bottom = TOP - 3 - (int) (22 * Math.sqrt(Math.max(0, 1 - t * t)) + Nest.noise(dx, dz, 4, 29 + salt) * 6);
        for (int y = bottom; y <= TOP - 2; y++) ColumnStructure.put(col, y, ColumnStructure.hash(dx * 17 + y, dz + salt) % 100 < 12 ? DIRT : STONE, 0);
        ColumnStructure.put(col, TOP - 1, DIRT, 0);
        for (int y = TOP + 1; y <= TOP + 40; y++) ColumnStructure.put(col, y, ColumnStructure.AIR, 0);
    }

    /** Родники капища Древнего голема: 4 штуки на радиусе SPRING_R по диагоналям. */
    public static final int SPRING_R = 8;

    public static int[] spring(int i) {
        double a = Math.PI / 4 + Math.PI / 2 * i;
        return new int[] { (int) Math.round(Math.cos(a) * SPRING_R), (int) Math.round(Math.sin(a) * SPRING_R) };
    }

    // ---------------------------------------------------------------- капище (Дикий лес)

    /**
     * Капище Дикого леса - арена Древнего голема: остров с кольцом из двенадцати мшистых менгиров (часть соединена
     * перекладинами), шестью низкими камнями-укрытиями внутри (их разбивают глыбы голема), четырьмя родниками
     * ({@link #spring}) и алтарём; на вершинах менгиров - факелы Дикого леса, по краю - корни.
     */
    static class Henge extends ColumnStructure {

        static final int MOSSY = 30, BRICK = 31, TORCH = 33;
        static final int R_ISLAND = 32, RING = 22, MENHIRS = 12, COVER_R = 14, COVERS = 6;
        private final int salt;

        Henge(int cx, int cz) {
            salt = hash(cx, cz) % 1000;
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? TwilightWorld.WILDWOOD.lampMeta() : meta;
        }

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case MOSSY: return Blocks.mossy_cobblestone;
                case BRICK: return Blocks.stonebrick;
                case TORCH: return ModBlocks.worldTorch[TwilightWorld.WILDWOOD.ordinal()];
                case WATER: return Blocks.water;
                default: return palette(TwilightWorld.WILDWOOD, code);
            }
        }

        @Override
        protected boolean lit(Block b) {
            return b == ModBlocks.worldTorch[TwilightWorld.WILDWOOD.ordinal()] || b == ModBlocks.nestAltar;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            double rOut = R_ISLAND + Nest.noise(dx, dz, 6, 13 + salt) * 3;
            if (r > rOut) return false;
            islandBase(dx, dz, r, rOut, salt, col);
            // земля мира с пятнами мха и каменной тропой по кольцу
            int h = hash(dx + salt, dz);
            put(col, TOP, Nest.noise(dx, dz, 3, 41 + salt) > 0.72 ? MOSSY : GRASS, 0);
            if (Math.abs(r - 18) < 1.0 && h % 3 != 0) put(col, TOP, BRICK, h % 2 == 0 ? 1 : 2);
            // внешнее кольцо менгиров с перекладинами
            int k = (int) Math.round((Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2 / MENHIRS)) % MENHIRS;
            int mh = 6 + hash(k, salt) % 4;
            double md = ringDist(dx, dz, RING, MENHIRS, 0);
            if (md < 1.3) {
                for (int y = TOP + 1; y <= TOP + mh; y++) put(col, y, hash(dx * 3 + y, dz + salt) % 3 == 0 ? BRICK : MOSSY, hash(y, k) % 3 == 0 ? 1 : 0);
                if (md < 0.5) put(col, TOP + mh + 1, TORCH, 5);
            }
            if (k % 2 == 0 && Math.abs(r - RING) < 1.0) {
                // перекладина над парой: от этого менгира до следующего по дуге
                double a = (Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2 / MENHIRS);
                double frac = a - Math.floor(a);
                int base = (int) Math.floor(a) % MENHIRS;
                if (base % 2 == 0 && frac > 0.05 && frac < 0.95) {
                    int lh = 6 + Math.min(hash(base, salt) % 4, hash((base + 1) % MENHIRS, salt) % 4);
                    put(col, TOP + lh + 1, MOSSY, 0);
                }
            }
            // внутренние камни-укрытия (низкие, их разбивают глыбы)
            if (ringDist(dx, dz, COVER_R, COVERS, Math.PI / COVERS) < 1.4) {
                int ch = 3 + hash(dx, dz + salt) % 2;
                for (int y = TOP + 1; y <= TOP + ch; y++) put(col, y, MOSSY, 0);
            }
            // родники: вода в мшистой оправе
            for (int i = 0; i < 4; i++) {
                int[] s = spring(i);
                double sd = Math.sqrt((dx - s[0]) * (dx - s[0]) + (dz - s[1]) * (dz - s[1]));
                if (sd < 1.6) {
                    put(col, TOP, WATER, 0);
                    put(col, TOP - 1, MOSSY, 0);
                } else if (sd < 2.6) put(col, TOP, MOSSY, 0);
            }
            // помост с алтарём
            double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ) * (dz + ALTAR_DZ));
            if (ad <= 2.5) put(col, TOP + 1, BRICK, 3);
            if (dx == 0 && dz == -ALTAR_DZ) put(col, TOP + 2, ALTAR, 0);
            // корни по краю острова
            if (r > 26 && r < rOut - 1 && Nest.noise(dx, dz, 4, 77 + salt) > 0.7) put(col, TOP + 1, LOG, 0);
            return true;
        }
    }

    // ---------------------------------------------------------------- колизей (Апалачия)

    /**
     * Колизей на вершине Апалачии - арена Короля-воина: круглая площадка, кольцо трибун с четырьмя проходами и в центре
     * ступенчатая пирамида в три яруса ({@link #TIER_R}, {@link #TIER_H}) с лестницами на все стороны. Каждую новую
     * встречу Король начинает ярусом выше; бортов нет - волна сбрасывает на ярус ниже.
     */
    static class Colosseum extends ColumnStructure {

        static final int BRICK = 31, STAIRS = 32;
        static final int R_ISLAND = 33, STANDS_IN = 25, STANDS_OUT = 31;
        private final int salt;

        Colosseum(int cx, int cz) {
            salt = hash(cx, cz) % 1000;
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? TwilightWorld.APALACHIA.lampMeta() : meta;
        }

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case BRICK: return Blocks.stonebrick;
                case STAIRS: return Blocks.stone_brick_stairs;
                default: return palette(TwilightWorld.APALACHIA, code);
            }
        }

        @Override
        protected boolean lit(Block b) {
            return isLamp(b) || b == ModBlocks.nestAltar;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            double rOut = R_ISLAND + Nest.noise(dx, dz, 6, 19 + salt) * 2;
            if (r > rOut) return false;
            islandBase(dx, dz, r, rOut, salt, col);
            int h = hash(dx + salt, dz);
            put(col, TOP, r < STANDS_IN ? BRICK : GRASS, r < STANDS_IN ? (h % 7 == 0 ? 2 : h % 5 == 0 ? 1 : 0) : 0);
            // трибуны: ступени вверх наружу, проходы по сторонам света
            if (r >= STANDS_IN && r <= STANDS_OUT && !gate(dx, dz)) {
                int lvl = Math.min(6, (int) ((r - STANDS_IN) * 1.2) + 1);
                for (int y = TOP + 1; y <= TOP + lvl; y++) put(col, y, BRICK, 0);
                if (r > STANDS_OUT - 1) for (int y = TOP + lvl + 1; y <= TOP + lvl + 2; y++) if (h % 4 != 0) put(col, y, BRICK, 2);
            }
            // пирамида: ярусы и лестницы по сторонам света
            int tier = 0;
            for (int i = 1; i < TIER_R.length; i++) if (Math.max(Math.abs(dx), Math.abs(dz)) <= TIER_R[i]) tier = i;
            if (tier > 0) for (int y = TOP + 1; y <= TOP + TIER_H[tier]; y++) put(col, y, BRICK, y == TOP + TIER_H[tier] ? 3 : 0);
            for (int i = 1; i < TIER_R.length; i++) {
                // ступени перед гранью яруса i (по одной оси, ширина 3)
                int edge = TIER_R[i] + 1, rise = TIER_H[i] - TIER_H[i - 1];
                for (int s = 0; s < rise; s++) {
                    int dist = edge + s, y = TOP + TIER_H[i] - s;
                    if (Math.abs(dz) <= 1 && Math.abs(dx) == dist) put(col, y, STAIRS, dx > 0 ? 1 : 0);
                    if (Math.abs(dx) <= 1 && Math.abs(dz) == dist) put(col, y, STAIRS, dz > 0 ? 3 : 2);
                }
            }
            // свет по краю арены
            if (Math.abs(r - (STANDS_IN - 1.5)) < 0.5 && h % 9 == 0) put(col, TOP, GLOW, 0);
            // помост с алтарём (за пирамидой, на полу)
            double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ + 6) * (dz + ALTAR_DZ + 6));
            if (ad <= 2.5) put(col, TOP + 1, BRICK, 3);
            if (dx == 0 && dz == -ALTAR_DZ - 6) put(col, TOP + 2, ALTAR, 0);
            return true;
        }
    }

    /** Ярусы пирамиды колизея: полуширина и высота над полом (0 - пол арены). */
    public static final int[] TIER_R = { 22, 11, 6 }, TIER_H = { 0, 4, 8 };

    /** Сдвиг алтаря от центра к северу: в колизее дальше (центр занят пирамидой). */
    public static int altarDz(TwilightWorld w) {
        return w == TwilightWorld.APALACHIA ? ALTAR_DZ + 6 : ALTAR_DZ;
    }

    // ---------------------------------------------------------------- храм ветров (Скайтерн)

    /**
     * Храм ветров Скайтерна - арена Повелителя ветров вместо рощи: парящий остров с мраморным стилобатом, кольцом из
     * шестнадцати высоких колонн под общим антаблементом (без крыши - бой под небом), алтарём на помосте у северной
     * стороны и четырьмя колоннами-постаментами для ветряных тотемов на разной высоте
     * ({@link com.divinerpgextended.entity.EntityWindLord#HEIGHTS}), с лестницей по внешней грани и облачной площадкой
     * сверху. Вокруг стилобата - травяная терраса, чтобы порыв не сразу скидывал с острова.
     */
    static class Temple extends ColumnStructure {

        static final int MARBLE = 20, LADDER = 21, CLOUD = 22, SLAB = 23;
        static final int R_ISLAND = 32, STYLO = 25, PERI = 21, COLS = 16, COL_H = 15;
        private final int salt;

        Temple(int cx, int cz) {
            salt = hash(cx, cz) % 1000;
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? TwilightWorld.SKYTHERN.lampMeta() : meta;
        }

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case MARBLE: return Blocks.quartz_block;
                case LADDER: return Blocks.ladder;
                case CLOUD: return ModBlocks.cloud;
                case SLAB: return Blocks.stone_slab;
                default: return palette(TwilightWorld.SKYTHERN, code);
            }
        }

        @Override
        protected boolean lit(Block b) {
            return isLamp(b) || b == ModBlocks.nestAltar;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            double rOut = R_ISLAND + Nest.noise(dx, dz, 6, 17 + salt) * 3;
            if (r > rOut) return false;
            // основание: каменный клин вниз, рваный по краю
            double t = r / rOut;
            int bottom = TOP - 3 - (int) (22 * Math.sqrt(Math.max(0, 1 - t * t)) + Nest.noise(dx, dz, 4, 29 + salt) * 6);
            for (int y = bottom; y <= TOP - 2; y++) put(col, y, hash(dx * 17 + y, dz + salt) % 100 < 12 ? DIRT : STONE, 0);
            put(col, TOP - 1, DIRT, 0);
            for (int y = TOP + 1; y <= TOP + 40; y++) put(col, y, AIR, 0);
            if (r > STYLO + 1.5) {
                put(col, TOP, GRASS, 0); // терраса
                return true;
            }
            // стилобат: мрамор, кольца узора и кайма из резного мрамора
            boolean border = r > STYLO - 0.5 || Math.abs(r - 9) < 0.5 || Math.abs(r - 17) < 0.5;
            put(col, TOP, MARBLE, border ? 1 : 0);
            if (r < 1.2 || ringDist(dx, dz, 9, 8, Math.PI / 8) < 0.6) put(col, TOP, GLOW, 0);
            if (r > STYLO - 0.5) put(col, TOP + 1, SLAB, 7); // низкая ступень по краю
            // колоннада: база, ствол из столбового мрамора, капитель, сверху общий антаблемент
            double cd = ringDist(dx, dz, PERI, COLS, 0);
            if (cd < 1.8) put(col, TOP + 1, MARBLE, 1);
            if (cd < 1.25) for (int y = TOP + 2; y < TOP + COL_H; y++) put(col, y, MARBLE, 2);
            if (cd < 1.8) put(col, TOP + COL_H, MARBLE, 1);
            if (Math.abs(r - PERI) < 1.6) {
                put(col, TOP + COL_H + 1, MARBLE, 0);
                put(col, TOP + COL_H + 2, MARBLE, ((int) Math.round(Math.atan2(dz, dx) * 20)) % 3 == 0 ? 1 : 0); // фриз
            }
            if (Math.abs(r - PERI) < 2.3) put(col, TOP + COL_H + 3, SLAB, 7); // карниз
            // помост с алтарём к северу
            double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ) * (dz + ALTAR_DZ));
            if (ad <= 2.5) put(col, TOP + 1, MARBLE, 1);
            if (dx == 0 && dz == -ALTAR_DZ) put(col, TOP + 2, ALTAR, 0);
            // постаменты тотемов: 3x3 мрамор, лестница снаружи, облачная площадка сверху
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 4 + Math.PI / 2 * i;
                int px = (int) Math.round(Math.cos(a) * com.divinerpgextended.entity.EntityWindLord.TOTEM_R);
                int pz = (int) Math.round(Math.sin(a) * com.divinerpgextended.entity.EntityWindLord.TOTEM_R);
                int h = com.divinerpgextended.entity.EntityWindLord.HEIGHTS[i];
                int sx = px > 0 ? 1 : -1;
                int ox = dx - px, oz = dz - pz;
                if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) {
                    for (int y = TOP + 1; y < TOP + h; y++) put(col, y, MARBLE, 2);
                    put(col, TOP + h, CLOUD, 0);
                } else if (ox == 2 * sx && oz == 0) {
                    // лестница к внешней грани постамента (висит на блоке ближе к центру постамента)
                    for (int y = TOP + 1; y < TOP + h; y++) put(col, y, LADDER, sx > 0 ? 5 : 4);
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- Демонический круг

    static class Circle extends ColumnStructure {

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? TwilightWorld.MORTUM.lampMeta() : meta;
        }

        @Override
        protected Block block(int code, int meta) {
            return palette(TwilightWorld.MORTUM, code);
        }

        @Override
        protected boolean lit(Block b) {
            return isLamp(b);
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
