package com.divinerpgextended.world.twilight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Большая постройка, заданная функцией "что стоит в столбце (dx, dz) от центра" - как Ледяной Трон. Каждый чанк при
 * заселении достраивает свой кусок прямо в хранилище чанка (без цепной генерации и пересчёта света на каждый блок),
 * а потом из соседних готовых кусков убирает то, что туда положило заселение Divine (деревья, цветы) поверх чертежа.
 */
public abstract class ColumnStructure {

    /** Код "столбец здесь не трогаем" и "воздух". */
    public static final int KEEP = -1, AIR = 0;
    /**
     * Фундамент: с этой высоты вниз, пока под ним пусто (воздух, жидкость, растения), кладётся {@link #foundation()} - до
     * {@link #FOUNDATION_DEPTH} блоков. Постройка стоит на земле, а не висит над пещерой.
     */
    public static final int FOUNDATION = -2, FOUNDATION_DEPTH = 48;

    /** Чем заполнять фундамент. */
    protected Block foundation() {
        return Blocks.stone;
    }

    /** Блок и мета по коду (коды > 0 - свои у каждой постройки). */
    protected abstract Block block(int code, int meta);

    /** Заполнить col[y * 2] = код, col[y * 2 + 1] = мета; false - столбец не входит в постройку. */
    protected abstract boolean column(int dx, int dz, int[] col);

    /** Мета блока по коду (лампы Divine - один блок на много метаданных). */
    protected int metaOf(int code, int meta) {
        return meta;
    }

    /** Блоки, после которых нужно пересчитать свет (светятся). */
    protected boolean lit(Block b) {
        return b.getLightValue() > 0;
    }

    public static void put(int[] col, int y, int code, int meta) {
        if (y <= 0 || y >= 255) return;
        col[y * 2] = code;
        col[y * 2 + 1] = meta;
    }

