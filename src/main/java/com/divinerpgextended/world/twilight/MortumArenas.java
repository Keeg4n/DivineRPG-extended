package com.divinerpgextended.world.twilight;

import java.util.ArrayList;
import java.util.List;

import net.divinerpg.utils.items.TwilightItemsOther;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.world.World;

/**
 * Арены боссов Мортума: у каждого кристалла - своя, вызвать босса можно только на ней. В каждом квадрате Мортума
 * ({@link WorldGenNests#spacing}) одна арена, тип из сида и координат квадрата так, что соседние квадраты всегда разные.
 * Все - залы-купола в пещере на высоте {@link WorldGenNests#MORTUM_TOP} с четырьмя тоннелями наружу.
 * <ul>
 * <li>Демонический круг - Демон сумерек (кольца, обелиски).</li>
 * <li>Зал близнецов - Денсос и Рейвор: светлая и тёмная половины, по помосту в каждой.</li>
 * <li>Зеркальный зал - Карот: квадратный зал со стенами-зеркалами.</li>
 * <li>Галерея - Вечный лучник: длинный зал, лучник на помосте в конце, ряды колонн-укрытий.</li>
 * <li>Погост - Пожиратель душ: могилы рядами, из них он поднимает души.</li>
 * <li>Костяная яма - Вамачерон: просевший пол и кольцо костяных столбов, о которые он разбивается в рывке.</li>
 * </ul>
 */
public final class MortumArenas {

    public enum Type {
        DEMON(WorldGenNests.CIRCLE_ZONE, WorldGenNests.CIRCLE_R - 2), TWINS(21, 22), KAROT(13, 13), ARCHER(25, 26), FIEND(21, 22), VAMACHERON(23, 23);

        /** Внутри (по горизонтали от центра) кристалл работает; босс не уходит дальше leash. */
        public final int zone, leash;

