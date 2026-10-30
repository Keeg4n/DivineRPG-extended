package com.divinerpgextended.world;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.divinerpgextended.world.twilight.ColumnStructure;

/**
 * Логово Смотрителя: обсерватория Ока под лавовым озером Ада. Зал радиусом {@link #R} у самого дна, оболочка из обсидиана,
 * над сводом - слой лавы; в центре свода - окулюс из стекла, сквозь него видно лаву над головой. На полу - мозаика ока
 * (белок из кварца, радужка из красной глины, обсидиановый зрачок) и обсидиановые лучи к стенам. Восемь обсидиановых колонн
 * с кварцевыми базами, поясами адского кирпича и лампами - укрытия от взгляда босса. В стенах - окна из красного стекла с
 * лавой за ними, на своде - звёзды из светокамня и лучи от окулюса.
 * <p>
 * Вход - наклонный тоннель от берега (+X): ступени-полублоки, рёбра из адского кирпича со светокамнем, лампы в стенах; у
 * зала - портал с кварцевыми колоннами. Снаружи - ворота в отвесной стене скального холма (пилоны, колонны, герб логова) и
 * короткое крыльцо: в скале - просто вход в обрыве, над лавой - каменистый островок с мостками на сваях.
 * Зал постоянный: взрывы босса его не ломают ({@link com.divinerpgextended.boss.WatcherHunt}). Логово старого вида
 * (с завалом) перестраивается в новое, когда туда приходит игрок, а Смотрителя нет ({@link #rebuild}).
 */
public class WatcherVault extends ColumnStructure {

    /** Радиус зала, пол (блок пола; стоят на FLOOR + 1), верх зала в центре. */
    public static final int R = 22, FLOOR = 7, TOP = FLOOR + 19, LAVA_TOP = 31;
    /** Тоннель: подъём на 1 блок за 2 по горизонтали до высоты над лавой, потом прямо и причал. */
    public static final int TUNNEL_Y = 33, TUNNEL_FLAT = 6;
    /** Колонны-укрытия: число, радиус круга, толщина. */
    public static final int PILLARS = 8, PILLAR_RING = 15;
    public static final double PILLAR_R = 1.75;
    /** Крыльцо перед воротами (длина); скальный холм над воротами (длина вглубь, полуширина). */
    static final int PIER = 5, MOUND_L = 12, MOUND_W = 7;

    protected static final int OBSIDIAN = 1, NETHERRACK = 2, BRICK = 4, GLOWSTONE = 5, QUARTZ = 6, LAMP = 7, LAVA = 8, SLAB = 9, CLAY = 10, GLASS = 11,
        FENCE = 12, CLEAR = 13, CHECKER = 14, FIRE = 15;

    public WatcherVault(int cx, int cz) {}

    @Override
    protected Block block(int code, int meta) {
        switch (code) {
            case OBSIDIAN: return Blocks.obsidian;
            case NETHERRACK: return Blocks.netherrack;
            case BRICK: return Blocks.nether_brick;
            case GLOWSTONE: return Blocks.glowstone;
            case QUARTZ: return Blocks.quartz_block;
            case LAMP: return net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
            case LAVA: return Blocks.lava;
            case SLAB: return Blocks.stone_slab;
            case CLAY: return Blocks.stained_hardened_clay;
            case GLASS: return Blocks.stained_glass;
            case FENCE: return Blocks.nether_brick_fence;
            case CLEAR: return Blocks.glass;
            case CHECKER: return net.divinerpg.utils.blocks.VanillaBlocks.checker; // чёрно-белая клетка Divine
            case FIRE: return Blocks.fire;
            default: return Blocks.air;
        }
    }

    @Override
    protected int metaOf(int code, int meta) {
        return code == LAMP ? 6 : meta; // фиолетовая лампа Divine
    }

    @Override
    protected Block foundation() {
        return Blocks.nether_brick; // сваи причала и башен
    }

    @Override
    protected boolean lit(Block b) {
        return b == Blocks.glowstone || b == Blocks.lava || b == Blocks.fire || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
    }