    public static int hash(int x, int z) {
        int h = x * 734287 + z * 912271;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    /** Достроить кусок постройки с центром (cx, cz) в области заселения [x0, x0 + 16) x [z0, z0 + 16). */
    public void buildSlice(World w, int cx, int cz, int x0, int z0) {
        Set<Chunk> touched = new HashSet<Chunk>();
        List<int[]> lights = new ArrayList<int[]>();
        // что поменяли в хранилище чанка напрямую - клиент не узнает сам (чанк мог уже уйти игроку): помечаем к отправке
        List<int[]> changed = new ArrayList<int[]>();
        int[] col = new int[256 * 2];
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int i = 0; i < col.length; i += 2) col[i] = KEEP;
                if (!column(x - cx, z - cz, col)) continue;
                Chunk ch = w.getChunkFromBlockCoords(x, z);
                touched.add(ch);
                ExtendedBlockStorage[] arr = ch.getBlockStorageArray();
                for (int y = 1; y < 255; y++) {
                    int code = col[y * 2];
                    if (code == KEEP) continue;
                    if (code == FOUNDATION) {
                        Block f = foundation();
                        for (int yy = y; yy > Math.max(1, y - FOUNDATION_DEPTH); yy--) {
                            Block was = w.getBlock(x, yy, z);
                            if (!(was.isAir(w, x, yy, z) || was.getMaterial().isLiquid() || was.getMaterial().isReplaceable() || !was.isOpaqueCube())) break;
                            ExtendedBlockStorage fs = arr[yy >> 4];
                            if (fs == null) fs = arr[yy >> 4] = new ExtendedBlockStorage(yy >> 4 << 4, !w.provider.hasNoSky);
                            fs.func_150818_a(x & 15, yy & 15, z & 15, f);
                            fs.setExtBlockMetadata(x & 15, yy & 15, z & 15, 0);
                            changed.add(new int[] { x, yy, z });
                        }
                        continue;
                    }
                    int meta = col[y * 2 + 1];
                    Block b = code == AIR ? Blocks.air : block(code, meta);
                    if (code != AIR) meta = metaOf(code, meta);
                    if (w.getBlock(x, y, z).hasTileEntity(w.getBlockMetadata(x, y, z))) w.removeTileEntity(x, y, z);
                    ExtendedBlockStorage s = arr[y >> 4];
                    if (s == null) {
                        if (b == Blocks.air) continue;
                        s = arr[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !w.provider.hasNoSky);
                    }
                    s.func_150818_a(x & 15, y & 15, z & 15, b);
                    s.setExtBlockMetadata(x & 15, y & 15, z & 15, meta);
                    changed.add(new int[] { x, y, z });
                    if (lit(b)) lights.add(new int[] { x, y, z });
                }
            }
        }
        for (Chunk ch : touched) {
            ch.generateSkylightMap();
            // свет по столбцам не знает соседей: под большой постройкой над пустотой остаётся чёрный столб. Пусть
            // сервер досчитает свет как для обычного острова, до отправки чанка игроку - иначе это делает клиент
            ch.isLightPopulated = false;
            ch.setChunkModified();
        }
        for (int[] l : lights) w.func_147451_t(l[0], l[1], l[2]);
        resend(w, changed);
        decorate(w, cx, cz, x0, z0);
    }

    /**
     * Сказать игрокам, видящим эти чанки, про блоки, записанные мимо мира: без этого уже отправленный клиенту соседний
     * чанк остаётся старым кусками (дыры и "стекло" до первого обновления рядом). Больше 64 блоков в чанке - игра сама
     * перешлёт изменённые секции целиком.
     */
    static void resend(World w, List<int[]> changed) {
        if (!(w instanceof net.minecraft.world.WorldServer) || changed.isEmpty()) return;
        net.minecraft.server.management.PlayerManager pm = ((net.minecraft.world.WorldServer) w).getPlayerManager();
        for (int[] c : changed) pm.markBlockForUpdate(c[0], c[1], c[2]);
    }

    /**
     * Блоки с данными (статуи, сундуки) - ставятся через мир, а не в хранилище чанка: только те, что попадают в область
     * заселения [x0, x0 + 16) x [z0, z0 + 16) (см. {@link #place}).
     */
    protected void decorate(World w, int cx, int cz, int x0, int z0) {}

    /** Поставить блок с данными, если (cx + dx, cz + dz) в области заселения; true - поставлен. */
    protected static boolean place(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, Block b, int meta) {
        int x = cx + dx, z = cz + dz;
        if (x < x0 || x >= x0 + 16 || z < z0 || z >= z0 + 16) return false;
        w.setBlock(x, y, z, b, meta, 2);
        return true;
    }

    /**
     * Убрать из готовых соседних кусков то, что туда положило заселение Divine поверх воздуха чертежа (в пределах
     * yFrom..yTo). Неготовые и незагруженные чанки не трогаем.
     */
    public void clean(World w, int cx, int cz, int xFrom, int zFrom, int size, int yFrom, int yTo) {
        int[] col = new int[256 * 2];
        for (int x = xFrom; x < xFrom + size; x++) {
            for (int z = zFrom; z < zFrom + size; z++) {
                if (!w.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
                int ax = (x - 8) >> 4, az = (z - 8) >> 4; // кусок, к которому относится столбец, уже построен
                if (!w.getChunkProvider().chunkExists(ax, az) || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) continue;
                for (int i = 0; i < col.length; i += 2) col[i] = KEEP;
                if (!column(x - cx, z - cz, col)) continue;
                for (int y = yFrom; y <= yTo; y++) {
                    if (col[y * 2] != AIR || w.isAirBlock(x, y, z)) continue;
                    if (w.getBlock(x, y, z).hasTileEntity(w.getBlockMetadata(x, y, z)) || w.getBlock(x, y, z) == com.divinerpgextended.registry.ModBlocks.arenaAltar) continue; // сундуки и алтари постройки ({@link #decorate})
                    w.removeTileEntity(x, y, z);
                    w.setBlock(x, y, z, Blocks.air, 0, 2);
                }
            }
        }
    }
}