        Type(int zone, int leash) {
            this.zone = zone;
            this.leash = leash;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    private MortumArenas() {}

    /** Тип арены в квадрате (rx, rz): соседние (и по диагонали) квадраты всегда разные. */
    public static Type type(long seed, int rx, int rz) {
        int n = Type.values().length;
        int s = (int) ((seed ^ seed >>> 29) & 0x7fffffff) % n;
        return Type.values()[Math.floorMod(rx * 2 + rz * 3 + s, n)];
    }

    /** Ближайшая арена: {x, пол, z, тип}. */
    public static int[] nearest(World w, double x, double z) {
        int[] c = WorldGenNests.nearest(w.getSeed(), w.provider.dimensionId, x, z);
        return c;
    }

    public static Type typeAt(World w, int[] c) {
        int g = WorldGenNests.spacing(w.provider.dimensionId);
        return type(w.getSeed(), Math.floorDiv(c[0], g), Math.floorDiv(c[2], g));
    }

    /** Ближайшая арена нужного типа (поиск по квадратам вокруг, до radius квадратов); null - нет. */
    public static int[] nearestOf(World w, Type t, double x, double z, int radius) {
        int g = WorldGenNests.spacing(w.provider.dimensionId);
        if (g <= 0) return null;
        int rx = (int) Math.floor(x / g), rz = (int) Math.floor(z / g);
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int i = -radius; i <= radius; i++) for (int k = -radius; k <= radius; k++) {
            if (type(w.getSeed(), rx + i, rz + k) != t) continue;
            int[] c = WorldGenNests.center(w.getSeed(), w.provider.dimensionId, rx + i, rz + k);
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    /** Точка на арене этого типа (там работает его кристалл). */
    public static boolean inArena(World w, Type t, double x, double y, double z) {
        if (TwilightWorld.of(w.provider.dimensionId) != TwilightWorld.MORTUM) return false;
        int[] c = nearest(w, x, z);
        if (c == null || typeAt(w, c) != t) return false;
        double dx = x - c[0] - 0.5, dz = z - c[2] - 0.5;
        boolean in = t == Type.KAROT || t == Type.ARCHER ? Math.abs(dx) <= halfX(t) && Math.abs(dz) <= halfZ(t) : dx * dx + dz * dz <= t.zone * t.zone;
        return in && Math.abs(y - floorY(t) - 1) <= 8;
    }

    static int halfX(Type t) {
        return t == Type.ARCHER ? Gallery.HX - 1 : Mirror.H - 1;
    }

    static int halfZ(Type t) {
        return t == Type.ARCHER ? Gallery.HZ - 1 : Mirror.H - 1;
    }

    /** Пол арены (у ямы Вамачерона - ниже). */
    public static int floorY(Type t) {
        return t == Type.VAMACHERON ? WorldGenNests.MORTUM_TOP - Pit.SINK : WorldGenNests.MORTUM_TOP;
    }

    /** Чей кристалл (null - не кристалл босса Мортума). */
    public static Type ofCrystal(Item i) {
        if (i == TwilightItemsOther.twilightDemonCrystal) return Type.DEMON;
        if (i == TwilightItemsOther.densosCrystal || i == TwilightItemsOther.reyvorCrystal) return Type.TWINS;
        if (i == TwilightItemsOther.karotCrystal) return Type.KAROT;
        if (i == TwilightItemsOther.eternalArcherCrystal) return Type.ARCHER;
        if (i == TwilightItemsOther.soulFiendCrystal) return Type.FIEND;
        if (i == TwilightItemsOther.vamacheronCrystal) return Type.VAMACHERON;
        return null;
    }

    /** Где стоит босс арены (смещение от центра): лучник - на помосте в конце галереи; null - где вызвали. */
    public static int[] bossSpot(Type t) {
        return t == Type.ARCHER ? new int[] { 0, Gallery.DAIS_H, -(Gallery.HZ - 3) } : null;
    }

    /** Могилы Погоста (смещения от центра): из них Пожиратель душ поднимает души. */
    public static List<int[]> graves() {
        List<int[]> l = new ArrayList<int[]>();
        for (int dx = -18; dx <= 18; dx++) for (int dz = -18; dz <= 18; dz++) if (Graveyard.grave(dx, dz)) l.add(new int[] { dx, dz });
        return l;
    }

    public static ColumnStructure shape(Type t) {
        switch (t) {
            case TWINS: return new Twins();
            case KAROT: return new Mirror();
            case ARCHER: return new Gallery();
            case FIEND: return new Graveyard();
            case VAMACHERON: return new Pit();
            default: return new WorldGenNests.Circle();
        }
    }

    // ---------------------------------------------------------------- общее для залов

    static final int BRICK = 50, CHISEL = 51, BONE = 52, MIRROR = 53, OBSIDIAN = 54, SLAB = 55;

    /** Зал-купол в пещере: пол на TOP, купол радиусом hallR, 4 тоннеля наружу (3 x 4) длиной TUNNEL. */
    abstract static class Hall extends ColumnStructure {

        static final int TOP = WorldGenNests.MORTUM_TOP, TUNNEL = 12;

        @Override
        protected int metaOf(int code, int meta) {
            return code == WorldGenNests.GLOW ? TwilightWorld.MORTUM.lampMeta() : meta;
        }

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case BRICK: case CHISEL: return Blocks.stonebrick;
                case BONE: return Blocks.quartz_block;
                case MIRROR: return Blocks.glass;
                case OBSIDIAN: return Blocks.obsidian;
                case SLAB: return Blocks.stone_slab;
                default: return WorldGenNests.palette(TwilightWorld.MORTUM, code);
            }
        }

        @Override
        protected boolean lit(Block b) {
            return WorldGenNests.isLamp(b);
        }

        /** Радиус зала (купол), высота купола, высота свода над полом в центре. */
        abstract int hallR();

        int domeH() {
            return 18;
        }

        /** Пол и убранство внутри зала (воздух над полом уже вырезан). */
        abstract void inside(int dx, int dz, double r, int[] col);

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int R = hallR();
            boolean tunnel = (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) && r <= R + TUNNEL;
            if (r > R + 0.5 && !tunnel) return false;
            if (r > R + 0.5) { // тоннель: пол, 4 воздуха, свод
                put(col, TOP, WorldGenNests.STONE, 0);
                for (int y = TOP + 1; y <= TOP + 4; y++) put(col, y, AIR, 0);
                put(col, TOP + 5, WorldGenNests.STONE, 0);
                return true;
            }
            int dome = (int) (domeH() * Math.sqrt(Math.max(0, 1 - (r / (R + 1)) * (r / (R + 1))))) + 5;
            for (int y = TOP + 1; y <= TOP + dome; y++) put(col, y, AIR, 0);
            put(col, TOP + dome + 1, WorldGenNests.STONE, 0); // свод
            for (int y = TOP - 4; y <= TOP; y++) put(col, y, WorldGenNests.STONE, 0); // фундамент
            inside(dx, dz, r, col);
            return true;
        }
    }

