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
            case GLOW: return Blocks.glowstone;
            case ALTAR: return ModBlocks.nestAltar;
            case ORE: return w.ore();
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
        protected Block block(int code, int meta) {
            if (code == WATER) return Blocks.water;
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
            return b == Blocks.glowstone || b == ModBlocks.nestAltar;
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
