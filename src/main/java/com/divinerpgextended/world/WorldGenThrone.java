package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Ледяной Трон в Iceika - подземная арена до бедрока.
 * <p>
 * Место вычисляется из сида мира: один Трон на квадрат throneSpacing x throneSpacing. Постройка огромная (300 блоков
 * арены, шпили по 60 блоков), поэтому её не ставят разом: каждый чанк при заселении сам достраивает свою часть по
 * функции "какой блок в этой точке", прямо в хранилище чанка (без пересчёта света на каждый блок).
 * <ul>
 * <li>Наверху - цитадель (вершина купола арены): площадка с парапетом, кольцо ледяных колонн, купольный зал радиусом
 * 20 с воротами на юг, у ворот два шпиля по 60 блоков. В зале - тёплые жаровни и два люка винтовых лестниц.</li>
 * <li>Внизу - купол радиусом 150 блоков от пола у бедрока (y=5) до ~66: рёбра из ледяного кирпича, лампы в своде,
 * сосульки, внешнее кольцо колонн до свода и внутреннее кольцо пилонов по 18 блоков с площадкой (на них
 * прыгает Король-лич), 12 жаровен вокруг центра, гигантский трон на севере с алтарём призыва.</li>
 * <li>Две винтовые лестницы спускаются из зала цитадели на пол арены вокруг ледяных столбов.</li>
 * </ul>
 */
public class WorldGenThrone implements IWorldGenerator {

    public static final int R = 150, FLOOR = 6, TOP = 72, CITADEL = 34;
    /** Алтарь стоит на помосте трона: от центра арены на ALTAR_DZ к северу, на ALTAR_DY над полом. */
    public static final int ALTAR_DZ = 37, ALTAR_DY = 3;
    private static final int HALL_R = 20, HALL_H = 17, TOWER_X = 14;
    private static final int KEEP = -1;

    // ---------------------------------------------------------------- место

    /** Центр Трона в области (rx, rz) или null, если Троны выключены. */
    public static int[] center(long seed, int rx, int rz) {
        int g = Config.throneSpacing;
        if (g <= 0) return null;
        Random r = new Random(seed ^ (rx * 341873128712L + rz * 132897987541L) ^ 0x7E3A11L);
        int margin = Math.min(g / 3, R + 60);
        return new int[] { rx * g + margin + r.nextInt(Math.max(1, g - 2 * margin)), TOP,
            rz * g + margin + r.nextInt(Math.max(1, g - 2 * margin)) };
    }

