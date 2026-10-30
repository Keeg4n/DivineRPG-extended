package com.divinerpgextended.world;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * Огненная палата Короля скорчеров - гоночный трек под лавовым озером Ада (король - лихач). Оболочка, свод с окулюсом,
 * окна с лавой и вход (тоннель, ворота в скальном холме, крыльцо) - как у логова Смотрителя ({@link WatcherVault}); свой
 * зал: в центре помост в чёрно-белую клетку Divine, вокруг лавовый ров с четырьмя мостками в клетку; дальше трек из адского
 * кирпича - белая пунктирная разметка, финишная полоса в клетку на западе, бордюр по краю из красных и белых блоков. По
 * внутренней бровке трека - восемь огненных столбов в клетку (не на осях: по осям король срезает через мостки), на них
 * вечный огонь. Над воротами вместо ока - клетчатый флаг. Бой - {@link com.divinerpgextended.boss.KingRace}.
 */
public class ScorcherPit extends WatcherVault {

    /** Помост, ров (от и до), столбы (круг, толщина), разметка трека. */
    public static final double DAIS = 6, MOAT_IN = 6, MOAT_OUT = 9.5, POSTS = 12, POST_R = 1.3, LANE = 15.3;
    public static final int POST_COUNT = 8, POST_H = 8;

    public ScorcherPit(int cx, int cz) {
        super(cx, cz);
    }

    @Override
    protected boolean lit(Block b) {
        return super.lit(b) || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp1;
    }

    @Override
    protected void hall(int dx, int dz, double r, int[] col) {
        int t = top(r);
        put(col, FLOOR - 1, OBSIDIAN, 0);
        boolean bridge = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        double a = Math.atan2(dz, dx);
        int code = BRICK, meta = 0;
        if (r < DAIS - 0.8) code = CHECKER; // помост в клетку
        else if (r < MOAT_IN) code = OBSIDIAN; // бортик помоста
        else if (r < MOAT_OUT) code = bridge ? CHECKER : LAVA; // ров и мостки
        else if (r >= R - 1) code = OBSIDIAN;
        else if (r >= R - 2.2) { // бордюр: красный и белый через два блока
            boolean red = ((int) Math.floor(a * r / 2)) % 2 == 0;
            code = red ? CLAY : QUARTZ;
            meta = red ? 14 : 0;
        } else if (dx < 0 && Math.abs(dz) <= 1 && r > MOAT_OUT + 0.5) code = CHECKER; // финишная полоса
        else if (Math.abs(r - LANE) < 0.5 && ((int) Math.floor(a * r / 2)) % 2 == 0) {
            code = QUARTZ; // пунктир
            meta = 0;
        }
        put(col, FLOOR, code, meta);
        if (code == LAVA) put(col, FLOOR - 1, BRICK, 0);
        double pd = postDist(dx, dz);
        for (int y = FLOOR + 1; y <= t; y++) {
            int h = y - FLOOR;
            if (pd < POST_R && h <= POST_H) {
                put(col, y, h == POST_H ? NETHERRACK : (h / 2) % 2 == 0 ? CHECKER : BRICK, 0); // столб в клетку, сверху незеррак
                continue;
            }
            if (pd < 0.7 && h == POST_H + 1) {
                put(col, y, FIRE, 0); // вечный огонь на незерраке
                continue;
            }
            if (r < 1 && h == 1) {
                put(col, y, AIR, 0);
                continue;
            }
            if (wallCell(dx, dz, r, y, col, 1)) continue; // окна - оранжевое стекло
            put(col, y, AIR, 0);
        }
        vault(dx, dz, r, t, col);
    }

    /** Расстояние до ближайшего огненного столба. */
    static double postDist(double dx, double dz) {
        return com.divinerpgextended.world.LairDomes.ringDist(dx, dz, POSTS, POST_COUNT, Math.PI / POST_COUNT); // не на осях: по ним король срезает
    }

    /** Над воротами - клетчатый флаг в рамке из адского кирпича. */
    @Override
    protected int[] emblem(int row, int dz) {
        return Math.abs(dz) == 2 && row != 1 ? new int[] { BRICK, 0 } : new int[] { CHECKER, 0 };
    }

    /** Палата построена по-новому (клетка в центре пола), а не старым шаром в небе. */
    public static boolean isNew(World w, int cx, int cz) {
        return w.getBlock(cx, FLOOR, cz) == net.divinerpg.utils.blocks.VanillaBlocks.checker;
    }

    /** Старая палата (шар в небе до 0.99.51): там пол на высоте 40 с лампой в центре. */
    public static final int OLD_FLOOR = 40;

    public static boolean isOld(World w, int cx, int cz) {
        Block b = w.getBlock(cx, OLD_FLOOR, cz);
        return !isNew(w, cx, cz) && (b == net.divinerpg.utils.blocks.VanillaBlocks.lamp1 || b == Blocks.nether_brick);
    }
}
