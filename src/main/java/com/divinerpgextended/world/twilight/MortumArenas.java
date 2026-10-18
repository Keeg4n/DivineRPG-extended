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
 * Все - отдельные постройки на высоте {@link WorldGenNests#MORTUM_TOP} на своём фундаменте до земли, с расчищенным
 * местом вокруг и четырьмя проходами, если стоят в толще камня ({@link Building}).
 * <ul>
 * <li>Замок мага - Демон сумерек: стены, угловые башни, шпиль мага, во дворе ритуальный круг с обелисками.</li>
 * <li>Зал близнецов - Денсос и Рейвор: крепость из светлой и тёмной половин, разлом с мостами, помост брата в каждой.</li>
 * <li>Зеркальный зал - Карот: закрытое здание с контрфорсами, внутри стены из чёрных зеркал.</li>
 * <li>Галерея - Вечный лучник: аркада под двускатной кровлей, трон лучника на помосте, колонны-укрытия.</li>
 * <li>Погост - Пожиратель душ: кладбище за оградой, дорожки, могилы рядами, мавзолей в центре.</li>
 * <li>Костяная яма - Вамачерон: амфитеатр с ямой, трибунами и костяными столбами под грудной клеткой гиганта.</li>
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
        if (t == Type.DEMON) return Spire.TOP; // площадка на вершине утёса
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
            default: return new Spire();
        }
    }

    // ---------------------------------------------------------------- общее для построек

    static final int BRICK = 50, CHISEL = 51, BONE = 52, MIRROR = 53, OBSIDIAN = 54, SLAB = 55, MOSSY = 56, CRACKED = 57, PILLAR = 58, FENCE = 59,
        GRAVEL = 60, SOUL = 61, BONE_CHISEL = 62, SLAB_TOP = 63, NETHER = 64, QSLAB = 65, BARS = 66;
    /** Блоки Divine (Аркана и обычный мир): кирпичи, камни, лампы, шахматка, растения Мортума. */
    static final int DDBRICK = 70, DBRICK = 71, ABRICK = 72, ASTONE = 73, ATILE = 74, SSTONE = 75, DLAMP = 76, MILK = 77, DARK = 78, CHECKER = 79,
        MINI = 80, DIVBRICK = 81, VLAMP = 82, MPLANK = 83, BRAMBLE = 84, EYEPLANT = 85, GLOWBONE = 86;

    /**
     * Постройка арены в пещере Мортума: стоит на своём фундаменте до земли ({@link ColumnStructure#FOUNDATION}), над ней
     * расчищено место (без каменного купола - Мортум почти весь из открытых пещер, купол висел бы зонтом), к ней ведут
     * четыре прохода через скалу, если она в толще камня.
     */
    abstract static class Building extends ColumnStructure {

        static final int TOP = WorldGenNests.MORTUM_TOP, TUNNEL = 12;

        @Override
        protected int metaOf(int code, int meta) {
            int sk = skin(code);
            if (sk != code) return sk == WorldGenNests.GLOW ? TwilightWorld.MORTUM.lampMeta() : meta(sk); // своя мета у подменённого
            return code == WorldGenNests.GLOW ? TwilightWorld.MORTUM.lampMeta() : meta;
        }

        /** Кожа постройки: подменить код материала блоком Divine (у каждой арены своя палитра). */
        int skin(int code) {
            return code;
        }

        /** Фундамент - земля Мортума: арена стоит на холме, а не на сером столбе. */
        @Override
        protected Block foundation() {
            return WorldGenNests.palette(TwilightWorld.MORTUM, foundationCode());
        }

        /** Чем засыпать под постройкой: земля (утёсу - камень). */
        int foundationCode() {
            return WorldGenNests.DIRT;
        }

        @Override
        protected Block block(int code, int meta) {
            switch (skin(code)) {
                case DDBRICK: return net.divinerpg.utils.blocks.VetheaBlocks.blackKarosBricks;
                case DBRICK: return Blocks.stonebrick;
                case ABRICK: return Blocks.nether_brick;
                case ASTONE: return Blocks.stonebrick;
                case ATILE: return net.divinerpg.utils.blocks.VetheaBlocks.darkEverstone;
                case SSTONE: return Blocks.obsidian;
                case DLAMP: return Blocks.glowstone;
                case MILK: return Blocks.stonebrick;
                case DARK: return net.divinerpg.utils.blocks.VetheaBlocks.darkEverstone;
                case CHECKER: return Blocks.nether_brick;
                case MINI: return Blocks.stonebrick;
                case DIVBRICK: return Blocks.nether_brick;
                case VLAMP: return Blocks.glowstone;
                case MPLANK: return net.divinerpg.utils.blocks.TwilightBlocks.mortumPlanks;
                case BRAMBLE: return net.divinerpg.utils.blocks.TwilightBlocks.demonBrambles;
                case EYEPLANT: return net.divinerpg.utils.blocks.TwilightBlocks.eyePlant;
                case GLOWBONE: return net.divinerpg.utils.blocks.TwilightBlocks.purpleGlowbone;
                case BRICK: case CHISEL: case MOSSY: case CRACKED: return Blocks.stonebrick;
                case BONE: case PILLAR: case BONE_CHISEL: return Blocks.quartz_block;
                case MIRROR: return Blocks.stained_glass;
                case OBSIDIAN: return Blocks.obsidian;
                case SLAB: case SLAB_TOP: return Blocks.stone_slab;
                case QSLAB: return Blocks.stone_slab;
                case FENCE: return Blocks.nether_brick_fence;
                case GRAVEL: return Blocks.gravel;
                case SOUL: return Blocks.soul_sand;
                case NETHER: return Blocks.nether_brick;
                case BARS: return Blocks.iron_bars;
                default: return WorldGenNests.palette(TwilightWorld.MORTUM, code);
            }
        }

        /** Мета по коду: резной/мшистый/треснувший кирпич, кварцевый столб, чёрное стекло, плиты. */
        static int meta(int code) {
            switch (code) {
                case DDBRICK: case DBRICK: case ABRICK: case ASTONE: case ATILE: case SSTONE: case DLAMP: case MILK: case DARK: case CHECKER: case MINI:
                case DIVBRICK: case VLAMP: case MPLANK: case BRAMBLE: case EYEPLANT: case GLOWBONE: return 0;
                case CHISEL: return 3;
                case MOSSY: return 1;
                case CRACKED: return 2;
                case PILLAR: return 2;
                case BONE_CHISEL: return 1;
                case MIRROR: return 15;
                case SLAB: return 5;
                case SLAB_TOP: return 13;
                case QSLAB: return 7;
                default: return 0;
            }
        }

        /** Положить код с его метой. */
        static void set(int[] col, int y, int code) {
            put(col, y, code, meta(code));
        }

        @Override
        protected boolean lit(Block b) {
            return WorldGenNests.isLamp(b) || b.getLightValue() > 0;
        }

        /** Статуя босса арены (Divine) лицом к югу. */
        static void statue(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, Block statue) {
            place(w, cx, cz, x0, z0, dx, y, dz, statue, 0);
        }

        /** Входит ли столбец в постройку (без проходов). */
        abstract boolean footprint(int dx, int dz);

        /** Сколько воздуха расчистить над полом (выше - как было). */
        abstract int clear(int dx, int dz);

        /** Сама постройка в столбце: пол на TOP, всё выше. */
        abstract void build(int dx, int dz, int[] col);

        /** Пол: старый кирпич с мхом и трещинами. */
        static int worn(int dx, int dz) {
            int h = hash(dx, dz) % 9;
            return h == 0 ? MOSSY : h == 1 ? CRACKED : BRICK;
        }

        /** Нужны ли склоны вокруг (утёсу - нет: его край и так сходит к земле). */
        boolean slopes() {
            return true;
        }

        /** Насколько столбец за краем постройки (по её форме); <= 0 - внутри. */
        double outside(int dx, int dz) {
            return Math.sqrt(dx * dx + dz * dz) - reach();
        }

        static final int SLOPE = 10;

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            boolean in = footprint(dx, dz);
            int ax = Math.abs(dx), az = Math.abs(dz), far = Math.max(ax, az);
            boolean tunnel = !in && slopes() && (ax <= 1 || az <= 1) && far <= reach() + TUNNEL;
            double out = outside(dx, dz);
            if (!in && !tunnel && out > 0 && out <= SLOPE && slopes()) { // склон вокруг: насыпь вниз до земли, откос вверх
                put(col, TOP - 1 - (int) (out * 1.3), FOUNDATION, 0);
                for (int y = TOP + 1 + (int) (out * 1.5); y <= TOP + 30; y++) put(col, y, AIR, 0);
                return true;
            }
            if (!in && !tunnel) return false;
            if (!in) { // проход к постройке: только вырезать, пол - камень
                put(col, TOP - 1, FOUNDATION, 0);
                put(col, TOP, WorldGenNests.STONE, 0);
                for (int y = TOP + 1; y <= TOP + 4; y++) put(col, y, AIR, 0);
                return true;
            }
            put(col, TOP - 5, FOUNDATION, 0);
            for (int y = TOP - 4; y < TOP; y++) put(col, y, WorldGenNests.DIRT, 0);
            int c = clear(dx, dz);
            for (int y = TOP + 1; y <= TOP + c; y++) put(col, y, AIR, 0);
            build(dx, dz, col);
            return true;
        }

        /** Докуда от центра доходит постройка (для проходов). */
        abstract int reach();
    }

    // ---------------------------------------------------------------- Денсос и Рейвор

    /**
     * Зал близнецов: круглая площадка-крепость с зубчатой стеной. Запад - светлый (кварц, светильники, кварцевые столбы),
     * восток - тёмный (обсидиан, камень Мортума, обсидиановые шпили). Между половинами - разлом с душевным песком на дне и
     * тремя мостами. В каждой половине помост с шпилем - место брата.
     */
    static class Twins extends Building {

        static final int R = 24, DAIS_X = 12;

        /** Гамма Мортума: светлая половина - камень и блок Мортума с лампами, тёмная - тёмный камень и тёмный деградированный кирпич. */
        @Override
        int skin(int code) {
            switch (code) {
                case BONE: case PILLAR: case QSLAB: return WorldGenNests.STONE;
                case BONE_CHISEL: return WorldGenNests.GEM;
                case OBSIDIAN: return DARK;
                case WorldGenNests.STONE: return DDBRICK;
                case CHISEL: return NETHER;
                default: return code;
            }
        }

        /** Статуи братьев у шпилей: Денсос - на светлом помосте, Рейвор - на тёмном. */
        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            statue(w, cx, cz, x0, z0, -DAIS_X, TOP + 2, -2, net.divinerpg.utils.blocks.VanillaBlocks.densosStatue);
            statue(w, cx, cz, x0, z0, DAIS_X, TOP + 2, -2, net.divinerpg.utils.blocks.VanillaBlocks.reyvorStatue);
        }

        @Override
        boolean footprint(int dx, int dz) {
            return dx * dx + dz * dz <= (R + 1) * (R + 1);
        }

        @Override
        int reach() {
            return R + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return 14;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            boolean light = dx < 0;
            int wall = light ? BONE : OBSIDIAN;
            if (r > R - 1.5) { // стена с зубцами; ворота с севера и юга
                set(col, TOP, light ? BONE : OBSIDIAN);
                boolean gate = Math.abs(dx) <= 1;
                if (!gate) {
                    for (int y = TOP + 1; y <= TOP + 4; y++) set(col, y, wall);
                    if ((int) Math.round(Math.atan2(dz, dx) * R) % 2 == 0) set(col, TOP + 5, wall);
                } else set(col, TOP + 5, CHISEL); // перемычка ворот
                return;
            }
            // разлом между половинами
            if (Math.abs(dx) <= 1) {
                boolean bridge = Math.abs(dz) <= 1 || Math.abs(Math.abs(dz) - 12) <= 1;
                if (bridge) {
                    set(col, TOP, SLAB_TOP);
                    if (Math.abs(dz) % 12 == 2 || Math.abs(dz) % 12 == 10) set(col, TOP + 1, FENCE);
                } else {
                    put(col, TOP, AIR, 0);
                    put(col, TOP - 1, AIR, 0);
                    put(col, TOP - 2, AIR, 0);
                    set(col, TOP - 3, SOUL);
                }
                return;
            }
            // пол: светлая половина - кварц с узором, тёмная - обсидиан и камень мира
            int ring = (int) r % 6;
            if (light) set(col, TOP, ring == 0 ? BONE_CHISEL : hash(dx, dz) % 11 == 0 ? WorldGenNests.GLOW : BONE);
            else set(col, TOP, ring == 0 ? WorldGenNests.GEM : hash(dx, dz) % 3 == 0 ? OBSIDIAN : WorldGenNests.STONE);
            // кольцо колонн
            double cd = WorldGenNests.ringDist(dx, dz, 18, 10, Math.PI / 10);
            if (cd < 0.8) {
                for (int y = TOP + 1; y <= TOP + 7; y++) set(col, y, light ? PILLAR : OBSIDIAN);
                set(col, TOP + 8, light ? WorldGenNests.GLOW : WorldGenNests.GEM);
            }
            // помост брата со шпилем
            double d = Math.sqrt((Math.abs(dx) - DAIS_X) * (Math.abs(dx) - DAIS_X) + dz * dz);
            if (d < 4.5) set(col, TOP + 1, light ? QSLAB : SLAB);
            if (d < 3.5) set(col, TOP + 1, light ? BONE : OBSIDIAN);
            if (d < 0.8) for (int y = TOP + 2; y <= TOP + (light ? 5 : 10); y++) set(col, y, light ? PILLAR : OBSIDIAN);
            if (d < 0.8) set(col, TOP + (light ? 6 : 11), light ? WorldGenNests.GLOW : WorldGenNests.GEM);
        }
    }

    // ---------------------------------------------------------------- Карот

    /**
     * Зеркальный зал: закрытое здание - снаружи стены из кирпича с контрфорсами и зубчатой крышей, внутри стены из чёрного
     * стекла-зеркал между резными колоннами, четыре колонны до потолка, мозаика в центре, свет из окон в потолке.
     */
    static class Mirror extends Building {

        static final int H = 14, WALL = 8;

        /** Гамма Мортума: стены - тёмный деградированный кирпич, колонны - адский кирпич, пол - шахматка из тёмного камня и блока Мортума. */
        @Override
        int skin(int code) {
            switch (code) {
                case BRICK: case MOSSY: case CRACKED: return DDBRICK;
                case CHISEL: return NETHER;
                case BONE_CHISEL: return WorldGenNests.GEM;
                case PILLAR: return DARK;
                case CHECKER: return DARK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            statue(w, cx, cz, x0, z0, 0, TOP + 1, -H + 2, net.divinerpg.utils.blocks.VanillaBlocks.karotStatue);
        }

        @Override
        double outside(int dx, int dz) {
            return Math.max(Math.abs(dx), Math.abs(dz)) - reach();
        }

        @Override
        boolean footprint(int dx, int dz) {
            return Math.abs(dx) <= H + 2 && Math.abs(dz) <= H + 2;
        }

        @Override
        int reach() {
            return H + 2;
        }

        @Override
        int clear(int dx, int dz) {
            return WALL + 4;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz), m = Math.max(ax, az), lo = Math.min(ax, az);
            boolean door = lo <= 1 && m >= H;
            if (m > H + 1) { // контрфорсы снаружи
                set(col, TOP, BRICK);
                if (lo % 5 == 2 && lo < H) for (int y = TOP + 1; y <= TOP + WALL - 2; y++) set(col, y, CHISEL);
                return;
            }
            if (m == H + 1) { // внешняя стена
                set(col, TOP, BRICK);
                for (int y = TOP + 1; y <= TOP + WALL + 1; y++) set(col, y, door && y <= TOP + 4 ? AIR : worn(dx, y * 7 + dz));
                if (door) set(col, TOP + 5, CHISEL);
                if ((ax + az) % 2 == 0) set(col, TOP + WALL + 2, BRICK); // зубцы
                return;
            }
            if (m == H) { // зеркала между колоннами
                set(col, TOP, BRICK);
                boolean pillar = lo % 4 == 0;
                for (int y = TOP + 1; y <= TOP + WALL; y++) set(col, y, door && y <= TOP + 4 ? AIR : pillar ? CHISEL : MIRROR);
                if (pillar && !door) set(col, TOP + WALL - 1, WorldGenNests.GLOW);
                set(col, TOP + WALL + 1, BRICK);
                return;
            }
            // пол шахматкой, мозаика в центре
            double r = Math.sqrt(dx * dx + dz * dz);
            set(col, TOP, r < 4 ? (r < 1.5 ? WorldGenNests.GEM : BONE_CHISEL) : ((dx + dz) & 1) == 0 ? WorldGenNests.GEM : CHECKER);
            if (ax == 7 && az == 7) for (int y = TOP + 1; y <= TOP + WALL; y++) set(col, y, PILLAR);
            if (ax == 7 && az == 7) set(col, TOP + 1, BONE_CHISEL);
            // потолок с окнами-светильниками
            set(col, TOP + WALL + 1, ax % 5 == 0 && az % 5 == 0 ? WorldGenNests.GLOW : BRICK);
            if ((ax + az) % 2 == 1 && m == H - 1) set(col, TOP + WALL + 2, BRICK);
        }
    }

    // ---------------------------------------------------------------- Вечный лучник

    /**
     * Галерея: длинная аркада вдоль Z с двускатной кровлей - по бокам столбы и арки (видно пещеру), под кровлей подвесные
     * светильники, ковровая дорожка из адского кирпича к трону лучника на помосте у северного торца; ряды колонн-укрытий
     * из резного кирпича (их рушат залпы).
     */
    static class Gallery extends Building {

        static final int HX = 11, HZ = 27, DAIS_H = 3, COL_H = 4, EAVE = 8;

        /** Гамма Мортума: тёмный деградированный кирпич, пол - тёмный камень, кровля - доски Мортума; колонны-укрытия - резной камень (их рушат залпы). */
        @Override
        int skin(int code) {
            switch (code) {
                case BRICK: case MOSSY: case CRACKED: return DDBRICK;
                case SLAB_TOP: return MPLANK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            statue(w, cx, cz, x0, z0, -3, TOP + DAIS_H + 1, -HZ + 1, net.divinerpg.utils.blocks.VanillaBlocks.eternalArcherStatue);
            statue(w, cx, cz, x0, z0, 3, TOP + DAIS_H + 1, -HZ + 1, net.divinerpg.utils.blocks.VanillaBlocks.eternalArcherStatue);
        }

        @Override
        double outside(int dx, int dz) {
            return Math.max(Math.abs(dx) - (HX + 1), Math.abs(dz) - (HZ + 1));
        }

        @Override
        boolean footprint(int dx, int dz) {
            return Math.abs(dx) <= HX + 1 && Math.abs(dz) <= HZ + 1;
        }

        @Override
        int reach() {
            return HZ + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return EAVE + 9;
        }

        /** Высота кровли над полом (скат от карниза к коньку). */
        static int roof(int ax) {
            return EAVE + (HX + 1 - ax) / 2;
        }

        static boolean cover(int dx, int dz) {
            int[] rows = { -14, -7, 0, 7, 14 };
            for (int i = 0; i < rows.length; i++) {
                int sh = i % 2 == 0 ? 0 : 4; // ряды вразбежку
                for (int cx = -8 + sh; cx <= 8; cx += 8) if (dx >= cx && dx <= cx + 1 && dz >= rows[i] && dz <= rows[i] + 1) return true;
            }
            return false;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            int roofY = TOP + roof(ax);
            set(col, roofY, MPLANK); // кровля из досок Мортума
            if (ax == HX + 1) set(col, roofY - 1, SLAB_TOP); // карниз
            if (ax <= HX + 1 && az == HZ + 1) { // торцы: глухие до кровли, на юге - вход
                set(col, TOP, BRICK);
                boolean door = dz > 0 && ax <= 1;
                for (int y = TOP + 1; y < roofY; y++) set(col, y, door && y <= TOP + 4 ? AIR : worn(dx, y * 3 + dz));
                if (door) set(col, TOP + 5, CHISEL);
                if (dz < 0 && ax <= 2 && roofY - TOP > 10) set(col, TOP + 9, WorldGenNests.GLOW); // окно над троном
                return;
            }
            set(col, TOP, ax <= 1 ? NETHER : worn(dx, dz));
            if (ax == HX + 1 || ax == HX) { // аркада: столбы через 4, между ними арки
                boolean pier = Math.floorMod(dz, 4) == 0;
                if (ax == HX + 1) {
                    for (int y = TOP + 1; y < roofY; y++) {
                        boolean arch = !pier && y <= TOP + 5 - (Math.floorMod(dz, 4) == 2 ? 0 : 1) + 1;
                        set(col, y, pier ? CHISEL : arch ? AIR : BRICK);
                    }
                    if (pier) set(col, TOP + EAVE - 2, WorldGenNests.GLOW);
                }
                return;
            }
            // подвесные светильники по оси
            if (ax == 0 && Math.floorMod(dz, 6) == 3) {
                set(col, roofY - 1, FENCE);
                set(col, roofY - 2, WorldGenNests.GLOW);
            }
            // помост и трон у северного торца
            if (dz <= -(HZ - 5)) {
                boolean step = dz == -(HZ - 5);
                for (int y = TOP + 1; y <= TOP + DAIS_H; y++) set(col, y, step && y == TOP + DAIS_H ? SLAB : BRICK);
                if (dz == -HZ && ax <= 2) for (int y = TOP + DAIS_H + 1; y <= TOP + DAIS_H + (ax == 0 ? 5 : 3); y++) set(col, y, ax == 0 ? CHISEL : BRICK);
                if (dz == -HZ + 1 && ax <= 1) set(col, TOP + DAIS_H + 1, ax == 0 ? SLAB : AIR);
            }
            if (cover(dx, dz)) for (int y = TOP + 1; y <= TOP + COL_H; y++) set(col, y, CHISEL);
        }
    }

    // ---------------------------------------------------------------- Пожиратель душ

    /**
     * Погост: кладбище за оградой из адского кирпича на кирпичном цоколе (на столбах - фонари), ворота с юга, дорожки из
     * гравия крестом и по кругу, могилы рядами (холмик + надгробие из резного камня с плитой), сухие деревья с ветками,
     * пятна душевного песка, в центре - мавзолей с пирамидальной крышей.
     */
    static class Graveyard extends Building {

        static final int R = 24, MAUS = 4;

        /** Ограда и мавзолей - тёмный деградированный кирпич, кровля - тёмный камень; надгробия - резной камень (механика). */
        @Override
        int skin(int code) {
            switch (code) {
                case BRICK: case MOSSY: case CRACKED: return DDBRICK;
                case SLAB_TOP: return DARK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            statue(w, cx, cz, x0, z0, 0, TOP + 1, -1, net.divinerpg.utils.blocks.VanillaBlocks.soulFiendStatue);
        }

        @Override
        boolean footprint(int dx, int dz) {
            return dx * dx + dz * dz <= (R + 1) * (R + 1);
        }

        @Override
        int reach() {
            return R + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return 10;
        }

        static boolean path(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            return Math.abs(dx) <= 1 || Math.abs(dz) <= 1 || Math.abs(r - 13) < 0.8;
        }

        /** Холмик могилы (сюда встаёт поднятая душа); надгробие - на блок южнее. */
        static boolean grave(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            return r > 6.5 && r < 20 && Math.floorMod(dx, 4) == 2 && Math.floorMod(dz, 4) == 0 && !path(dx, dz) && !path(dx, dz + 1);
        }

        @Override
        void build(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int ax = Math.abs(dx), az = Math.abs(dz);
            if (r > R - 0.5) { // ограда
                set(col, TOP, BRICK);
                boolean gate = dz > 0 && ax <= 1;
                set(col, TOP + 1, gate ? AIR : BRICK);
                boolean post = Math.round(Math.atan2(dz, dx) * R) % 6 == 0;
                if (!gate) {
                    set(col, TOP + 2, post ? BRICK : FENCE);
                    set(col, TOP + 3, post ? WorldGenNests.GLOW : FENCE);
                }
                if (gate) set(col, TOP + 5, CHISEL);
                if (dz > 0 && ax == 2 && r > R - 0.5) for (int y = TOP + 1; y <= TOP + 5; y++) set(col, y, CHISEL); // столбы ворот
                return;
            }
            // мавзолей
            if (ax <= MAUS && az <= MAUS) {
                set(col, TOP, BRICK);
                boolean wallX = ax == MAUS, wallZ = az == MAUS, door = dz == MAUS && ax <= 1;
                if (wallX || wallZ) for (int y = TOP + 1; y <= TOP + 4; y++) set(col, y, door && y <= TOP + 3 ? AIR : (ax == MAUS && az == MAUS ? CHISEL : worn(dx, y + dz)));
                int roof = TOP + 5 + (MAUS - Math.max(ax, az)); // пирамида
                set(col, roof, Math.max(ax, az) == 0 ? WorldGenNests.GEM : SLAB_TOP);
                if (ax == 0 && az == 0) set(col, TOP + 4, WorldGenNests.GLOW); // свет под кровлей, внизу - статуя
                return;
            }
            // земля, дорожки, пятна душевного песка
            set(col, TOP - 1, WorldGenNests.DIRT);
            if (path(dx, dz)) set(col, TOP, GRAVEL);
            else set(col, TOP, hash(dx, dz) % 23 == 0 ? SOUL : WorldGenNests.GRASS);
            if (grave(dx, dz)) set(col, TOP, WorldGenNests.DIRT);
            else if (!path(dx, dz) && !grave(dx, dz - 1) && hash(dz, dx) % 13 == 0) set(col, TOP + 1, hash(dx, dz) % 3 == 0 ? EYEPLANT : BRAMBLE); // заросли
            if (grave(dx, dz - 1)) { // надгробие
                set(col, TOP + 1, CHISEL);
                set(col, TOP + 2, SLAB);
            }
            // сухие деревья: ствол и ветки
            double td = WorldGenNests.ringDist(dx, dz, 20, 6, 0.4);
            if (td < 0.6) for (int y = TOP + 1; y <= TOP + 6; y++) set(col, y, WorldGenNests.LOG);
            else if (td < 1.6 && hash(dx, dz) % 2 == 0) put(col, TOP + 4 + hash(dz, dx) % 2, WorldGenNests.LOG, 4 + 4 * (hash(dx, dz) % 2));
        }
    }

    // ---------------------------------------------------------------- Вамачерон

    /**
     * Костяная яма - амфитеатр: яма (пол на SINK ниже) с костяными столбами и разбросанными костями, отвесная стена ямы со
     * спусками с севера и юга, ступени-трибуны вокруг, внешняя стена с арками. Над ямой - грудная клетка гиганта:
     * хребет поперёк и рёбра-дуги из кварца.
     */
    static class Pit extends Building {

        static final int R = 28, PIT = 14, SINK = 4, RING = 9, PILLARS = 6, PILLAR_H = 6;

        /** Тёмный камень Divine и тёмный деградированный кирпич; кости - кварц. */
        @Override
        int skin(int code) {
            switch (code) {
                case BRICK: case MOSSY: case CRACKED: return DARK;
                case CHISEL: return DDBRICK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            int dz = -(R - 3), dx = 5;
            int tier = (int) ((Math.sqrt(dx * dx + dz * dz) - PIT - 1.5) / 2.2);
            statue(w, cx, cz, x0, z0, dx, TOP + tier + 1, dz, net.divinerpg.utils.blocks.VanillaBlocks.vamacheronStatue);
        }
        static final int[] RIBS = { -12, -8, -4, 0, 4, 8, 12 };

        @Override
        boolean footprint(int dx, int dz) {
            return dx * dx + dz * dz <= (R + 1) * (R + 1);
        }

        @Override
        int reach() {
            return R + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return 18;
        }

        static boolean pillar(int dx, int dz) {
            return WorldGenNests.ringDist(dx + 0.5, dz + 0.5, RING, PILLARS, Math.PI / PILLARS) < 1.1;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int ax = Math.abs(dx), floor = TOP - SINK;
            boolean ramp = ax <= 1 && r > PIT - 6; // спуски с севера и юга
            if (r <= PIT) { // яма
                int f = ramp ? Math.min(TOP, floor + (int) Math.max(0, (r - (PIT - 6)) * SINK / 6)) : floor;
                for (int y = f + 1; y <= TOP; y++) put(col, y, AIR, 0);
                set(col, f, hash(dx, dz) % 6 == 0 ? BONE : hash(dx, dz) % 5 == 0 ? SOUL : WorldGenNests.STONE);
                if (pillar(dx, dz)) for (int y = f + 1; y <= f + PILLAR_H; y++) set(col, y, PILLAR);
            } else if (r <= PIT + 1.5) { // стена ямы
                if (ramp) set(col, TOP, BRICK);
                else for (int y = floor; y <= TOP + 1; y++) set(col, y, y == TOP + 1 ? CHISEL : BRICK);
            } else if (r <= R - 1.5) { // трибуны: ступени вверх
                int tier = (int) ((r - PIT - 1.5) / 2.2);
                for (int y = TOP + 1; y <= TOP + tier; y++) set(col, y, BRICK);
                set(col, TOP + tier, (int) ((r - PIT - 1.5) * 10) % 22 < 11 ? BRICK : SLAB_TOP);
                if (ramp) for (int y = TOP + 1; y <= TOP + tier; y++) put(col, y, AIR, 0); // проход
            } else { // внешняя стена с арками
                boolean arch = Math.round(Math.atan2(dz, dx) * R) % 5 == 0 || ramp;
                for (int y = TOP + 1; y <= TOP + 9; y++) set(col, y, arch && y <= TOP + 4 ? AIR : worn(dx, y + dz));
                if (Math.round(Math.atan2(dz, dx) * R) % 10 == 0) set(col, TOP + 10, WorldGenNests.GLOW);
            }
            if (r <= PIT && !ramp && hash(dx, dz) % 31 == 0) set(col, floor, WorldGenNests.GLOW);
            // грудная клетка гиганта над ямой: хребет вдоль X, рёбра - дуги в плоскости YZ
            int base = floor, spineY = base + 13;
            if (dz == 0 && ax <= 15) put(col, spineY, PILLAR, 3); // хребет (столб лёжа вдоль X)
            for (int rib : RIBS) {
                if (Math.abs(dx - rib) > 0) continue;
                for (int y = base; y <= spineY; y++) {
                    double d = Math.sqrt(dz * dz + (y - base) * (y - base));
                    boolean broken = rib == 8 && dz < 0 && y < base + 6; // одно ребро обломано
                    if (Math.abs(d - 13) < 0.6 && !broken) set(col, y, BONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Демон сумерек

    /**
     * Замок мага: квадратная стена из адского кирпича (2 блока, 10 высотой, зубцы, ворота с решёткой на юге), четыре
     * круглые угловые башни с конусными кровлями; северо-западная - шпиль мага вдвое выше с багровым огнём на вершине.
     * Во дворе - ритуальный круг Демона: кольца из обсидиана и камня Мортума, кольцо света, шесть обелисков, жаровни.
     */
    static class Castle extends Building {

        static final int WALL = 23, WALL_H = 10, TOWER = 23, TOWER_R = 5, TOWER_H = 18, SPIRE_H = 32, COURT = 19;

        /** Гамма Мортума: стены и башни - тёмный деградированный кирпич, пояса и бортик - адский кирпич, кровли и обелиски - тёмный камень. */
        @Override
        int skin(int code) {
            switch (code) {
                case NETHER: return DDBRICK;
                case CHISEL: return NETHER;
                case OBSIDIAN: return DARK;
                case BRICK: case MOSSY: case CRACKED: return DARK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            statue(w, cx, cz, x0, z0, 0, TOP + 1, -11, net.divinerpg.utils.blocks.VanillaBlocks.twilightDemonStatue);
        }

        @Override
        double outside(int dx, int dz) {
            return Math.max(Math.abs(dx), Math.abs(dz)) - reach();
        }

        @Override
        boolean footprint(int dx, int dz) {
            return Math.max(Math.abs(dx), Math.abs(dz)) <= WALL + TOWER_R;
        }

        @Override
        int reach() {
            return WALL + TOWER_R;
        }

        @Override
        int clear(int dx, int dz) {
            return dx < -WALL + TOWER_R && dz < -WALL + TOWER_R ? SPIRE_H + 8 : TOWER_H + 6;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz), m = Math.max(ax, az);
            double r = Math.sqrt(dx * dx + dz * dz);
            // угловые башни
            double tr = Math.sqrt((ax - TOWER) * (ax - TOWER) + (az - TOWER) * (az - TOWER));
            if (tr <= TOWER_R) {
                boolean spire = dx < 0 && dz < 0;
                int h = spire ? SPIRE_H : TOWER_H;
                set(col, TOP, NETHER);
                if (tr > TOWER_R - 1.3) { // стена башни, окна-светильники
                    for (int y = TOP + 1; y <= TOP + h; y++) set(col, y, (y - TOP) % 6 == 4 && (ax + az) % 3 == 0 ? WorldGenNests.GLOW : NETHER);
                    if ((ax * 3 + az) % 2 == 0) set(col, TOP + h + 1, NETHER); // зубцы
                }
                // конусная кровля
                int cone = TOP + h + 1 + (int) Math.round((TOWER_R - tr) * (spire ? 2.4 : 1.4));
                set(col, cone, OBSIDIAN);
                if (tr < 0.8) {
                    set(col, cone + 1, spire ? WorldGenNests.GEM : OBSIDIAN);
                    if (spire) set(col, cone + 2, WorldGenNests.GLOW);
                }
                return;
            }
            // стена с зубцами и воротами
            if (m >= WALL && m <= WALL + 1) {
                set(col, TOP, NETHER);
                boolean gate = dz > 0 && ax <= 1;
                for (int y = TOP + 1; y <= TOP + WALL_H; y++) set(col, y, gate && y <= TOP + 4 ? AIR : (y - TOP) % 5 == 0 ? CHISEL : NETHER);
                if (gate) set(col, TOP + 4, BARS); // решётка ворот
                if (m == WALL + 1 && (ax + az) % 2 == 0) set(col, TOP + WALL_H + 1, NETHER);
                if (m == WALL && (ax + az) % 8 == 0 && !gate) set(col, TOP + WALL_H - 3, WorldGenNests.GLOW);
                return;
            }
            if (m > WALL + 1) { // за стеной
                set(col, TOP, worn(dx, dz));
                return;
            }
            // двор
            if (r > COURT) {
                set(col, TOP, ((dx + dz) & 1) == 0 ? NETHER : OBSIDIAN);
                if (WorldGenNests.ringDist(dx, dz, 21, 12, 0) < 0.5) {
                    set(col, TOP + 1, FENCE);
                    set(col, TOP + 2, WorldGenNests.GLOW); // жаровни
                }
                return;
            }
            int ri = (int) r;
            set(col, TOP, r < 2.5 ? WorldGenNests.GEM : ri % 4 == 0 ? OBSIDIAN : ri % 4 == 2 ? WorldGenNests.GEM : WorldGenNests.STONE);
            if (r < 0.7) set(col, TOP, WorldGenNests.GLOW);
            if (WorldGenNests.ringDist(dx, dz, 6, 12, 0) < 0.5) set(col, TOP, WorldGenNests.GLOW);
            // бортик круга с проходами
            if (r > COURT - 0.6 && !(ax <= 1 || az <= 1)) set(col, TOP + 1, CHISEL);
            // обелиски
            double ob = WorldGenNests.ringDist(dx, dz, 15, 6, 0);
            if (ob < 1.6) {
                int oh = ob < 0.9 ? 9 : 6;
                for (int y = TOP + 1; y <= TOP + oh; y++) set(col, y, ob < 0.9 ? OBSIDIAN : WorldGenNests.GEM);
                if (ob < 0.9) set(col, TOP + oh + 1, WorldGenNests.GLOW);
            }
        }
    }

    // ---------------------------------------------------------------- Демон сумерек: башня мага

    /**
     * Башня Сумеречного мага на утёсе над Летой - и данж, и источник силы Демона.
     * <ul>
     * <li>Утёс из рельефа: рваные обрывы, каменные пики, тёмные трещины. С юга к площадке вьётся тропа, у подножия её
     * переносит арочный мост через ров Леты.</li>
     * <li>Площадка боя на вершине: ритуальный круг, обелиски, зубчатый парапет, черепа на пиках.</li>
     * <li>Главная башня с севера: от подножия утёса до короны, контрфорсы, балкон посередине, навесной зубчатый пояс с пиками и
     * цепями, конусный шпиль. Внутри винтовая лестница по стене и этажи: темница, кладовая, зал с выходом на площадку,
     * библиотека, лаборатория, покои, коронный зал с главным фокусом силы.</li>
     * <li>Три башенки с фонарями-фокусами на макушках (их гасят фазером с площадки) и клетками с черепами под консолями,
     * круглый бастион сбоку.</li>
     * </ul>
     */
    static class Spire extends Building {

        /** Площадка на LIFT выше пола прочих арен: утёс почти 30 блоков над дном мира. */
        static final int LIFT = 12, TOP = WorldGenNests.MORTUM_TOP + LIFT;
        /** Шпиль кончается ниже бедрокового потолка Мортума (~123). */
        static final int COURT = 15, CRAG = 44, TX = 0, TZ = -21, TR = 9, TH = 44, ROOF = 18, STEP = 14, BASE = -28;
        /** Этажи башни (от пола площадки): темница, кладовая, зал, библиотека, лаборатория, покои, коронный зал. */
        static final int[] FLOORS = { -21, -14, -7, 0, 7, 14, 21, 28, 35 };
        static final int CROWN = 35, BALCONY = 21;
        /** Башенки: угол вокруг башни, низ и верх (от пола площадки), радиус. */
        static final double[][] TURRETS = { { 0.15, 6, 30, 2.8 }, { -1.15, 16, 38, 2.6 }, { -2.6, -2, 26, 2.9 } };
        static final int BX = 17, BZ = -6, BR = 5, BH = 9;
        /** Ров Леты у подножия (с юга и боков), мост на тропе. */
        static final int MOAT_IN = 39, MOAT_OUT = 44;
        static final int FOCUS = 90, BOOKS = 91;

        @Override
        int skin(int code) {
            switch (code) {
                case NETHER: return DDBRICK;
                case CHISEL: return NETHER;
                case OBSIDIAN: return DARK;
                default: return code;
            }
        }

        @Override
        protected Block block(int code, int meta) {
            if (code == FOCUS) return Blocks.glowstone;
            if (code == BOOKS) return Blocks.bookshelf;
            if (code == WorldGenNests.WATER) return com.divinerpgextended.registry.ModBlocks.lethe;
            return super.block(code, meta);
        }

        @Override
        boolean slopes() {
            return false;
        }

        @Override
        int foundationCode() {
            return WorldGenNests.STONE;
        }

        @Override
        boolean footprint(int dx, int dz) {
            return dx * dx + dz * dz <= MOAT_OUT * MOAT_OUT || tower(dx, dz) <= TR + 4;
        }

        @Override
        int reach() {
            return MOAT_OUT + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return TH + ROOF + 8;
        }

        static double tower(int dx, int dz) {
            return Math.sqrt((dx - TX) * (dx - TX) + (dz - TZ) * (dz - TZ));
        }

        /** Плавный шум [0, 1) по сетке cell. */
        static double smooth(int x, int z, int cell, int salt) {
            int ix = Math.floorDiv(x, cell), iz = Math.floorDiv(z, cell);
            double tx = (x - ix * cell) / (double) cell, tz = (z - iz * cell) / (double) cell;
            double a = hash(ix * 7 + salt, iz) % 1000 / 1000.0, b = hash(ix * 7 + 7 + salt, iz) % 1000 / 1000.0;
            double c = hash(ix * 7 + salt, iz + 1) % 1000 / 1000.0, d = hash(ix * 7 + 7 + salt, iz + 1) % 1000 / 1000.0;
            return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
        }

        /**
         * Высота скалы утёса (0 - вровень с площадкой): плато - круг площадки и круг под башней, дальше обрыв - крутой вверху,
         * положе к низу, с выступами; изредка каменные пики выше склона.
         */
        static int crag(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz), td = tower(dx, dz);
            double edge = Math.min(r - COURT - 1, td - TR - 2) - smooth(dx, dz, 7, 1) * 4;
            if (edge <= 0) return 0;
            double drop = edge * (3.0 - edge * 0.06) + smooth(dx, dz, 3, 5) * 3;
            double ledge = smooth(dx, dz, 5, 9) > 0.62 ? 3 : 0;
            double pin = smooth(dx, dz, 4, 13) > 0.8 && hash(dx, dz) % 3 != 0 ? 4 + smooth(dx, dz, 2, 17) * 8 : 0; // пики
            return -(int) Math.max(0, drop - ledge - pin);
        }

        /** Камень утёса: тёмные трещины-прожилки по светлому камню Мортума. */
        static int rock(int dx, int y, int dz) {
            boolean crack = smooth(dx + y, dz - y, 3, 21) > 0.82;
            return crack || hash(dx + y * 3, dz - y) % 6 == 0 ? DARK : WorldGenNests.STONE;
        }

        /** Высота ступени винтовой лестницы в столбце кольца (от пола площадки), ближайшая к h. */
        static int stair(int dx, int dz, int k) {
            double ang = (Math.atan2(dz - TZ, dx - TX) + Math.PI * 2) % (Math.PI * 2);
            return BASE + (int) (ang / (Math.PI * 2) * STEP) + k * STEP;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz), td = tower(dx, dz);
            int ax = Math.abs(dx), az = Math.abs(dz);
            boolean path = dz > 0 && ax <= 1 && r > COURT - 1; // тропа с юга
            int ground = TOP - LIFT - 16; // дно мира
            int top = TOP + crag(dx, dz);
            if (path) top = Math.max(top, TOP - (int) ((r - COURT) * 1.0));
            top = Math.max(top, ground);
            // ров Леты у подножия (юг и бока), мост на тропе
            boolean moat = r >= MOAT_IN && r <= MOAT_OUT && dz > -14;
            if (moat) { // общий фундамент постройки (TOP - 5) здесь не нужен - он засыпал бы ров
                for (int y = ground - 4; y <= TOP + 10; y++) put(col, y, AIR, 0);
                set(col, ground - 4, WorldGenNests.STONE);
                for (int y = ground - 3; y <= MortumWorld.SEA; y++) put(col, y, WorldGenNests.WATER, 0);
                if (path || (ax <= 2 && dz > 0)) { // мост: настил, перила, арка снизу
                    int deck = MortumWorld.SEA + 4;
                    if (ax <= 1) set(col, deck, NETHER);
                    if (ax == 2) {
                        set(col, deck, NETHER);
                        set(col, deck + 1, FENCE);
                    }
                    double mid = (MOAT_IN + MOAT_OUT) / 2.0;
                    int arch = deck - 1 - (int) Math.round(2.5 * (1 - Math.abs(r - mid) / ((MOAT_OUT - MOAT_IN) / 2.0 + 0.5)));
                    for (int y = arch; y < deck; y++) if (ax <= 2) set(col, y, NETHER);
                }
                return;
            }
            if (r > MOAT_OUT) { // за рвом - земля как была
                for (int y = TOP - 5; y <= TOP + 10; y++) put(col, y, KEEP, 0);
                return;
            }
            if (r > COURT + 1) { // склон утёса
                for (int y = top + 1; y <= TOP + 10; y++) put(col, y, AIR, 0);
                put(col, top - 4, FOUNDATION, 0);
                for (int y = top - 3; y < top; y++) set(col, y, rock(dx, y, dz));
                set(col, top, path ? GRAVEL : hash(dx, dz) % 7 == 0 ? WorldGenNests.GRASS : rock(dx, top, dz));
                if (path && (int) r % 3 == 0) set(col, top, SLAB_TOP);
            } else { // площадка: ритуальный круг, под ней скала
                put(col, TOP - 4, FOUNDATION, 0);
                for (int y = TOP - 3; y < TOP; y++) set(col, y, rock(dx, y, dz));
                int ri = (int) r;
                set(col, TOP, r < 2.5 ? WorldGenNests.GEM : ri % 4 == 0 ? OBSIDIAN : ri % 4 == 2 ? WorldGenNests.GEM : WorldGenNests.STONE);
                if (r < 0.7 || WorldGenNests.ringDist(dx, dz, 6, 12, 0) < 0.5) set(col, TOP, WorldGenNests.GLOW);
                double ob = WorldGenNests.ringDist(dx, dz, 11, 6, Math.PI / 6);
                if (ob < 1.2 && td > TR + 2) {
                    for (int y = TOP + 1; y <= TOP + (ob < 0.7 ? 6 : 4); y++) set(col, y, OBSIDIAN);
                    if (ob < 0.7) set(col, TOP + 7, WorldGenNests.GLOW);
                }
                if (r > COURT - 0.5 && !(dz > 0 && ax <= 1) && td > TR + 1) { // парапет с зубцами, ворота на юге
                    set(col, TOP + 1, NETHER);
                    set(col, TOP + 2, NETHER);
                    if ((ax + az) % 2 == 0) set(col, TOP + 3, NETHER);
                }
                if (r > COURT - 0.5 && dz > 0 && ax == 2) for (int y = TOP + 1; y <= TOP + 5; y++) set(col, y, CHISEL);
                if (r > COURT - 0.5 && dz > 0 && ax <= 1) set(col, TOP + 5, CHISEL);
            }
            // бастион сбоку
            double bd = Math.sqrt((dx - BX) * (dx - BX) + (dz - BZ) * (dz - BZ));
            if (bd <= BR) {
                put(col, top - 4, FOUNDATION, 0);
                for (int y = top - 3; y <= TOP + BH; y++) set(col, y, bd > BR - 1.2 ? (y % 6 == 0 ? CHISEL : NETHER) : WorldGenNests.STONE);
                if (bd > BR - 1.2 && (ax + az) % 2 == 0) {
                    set(col, TOP + BH + 1, NETHER);
                    if ((ax * 7 + az) % 4 == 0) set(col, TOP + BH + 2, FENCE); // пики на зубцах
                }
                return;
            }
            tower(dx, dz, td, col);
            turrets(dx, dz, col);
        }

        /** Главная башня: стены с окнами, контрфорсы, этажи и винтовая лестница, балкон, пояс с пиками и цепями, шпиль. */
        private void tower(int dx, int dz, double td, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            double ang = Math.atan2(dz - TZ, dx - TX);
            int base = TOP + BASE;
            // контрфорсы: четыре ребра наискосок, от подножия сужаются к середине башни
            double ribA = Math.abs(((ang + Math.PI / 4) % (Math.PI / 2) + Math.PI / 2) % (Math.PI / 2) - Math.PI / 4);
            if (td > TR && td <= TR + 3.5 && ribA * td < 0.8) {
                int h = (int) ((TR + 3.5 - td) / 3.5 * (BALCONY - BASE)) + base;
                put(col, base - 1, FOUNDATION, 0);
                for (int y = base; y <= h; y++) set(col, y, NETHER);
                set(col, h + 1, SLAB_TOP);
            }
            // балкон: кольцо плит снаружи с перилами
            if (td > TR && td <= TR + 1.6) {
                set(col, TOP + BALCONY, SLAB_TOP);
                if (td > TR + 0.8) set(col, TOP + BALCONY + 1, FENCE);
            }
            if (td > TR + 1.6) return;
            if (td <= TR) {
                put(col, base - 1, FOUNDATION, 0);
                boolean wall = td > TR - 1.3;
                for (int y = base; y <= TOP + TH; y++) {
                    int h = y - TOP;
                    if (wall) {
                        boolean window = h % 7 == 3 && (int) ((ang + Math.PI) * 4) % 3 == 0 && h > -20;
                        boolean crownWin = h > CROWN && h < TH && (int) ((ang + Math.PI) * 4) % 2 == 0; // широкие окна коронного зала
                        put(col, y, AIR, 0);
                        if (!crownWin) set(col, y, window ? WorldGenNests.GLOW : h % 7 == 0 ? CHISEL : NETHER);
                        continue;
                    }
                    boolean ring = td > TR - 3.3; // кольцо лестницы вдоль стены
                    boolean floor = false;
                    for (int f : FLOORS) if (h == f && (!ring || h == CROWN)) floor = true;
                    put(col, y, floor ? (h == CROWN ? OBSIDIAN : NETHER) : AIR, 0);
                    if (ring && h < CROWN) for (int k = 0; k < 6; k++) if (h == stair(dx, dz, k)) set(col, y, NETHER);
                }
                // двери: на площадку (юг, вровень с ней) и внизу к тропе у подножия (юг)
                if (dz > TZ && ax <= 1 && td > TR - 1.3) {
                    for (int y = TOP + 1; y <= TOP + 4; y++) put(col, y, AIR, 0);
                    for (int y = base + 1; y <= base + 4; y++) put(col, y, AIR, 0);
                }
                // главный фокус силы в коронном зале на пьедестале
                if (td < 0.7) {
                    set(col, TOP + CROWN + 1, OBSIDIAN);
                    put(col, TOP + CROWN + 2, FOCUS, 0);
                }
            }
            // навесной пояс с зубцами и пиками, цепи вниз
            if (td > TR - 1.3) {
                set(col, TOP + TH + 1, NETHER);
                set(col, TOP + TH + 2, NETHER);
                if ((ax + az) % 2 == 0) {
                    set(col, TOP + TH + 3, NETHER);
                    if ((ax * 3 + az) % 6 == 0) {
                        set(col, TOP + TH + 4, FENCE);
                        set(col, TOP + TH + 5, OBSIDIAN);
                    }
                }
                if (td > TR) {
                    set(col, TOP + TH, SLAB_TOP);
                    if ((ax * 5 + az * 3) % 11 == 0) for (int y = TOP + TH - 7; y < TOP + TH; y++) set(col, y, BARS); // цепи
                }
            }
            // шпиль
            if (td <= TR - 1) {
                int cone = TOP + TH + 2 + (int) Math.round((TR - td + 0.3) * ROOF / TR);
                for (int y = TOP + TH + 2; y < cone; y++) put(col, y, AIR, 0);
                set(col, cone, OBSIDIAN);
                if (td < 0.7) for (int y = cone; y <= cone + 3; y++) set(col, y, y == cone + 3 ? WorldGenNests.GLOW : OBSIDIAN);
            }
        }

        /** Центр башенки (x, z). */
        static double[] turret(double[] t) {
            return new double[] { TX + Math.cos(t[0]) * (TR + 1.8), TZ + Math.sin(t[0]) * (TR + 1.8) };
        }

        /** Башенки: стены, консоль снизу, фонарь-фокус на макушке под конусом. */
        private void turrets(int dx, int dz, int[] col) {
            for (double[] t : TURRETS) {
                double[] c = turret(t);
                double d = Math.sqrt((dx - c[0]) * (dx - c[0]) + (dz - c[1]) * (dz - c[1])), tr = t[3];
                if (d > tr + 0.3) continue;
                int lo = TOP + (int) t[1], hi = TOP + (int) t[2];
                for (int y = lo; y <= hi; y++) set(col, y, NETHER);
                set(col, lo - 1, SLAB_TOP); // консоль
                // фонарь: открытые окна у макушки, фокус в центре
                if (d > tr - 1) {
                    boolean win = ((int) Math.round((Math.atan2(dz - c[1], dx - c[0]) + Math.PI) * 2)) % 2 == 0;
                    if (win) for (int y = hi - 3; y <= hi - 1; y++) put(col, y, AIR, 0);
                } else {
                    for (int y = hi - 3; y <= hi - 1; y++) put(col, y, AIR, 0);
                    if (d < 0.8) put(col, hi - 2, FOCUS, 0);
                }
                int cone = hi + 1 + (int) Math.round((tr + 0.3 - d) * 2.4);
                set(col, cone, OBSIDIAN);
                if (d < 0.6) {
                    set(col, cone + 1, OBSIDIAN);
                    set(col, cone + 2, FENCE);
                }
            }
        }

        /** Фокусы силы Демона (смещения от центра): три фонаря башенок и главный в коронном зале. */
        static java.util.List<int[]> foci() {
            java.util.List<int[]> l = new java.util.ArrayList<int[]>();
            for (double[] t : TURRETS) {
                double[] c = turret(t);
                l.add(new int[] { (int) Math.floor(c[0] + 0.5), TOP + (int) t[2] - 2, (int) Math.floor(c[1] + 0.5) });
            }
            l.add(new int[] { TX, TOP + CROWN + 2, TZ });
            return l;
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            statue(w, cx, cz, x0, z0, 0, TOP + 1, -COURT + 4, net.divinerpg.utils.blocks.VanillaBlocks.twilightDemonStatue);
            // черепа на пиках вокруг площадки
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5 + 0.3;
                int px = (int) Math.round(Math.cos(a) * (COURT + 2)), pz = (int) Math.round(Math.sin(a) * (COURT + 2));
                if (tower(px, pz) < TR + 3 || (pz > 0 && Math.abs(px) <= 3)) continue;
                int y = TOP + crag(px, pz) + 1;
                place(w, cx, cz, x0, z0, px, y, pz, Blocks.nether_brick_fence, 0);
                place(w, cx, cz, x0, z0, px, y + 1, pz, Blocks.nether_brick_fence, 0);
                skull(w, cx, cz, x0, z0, px, y + 2, pz, i);
            }
            // клетки с черепами под консолями башенок
            for (double[] t : TURRETS) {
                double[] c = turret(t);
                int tx = (int) Math.floor(c[0] + 0.5) + (int) Math.round(Math.cos(t[0]) * 4), tz = (int) Math.floor(c[1] + 0.5) + (int) Math.round(Math.sin(t[0]) * 4);
                int ty = TOP + (int) t[1] - 3;
                for (int y = ty; y <= ty + 2; y++) for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++)
                    if (Math.abs(i) == 1 || Math.abs(k) == 1 || y == ty) place(w, cx, cz, x0, z0, tx + i, y, tz + k, Blocks.iron_bars, 0);
                place(w, cx, cz, x0, z0, tx, ty - 1, tz, Blocks.iron_bars, 0);
                skull(w, cx, cz, x0, z0, tx, ty + 1, tz, 3);
                for (int y = ty + 3; y < TOP + (int) t[1] - 1; y++) place(w, cx, cz, x0, z0, tx, y, tz, Blocks.iron_bars, 0); // цепь к консоли
            }
            // начинка этажей
            int base = TOP + BASE;
            TwilightWorld tw = TwilightWorld.MORTUM;
            spawner(w, cx, cz, x0, z0, TX, base + 1, TZ, tw); // темница
            spawner(w, cx, cz, x0, z0, TX + 2, TOP - 21 + 1, TZ, tw);
            chest(w, cx, cz, x0, z0, TX + 3, TOP - 14 + 1, TZ, 5); // кладовая
            chest(w, cx, cz, x0, z0, TX - 3, TOP + 7 + 1, TZ + 1, 7); // библиотека
            for (int i = -2; i <= 2; i++) {
                place(w, cx, cz, x0, z0, TX + i, TOP + 7 + 1, TZ - 4, Blocks.bookshelf, 0);
                place(w, cx, cz, x0, z0, TX + i, TOP + 7 + 2, TZ - 4, Blocks.bookshelf, 0);
            }
            place(w, cx, cz, x0, z0, TX + 1, TOP + 14 + 1, TZ, Blocks.brewing_stand, 0); // лаборатория
            place(w, cx, cz, x0, z0, TX - 1, TOP + 14 + 1, TZ, Blocks.cauldron, 3);
            spawner(w, cx, cz, x0, z0, TX, TOP + 21 + 1, TZ + 2, tw); // покои - стража
            chest(w, cx, cz, x0, z0, TX + 2, TOP + 28 + 1, TZ - 2, 6);
        }

        static void skull(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, int rot) {
            if (!place(w, cx, cz, x0, z0, dx, y, dz, Blocks.skull, 1)) return;
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(cx + dx, y, cz + dz);
            if (te instanceof net.minecraft.tileentity.TileEntitySkull) ((net.minecraft.tileentity.TileEntitySkull) te).func_145903_a(rot * 3 % 16);
        }

        static void chest(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, int count) {
            if (!place(w, cx, cz, x0, z0, dx, y, dz, Blocks.chest, 0)) return;
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(cx + dx, y, cz + dz);
            if (te instanceof net.minecraft.tileentity.TileEntityChest)
                net.minecraft.util.WeightedRandomChestContent.generateChestContents(new java.util.Random(cx * 31L + y * 7L + cz), TwilightLoot.dungeon(TwilightWorld.MORTUM),
                    (net.minecraft.tileentity.TileEntityChest) te, count);
        }

        static void spawner(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, TwilightWorld tw) {
            if (!place(w, cx, cz, x0, z0, dx, y, dz, Blocks.mob_spawner, 0)) return;
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(cx + dx, y, cz + dz);
            Class<? extends net.minecraft.entity.EntityLiving>[] mobs = tw.mobs();
            String name = (String) net.minecraft.entity.EntityList.classToStringMapping.get(mobs[Math.abs(dx * 7 + y) % mobs.length]);
            if (te instanceof net.minecraft.tileentity.TileEntityMobSpawner && name != null)
                ((net.minecraft.tileentity.TileEntityMobSpawner) te).func_145881_a().setEntityName(name);
        }
    }
}
