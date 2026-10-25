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
 * Висячие озёра: с потолка этажа (33 блока над полом) свисает каменный сталактит, внизу - чаша с водой мечтаний,
 * которая течёт вверх: гладь смотрит вниз, в {@link #HANG} блоках над травой - удочкой с пола достать. Внутри верх и
 * низ меняются местами ({@link com.divinerpgextended.block.BlockDreamWater}).
 */
public class VetheaLakes implements IWorldGenerator {

    /** Поверхности этажей. */
    public static final int[] FLOORS = { 16, 64, 112, 160 };

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World w, IChunkProvider gen, IChunkProvider prov) {
        if (w.provider.dimensionId != ConfigurationHelper.vethea || Config.vetheaLakeRarity <= 0) return;
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

    /** Высота глади висячего озера над травой. */
    public static final int HANG = 7;
    /** Радиус чаши. */
    static final int BOWL = 5;

    /** Нижний твёрдый блок потолка над (x, y, z) не выше limit или -1. */
    static int ceiling(World w, int x, int y, int z, int limit) {
        for (int h = y; h <= limit; h++) if (!w.isAirBlock(x, h, z)) return h;
        return -1;
    }

    /** Висячее озеро с центром в (x, z) на этаже floor; false - место не подошло (неровно, низкий потолок). */
    public static boolean hanging(World w, Random r, int x, int z, int floor) {
        int g = ground(w, x, z, floor);
        if (g < 0) return false;
        int s = g + HANG, top = BOWL + 1;
        for (int dx = -top; dx <= top; dx += 2) for (int dz = -top; dz <= top; dz += 2) { // под чашей - ровный пол и воздух
            int gg = ground(w, x + dx, z + dz, floor);
            if (gg < 0 || gg > g + 2) return false;
        }
        int c = ceiling(w, x, s, z, s + 40);
        if (c < 0 || c - s < 8) return false;
        net.minecraft.block.Block stone = VetheaBlocks.dreamStone, water = com.divinerpgextended.registry.ModBlocks.dreamWater;
        for (int dx = -top - 1; dx <= top + 1; dx++) for (int dz = -top - 1; dz <= top + 1; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            // сталактит: от края чаши (радиус BOWL+1) сужается к потолку (радиус 2)
            for (int y = s; y < c + 2; y++) {
                double k = (y - s) / (double) Math.max(1, c - s), rad = top + 0.5 - (top - 1.5) * k;
                if (d >= rad) continue;
                boolean bowl = d < BOWL && y <= s + 2;
                if (bowl) w.setBlock(x + dx, y, z + dz, water, 0, 2);
                else if (w.isAirBlock(x + dx, y, z + dz) || y <= s + 3) w.setBlock(x + dx, y, z + dz, stone, 0, 2);
            }
            // под гладью - воздух (деревья и кусты не мешают закинуть)
            if (d < top) for (int y = g + 1; y < s; y++) if (!w.isAirBlock(x + dx, y, z + dz)) w.setBlock(x + dx, y, z + dz, net.minecraft.init.Blocks.air, 0, 2);
        }
        return true;
    }

    /** Трава этажа в столбце около высоты floor (верхний блок с воздухом над ним) или -1. */
    static int ground(World w, int x, int z, int floor) {
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