    /** Верх зала на расстоянии r от центра: купол, у стен ниже. */
    public static int top(double r) {
        return r < R - 6 ? TOP : TOP - (int) Math.ceil((r - (R - 6)) * 1.1);
    }

    /** Внутри зала (оболочка и тоннель - нет). */
    public static boolean interior(int dx, int y, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        return r < R && y > FLOOR && y <= top(r);
    }

    /** Высота, где висит босс (низ его рамки - ниже на половину роста). */
    public static int bossY() {
        return FLOOR + 9;
    }

    /** Центр ближайшей колонны {x, z} от центра зала. */
    public static double[] pillar(double dx, double dz) {
        double step = Math.PI * 2 / PILLARS, a = Math.atan2(dz, dx) - step / 2;
        double ca = Math.round(a / step) * step + step / 2;
        return new double[] { Math.cos(ca) * PILLAR_RING, Math.sin(ca) * PILLAR_RING };
    }

    // ---------------------------------------------------------------- зал

    /** Око на полу: внутри "миндалины" вдоль оси X (grow - расширить на столько блоков). */
    static boolean lens(double dx, double dz, double grow) {
        double w = 15.5 + grow, half = 8.5 * (1 - (dx / w) * (dx / w)) + grow;
        return Math.abs(dx) < w && Math.abs(dz) <= half;
    }

    /** Пол зала: мозаика ока, лучи, светокамни у стен. */
    static void floor(int dx, int dz, double r, int[] col) {
        put(col, FLOOR - 1, OBSIDIAN, 0);
        int code = BRICK, meta = 0;
        if (lens(dx, dz, 0)) {
            double a = Math.atan2(dz, dx);
            if (!lens(dx, dz, -1)) code = OBSIDIAN; // контур века
            else if (r < 2.3) code = OBSIDIAN; // зрачок
            else if (r < 5.5) {
                code = CLAY;
                meta = Math.abs(Math.sin(a * 6)) > 0.85 ? 1 : 14; // радужка с прожилками
            } else if (r < 6.3) {
                code = CLAY;
                meta = 15;
            } else {
                code = QUARTZ;
                if (hash(dx * 17 + 3, dz * 29 - 5) % 19 == 0) {
                    code = CLAY; // сосудики на белке
                    meta = 14;
                }
            }
        } else {
            double[] p = pillar(dx, dz);
            double ray = Math.abs(Math.sin(Math.atan2(dz, dx) * 4)) * r; // 8 лучей по осям и диагоналям
            if (Math.hypot(dx - p[0], dz - p[1]) < PILLAR_R + 0.8) code = OBSIDIAN;
            else if (r > 16.4 && r < 17.4) {
                code = QUARTZ;
                meta = 1;
            } else if (ray < 0.7 && r > 17) code = Math.abs(r - 20) < 0.7 ? GLOWSTONE : OBSIDIAN;
            else if (r >= R - 1) code = OBSIDIAN;
        }
        put(col, FLOOR, code, meta);
    }

    /** Окно в стене: 11 штук через 30 градусов (на востоке - вход), по 3 блока в ширину. */
    protected static boolean window(int dx, int dz, double r) {
        double step = Math.PI / 6, a = Math.atan2(dz, dx);
        long k = Math.round(a / step);
        return k != 0 && Math.abs(a - k * step) * r <= 1.5;
    }

    protected static final int WIN_LO = FLOOR + 2, WIN_HI = FLOOR + 7;

    /** Ячейка у стены зала: окно (подоконник, стекло, перемычка); true - поставлена. glass - мета стекла. */
    protected static boolean wallCell(int dx, int dz, double r, int y, int[] col, int glass) {
        if (r < R - 1 || !window(dx, dz, r)) return false;
        if (y == WIN_LO - 1 || y == WIN_HI + 1) put(col, y, QUARTZ, 1); // подоконник и перемычка
        else if (y >= WIN_LO && y <= WIN_HI) put(col, y, GLASS, glass);
        else return false;
        return true;
    }