    // ---------------------------------------------------------------- Денсос и Рейвор

    /** Зал близнецов: запад - светлый (камень мира, светильники), восток - тёмный (обсидиан); по помосту в каждой половине. */
    static class Twins extends Hall {

        static final int R = 24, DAIS_X = 12;

        @Override
        int hallR() {
            return R + 2;
        }

        @Override
        void inside(int dx, int dz, double r, int[] col) {
            boolean light = dx < 0;
            if (r > R) {
                put(col, TOP + 1, BRICK, 0); // бортик
                return;
            }
            put(col, TOP, light ? (hash(dx, dz) % 5 == 0 ? WorldGenNests.GLOW : WorldGenNests.GEM) : (hash(dx, dz) % 3 == 0 ? OBSIDIAN : WorldGenNests.STONE), 0);
            // граница половин: низкая стена с проходами
            if (dx == 0 && Math.abs(dz) > 3 && Math.abs(dz) % 6 != 0) put(col, TOP + 1, CHISEL, 3);
            // помосты: круг радиусом 3 на высоту 1
            double d = Math.sqrt((Math.abs(dx) - DAIS_X) * (Math.abs(dx) - DAIS_X) + dz * dz);
            if (d < 3.5) put(col, TOP + 1, light ? WorldGenNests.GEM : OBSIDIAN, 0);
            if (d < 0.5) put(col, TOP + 2, light ? WorldGenNests.GLOW : OBSIDIAN, 0);
        }
    }

    // ---------------------------------------------------------------- Карот

    /** Зеркальный зал: квадрат, стены из зеркал (стекло) между колоннами, пол шахматкой. */
    static class Mirror extends Hall {

        static final int H = 14, WALL = 7;

        @Override
        int hallR() {
            return 21; // квадрат H целиком внутри купола
        }

        @Override
        void inside(int dx, int dz, double r, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            if (ax > H || az > H) { // вне квадрата - камень до свода зала (зал квадратный), кроме тоннелей
                for (int y = TOP + 1; y <= TOP + WALL + 1; y++) put(col, y, WorldGenNests.STONE, 0);
                if (ax <= 1 || az <= 1) for (int y = TOP + 1; y <= TOP + 4; y++) put(col, y, AIR, 0);
                return;
            }
            put(col, TOP, (dx + dz & 1) == 0 ? WorldGenNests.GEM : WorldGenNests.STONE, 0);
            if (ax == H || az == H) {
                boolean pillar = (ax == H ? az : ax) % 4 == 0;
                for (int y = TOP + 1; y <= TOP + WALL; y++) put(col, y, pillar ? CHISEL : MIRROR, pillar ? 3 : 0);
                if (pillar) put(col, TOP + WALL + 1, WorldGenNests.GLOW, 0);
                // проходы к тоннелям
                if ((ax == H && az <= 1) || (az == H && ax <= 1)) for (int y = TOP + 1; y <= TOP + 4; y++) put(col, y, AIR, 0);
            }
            if (ax == 7 && az == 7) put(col, TOP, WorldGenNests.GLOW, 0);
        }
    }

    // ---------------------------------------------------------------- Вечный лучник

    /** Галерея: прямоугольник HX x HZ (вдоль Z), лучник на помосте у северного торца, колонны-укрытия рядами. */
    static class Gallery extends Hall {

        static final int HX = 11, HZ = 27, DAIS_H = 3, COL_H = 4;

        @Override
        int hallR() {
            return 30;
        }

        @Override
        int domeH() {
            return 14;
        }

        /** Колонна-укрытие 2 x 2 (из резного камня - его рушат залпы). */
        static boolean cover(int dx, int dz) {
            int[] rows = { -14, -7, 0, 7, 14 };
            for (int i = 0; i < rows.length; i++) {
                int sh = i % 2 == 0 ? 0 : 4; // ряды вразбежку
                for (int cx = -8 + sh; cx <= 8; cx += 8) if (dx >= cx && dx <= cx + 1 && dz >= rows[i] && dz <= rows[i] + 1) return true;
            }
            return false;
        }

