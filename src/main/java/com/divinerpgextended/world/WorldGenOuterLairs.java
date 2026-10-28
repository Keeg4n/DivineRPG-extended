package com.divinerpgextended.world;

import java.util.Random;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.world.twilight.ColumnStructure;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Логова боссов Ада и Энда, у которых в Divine нет своего места. Место вычисляется из сида; постройка задана по
 * столбцам ({@link ColumnStructure}). Предмет вызова Divine работает только внутри логова (см.
 * {@link com.divinerpgextended.boss.LairSummons}), сам вызов делает Divine.
 * <ul>
 * <li>Обсерватория Смотрителя (Ад): купольный зал радиусом 20 в толще незеррака, пол из адского кирпича со
 * светящимися кольцами, четыре тоннеля наружу. Зов Смотрителя - в зале.</li>
 * <li>Огненная палата Короля скорчеров (Ад): зал радиусом 17, лавовый ров с четырьмя мостиками, колонны. Инфернальное
 * пламя - в палате.</li>
 * <li>Гнездовье Айрако (Энд): чаша из эндерняка на краю главного острова, обсидиановые зубцы, дорога к центру острова.
 * Рог орды - в гнездовье.</li>
 * </ul>
 */
public class WorldGenOuterLairs implements IWorldGenerator {

    public static final int WATCHER = 0, SCORCHERS = 1, AYERACO = 2;
    /** Радиус зала, высота пола. */
    public static final int[] RADIUS = { 20, 17, 18 }, FLOOR = { 56, 40, 62 };
    private static final int[] CENTER_Y = { 68, 50, 0 };
    private static final long[] SALT = { 0x5A7C4L, 0x5C0BCL, 0xA7E2AL };
    /** Гнездовье Айрако - одно на мир: дальний остров на этом расстоянии от центра Энда (долететь - на ангельской броне). */
    public static final int AYERACO_DIST = 1000;
    /** Островки-передышки по пути к гнездовью: через столько блоков, сколько их. */
    public static final int ISLET_STEP = 170, ISLETS = 5;

    /**
     * Мир логова (как в конфиге Divine); логово строится только в Аду / Энде. Настройки миров боссов есть только с
     * Divine 1.4.2 - в 1.4.1.5 их нет, там миры зашиты: Смотритель и скорчеры - Ад, орда Айрако - Энд.
     */
    public static int dim(int type) {
        return type == WATCHER ? option("theWatcherDim", -1) : type == SCORCHERS ? option("kingOfScorchersDim", -1)
            : option("ayeracoHordeDim", 1);
    }