    /** Свод над залом: окулюс в лаву, кольцо и лучи вокруг, звёзды; выше - лавовое озеро. */
    protected static void vault(int dx, int dz, double r, int t, int[] col) {
        if (r < 3.5) {
            put(col, t + 1, CLEAR, 0);
            put(col, t + 2, CLEAR, 0);
        } else {
            int code = OBSIDIAN, meta = 0;
            if (r < 4.6) {
                code = QUARTZ;
                meta = 1;
            } else if (r < 11 && Math.abs(Math.sin(Math.atan2(dz, dx) * 6)) * r < 0.55) code = BRICK;
            else if (hash(dx * 7 + 1, dz * 13 + 2) % 31 == 0) code = GLOWSTONE;
            put(col, t + 1, code, meta);
            put(col, t + 2, OBSIDIAN, 0);
        }
        for (int y = t + 3; y <= LAVA_TOP; y++) put(col, y, LAVA, 0);
    }

    /** Столб зала (r < R): пол, колонна или воздух, свод с окулюсом, лава над ним. */
    protected void hall(int dx, int dz, double r, int[] col) {
        int t = top(r);
        floor(dx, dz, r, col);
        double[] p = pillar(dx, dz);
        double pd = Math.hypot(dx - p[0], dz - p[1]);
        double inward = -((dx - p[0]) * p[0] + (dz - p[1]) * p[1]) / Math.max(0.01, pd * PILLAR_RING); // грань к центру зала
        for (int y = FLOOR + 1; y <= t; y++) {
            int code = AIR, meta = 0, h = y - FLOOR;
            if (pd < PILLAR_R + (y >= t - 2 ? (y - (t - 3)) * 0.8 : 0)) {
                if (y >= t - 2 && pd >= PILLAR_R - 0.3) code = BRICK; // капитель уходит в свод
                else if (h % 6 == 0) code = BRICK; // пояса
                else if (h % 6 == 3 && pd > 0.9 && inward > 0.75) code = LAMP; // лампы к центру
                else code = OBSIDIAN;
            } else if (h <= 2 && pd < PILLAR_R + 0.6) {
                code = QUARTZ; // база
                meta = h == 1 ? 0 : 1;
            } else if (wallCell(dx, dz, r, y, col, 14)) continue;
            put(col, y, code, meta);
        }
        vault(dx, dz, r, t, col);
    }

    @Override
    protected boolean column(int dx, int dz, int[] col) {
        double r = Math.sqrt(dx * dx + dz * dz);
        boolean tunnel = dx >= R - 4 && Math.abs(dz) <= MOUND_W && dx <= tunnelEnd() + PIER;
        if (r > R + 4 && !tunnel) return false;
        if (r < R) hall(dx, dz, r, col);
        else if (r < R + 2) {
            int t = top(R - 0.5);
            for (int y = FLOOR - 1; y <= t + 2; y++) put(col, y, OBSIDIAN, 0); // стена
            if (r < R + 1 && window(dx, dz, r)) for (int y = WIN_LO; y <= WIN_HI; y++) put(col, y, LAVA, 0); // лава за стеклом
            for (int y = t + 3; y <= LAVA_TOP; y++) put(col, y, LAVA, 0);
        } else if (r <= R + 4) {
            for (int y = FLOOR - 1; y <= LAVA_TOP; y++) put(col, y, NETHERRACK, 0); // берег озера
        }
        if (tunnel) tunnel(dx, dz, col);
        return true;
    }

    // ---------------------------------------------------------------- вход

    /** Последний столбец тоннеля - фасад ворот (дальше крыльцо). */
    public static int tunnelEnd() {
        return R - 1 + (TUNNEL_Y - (FLOOR + 1)) * 2 + TUNNEL_FLAT;
    }

