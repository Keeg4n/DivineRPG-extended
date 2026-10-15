package com.divinerpgextended.world;

import java.util.Random;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Аркана без тупиков. В Divine каждый чанк Арканы - стопка из четырёх комнат 16x16 (этажи с полом на y = 8, 16, 24,
 * 32), каждая из 21 случайного шаблона. Проёмы у шаблонов только по центру стороны (z или x = 7..8, высота 2), и у
 * большинства одна-три стороны глухие; две соседние комнаты соединены, только если проём есть у обеих - поэтому этажи
 * рвутся на острова, и порой проще перенести портал.
 * <ul>
 * <li>Если проём есть только у одной из двух комнат (проход упирается в стену) - прорубаем его насквозь.</li>
 * <li>Если обе стороны глухие - оставляем стену, кроме одного стыка на чанк и этаж: каждый чанк открывает либо
 * восточный, либо южный стык (выбор постоянный, от координат). Это лабиринт "бинарное дерево": этаж не распадается
 * на острова, но не превращается в сплошную сетку - у многих комнат остаётся 2-3 выхода.</li>
 * </ul>
 * Проход 2x2 по центру стыка: с каждой стороны - до первого свободного места в комнате, не глубже 7 блоков; если с
 * какой-то стороны свободного места не нашлось - стык не трогаем. Сундуки, спавнеры и прочие тайлы не ломаем.
 */
public class ArcanaDoors implements IWorldGenerator {

    private static final int[] LEVELS = { 8, 16, 24, 32 };
    private static final int MAX_DEPTH = 7;

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (!Config.arcanaNoDeadEnds || world.provider.dimensionId != ConfigurationHelper.arcana) return;
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        for (int y0 : LEVELS) {
            // восточный стык с чанком (x+1) и южный с (z+1): каждый стык обрабатывает ровно один чанк;
            // один из них обязательный (связность этажа), выбор постоянный от координат
            boolean east = (((chunkX * 73856093) ^ (chunkZ * 19349663) ^ (y0 * 83492791)) >>> 7 & 1) == 1;
            door(world, x0 + 15, y0, z0 + 7, 1, 0, east);
            door(world, x0 + 7, y0, z0 + 15, 0, 1, !east);
        }
    }

    private static boolean blocks(World w, int x, int y, int z) {
        return w.getBlock(x, y, z)
            .getMaterial()
            .blocksMovement();
    }

    /** Свободно ли сечение прохода 2x2 в точке (смещение вдоль стыка - по поперечной оси). */
    private static boolean free(World w, int x, int y0, int z, int ax, int az) {
        int px = az, pz = ax; // поперёк направления прохода
        for (int s = 0; s <= 1; s++) for (int y = y0 + 1; y <= y0 + 2; y++) {
            if (blocks(w, x + px * s, y, z + pz * s)) return false;
        }
        return true;
    }

    /**
     * Стык между клеткой (x, z) и следующей по оси (ax, az). x, z - последний ряд этой комнаты, z (или x) - левая из
     * двух клеток проёма.
     */
    private static void door(World w, int x, int y0, int z, int ax, int az, boolean force) {
        int bx = x + ax, bz = z + az; // первый ряд соседней комнаты
        if (free(w, x, y0, z, ax, az) && free(w, bx, y0, bz, ax, az)) return; // и так открыто
        int dA = depth(w, x, y0, z, -ax, -az), dB = depth(w, bx, y0, bz, ax, az);
        if (dA < 0 || dB < 0) return; // с какой-то стороны не дорубиться - не трогаем
        if (dA > 0 && dB > 0 && !force) return; // обе стороны глухие и стык не обязательный - пусть будет стена
        carve(w, x, y0, z, -ax, -az, dA, ax, az);
        carve(w, bx, y0, bz, ax, az, dB, ax, az);
    }

    /** Сколько рядов от границы вглубь (dx, dz) надо прорубить до свободного сечения; -1 - не найдено. */
    private static int depth(World w, int x, int y0, int z, int dx, int dz) {
        for (int d = 0; d <= MAX_DEPTH; d++) {
            if (free(w, x + dx * d, y0, z + dz * d, Math.abs(dx), Math.abs(dz))) return d;
        }
        return -1;
    }

    /** Прорубить n рядов от границы вглубь (dx, dz), сечение 2x2; тайлы (сундуки, спавнеры) не трогаем. */
    private static void carve(World w, int x, int y0, int z, int dx, int dz, int n, int ax, int az) {
        int px = az, pz = ax;
        for (int d = 0; d < n; d++) {
            for (int s = 0; s <= 1; s++) for (int y = y0 + 1; y <= y0 + 2; y++) {
                int cx = x + dx * d + px * s, cz = z + dz * d + pz * s;
                Block b = w.getBlock(cx, y, cz);
                if (!b.getMaterial()
                    .blocksMovement() || b.hasTileEntity(w.getBlockMetadata(cx, y, cz)) || b == Blocks.bedrock) continue;
                w.setBlock(cx, y, cz, Blocks.air, 0, 2);
            }
        }
    }
}