    /** Ближайший к точке Трон (смотрим свою и соседние области). */
    public static int[] nearest(long seed, double x, double z) {
        int g = Config.throneSpacing;
        if (g <= 0) return null;
        int rx = (int) Math.floor(x / g), rz = (int) Math.floor(z / g);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] c = center(seed, rx + i, rz + k);
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != ConfigurationHelper.iceika || Config.throneSpacing <= 0) return;
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8; // область заселения этого чанка
        int[] c = nearest(world.getSeed(), x0 + 8, z0 + 8);
        if (c == null || Math.abs(c[0] - (x0 + 8)) > R + 16 || Math.abs(c[2] - (z0 + 8)) > R + 16) return;
        buildSlice(world, c[0], c[2], x0, z0);
        clean(world, c[0], c[2], x0 - 16, z0 - 16, 48);
    }

    /**
     * Divine заселяет чанки своими данжами и деревьями со сдвигом относительно нас, и они могут залезть в уже готовую
     * часть арены (висящий в воздухе данж со снежинками). Поэтому после каждого куска проверяем соседние готовые
     * куски: где по чертежу воздух, а стоит блок, - убираем его. Неготовые и незагруженные чанки не трогаем.
     */
    private static void clean(World w, int cx, int cz, int xFrom, int zFrom, int size) {
        int[] col = new int[256 * 2];
        for (int x = xFrom; x < xFrom + size; x++) {
            for (int z = zFrom; z < zFrom + size; z++) {
                int dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz >= R * R) continue;
                int chx = x >> 4, chz = z >> 4;
                if (!w.getChunkProvider()
                    .chunkExists(chx, chz)) continue;
                // кусок, к которому относится столбец, уже построен (его чанк заселён)
                int ax = (x - 8) >> 4, az = (z - 8) >> 4;
                if (!w.getChunkProvider()
                    .chunkExists(ax, az)
                    || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) continue;
                if (!column(dx, dz, col)) continue;
                for (int y = FLOOR; y < 80; y++) {
                    if (col[y * 2] != AIR || w.isAirBlock(x, y, z)) continue;
                    w.removeTileEntity(x, y, z);
                    w.setBlock(x, y, z, Blocks.air, 0, 2);
                }
            }
        }
    }

    // ---------------------------------------------------------------- запись в чанки

    private static void buildSlice(World w, int cx, int cz, int x0, int z0) {
        Set<Chunk> touched = new HashSet<Chunk>();
        List<int[]> lights = new ArrayList<int[]>();
        int[] col = new int[256 * 2];
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int dx = x - cx, dz = z - cz;
                if (!column(dx, dz, col)) continue;
                Chunk ch = w.getChunkFromBlockCoords(x, z);
                touched.add(ch);
                ExtendedBlockStorage[] arr = ch.getBlockStorageArray();
                for (int y = 1; y < 250; y++) {
                    int code = col[y * 2];
                    if (code == KEEP) continue;
                    Block b = block(code);
                    int meta = col[y * 2 + 1];
                    if (w.getBlock(x, y, z)
                        .hasTileEntity(w.getBlockMetadata(x, y, z))) w.removeTileEntity(x, y, z);
                    ExtendedBlockStorage s = arr[y >> 4];
                    if (s == null) {
                        if (b == Blocks.air) continue;
                        s = arr[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !w.provider.hasNoSky);
                    }
                    s.func_150818_a(x & 15, y & 15, z & 15, b);
                    s.setExtBlockMetadata(x & 15, y & 15, z & 15, meta);
                    if (b.getLightValue() > 0 || b == ModBlocks.brazier) lights.add(new int[] { x, y, z });
                }
            }
        }
        for (Chunk ch : touched) {
            ch.generateSkylightMap();
            ch.setChunkModified();
        }
        for (int[] l : lights) w.func_147451_t(l[0], l[1], l[2]);
    }

    // ---------------------------------------------------------------- что где стоит

    private static final int AIR = 0, ICY = 1, SNOW = 2, PACKED = 3, STONE = 4, GLASS = 5, LAMP = 6, BRAZIER = 7,
        ALTAR = 8;

    private static Block block(int code) {
        switch (code) {
            case ICY:
                return IceikaBlocks.icyBricks;
            case SNOW:
                return IceikaBlocks.snowBricks;
            case PACKED:
                return Blocks.packed_ice;
            case STONE:
                return IceikaBlocks.frozenStone;
            case GLASS:
                return IceikaBlocks.frostedGlass;
            case LAMP:
                return VanillaBlocks.lamp2;
            case BRAZIER:
                return ModBlocks.brazier;
            case ALTAR:
                return ModBlocks.throneAltar;
            default:
                return Blocks.air;
        }
    }

    private static void put(int[] col, int y, int code, int meta) {
        if (y <= 0 || y >= 250) return;
        col[y * 2] = code;
        col[y * 2 + 1] = meta;
    }

    private static int hash(int x, int z) {
        int h = x * 734287 + z * 912271;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    /** Нижняя граница свода арены над точкой на расстоянии r от центра. */
    private static int ceil(double r) {
        if (r >= R) return FLOOR + 12;
        return FLOOR + 12 + (int) (48 * Math.sqrt(1 - (r / R) * (r / R)));
    }

    /** Ближайший центр колонны кольца (радиус ring, count штук) и расстояние до него. */
    private static double ringDist(double dx, double dz, double ring, int count, double phase) {
        double a = Math.atan2(dz, dx) - phase, step = Math.PI * 2 / count;
        double k = Math.round(a / step);
        double ca = k * step + phase;
        double px = Math.cos(ca) * ring, pz = Math.sin(ca) * ring;
        return Math.sqrt((dx - px) * (dx - px) + (dz - pz) * (dz - pz));
    }

    /** Колонна-"песочные часы": толще у пола и у свода. */
    private static double hourglass(int y, int lo, int hi, double rMid, double rEnd) {
        double t = (y - lo) / (double) Math.max(1, hi - lo);
        return rMid + (rEnd - rMid) * Math.abs(t - 0.5) * 2;
    }

    /** Заполняет col[y] = {код блока, мета} для столбца (dx, dz) от центра. false - столбец не трогаем. */
    private static boolean column(int dx, int dz, int[] col) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > R + 4 && !inSpire(dx, dz)) return false;
        for (int i = 0; i < col.length; i += 2) col[i] = KEEP;

        // ---- арена
        int ce = ceil(r);
        if (r < R) {
            boolean ring = ((int) r / 8) % 2 == 0;
            put(col, FLOOR - 1, r < 12 || ring ? ICY : SNOW, 0);
            for (int y = FLOOR; y < ce; y++) put(col, y, AIR, 0);
            // свод: рёбра из ледяного кирпича каждые 15 градусов, между ними паковый лёд
            double ang = Math.toDegrees(Math.atan2(dz, dx)) + 360;
            boolean rib = ang % 15 < 1.6;
            for (int y = ce; y < ce + 3; y++) put(col, y, rib ? ICY : PACKED, 0);
            if (dx % 12 == 0 && dz % 12 == 0 && r > 6) put(col, ce, LAMP, 0);
            else if (hash(dx, dz) % 23 == 0 && ce - FLOOR > 14 && r > 6) {
                int len = 1 + hash(dz, dx) % 4;
                for (int y = ce - len; y < ce; y++) put(col, y, PACKED, 0);
            }
            // кольца колонн
            double pa = ringDist(dx, dz, 115, 32, 0), pb = ringDist(dx, dz, 70, 16, Math.PI / 16);
            for (int y = FLOOR; y < ce; y++) {
                if (pa <= hourglass(y, FLOOR, ce, 2.5, 4.5)) put(col, y, y < FLOOR + 3 ? ICY : PACKED, 0);
            }
            int pylon = FLOOR + 18;
            for (int y = FLOOR; y <= pylon; y++) {
                if (pb <= 2.2 && y < pylon) put(col, y, y < FLOOR + 3 ? ICY : PACKED, 0);
                if (pb <= 3.4 && y >= pylon - 1) put(col, y, ICY, 0); // площадка наверху
            }
            // жаровни вокруг центра
            if (ringDist(dx, dz, 30, 12, 0) < 0.5) {
                put(col, FLOOR, ICY, 0);
                put(col, FLOOR + 1, BRAZIER, 1);
            }
            throne(dx, dz, col);
        } else {
            for (int y = FLOOR - 1; y < FLOOR + 15; y++) put(col, y, ICY, 0); // стена у края
        }

        // ---- цитадель наверху
        if (r <= CITADEL) {
            for (int y = ce + 3; y < TOP; y++) put(col, y, STONE, 0); // толща между сводом и площадкой
            boolean rim = r > CITADEL - 2;
            put(col, TOP, rim ? ICY : r < HALL_R - 1 ? ((dx + dz & 1) == 0 ? ICY : SNOW) : SNOW, 0);
            for (int y = TOP + 1; y <= TOP + 60; y++) put(col, y, AIR, 0);
            if (rim && r > CITADEL - 1 && (dx + dz) % 2 == 0) put(col, TOP + 1, ICY, 0);
            // кольцо колонн во дворе
            if (ringDist(dx, dz, 27, 12, Math.PI / 12) < 0.5 && !(dz < -24 && Math.abs(dx) < 9)) {
                put(col, TOP + 1, ICY, 0);
                for (int y = TOP + 2; y <= TOP + 8; y++) put(col, y, PACKED, 0);
                put(col, TOP + 9, ICY, 0);
                put(col, TOP + 10, LAMP, 0);
            }
            hall(dx, dz, r, col);
        }
        spires(dx, dz, col);
        towers(dx, dz, col);
        return true;
    }

    /** Купольный зал цитадели с воротами на юг, лампами, сосульками и четырьмя тёплыми жаровнями. */
    private static void hall(int dx, int dz, double r, int[] col) {
        for (int dy = 1; dy <= HALL_H + 1; dy++) {
            double d = Math.sqrt((dx / (double) HALL_R) * (dx / (double) HALL_R) + (dy / (double) HALL_H) * (dy / (double) HALL_H)
                + (dz / (double) HALL_R) * (dz / (double) HALL_R));
            if (d > 1.0 || d < 0.93) continue;
            boolean gate = Math.abs(dx) <= 2 && dy <= 7 && dz < -14;
            if (gate) continue;
            int code = dy <= 3 ? ICY
                : dy >= 6 && dy <= 8 && (Math.round(Math.toDegrees(Math.atan2(dz, dx)) + 360) % 30) < 12 ? GLASS : PACKED;
            put(col, TOP + dy, code, 0);
        }
        if (Math.abs(dx) == 3 && dz >= -21 && dz <= -18) for (int dy = 1; dy <= 8; dy++) put(col, TOP + dy, ICY, 0);
        if (r < HALL_R - 2) {
            int top = -1;
            for (int dy = HALL_H; dy >= 4; dy--) {
                double d = Math.sqrt((dx / (double) HALL_R) * (dx / (double) HALL_R) + (dy / (double) HALL_H) * (dy / (double) HALL_H)
                    + (dz / (double) HALL_R) * (dz / (double) HALL_R));
                if (d < 0.93) {
                    top = dy;
                    break;
                }
            }
            if (top > 10 && dx % 6 == 0 && dz % 6 == 0) put(col, TOP + top, LAMP, 0);
            else if (top > 8 && hash(dx + 7, dz) % 9 == 0) {
                int len = 1 + hash(dz, dx + 3) % 3;
                for (int i = 0; i < len; i++) put(col, TOP + top - i, PACKED, 0);
            }
        }
        if (ringDist(dx, dz, 12, 4, Math.PI / 4) < 0.5) {
            put(col, TOP + 1, ICY, 0);
            put(col, TOP + 2, BRAZIER, 1);
        }
    }

    private static boolean inSpire(int dx, int dz) {
        for (int side = -1; side <= 1; side += 2) {
            int sx = dx - side * 9, sz = dz + 26;
            if (sx * sx + sz * sz <= 36) return true;
        }
        return false;
    }

    /** Два шпиля у ворот: 60 блоков, у основания радиус 5. */
    private static void spires(int dx, int dz, int[] col) {
        for (int side = -1; side <= 1; side += 2) {
            int sx = dx - side * 9, sz = dz + 26;
            double rr = Math.sqrt(sx * sx + sz * sz);
            if (rr > 5.5) continue;
            int h = 60;
            for (int dy = 1; dy <= h; dy++) {
                double rad = 4.8 * (1 - dy / (double) (h + 6)) + 0.4;
                if (rr <= rad) put(col, TOP + dy, dy < h / 3 ? ICY : PACKED, 0);
            }
            if (rr < 0.5) put(col, TOP + h + 1, LAMP, 0);
            for (int y = TOP - 6; y <= TOP; y++) if (rr <= 5) put(col, y, ICY, 0); // фундамент
        }
    }

    /** Винтовые лестницы вокруг ледяных столбов: из зала цитадели на пол арены. */
    private static void towers(int dx, int dz, int[] col) {
        for (int side = -1; side <= 1; side += 2) {
            int tx = dx - side * TOWER_X, tz = dz;
            double rt = Math.sqrt(tx * tx + tz * tz);
            if (rt > 5.6) continue;
            double r = Math.sqrt(dx * dx + dz * dz);
            int ce = ceil(r);
            double a = Math.atan2(tz, tx);
            if (a < 0) a += Math.PI * 2;
            int sector = (int) (a / (Math.PI * 2 / 16));
            for (int y = FLOOR; y <= TOP; y++) {
                if (y >= ce) put(col, y, AIR, 0); // шахта сквозь свод и толщу
                int stepSector = ((y - FLOOR) % 16 + 16) % 16;
                if (rt <= 1.5) put(col, y, ICY, 0); // столб
                else if (rt <= 4.6 && sector == stepSector) put(col, y, ICY, 0); // ступень
                else if (rt > 4.6 && rt <= 5.4 && sector == ((y - 1 - FLOOR) % 16 + 16) % 16 && y > FLOOR) {
                    put(col, y, PACKED, 0); // перильце
                }
            }
            if (rt <= 1.5) put(col, TOP + 1, ICY, 0);
            if (rt <= 1.5) put(col, TOP + 2, LAMP, 0);
        }
    }

    /** Гигантский трон на севере арены: помост в три ступени, сиденье, подлокотники, спинка с шипами, алтарь. */
    private static void throne(int dx, int dz, int[] col) {
        int tz = dz - 40;
        double rd = Math.sqrt(dx * dx + tz * tz);
        if (rd <= 9) put(col, FLOOR, ICY, 0);
        if (rd <= 7) put(col, FLOOR + 1, ICY, 0);
        if (rd <= 5) put(col, FLOOR + 2, ICY, 0);
        if (dx == 0 && dz == ALTAR_DZ) {
            put(col, FLOOR + ALTAR_DY, ALTAR, 0);
            put(col, FLOOR + ALTAR_DY + 1, AIR, 0);
        }
        int ax = Math.abs(dx);
        if (ax <= 2 && tz >= 0 && tz <= 3) for (int y = FLOOR + 3; y <= FLOOR + 4; y++) put(col, y, PACKED, 0); // сиденье
        if (ax >= 3 && ax <= 4 && tz >= 0 && tz <= 3) for (int y = FLOOR + 3; y <= FLOOR + 6; y++) put(col, y, ICY, 0);
        if (ax <= 4 && tz >= 4 && tz <= 5) {
            for (int y = FLOOR + 3; y <= FLOOR + 18; y++) put(col, y, ICY, 0); // спинка
            if (tz == 4 && ax % 2 == 0) {
                int spike = ax == 0 ? 7 : ax == 2 ? 5 : 3;
                for (int y = FLOOR + 19; y < FLOOR + 19 + spike; y++) put(col, y, PACKED, 0);
            }
        }
    }
}