        @Override
        void inside(int dx, int dz, double r, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            if (ax > HX || az > HZ) {
                for (int y = TOP + 1; y <= TOP + 8; y++) put(col, y, WorldGenNests.STONE, 0);
                if (az <= 1 || ax <= 1) for (int y = TOP + 1; y <= TOP + 4; y++) put(col, y, AIR, 0); // тоннели
                return;
            }
            put(col, TOP, az % 6 == 0 && ax == HX - 1 ? WorldGenNests.GLOW : BRICK, 0);
            if (ax == HX) for (int y = TOP + 1; y <= TOP + 3; y++) put(col, y, BRICK, 0);
            if (ax == HX && az % 6 == 3) put(col, TOP + 3, WorldGenNests.GLOW, 0);
            // помост лучника у северного торца
            if (dz <= -(HZ - 5)) {
                for (int y = TOP + 1; y <= TOP + DAIS_H; y++) put(col, y, dz == -(HZ - 5) && ax <= 2 ? SLAB : BRICK, 0);
                if (dz == -(HZ - 5) && ax <= 2) for (int y = TOP + 1; y < TOP + DAIS_H; y++) put(col, y, BRICK, 0);
            }
            if (cover(dx, dz)) for (int y = TOP + 1; y <= TOP + COL_H; y++) put(col, y, CHISEL, 3);
        }
    }

    // ---------------------------------------------------------------- Пожиратель душ

    /** Погост: земля Мортума, ряды могил (надгробие + холмик), сухие деревья, тусклый свет. */
    static class Graveyard extends Hall {

        static final int R = 24;

        @Override
        int hallR() {
            return R + 2;
        }

        /** Холмик могилы (сюда встаёт поднятая душа). */
        static boolean grave(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            return r > 6 && r < 19 && Math.floorMod(dx, 5) == 0 && Math.floorMod(dz, 4) == 1;
        }

        @Override
        void inside(int dx, int dz, double r, int[] col) {
            if (r > R) {
                put(col, TOP + 1, WorldGenNests.STONE, 0);
                if (hash(dx, dz) % 2 == 0) put(col, TOP + 2, WorldGenNests.STONE, 0);
                return;
            }
            put(col, TOP - 1, WorldGenNests.DIRT, 0);
            put(col, TOP, WorldGenNests.GRASS, 0);
            if (grave(dx, dz)) put(col, TOP, WorldGenNests.DIRT, 0);
            if (grave(dx, dz - 1)) { // надгробие к северу от холмика
                put(col, TOP + 1, CHISEL, 3);
                put(col, TOP + 2, SLAB, 5);
            }
            if (r < 2.5) put(col, TOP, WorldGenNests.GEM, 0);
            if (r < 0.5) put(col, TOP + 1, WorldGenNests.GLOW, 0);
            // сухие деревья
            if (WorldGenNests.ringDist(dx, dz, 21, 5, 0.3) < 0.6) for (int y = TOP + 1; y <= TOP + 5; y++) put(col, y, WorldGenNests.LOG, 0);
        }
    }

    // ---------------------------------------------------------------- Вамачерон

    /** Костяная яма: пол просел на SINK, к краю - склоны; кольцо костяных столбов 2 x 2. */
    static class Pit extends Hall {

        static final int R = 26, SINK = 3, RING = 15, PILLARS = 8, PILLAR_H = 7;

        @Override
        int hallR() {
            return R + 2;
        }

        static boolean pillar(int dx, int dz) {
            return WorldGenNests.ringDist(dx + 0.5, dz + 0.5, RING, PILLARS, Math.PI / PILLARS) < 1.1;
        }

        @Override
        void inside(int dx, int dz, double r, int[] col) {
            // яма: глубже к центру, по краю на уровне пола
            int sink = r > R - 3 ? Math.max(0, (int) (SINK * (R - r) / 3)) : SINK;
            int floor = TOP - sink;
            for (int y = floor + 1; y <= TOP; y++) put(col, y, AIR, 0);
            put(col, floor, hash(dx, dz) % 7 == 0 ? BONE : WorldGenNests.STONE, hash(dx, dz) % 7 == 0 ? 2 : 0);
            if (r > R) put(col, TOP + 1, BONE, 2);
            if (pillar(dx, dz)) for (int y = floor + 1; y <= floor + PILLAR_H; y++) put(col, y, BONE, 2);
            if (WorldGenNests.ringDist(dx, dz, RING, PILLARS, 0) < 0.5) put(col, floor, WorldGenNests.GLOW, 0);
        }
    }
}