    private static int option(String name, int fallback) {
        try {
            return ConfigurationHelper.class.getField(name).getInt(null);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Логово есть: мир этого типа - Ад (для Смотрителя и скорчеров) или Энд (для Айрако), и логова включены. */
    public static boolean enabled(int type) {
        if (Config.netherLairSpacing <= 0) return false;
        return type == AYERACO ? dim(type) == 1 : dim(type) == -1;
    }

    /** Центр логова в области (rx, rz); для Айрако область одна. */
    public static int[] center(long seed, int type, int rx, int rz) {
        Random r = new Random(seed ^ (rx * 341873128712L + rz * 132897987541L) ^ SALT[type]);
        if (type == AYERACO) {
            double a = r.nextDouble() * Math.PI * 2;
            return new int[] { (int) (Math.cos(a) * AYERACO_DIST), FLOOR[type], (int) (Math.sin(a) * AYERACO_DIST) };
        }
        int g = Config.netherLairSpacing, margin = Math.min(g / 3, RADIUS[type] + 40);
        return new int[] { rx * g + margin + r.nextInt(Math.max(1, g - 2 * margin)), FLOOR[type],
            rz * g + margin + r.nextInt(Math.max(1, g - 2 * margin)) };
    }

    public static int[] nearest(long seed, int type, double x, double z) {
        if (!enabled(type)) return null;
        if (type == AYERACO) return center(seed, type, 0, 0);
        int g = Config.netherLairSpacing;
        int rx = (int) Math.floor(x / g), rz = (int) Math.floor(z / g);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] c = center(seed, type, rx + i, rz + k);
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** Точка внутри логова этого типа (там работает предмет вызова). */
    public static boolean inside(World w, int type, double x, double y, double z) {
        if (w.provider.dimensionId != dim(type)) return false;
        int[] c = nearest(w.getSeed(), type, x, z);
        if (c == null) return false;
        double dx = x - c[0] - 0.5, dz = z - c[2] - 0.5, R = RADIUS[type] - 2;
        return dx * dx + dz * dz <= R * R && y >= FLOOR[type] - 2 && y <= FLOOR[type] + 24;
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8;
        for (int type = 0; type < 3; type++) {
            if (!enabled(type) || world.provider.dimensionId != dim(type)) continue;
            int[] c = nearest(world.getSeed(), type, x0 + 8, z0 + 8);
            int reach = RADIUS[type] + (type == AYERACO ? AYERACO_DIST : 26) + 16;
            if (c == null || Math.abs(c[0] - (x0 + 8)) > reach || Math.abs(c[2] - (z0 + 8)) > reach) continue;
            ColumnStructure s = shape(type, c);
            s.buildSlice(world, c[0], c[2], x0, z0);
            if (type != AYERACO) s.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, FLOOR[type] + 1, FLOOR[type] + 22);
        }
    }

    public static ColumnStructure shape(int type, int[] c) {
        return type == AYERACO ? new Aerie(c[0], c[2]) : new Hall(type);
    }

    /** С какого расстояния от центра режется тоннель (стена купола на высоте тоннеля - от ~12.6 у палаты, ~14.9 у обсерватории). */
    static final double TUNNEL_FROM = 12.5;

    private static final int BRICK = 1, GLOW = 2, LAVA = 3, OBSIDIAN = 4, END_STONE = 5, QUARTZ = 6, FENCE = 7, CLAY = 8;

    private static Block block(int code) {
        switch (code) {
            case BRICK: return Blocks.nether_brick;
            case GLOW: return net.divinerpg.utils.blocks.VanillaBlocks.lamp1;
            case LAVA: return Blocks.lava;
            case OBSIDIAN: return Blocks.obsidian;
            case END_STONE: return Blocks.end_stone;
            case QUARTZ: return Blocks.quartz_block;
            case FENCE: return Blocks.nether_brick_fence;
            case CLAY: return Blocks.stained_hardened_clay;
            default: return Blocks.air;
        }
    }

    private static double ringDist(double dx, double dz, double ring, int count, double phase) {
        double a = Math.atan2(dz, dx) - phase, step = Math.PI * 2 / count;
        double k = Math.round(a / step);
        double ca = k * step + phase;
        return Math.sqrt((dx - Math.cos(ca) * ring) * (dx - Math.cos(ca) * ring) + (dz - Math.sin(ca) * ring) * (dz - Math.sin(ca) * ring));
    }

    // ---------------------------------------------------------------- залы Ада

    static class Hall extends ColumnStructure {

        private final int type, R, cy, floor;

        Hall(int type) {
            this.type = type;
            R = RADIUS[type];
            cy = CENTER_Y[type];
            floor = FLOOR[type];
        }

        @Override
        protected Block block(int code, int meta) {
            if (code == GLOW && type == WATCHER) return net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
            return WorldGenOuterLairs.block(code);
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? (type == WATCHER ? 6 : 2) : meta;
        }

        @Override
        protected boolean lit(Block b) {
            return b == net.divinerpg.utils.blocks.VanillaBlocks.lamp1 || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp2 || b == Blocks.lava;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            boolean tunnel = (Math.abs(dx) <= 1 && Math.abs(dz) <= R + 26) || (Math.abs(dz) <= 1 && Math.abs(dx) <= R + 26);
            if (r > R + 1 && !tunnel) return false;
            if (r <= R + 1) {
                for (int y = cy - R - 1; y <= cy + R + 1; y++) {
                    double d = Math.sqrt(r * r + (y - cy) * (y - cy));
                    if (y < floor && d <= R) put(col, y, BRICK, 0); // под полом сплошь
                    else if (d < R - 1.5) put(col, y, AIR, 0);
                    else if (d <= R + 0.5) put(col, y, type == WATCHER && y > floor + 10 && (dx + dz & 3) == 0 ? QUARTZ : BRICK, 0);
                }
                if (r < R - 1) put(col, floor, ((int) r) % 5 == 0 ? GLOW : BRICK, 0);
                if (type == WATCHER) {
                    // кольцо колонн до свода и световое око в центре пола
                    if (ringDist(dx, dz, 13, 8, Math.PI / 8) < 1.2) {
                        for (int y = floor + 1; y < cy + R; y++) {
                            double d = Math.sqrt(r * r + (y - cy) * (y - cy));
                            if (d < R - 1.5) put(col, y, y % 6 == 0 ? GLOW : QUARTZ, 0);
                        }
                    }
                    if (r < 2.5) put(col, floor, QUARTZ, 0);
                    if (r < 1) put(col, floor, GLOW, 0);
                } else {
                    // лавовый ров с мостиками на четыре стороны, колонны с пламенем
                    boolean bridge = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
                    if (r >= 10 && r <= 12 && !bridge) {
                        put(col, floor, LAVA, 0);
                        put(col, floor - 1, BRICK, 0);
                    }
                    if (r >= 10 && r <= 12 && bridge) {
                        if (Math.abs(dx) == 1 && Math.abs(dz) > 1 || Math.abs(dz) == 1 && Math.abs(dx) > 1) put(col, floor + 1, FENCE, 0);
                    }
                    if (ringDist(dx, dz, 7, 6, 0) < 1.1) for (int y = floor + 1; y <= floor + 9; y++) put(col, y, BRICK, 0);
                    if (ringDist(dx, dz, 7, 6, 0) < 0.6) put(col, floor + 10, GLOW, 0);
                }
            }
            // тоннели наружу (3 x 3, пол из кирпича). Купол сферический: на высоте тоннеля его стена ближе к центру, чем
            // край зала, - проход режем сквозь всю её толщу (с TUNNEL_FROM; ров и мостики палаты ближе к центру не трогаем)
            if (tunnel && r > TUNNEL_FROM) {
                for (int y = floor + 1; y <= floor + 3; y++) put(col, y, AIR, 0);
                if (r >= R - 2.5) {
                    put(col, floor, BRICK, 0);
                    put(col, floor + 4, BRICK, 0);
                    if ((Math.abs(dx) == 1 || Math.abs(dz) == 1) && ((int) r) % 6 == 0) put(col, floor + 3, GLOW, 0);
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- гнездовье Айрако

    static class Aerie extends ColumnStructure {

        private final double toX, toZ;

        /** Центр гнездовья (cx, cz): дорога идёт от него к центру Энда. */
        Aerie(int cx, int cz) {
            double len = Math.sqrt(cx * cx + cz * cz);
            toX = len < 1 ? 0 : -cx / len;
            toZ = len < 1 ? 0 : -cz / len;
        }

        @Override
        protected Block block(int code, int meta) {
            return WorldGenOuterLairs.block(code);
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? 8 : meta;
        }

        @Override
        protected boolean lit(Block b) {
            return b == net.divinerpg.utils.blocks.VanillaBlocks.lamp1;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            int R = RADIUS[AYERACO], top = FLOOR[AYERACO];
            double r = Math.sqrt(dx * dx + dz * dz);
            // островки-передышки по пути к центру Энда: чтобы на ангельской броне перевести дух (аркана)
            double along = dx * toX + dz * toZ, across = Math.abs(dx * toZ - dz * toX);
            for (int k = 1; k <= ISLETS; k++) {
                double at = k * ISLET_STEP, ir = Math.sqrt((along - at) * (along - at) + across * across);
                if (ir > 4.5) continue;
                int depth = 1 + (int) (4 * Math.sqrt(Math.max(0, 1 - (ir / 4.5) * (ir / 4.5))));
                for (int y = top - depth; y <= top; y++) put(col, y, END_STONE, 0);
                if (ir < 0.8) {
                    put(col, top, GLOW, 0);
                    for (int y = top + 1; y <= top + 3; y++) put(col, y, OBSIDIAN, 0); // веха: видно издалека
                    put(col, top + 4, GLOW, 0);
                }
                return true;
            }
            // скалы со столпами орды: пять цветов вокруг острова, выше него
            for (com.divinerpgextended.boss.AyeracoHorde.Hue h : com.divinerpgextended.boss.AyeracoHorde.Hue.values()) {
                int[] pa = com.divinerpgextended.boss.AyeracoHorde.pillarAt(0, top, 0, h);
                double pr = Math.sqrt((dx - pa[0]) * (dx - pa[0]) + (dz - pa[2]) * (dz - pa[2]));
                if (pr > 3.5) continue;
                int rockTop = pa[1] - 1, depth = 1 + (int) (3 * Math.sqrt(Math.max(0, 1 - (pr / 3.5) * (pr / 3.5))));
                for (int y = rockTop - depth; y <= rockTop; y++) put(col, y, END_STONE, 0);
                if (pr < 0.5) {
                    for (int y = pa[1]; y < pa[1] + com.divinerpgextended.boss.AyeracoHorde.PILLAR_H; y++) put(col, y, CLAY, h.clay());
                    put(col, pa[1] + com.divinerpgextended.boss.AyeracoHorde.PILLAR_H, GLOW, 0);
                }
                return true;
            }
            // сам остров гнездовья - висит над бездной
            if (r > R + 0.5) return false;
            int bottom = top - 2 - (int) (14 * Math.sqrt(Math.max(0, 1 - (r / R) * (r / R))));
            for (int y = bottom; y < top; y++) put(col, y, END_STONE, 0);
            put(col, top, ((int) r) % 4 == 0 ? OBSIDIAN : END_STONE, 0);
            for (int y = top + 1; y <= top + 20; y++) put(col, y, AIR, 0);
            if (r < 1.5) put(col, top, GLOW, 0);
            if (ringDist(dx, dz, 9, 6, 0) < 0.5) put(col, top, GLOW, 0);
            // обсидиановые зубцы по краю
            if (r >= R - 1.5 && ringDist(dx, dz, R - 1, 16, 0) < 1.2) {
                int h = 3 + hash(dx, dz) % 4;
                for (int y = top + 1; y <= top + h; y++) put(col, y, OBSIDIAN, 0);
            }
            return true;
        }
    }
}
