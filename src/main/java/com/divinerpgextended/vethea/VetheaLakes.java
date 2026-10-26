package com.divinerpgextended.vethea;

import java.util.Random;

import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Озёра на этажах Ветеи - рыбалка мечтаний: овальная вода в траве этажа, глубиной до 3 (в пределах верхних 4 блоков
 * плиты, их можно копать - нижнюю часть плиты не трогаем, {@link VetheaFloors}).
 * <p>
 * Озёра в потолке: в низу плиты над этажом вырезана чаша с водой мечтаний, которая течёт вверх: гладь смотрит вниз,
 * вровень с потолком. Доплыл до неё - висишь вверх ногами, ногами в воде, и рыбачишь
 * ({@link com.divinerpgextended.block.BlockDreamWater}).
 */
public class VetheaLakes implements IWorldGenerator {

    /** Поверхности этажей. */
    public static final int[] FLOORS = { 16, 64, 112, 160 };

    /** Для замеров: сколько наносекунд ушло на озёра и на сколько чанков. */
    public static long nanos, chunks;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World w, IChunkProvider gen, IChunkProvider prov) {
        if (w.provider.dimensionId != ConfigurationHelper.vethea || Config.vetheaLakeRarity <= 0) return;
        long t0 = System.nanoTime();
        try {
            lakes(chunkX, chunkZ, w);
        } finally {
            nanos += System.nanoTime() - t0;
            chunks++;
        }
    }

    private void lakes(int chunkX, int chunkZ, World w) {
        Random r = new Random(w.getSeed() ^ chunkX * 341873128712L ^ chunkZ * 132897987541L ^ 0x1A4EL);
        for (int f : FLOORS) {
            if (r.nextInt(Config.vetheaLakeRarity) != 0) continue;
            lake(w, r, chunkX * 16 + 8 + r.nextInt(4), chunkZ * 16 + 8 + r.nextInt(4), f);
        }
        if (Config.vetheaHangingLakeRarity <= 0) return;
        for (int f : FLOORS) {
            if (r.nextInt(Config.vetheaHangingLakeRarity) != 0) continue;
            hanging(w, r, chunkX * 16 + 16 + r.nextInt(4), chunkZ * 16 + 16 + r.nextInt(4), f);
        }
    }

    /** Наименьшая высота потолка над травой, где ставим озеро в потолке. */
    static final int MIN_CEILING = 10;

    /** Нижний твёрдый блок потолка над (x, y, z) не выше limit или -1. */
    static int ceiling(World w, int x, int y, int z, int limit) {
        for (int h = y; h <= limit; h++) if (!w.isAirBlock(x, h, z)) return h;
        return -1;
    }

    /**
     * Озеро в потолке с центром в (x, z) на этаже floor: овальная чаша вырезана в низу плиты над этажом, гладь вровень с
     * потолком; false - место не подошло (потолок неровный или низкий).
     */
    public static boolean hanging(World w, Random r, int x, int z, int floor) {
        int g = ground(w, x, z, floor);
        if (g < 0) return false;
        int c = ceiling(w, x, g + 3, z, g + 45);
        if (c < 0 || c - g < MIN_CEILING) return false;
        int rx = 5 + r.nextInt(2), rz = 4 + r.nextInt(2);
        for (int dx = -rx - 1; dx <= rx + 1; dx++) for (int dz = -rz - 1; dz <= rz + 1; dz++) {
            double d = Math.pow(dx / (rx + 1.0), 2) + Math.pow(dz / (rz + 1.0), 2);
            if (d >= 1) continue;
            // вокруг и над чашей - сплошная плита: вода не вытечет вверх и вбок
            if (!w.isAirBlock(x + dx, c - 1, z + dz) || w.isAirBlock(x + dx, c, z + dz)) return false;
            for (int y = c; y <= c + 4; y++) if (w.isAirBlock(x + dx, y, z + dz) || w.getBlock(x + dx, y, z + dz).getMaterial().isLiquid()) return false;
        }
        net.minecraft.block.Block water = com.divinerpgextended.registry.ModBlocks.dreamWater;
        for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) {
            double d = Math.pow(dx / (double) rx, 2) + Math.pow(dz / (double) rz, 2);
            if (d >= 1) continue;
            int depth = d < 0.3 ? 3 : d < 0.7 ? 2 : 1;
            for (int y = c; y < c + depth; y++) w.setBlock(x + dx, y, z + dz, water, 0, 2);
        }
        return true;
    }

    /** Трава этажа в столбце около высоты floor (верхний блок с воздухом над ним) или -1. */
    public static int ground(World w, int x, int z, int floor) {
        for (int y = floor + 4; y >= floor - 4; y--)
            if (w.getBlock(x, y, z) == VetheaBlocks.dreamGrass && w.isAirBlock(x, y + 1, z) && w.isAirBlock(x, y + 3, z)) return y;
        return -1;
    }

    /** Озеро 12 x 12 с углом в (x, z) на этаже floor; false - место не подошло. */
    public static boolean lake(World w, Random r, int x, int z, int floor) {
        int lo = 999, hi = -1;
        for (int i = 0; i < 12; i++) for (int k = 0; k < 12; k++) {
            int y = ground(w, x + i, z + k, floor);
            if (y < 0) return false;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        if (hi - lo > 1) return false;
        int rx = 3 + r.nextInt(2), rz = 3 + r.nextInt(2);
        for (int i = 0; i < 12; i++) for (int k = 0; k < 12; k++) {
            double d = Math.pow((i - 5.5) / rx, 2) + Math.pow((k - 5.5) / rz, 2);
            if (d >= 1) continue;
            int top = ground(w, x + i, z + k, floor), depth = d < 0.35 ? 3 : d < 0.75 ? 2 : 1;
            for (int y = lo + 1; y <= top; y++) w.setBlock(x + i, y, z + k, Blocks.air, 0, 2);
            for (int y = lo - depth + 1; y <= lo; y++) w.setBlock(x + i, y, z + k, Blocks.water, 0, 2);
            w.setBlock(x + i, lo - depth, z + k, VetheaBlocks.dreamDirt, 0, 2);
        }
        return true;
    }
}