    /**
     * Холм над воротами: скальный бугор из незеррака, обрезанный отвесной стеной по фасаду. В скале - просто стена с
     * воротами; над открытой лавой - каменистый островок с воротами и мостками.
     */
    static int moundTop(int dx, int dz) {
        int end = tunnelEnd();
        if (dx > end || dx < end - MOUND_L) return -1;
        double ex = (dx - (end - 2)) / (double) (MOUND_L - 2), ez = dz / (double) MOUND_W, e = ex * ex + ez * ez;
        if (e >= 1) return -1;
        return TUNNEL_Y + (int) Math.round((1 - e) * 9 + (hash(dx * 5, dz * 7) % 3) - 1);
    }

    /** Наклонный тоннель 5 x 5 в обсидиане, у зала портал; наверху - ворота в скальном холме и крыльцо. */
    private void tunnel(int dx, int dz, int[] col) {
        int t = dx - (R - 1), az = Math.abs(dz), end = tunnelEnd();
        if (t < 0) return; // внутри зала
        int mt = moundTop(dx, dz);
        if (mt > 0) for (int y = LAVA_TOP - 6; y <= mt; y++) put(col, y, NETHERRACK, 0);
        if (dx > end) {
            porch(dx - end, az, col);
            return;
        }
        if (dx > end - 6) {
            gate(end - dx, dz, az, col);
            return;
        }
        if (az > 3) return;
        int rise = Math.min(t, (TUNNEL_Y - (FLOOR + 1)) * 2);
        int yb = FLOOR + 1 + rise / 2;
        boolean half = rise % 2 == 1, rib = t % 6 == 0 && t > 0;
        if (t == 0) { // портал у зала: кварцевые колонны, арка, светокамень в замке
            for (int y = yb - 1; y <= yb + 5; y++) {
                if (az == 3) put(col, y, y >= yb && y <= yb + 4 ? QUARTZ : BRICK, 2);
                else put(col, y, y == yb - 1 || y == yb + 5 ? BRICK : AIR, 0);
            }
            if (dz == 0) put(col, yb + 5, GLOWSTONE, 0);
            return;
        }
        if (az == 3) { // стена: обсидиан, рёбра из кирпича, лампы между рёбрами
            for (int y = yb - 1; y <= yb + 5; y++) put(col, y, rib ? BRICK : OBSIDIAN, 0);
            if (t % 6 == 3) put(col, yb + 2, LAMP, 0);
            return;
        }
        put(col, yb - 1, BRICK, 0);
        put(col, yb, half ? SLAB : AIR, 6);
        for (int y = yb + 1; y <= yb + 4; y++) put(col, y, AIR, 0);
        if (az == 2 && rib) put(col, yb + 4, BRICK, 0); // ребро: консоли под сводом
        put(col, yb + 5, rib ? (dz == 0 ? GLOWSTONE : BRICK) : OBSIDIAN, 0);
        put(col, yb + 6, OBSIDIAN, 0);
    }

    /** Ворота (k - столбцов от фасада внутрь): проход в скале; на фасаде - пилоны, колонны, герб логова, карниз. */
    private void gate(int k, int dz, int az, int[] col) {
        int y0 = TUNNEL_Y;
        if (az > 3) return; // скала холма
        put(col, y0 - 1, BRICK, 0);
        if (k > 0) {
            if (az == 3) {
                for (int y = y0; y <= y0 + 5; y++) put(col, y, OBSIDIAN, 0);
                if (k == 3) put(col, y0 + 2, LAMP, 0);
            } else {
                for (int y = y0; y <= y0 + 4; y++) put(col, y, AIR, 0);
                put(col, y0 + 5, OBSIDIAN, 0);
            }
            return;
        }
        // фасад: обсидиановые пилоны со светокамнем, кварцевые колонны, перемычка, герб, карниз
        if (az == 3) {
            for (int y = y0; y <= y0 + 8; y++) put(col, y, OBSIDIAN, 0);
            put(col, y0 + 9, GLOWSTONE, 0);
            return;
        }
        if (az <= 1) for (int y = y0; y <= y0 + 3; y++) put(col, y, AIR, 0);
        else for (int y = y0; y <= y0 + 3; y++) put(col, y, QUARTZ, 2);
        put(col, y0 + 4, BRICK, 0);
        for (int row = 0; row < 3; row++) {
            int[] e = emblem(row, dz);
            put(col, y0 + 7 - row, e[0], e[1]);
        }
        put(col, y0 + 8, BRICK, 0);
    }

