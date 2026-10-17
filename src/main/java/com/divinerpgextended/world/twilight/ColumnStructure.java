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
                    w.removeTileEntity(x, y, z);
                    w.setBlock(x, y, z, Blocks.air, 0, 2);
                }
            }
        }
    }
}
