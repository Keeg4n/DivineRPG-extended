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
 * Аркана - лабиринт без глухих тупиков. В Divine каждый чанк Арканы - стопка из четырёх комнат 16x16 (этажи с полом на y =
 * 8, 16, 24, 32), каждая из 21 случайного шаблона. Проёмы у шаблонов только по центру стороны (z или x = 7..8, высота 2), и
 * у большинства одна-три стороны глухие; две соседние комнаты соединены, только если проём есть у обеих - поэтому этажи
 * рвутся на острова, и порой проще перенести портал.
 * <p>
 * Каждый стык решается правилом лабиринта (с 0.99.120; раньше любой проём с одной стороны прорубался насквозь - открыто
 * было ~80% стыков, и по Аркане ходили напрямик):
 * <ul>
 * <li>каждый чанк на каждом этаже обязательно открывает один стык - восточный или южный (выбор постоянный, от координат);
 * не прорубить - открывает второй. Это лабиринт "бинарное дерево": этаж связный, но ходы петляют;</li>
 * <li>родной проход Divine (проём у обеих комнат) остаётся открытым с шансом {@link #LOOP} - петли, чтобы не ходить
 * длинными коридорами;</li>
 * <li>остальные стыки закрыты: проём, который в него смотрит, закладывается кладкой стены рядом - дверей в стену нет.</li>
 * </ul>
 * Проход 2x2 по центру стыка: с каждой стороны - до первого свободного места в комнате, не глубже 7 блоков; если с
 * какой-то стороны свободного места не нашлось - стык не трогаем. Сундуки, спавнеры и прочие тайлы не ломаем.
 */
public class ArcanaDoors implements IWorldGenerator {

    private static final int[] LEVELS = { 8, 16, 24, 32 };
    private static final int MAX_DEPTH = 7;
    /** Доля родных проходов Divine, которые остаются открытыми сверх обязательных (петли лабиринта). */
    public static final float LOOP = 0.3F;

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (!Config.arcanaNoDeadEnds || world.provider.dimensionId != ConfigurationHelper.arcana) return;
        // стыки не только этого чанка, но и соседей: заселение чанка (залы боссов Divine) перестраивает и соседние комнаты, и
        // уже решённый стык снова получал проём в стену. Решения постоянные (от координат), повторный проход их не меняет
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) junctions(world, chunkX + dx, chunkZ + dz);
    }

    /** Восточный и южный стыки чанка на всех этажах - если оба соседа уже есть (чанков не создаём). */
    private static void junctions(World world, int chunkX, int chunkZ) {
        IChunkProvider cp = world.getChunkProvider();
        if (!cp.chunkExists(chunkX, chunkZ) || !cp.chunkExists(chunkX + 1, chunkZ) || !cp.chunkExists(chunkX, chunkZ + 1)) return;
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        for (int y0 : LEVELS) {
            // восточный стык с чанком (x+1) и южный с (z+1): каждый стык обрабатывает ровно один чанк;
            // один из них обязательный (связность этажа), выбор постоянный от координат; не прорубился - обязателен второй
            int h = (chunkX * 73856093) ^ (chunkZ * 19349663) ^ (y0 * 83492791);
            boolean east = (h >>> 7 & 1) == 1;
            boolean loopE = (h >>> 9 & 1023) < LOOP * 1024, loopS = (h >>> 19 & 1023) < LOOP * 1024;
            if (east) door(world, x0 + 7, y0, z0 + 15, 0, 1, !door(world, x0 + 15, y0, z0 + 7, 1, 0, true, loopE), loopS);
            else door(world, x0 + 15, y0, z0 + 7, 1, 0, !door(world, x0 + 7, y0, z0 + 15, 0, 1, true, loopS), loopE);
        }
    }

    /** Открыт ли стык (сечение 2x2 свободно с обеих сторон границы) - для самопроверки. */
    public static boolean open(World w, int x, int y0, int z, int ax, int az) {
        return free(w, x, y0, z, ax, az) && free(w, x + ax, y0, z + az, ax, az);
    }

    /** Проём только с одной стороны стыка (дверь в стену) - для самопроверки. */
    public static boolean blind(World w, int x, int y0, int z, int ax, int az) {
        return free(w, x, y0, z, ax, az) != free(w, x + ax, y0, z + az, ax, az);
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
     * двух клеток проёма. force - обязательный стык (прорубить), loop - родной проход оставить. @return открыт ли стык
     */
    private static boolean door(World w, int x, int y0, int z, int ax, int az, boolean force, boolean loop) {
        int bx = x + ax, bz = z + az; // первый ряд соседней комнаты
        boolean fa = free(w, x, y0, z, ax, az), fb = free(w, bx, y0, bz, ax, az);
        if (!force && !(fa && fb && loop)) { // стык закрыт: проёмы, что в него смотрят, заложить
            if (fa) seal(w, x, y0, z, ax, az);
            if (fb) seal(w, bx, y0, bz, ax, az);
            return false;
        }
        if (fa && fb) return true; // и так открыто
        int dA = depth(w, x, y0, z, -ax, -az), dB = depth(w, bx, y0, bz, ax, az);
        if (dA < 0 || dB < 0) { // с какой-то стороны не дорубиться - стык закрыт, проём заложить
            if (fa) seal(w, x, y0, z, ax, az);
            if (fb) seal(w, bx, y0, bz, ax, az);
            return false;
        }
        carve(w, x, y0, z, -ax, -az, dA, ax, az);
        carve(w, bx, y0, bz, ax, az, dB, ax, az);
        return true;
    }

    /** Заложить сечение 2x2 в ряду (x, z) кладкой стены рядом с проёмом; тайлы не трогаем. */
    private static void seal(World w, int x, int y0, int z, int ax, int az) {
        int px = az, pz = ax; // вдоль стены
        Block wall = null;
        int meta = 0;
        for (int[] at : new int[][] { { x - px, z - pz }, { x + 2 * px, z + 2 * pz } }) {
            Block b = w.getBlock(at[0], y0 + 1, at[1]);
            int m = w.getBlockMetadata(at[0], y0 + 1, at[1]);
            if (b.getMaterial().blocksMovement() && b.isOpaqueCube() && !b.hasTileEntity(m) && b != Blocks.bedrock) {
                wall = b;
                meta = m;
                break;
            }
        }
        if (wall == null) return;
        for (int s = 0; s <= 1; s++) for (int y = y0 + 1; y <= y0 + 2; y++) {
            int cx = x + px * s, cz = z + pz * s;
            if (!blocks(w, cx, y, cz)) w.setBlock(cx, y, cz, wall, meta, 2);
        }
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
                    .blocksMovement() || b.hasTileEntity(w.getBlockMetadata(cx, y, cz)) || b == Blocks.bedrock || b instanceof net.minecraft.block.BlockDoor
                    || b.getClass().getSimpleName().contains("Door")) continue; // двери Арканы (с ключами) не ломаем
                w.setBlock(cx, y, cz, Blocks.air, 0, 2);
            }
        }
    }
}