    /** Герб над воротами: {код, мета} в строке row (0 - верх) и столбце dz (-2..2). У Смотрителя - око. */
    protected int[] emblem(int row, int dz) {
        int[][] eye = { { 0, 6, 6, 6, 0 }, { 6, 14, 1, 14, 6 }, { 0, 6, 6, 6, 0 } }; // 6 - кварц, 14 - радужка, 1 - зрачок
        int v = eye[row][dz + 2];
        return v == 0 ? new int[] { BRICK, 0 } : v == 6 ? new int[] { QUARTZ, 1 } : v == 1 ? new int[] { OBSIDIAN, 0 } : new int[] { CLAY, 14 };
    }

    /** Крыльцо перед воротами (k - столбцов от фасада): настил, перила по бокам, лампы на углах; проход вырублен невысоко. */
    private void porch(int k, int az, int[] col) {
        if (az > 3 || k > PIER) return;
        int y0 = TUNNEL_Y;
        put(col, y0 - 1, BRICK, 0);
        for (int y = y0; y <= y0 + 3; y++) put(col, y, AIR, 0);
        if (az == 3) put(col, y0, FENCE, 0);
        if (az == 3 && k == PIER) put(col, y0 + 1, LAMP, 0);
        if ((k == 2 || k == PIER) && (az == 3 || az == 0)) put(col, y0 - 2, FOUNDATION, 0);
    }

    // ---------------------------------------------------------------- старые логова

    /** Логово старого вида (с завалом): где у нового белок ока из кварца, у старого обсидиан пола. */
    public static boolean old(World w, int cx, int cz) {
        int n = 0;
        for (int dx : new int[] { -9, 9, 11 }) if (w.getBlock(cx + dx, FLOOR, cz) == Blocks.obsidian) n++;
        return n >= 2;
    }

    /** Блоки, которые перестройка может заменить: воздух, огонь, материалы старого и нового зала. */
    static boolean ours(Block b) {
        return b == Blocks.air || b == Blocks.fire || b == Blocks.netherrack || b == Blocks.soul_sand || b == Blocks.nether_brick || b == Blocks.glowstone
            || b == Blocks.quartz_block || b == Blocks.obsidian || b == Blocks.stone_slab || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
    }

    /**
     * Перестроить зал старого логова по новому чертежу: пол, всё внутри и свод. Меняются только воздух, огонь и блоки
     * постройки - остальное, что поставили игроки, остаётся; у игроков в зале - как было.
     */
    public int rebuild(World w, int cx, int cz, List<EntityPlayer> keepFree) {
        int[] col = new int[256 * 2];
        int changed = 0;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r >= R) continue;
                for (int i = 0; i < col.length; i += 2) col[i] = KEEP;
                column(dx, dz, col);
                int x = cx + dx, z = cz + dz, t = top(r);
                for (int y = FLOOR; y <= t + 2; y++) {
                    if (col[y * 2] == KEEP || near(keepFree, x, y, z)) continue;
                    Block b = blockAt(col[y * 2], col[y * 2 + 1]);
                    int meta = metaAt(col[y * 2], col[y * 2 + 1]);
                    Block was = w.getBlock(x, y, z);
                    if (was == b && w.getBlockMetadata(x, y, z) == meta) continue;
                    if (was.hasTileEntity(w.getBlockMetadata(x, y, z)) || !ours(was)) continue;
                    w.setBlock(x, y, z, b, meta, 2);
                    changed++;
                }
            }
        }
        return changed;
    }

    static boolean near(List<EntityPlayer> ps, int x, int y, int z) {
        for (EntityPlayer p : ps) {
            double dx = x + 0.5 - p.posX, dz = z + 0.5 - p.posZ;
            if (dx * dx + dz * dz < 2.3 * 2.3 && y >= p.boundingBox.minY - 1 && y <= p.boundingBox.maxY + 1) return true;
        }
        return false;
    }
}
