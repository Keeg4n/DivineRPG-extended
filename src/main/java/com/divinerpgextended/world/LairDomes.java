package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.world.twilight.ColumnStructure;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Подземные купола логов обычного мира - строятся по столбцам (как Ледяной Трон): каждый чанк при заселении достраивает
 * свой кусок по чертежу, без цепной генерации соседей. Чужих враждебных мобов внутри куполов не бывает ({@link NoSpawns}),
 * поэтому свет - редкий и для вида, а не сплошной ковёр ламп.
 * <ul>
 * <li>Ледяной купол Снежного Глакона ({@link Glacon}) под иглу (место - {@link LairSites#SNOW}): в центре арена-гнездо из
 * снега и льда с алтарём, вокруг замёрзшее озеро, дальше снежный берег; со свода свисают ледяные сталактиты, местами
 * срастаясь с полом в сталагнаты; на концах некоторых - лампы. Из иглу вниз ведёт шахта с винтовой лестницей и тоннель в купол.</li>
 * <li>Заброшенный подземный город под храмом джунглей ({@link JungleCity}): купол вдвое меньше Трона, улицы сеткой,
 * заросшие дома с обвалившимися стенами, сады; со свода свисают корни, некоторые светятся. В центре под храмом -
 * башня с винтовой лестницей (продолжение колодца из храма), на востоке - площадь-арена Древнего драмкрикса с алтарём.</li>
 * </ul>
 */
public class LairDomes implements IWorldGenerator {

    // ---------------------------------------------------------------- Глакон

    /** Купол Глакона: радиус, пол, высота свода; центр - на G_DX восточнее иглу; гнездо и озеро - радиусы. */
    public static final int G_R = 46, G_FL = 14, G_H = 26, G_DX = 56, G_NEST = 13, G_LAKE = 30;
    // ---------------------------------------------------------------- джунгли
    /** Город: радиус, пол, высота свода; площадь-арена - на J_ARENA_DX восточнее центра, полуширина J_PLAZA. */
    public static final int J_R = 72, J_FL = 10, J_H = 34, J_ARENA_DX = 38, J_PLAZA = 15;

    static final Glacon GLACON = new Glacon();
    static final JungleCity JUNGLE = new JungleCity();

    /** Центр купола Глакона по месту иглу {x, z}: {x, пол, z}. */
    public static int[] glaconCenter(int[] site) {
        return new int[] { site[0] + G_DX, G_FL, site[1] };
    }

    /** Центр города под храмом джунглей в чанке (sx, sz): храм стоит от угла чанка, центр - внутри него. */
    public static int[] jungleCenter(int sx, int sz) {
        return new int[] { sx * 16 + 7, J_FL, sz * 16 + 7 };
    }

    public static int glaconCeil(double r) {
        return G_FL + 5 + (int) Math.round((G_H - 5) * Math.sqrt(Math.max(0, 1 - (r / G_R) * (r / G_R))));
    }

    public static int jungleCeil(double r) {
        return J_FL + 7 + (int) Math.round((J_H - 7) * Math.sqrt(Math.max(0, 1 - (r / J_R) * (r / J_R))));
    }

    /** Места куполов Глакона в регионах вокруг точки. */
    static List<int[]> glaconCenters(World w, int x, int z) {
        List<int[]> out = new ArrayList<int[]>();
        int g = LairSites.spacing(LairSites.SNOW);
        if (g <= 0) return out;
        int rx = Math.floorDiv(x, g), rz = Math.floorDiv(z, g);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] s = LairSites.site(w, LairSites.SNOW, rx + i, rz + k);
            if (s != null && s.length >= 2) out.add(glaconCenter(s));
        }
        return out;
    }

    private static final Map<World, Boolean> JUNGLES = new WeakHashMap<World, Boolean>();

    /** Центры городов под храмами джунглей в регионах ванильной сетки вокруг точки. */
    static List<int[]> jungleCenters(World w, int x, int z) {
        List<int[]> out = new ArrayList<int[]>();
        Boolean has = JUNGLES.get(w);
        if (has == null) JUNGLES.put(w, has = !com.divinerpgextended.Config.scatterInModBiomes || ScatterGen.hasJungles(w));
        int span = ScatterGen.MAX * 16;
        int rx = Math.floorDiv(x, span), rz = Math.floorDiv(z, span);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] s = ScatterGen.spot(w, rx + i, rz + k);
            if (ScatterGen.kind(w.getWorldChunkManager().getBiomeGenAt(s[0] * 16 + 8, s[1] * 16 + 8), has) == ScatterGen.JUNGLE) out.add(jungleCenter(s[0], s[1]));
        }
        return out;
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != 0) return;
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8; // область заселения этого чанка
        for (int[] c : glaconCenters(world, x0 + 8, z0 + 8)) {
            // купол и тоннель на запад к иглу
            if (x0 + 16 <= c[0] - G_DX - 3 || x0 > c[0] + G_R + 4 || z0 + 16 <= c[2] - G_R - 4 || z0 > c[2] + G_R + 4) continue;
            GLACON.buildSlice(world, c[0], c[2], x0, z0);
            GLACON.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, G_FL + 1, G_FL + G_H);
            seal(GLACON, world, c[0], c[2], x0 - 16, z0 - 16, 48, G_FL - 4, G_FL + G_H + 4);
        }
        for (int[] c : jungleCenters(world, x0 + 8, z0 + 8)) {
            if (x0 + 16 <= c[0] - J_R - 4 || x0 > c[0] + J_R + 4 || z0 + 16 <= c[2] - J_R - 4 || z0 > c[2] + J_R + 4) continue;
            JUNGLE.buildSlice(world, c[0], c[2], x0, z0);
            JUNGLE.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, J_FL + 1, J_FL + J_H);
            seal(JUNGLE, world, c[0], c[2], x0 - 16, z0 - 16, 48, J_FL - 4, J_FL + J_H + 4);
        }
    }

    /**
     * Жидкости в уже готовых кусках: родники и озёра, которые заселение ставит в стенки пещер уже после купола, текли
     * внутрь. В стене и своде жидкость - снова блок чертежа, внутри (воздух чертежа) - убрать. Незагруженное не трогаем.
     */
    static void seal(ColumnStructure s, World w, int cx, int cz, int xFrom, int zFrom, int size, int yFrom, int yTo) {
        int[] col = new int[256 * 2];
        for (int x = xFrom; x < xFrom + size; x++) for (int z = zFrom; z < zFrom + size; z++) {
            if (!w.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
            int ax = (x - 8) >> 4, az = (z - 8) >> 4; // кусок столбца уже построен
            if (!w.getChunkProvider().chunkExists(ax, az) || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) continue;
            for (int i = 0; i < col.length; i += 2) col[i] = ColumnStructure.KEEP;
            if (!s.columnAt(x - cx, z - cz, col)) continue;
            for (int y = Math.max(1, yFrom); y <= yTo; y++) {
                int code = col[y * 2];
                if (code == ColumnStructure.KEEP || !w.getBlock(x, y, z).getMaterial().isLiquid()) continue;
                if (code == ColumnStructure.AIR) w.setBlock(x, y, z, Blocks.air, 0, 2);
                else w.setBlock(x, y, z, s.blockAt(code, col[y * 2 + 1]), s.metaAt(code, col[y * 2 + 1]), 2);
            }
        }
    }

    /** Построить весь купол в мире сразу (для самопроверки): glacon - купол Глакона, иначе город. */
    public static void buildAll(World w, int cx, int cz, boolean glacon) {
        ColumnStructure s = glacon ? GLACON : JUNGLE;
        int r = glacon ? G_R + G_DX + 8 : J_R + 8;
        for (int x0 = cx - r; x0 < cx + r; x0 += 16) for (int z0 = cz - r; z0 < cz + r; z0 += 16) s.buildSlice(w, cx, cz, x0, z0);
    }

    /** Точка внутри купола: внутри - чужие мобы не появляются. */
    public static boolean inside(World w, double x, double y, double z) {
        if (w.provider.dimensionId != 0 || y > J_FL + J_H + 2) return false;
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        if (y <= G_FL + G_H + 1) for (int[] c : glaconCenters(w, ix, iz)) {
            double dx = x - c[0], dz = z - c[2];
            if (dx * dx + dz * dz < G_R * G_R && y >= G_FL) return true;
        }
        for (int[] c : jungleCenters(w, ix, iz)) {
            double dx = x - c[0], dz = z - c[2];
            if (dx * dx + dz * dz < J_R * J_R && y >= J_FL) return true;
        }
        return false;
    }

    /** Чужие враждебные мобы в куполах не появляются (свет там - для вида, не от спавна). */
    public static class NoSpawns {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onCheckSpawn(net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn e) {
            if (e.world.isRemote || e.y > J_FL + J_H + 2 || !(e.entityLiving instanceof net.minecraft.entity.monster.IMob)) return;
            if (inside(e.world, e.x, e.y, e.z)) e.setResult(cpw.mods.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    // ---------------------------------------------------------------- общее

    static int hash(int x, int z) {
        return ColumnStructure.hash(x, z);
    }

    /** Ступени винтовой лестницы шахты 3x3: на высоте y ступень в клетке RING[y & 7] (одинаково в колодце и в куполе). */
    public static final int[][] RING = { { -1, -1 }, { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 } };

    /** Номер клетки (dx, dz) кольца шахты или -1 (центр и вне шахты). */
    public static int ringIndex(int dx, int dz) {
        for (int k = 0; k < 8; k++) if (RING[k][0] == dx && RING[k][1] == dz) return k;
        return -1;
    }

    static double ringDist(double dx, double dz, double ring, int count, double phase) {
        double a = Math.atan2(dz, dx) - phase, step = Math.PI * 2 / count;
        double ca = Math.round(a / step) * step + phase;
        return Math.sqrt((dx - Math.cos(ca) * ring) * (dx - Math.cos(ca) * ring) + (dz - Math.sin(ca) * ring) * (dz - Math.sin(ca) * ring));
    }

    // ---------------------------------------------------------------- ледяной купол Глакона

    static final class Glacon extends ColumnStructure {

        static final int SHELL = 1, SNOW = 2, PACKED = 3, ICE = 4, LAYER = 5, LAMP = 6, LANTERN = 7, ALTAR = 8, SNOW_BRICK = 9, ICY_BRICK = 10;

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case SHELL: return meta == 1 ? Blocks.packed_ice : IceikaBlocks.frozenStone;
                case SNOW: return Blocks.snow;
                case PACKED: return Blocks.packed_ice;
                case ICE: return Blocks.ice;
                case LAYER: return Blocks.snow_layer;
                case LAMP: return IceikaBlocks.workshopLamp;
                case LANTERN: return net.divinerpg.utils.blocks.VanillaBlocks.lightFenceBlueOn;
                case ALTAR: return ModBlocks.glaconAltar;
                case SNOW_BRICK: return IceikaBlocks.snowBricks;
                case ICY_BRICK: return IceikaBlocks.icyBricks;
                default: return Blocks.air;
            }
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == SHELL ? 0 : meta;
        }

        private static void shell(int[] col, int y, int dx, int dz) {
            put(col, y, SHELL, hash(dx * 7 + y, dz * 13 - y) % 10 < 3 ? 1 : 0);
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int sx = dx + G_DX; // от шахты иглу
            boolean shaft = Math.abs(sx) <= 1 && Math.abs(dz) <= 1;
            boolean tunnel = Math.abs(dz) <= 2 && dx >= -G_DX - 2 && dx <= -G_R + 3;
            if (r > G_R + 3 && !tunnel) return false;
            int h = hash(dx, dz);
            if (r <= G_R + 3) dome(dx, dz, r, h, col);
            if (tunnel && r >= G_R - 3) {
                // тоннель от шахты иглу к куполу: 3 в ширину, 3 в высоту, лёд и мёрзлый камень вокруг
                if (Math.abs(dz) <= 1 && dx >= -G_DX - 1) {
                    put(col, G_FL, PACKED, 0);
                    for (int y = G_FL + 1; y <= G_FL + 3; y++) put(col, y, AIR, 0);
                    if (!shaft) shell(col, G_FL + 4, dx, dz);
                } else {
                    for (int y = G_FL; y <= G_FL + 4; y++) shell(col, y, dx, dz);
                }
                for (int y = G_FL - 3; y < G_FL; y++) shell(col, y, dx, dz);
            }
            if (shaft) {
                // низ шахты из иглу (верх копает генератор логова у иглу): винтовые ступени по кольцу 3x3
                int k = ringIndex(sx, dz);
                for (int y = G_FL + 1; y <= G_FL + 8; y++) put(col, y, k >= 0 && (y & 7) == k ? ICY_BRICK : AIR, 0);
            }
            return true;
        }

        private void dome(int dx, int dz, double r, int h, int[] col) {
            for (int y = G_FL - 4; y < G_FL; y++) shell(col, y, dx, dz);
            if (r >= G_R) { // стена
                for (int y = G_FL; y <= G_FL + 9; y++) shell(col, y, dx, dz);
                return;
            }
            int ceil = glaconCeil(r);
            for (int y = G_FL + 1; y <= ceil; y++) put(col, y, AIR, 0);
            for (int y = ceil + 1; y <= ceil + 3; y++) put(col, y, SHELL, 1);
            // пол по кольцам: гнездо, замёрзшее озеро, снежный берег
            if (r < G_NEST) nest(dx, dz, r, h, col);
            else if (r < G_LAKE) {
                // озеро: у гнезда паковый лёд (от ламп гнезда простой лёд бы таял), дальше прозрачный лёд с трещинами
                put(col, G_FL, r < 17 || h % 6 == 0 ? PACKED : ICE, 0);
                put(col, G_FL - 1, PACKED, 0);
                if (h % 13 == 0) put(col, G_FL + 1, LAYER, h % 2);
            } else {
                put(col, G_FL, h % 10 < 7 ? SNOW : SHELL, 0);
                if (h % 9 == 0) put(col, G_FL + 1, LAYER, h % 3);
                if (h % 41 == 0) { // валун
                    put(col, G_FL + 1, SHELL, 0);
                    if (h % 2 == 0) put(col, G_FL + 2, SNOW, 0);
                }
                // фонари у стены: ледяной столбик с лампой
                if (r > G_R - 3 && ringDist(dx, dz, G_R - 2, 14, 0.2) < 0.6) {
                    put(col, G_FL + 1, PACKED, 0);
                    put(col, G_FL + 2, LAMP, 0);
                }
            }
            icicles(dx, dz, r, ceil, col);
        }

        /** Гнездо: снежная чаша, борт из снега и льда со щелями на запад и восток, ледяные "перья", алтарь в центре. */
        private void nest(int dx, int dz, double r, int h, int[] col) {
            put(col, G_FL, PACKED, 0);
            put(col, G_FL + 1, SNOW, 0);
            if (r < 1.6) {
                put(col, G_FL + 2, SNOW_BRICK, 0);
                if (r < 0.5) put(col, G_FL + 3, ALTAR, 0);
                return;
            }
            // огни в чаше гнезда
            if (ringDist(dx, dz, 6, 4, Math.PI / 4) < 0.5) put(col, G_FL + 1, LAMP, 0);
            if (r < 10.5) {
                if (h % 7 == 0) put(col, G_FL + 2, LAYER, h % 2);
                return;
            }
            boolean gap = Math.abs(dz) <= 1; // проходы с запада (тоннель) и востока
            if (gap) return;
            int top = 2 + h % 3;
            for (int y = G_FL + 2; y <= G_FL + top; y++) put(col, y, (y + h) % 3 == 0 ? PACKED : (y + h) % 3 == 1 ? SNOW : SHELL, 0);
            // ледяные перья по борту
            if (ringDist(dx, dz, 12, 8, Math.PI / 8) < 0.8) {
                int k = (int) Math.round((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 4));
                for (int y = G_FL + 2; y <= G_FL + 5 + k % 3; y++) put(col, y, PACKED, 0);
            }
        }

        /** Сталактиты со свода (часть - со светящимся концом), местами сросшиеся с полом в сталагнаты; маленькие сталагмиты на берегу. */
        private void icicles(int dx, int dz, double r, int ceil, int[] col) {
            final int cell = 7;
            int cx0 = Math.floorDiv(dx, cell), cz0 = Math.floorDiv(dz, cell);
            for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
                int gx = cx0 + i, gz = cz0 + k;
                int ch = hash(gx * 31 + 7, gz * 17 + 3);
                if (ch % 2 != 0) continue; // редко: свод не лес из льда
                double px = gx * cell + ch % 5 + 1.5, pz = gz * cell + (ch >> 3) % 5 + 1.5, pr = Math.sqrt(px * px + pz * pz);
                if (pr > G_R - 3 || pr < 16) continue; // над гнездом свод чистый
                double rb = 0.8 + (ch >> 6) % 3 * 0.5, d = Math.sqrt((dx + 0.5 - px) * (dx + 0.5 - px) + (dz + 0.5 - pz) * (dz + 0.5 - pz));
                if (d > rb + 0.6) continue;
                int top = glaconCeil(pr), floor = G_FL + 1;
                if ((ch >> 9) % 5 == 0 && pr >= 18) {
                    // сталагнат: толстый у свода и у пола, тонкий посередине
                    for (int y = floor; y <= top; y++) {
                        double t = (double) (y - floor) / Math.max(1, top - floor), rad = rb * (0.45 + 0.55 * Math.abs(t - 0.5) * 2) + 0.4;
                        if (d <= rad) put(col, y, PACKED, 0);
                    }
                    continue;
                }
                if (d > rb) continue;
                int len = Math.min(3 + (ch >> 12) % 10, top - floor - 4);
                if (len < 2) continue;
                int tip = top - len + 1;
                for (int y = tip; y <= top; y++) {
                    double rad = rb * (y - tip + 1) / len + 0.3;
                    if (d <= rad) put(col, y, PACKED, 0);
                }
                // светящийся конец у каждого третьего (высоко над полом - простой лёд озера от него не тает)
                if ((ch >> 16) % 3 == 0 && d < 0.5 && tip >= G_FL + 10) put(col, tip, LAMP, 0);
                // сталагмит под ним на берегу
                if ((ch >> 20) % 3 == 0 && pr > G_LAKE + 1 && d <= rb * 0.8) {
                    int hgt = 1 + (ch >> 22) % 3;
                    for (int y = floor; y < floor + hgt; y++) if (d <= rb * 0.8 * (floor + hgt - y) / hgt + 0.2) put(col, y, PACKED, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- заброшенный город под храмом джунглей

    public static final class JungleCity extends ColumnStructure {

        static final int SHELL = 1, GRASS = 2, DIRT = 3, BRICK = 4, COBBLE = 5, MOSSY = 6, GRAVEL = 7, LOG = 8, LEAVES = 9, GLOW = 10, FENCE = 11, FERN = 12,
            ALTAR = 13, STEP = 14;
        /** Квартал особого дома: целый дом, внутри у дальней стены - диван с РПГ (пасхалка). */
        public static final int HOME_IX = -3, HOME_IZ = 1;

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case SHELL: return meta == 0 ? Blocks.stone : meta == 1 ? Blocks.cobblestone : Blocks.mossy_cobblestone;
                case GRASS: return Blocks.grass;
                case DIRT: return Blocks.dirt;
                case BRICK: return Blocks.stonebrick;
                case COBBLE: return Blocks.cobblestone;
                case MOSSY: return Blocks.mossy_cobblestone;
                case GRAVEL: return Blocks.gravel;
                case LOG: return Blocks.log;
                case LEAVES: return Blocks.leaves;
                case GLOW: return Blocks.glowstone;
                case FENCE: return Blocks.fence;
                case FERN: return Blocks.tallgrass;
                case ALTAR: return ModBlocks.jungleAltar;
                case STEP: return Blocks.stonebrick;
                default: return Blocks.air;
            }
        }

        @Override
        protected int metaOf(int code, int meta) {
            if (code == SHELL) return 0;
            if (code == LOG) return 3; // ствол джунглей
            if (code == LEAVES) return 3 | 4; // листва джунглей, не опадает
            if (code == FERN) return 2;
            if (code == STEP) return 1;
            return meta;
        }

        private static void shell(int[] col, int y, int dx, int dz) {
            int v = hash(dx * 5 + y, dz * 11 - y) % 10;
            put(col, y, SHELL, v < 5 ? 0 : v < 8 ? 1 : 2);
        }

        static boolean tower(int dx, int dz) {
            return dx * dx + dz * dz < 4.2 * 4.2;
        }

        static boolean plaza(int dx, int dz) {
            return Math.abs(dx - J_ARENA_DX) <= J_PLAZA && Math.abs(dz) <= J_PLAZA;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > J_R + 3) return false;
            int h = hash(dx, dz);
            for (int y = J_FL - 4; y < J_FL; y++) shell(col, y, dx, dz);
            if (r >= J_R) {
                for (int y = J_FL; y <= J_FL + 12; y++) shell(col, y, dx, dz);
                return true;
            }
            int ceil = tower(dx, dz) ? jungleCeil(0) : jungleCeil(r);
            for (int y = J_FL + 1; y <= ceil; y++) put(col, y, AIR, 0);
            for (int y = ceil + 1; y <= ceil + 3; y++) shell(col, y, dx, dz);
            // пол: трава и земля
            put(col, J_FL, h % 3 == 0 ? DIRT : GRASS, 0);
            if (tower(dx, dz)) tower(dx, dz, ceil, h, col);
            else if (plaza(dx, dz)) plaza(dx, dz, h, col);
            else city(dx, dz, r, h, col);
            if (!tower(dx, dz) && !plaza(dx, dz) && dx * dx + dz * dz > 7 * 7) roots(dx, dz, r, ceil, col);
            return true;
        }

        /** Башня под храмом: колодец из храма продолжается винтовой лестницей до пола, вокруг - стена с проходами внизу. */
        private void tower(int dx, int dz, int ceil, int h, int[] col) {
            put(col, J_FL, BRICK, 0);
            int k = ringIndex(dx, dz);
            if (dx == 0 && dz == 0) { // столб посередине: в колодец не упасть до пола
                for (int y = J_FL + 1; y <= ceil; y++) put(col, y, BRICK, 3);
                for (int y = ceil + 1; y <= ceil + 3; y++) put(col, y, AIR, 0); // низ колодца из храма
                return;
            }
            if (k >= 0) { // ступени по кольцу
                for (int y = J_FL + 1; y <= ceil + 3; y++) put(col, y, (y & 7) == k ? STEP : AIR, 0);
                return;
            }
            for (int y = J_FL + 1; y <= ceil; y++) {
                boolean door = y <= J_FL + 3 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1);
                boolean broken = hash(dx * 3 + y, dz * 7) % 17 == 0;
                if (door || broken) continue;
                put(col, y, BRICK, hash(dx + y, dz - y) % 3);
            }
        }

        /** Площадь-арена Древнего драмкрикса: мощёная, кольцо обломанных колонн, помост с алтарём на севере, фонари. */
        private void plaza(int dx, int dz, int h, int[] col) {
            int px = dx - J_ARENA_DX;
            put(col, J_FL, BRICK, h % 3);
            double pr = Math.sqrt(px * px + dz * dz);
            if (ringDist(px, dz, 13, 12, 0.13) < 0.8) {
                int top = J_FL + 3 + h % 7;
                for (int y = J_FL + 1; y <= top; y++) put(col, y, BRICK, (y + h) % 3);
                put(col, top + 1, LEAVES, 0);
            }
            if (dz == -WorldGenJungleLair.ALTAR_DZ && Math.abs(px) <= 1) {
                put(col, J_FL + 1, BRICK, 3);
                if (px == 0) put(col, J_FL + 2, ALTAR, 0);
            }
            if (Math.abs(px) == 10 && Math.abs(dz) == 10) {
                put(col, J_FL + 1, FENCE, 0);
                put(col, J_FL + 2, FENCE, 0);
                put(col, J_FL + 3, GLOW, 0);
            }
            if (pr > J_PLAZA - 1 && h % 4 == 0) put(col, J_FL + 1, LEAVES, 0); // кусты по краю площади
        }

        /** Квартал: улица, дом, развалины или сад. */
        private void city(int dx, int dz, double r, int h, int[] col) {
            int ix = Math.floorDiv(dx, 16), iz = Math.floorDiv(dz, 16), lx = dx - ix * 16, lz = dz - iz * 16;
            boolean streetX = lx == 15 || lx <= 1, streetZ = lz == 15 || lz <= 1;
            if (streetX || streetZ) {
                put(col, J_FL, h % 4 == 0 ? GRAVEL : h % 4 == 1 ? MOSSY : BRICK, 2);
                // фонари на каждом втором перекрёстке
                if (lx == 0 && lz == 0 && ((ix + iz) & 1) == 0 && r < J_R - 6) {
                    put(col, J_FL + 1, FENCE, 0);
                    put(col, J_FL + 2, FENCE, 0);
                    put(col, J_FL + 3, GLOW, 0);
                } else if (h % 23 == 0) fern(col); // пробился сквозь мостовую
                return;
            }
            int lh = hash(ix * 7919 + 13, iz * 104729 - 7);
            // квартал целиком внутри купола и не у башни и площади
            double far = Math.max(Math.max(Math.hypot(ix * 16, iz * 16), Math.hypot(ix * 16 + 16, iz * 16)), Math.max(Math.hypot(ix * 16, iz * 16 + 16), Math.hypot(ix * 16 + 16, iz * 16 + 16)));
            boolean near = Math.abs(ix * 16 + 8) < 16 && Math.abs(iz * 16 + 8) < 16 || ix * 16 + 16 > J_ARENA_DX - J_PLAZA - 2 && ix * 16 < J_ARENA_DX + J_PLAZA + 2 && iz * 16 + 16 > -J_PLAZA - 2 && iz * 16 < J_PLAZA + 2;
            int kind = far > J_R - 4 || near ? 9 : lh % 10;
            int a0 = 2 + (lh >> 4) % 3, a1 = 14 - (lh >> 6) % 3, b0 = 2 + (lh >> 8) % 3, b1 = 14 - (lh >> 10) % 3;
            boolean home = ix == HOME_IX && iz == HOME_IZ;
            if (home) {
                kind = 0;
                a0 = b0 = 3;
                a1 = b1 = 13;
            }
            boolean inRect = lx >= a0 && lx <= a1 && lz >= b0 && lz <= b1, edge = inRect && (lx == a0 || lx == a1 || lz == b0 || lz == b1);
            if (kind <= 4 && inRect) { // дом
                int height = home ? 6 : Math.min(5 + (lh >> 12) % 5, jungleCeil(far) - J_FL - 3);
                put(col, J_FL, MOSSY, 0);
                if (!edge) {
                    if (home) put(col, J_FL + height, BRICK, 1); // особый дом - под целой крышей
                    else if ((lh >> 15) % 3 == 0 && h % 10 < 7) put(col, J_FL + height, LEAVES, 0); // заросшая крыша
                    else if (h % 11 == 0) put(col, J_FL + 1, LEAVES, 0);
                    return;
                }
                int top = J_FL + height - (!home && h % 4 == 0 ? 1 + (h >> 3) % 3 : 0); // обвалившийся край стены
                int mid = (a0 + a1) / 2;
                for (int y = J_FL + 1; y <= top; y++) {
                    boolean door = lz == b0 && (lx == mid || lx == mid + 1) && y <= J_FL + 2;
                    boolean corner = (lx == a0 || lx == a1) && (lz == b0 || lz == b1);
                    boolean window = y == J_FL + 3 && (lx + lz) % 3 == 0 && !corner;
                    if (door || window) continue;
                    put(col, y, h % 5 == 0 ? MOSSY : BRICK, (y + h) % 3);
                }
                if (h % 3 == 0) put(col, top + 1, LEAVES, 0); // заросшая верхушка стены
            } else if (kind <= 7 && edge) { // развалины: невысокие обломки стен
                int top = J_FL + (h % 4 == 0 ? 0 : 1 + h % 3);
                for (int y = J_FL + 1; y <= top; y++) put(col, y, h % 2 == 0 ? COBBLE : MOSSY, 0);
            } else { // сад и пустырь
                if (h % 13 == 0) {
                    put(col, J_FL + 1, LOG, 0);
                    put(col, J_FL + 2, LEAVES, 0);
                } else if (h % 5 == 0) put(col, J_FL + 1, LEAVES, 0);
                else if (h % 3 == 0) fern(col);
            }
        }

        /** Папоротник - всегда на траве (пол сада с тем же хешем кладётся землёй). */
        private static void fern(int[] col) {
            put(col, J_FL, GRASS, 0);
            put(col, J_FL + 1, FERN, 0);
        }

        /** Корни со свода: стволы джунглей вниз, листва у конца; у некоторых - светящийся конец. */
        private void roots(int dx, int dz, double r, int ceil, int[] col) {
            final int cell = 9;
            int cx0 = Math.floorDiv(dx, cell), cz0 = Math.floorDiv(dz, cell);
            for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
                int gx = cx0 + i, gz = cz0 + k;
                int ch = hash(gx * 37 + 11, gz * 23 + 5);
                if (ch % 3 == 0) continue;
                double px = gx * cell + ch % 7 + 0.5, pz = gz * cell + (ch >> 3) % 7 + 0.5, pr = Math.sqrt(px * px + pz * pz);
                if (pr > J_R - 6 || pr < 8 || plaza((int) Math.floor(px), (int) Math.floor(pz))) continue;
                double d = Math.sqrt((dx + 0.5 - px) * (dx + 0.5 - px) + (dz + 0.5 - pz) * (dz + 0.5 - pz));
                if (d > 1.8) continue;
                int top = jungleCeil(pr), len = Math.min(3 + (ch >> 6) % 12, top - J_FL - 8);
                if (len < 2) continue;
                int tip = top - len + 1;
                boolean thick = (ch >> 11) % 3 == 0;
                if (d <= (thick ? 1.1 : 0.6)) {
                    for (int y = tip; y <= top; y++) put(col, y, LOG, 0);
                    if ((ch >> 14) % 4 == 0 && d < 0.6) put(col, tip - 1, GLOW, 0); // светящийся корень
                } else if (d <= 1.8) {
                    for (int y = tip - 1; y <= tip + 1; y++) if ((y + (int) (d * 7)) % 2 == 0 || y == tip) put(col, y, LEAVES, 0);
                }
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            // особый дом: диван с РПГ у дальней стены, лицом к двери (на север)
            int sx = HOME_IX * 16 + 7, sz = HOME_IZ * 16 + 12;
            if (com.divinerpgextended.Config.easterEggs) place(w, cx, cz, x0, z0, sx, J_FL + 1, sz, ModBlocks.sofa, 2);
            if (com.divinerpgextended.Config.easterEggs) place(w, cx, cz, x0, z0, sx + 1, J_FL + 1, sz, ModBlocks.sofa, 2 | com.divinerpgextended.easter.BlockSofa.SECOND);
            // сундуки в домах: в углу каждого третьего дома
            for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
                int dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz >= (J_R - 4) * (J_R - 4)) continue;
                int ix = Math.floorDiv(dx, 16), iz = Math.floorDiv(dz, 16), lx = dx - ix * 16, lz = dz - iz * 16;
                int lh = hash(ix * 7919 + 13, iz * 104729 - 7);
                int a0 = 2 + (lh >> 4) % 3, b0 = 2 + (lh >> 8) % 3;
                if (lx != a0 + 1 || lz != b0 + 1 || lh % 10 > 4 || (lh >> 18) % 3 != 0) continue;
                if (w.getBlock(x, J_FL, z) != Blocks.mossy_cobblestone || !w.isAirBlock(x, J_FL + 1, z)) continue; // дом здесь построен
                LairLoot.chest(w, new Random(lh), x, J_FL + 1, z, false);
            }
        }
    }
}
