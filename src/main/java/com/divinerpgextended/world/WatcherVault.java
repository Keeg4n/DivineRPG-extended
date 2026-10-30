package com.divinerpgextended.world;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.divinerpgextended.world.twilight.ColumnStructure;

/**
 * Логово Смотрителя: засыпанная обсерватория под лавовым озером Ада. Зал радиусом {@link #R} у самого дна, оболочка из
 * обсидиана (его не берут ни взрывы босса, ни лава сверху), над сводом - слой лавы. Без боя зал пуст: обломанные колонны
 * старой обсерватории и помост с оком. Завал (незеррак с пещерами, песок душ, обломки адского кирпича, светокамень; в центре -
 * карман, где висит босс, у входа - прихожая) обрушивается при вызове ({@link #refill}) и уходит, когда Смотритель погиб или
 * исчез ({@link #clear}). Бой прорубает арену сам: игрок прокапывается, Смотритель взрывами пробивает дорогу к нему
 * ({@link com.divinerpgextended.boss.WatcherHunt}). Вход - наклонный тоннель со ступенями-полублоками от берега (+X), на
 * выходе - причал над лавой.
 */
public class WatcherVault extends ColumnStructure {

    /** Радиус зала, пол (блок пола; стоят на FLOOR + 1), верх зала в центре. */
    public static final int R = 22, FLOOR = 7, TOP = FLOOR + 19, LAVA_TOP = 31;
    /** Тоннель: подъём на 1 блок за 2 по горизонтали до высоты над лавой, потом прямо и причал. */
    public static final int TUNNEL_Y = 33, TUNNEL_FLAT = 6;

    static final int OBSIDIAN = 1, NETHERRACK = 2, SOUL = 3, BRICK = 4, GLOWSTONE = 5, QUARTZ = 6, LAMP = 7, LAVA = 8, SLAB = 9;

    final int salt;
    /** Чертёж с завалом (на время боя) или пустой зал. */
    private boolean rubble;

    public WatcherVault(int cx, int cz) {
        salt = hash(cx * 31 + 7, cz * 17 - 3);
    }

    @Override
    protected Block block(int code, int meta) {
        switch (code) {
            case OBSIDIAN: return Blocks.obsidian;
            case NETHERRACK: return Blocks.netherrack;
            case SOUL: return Blocks.soul_sand;
            case BRICK: return Blocks.nether_brick;
            case GLOWSTONE: return Blocks.glowstone;
            case QUARTZ: return Blocks.quartz_block;
            case LAMP: return net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
            case LAVA: return Blocks.lava;
            case SLAB: return Blocks.stone_slab;
            default: return Blocks.air;
        }
    }

    @Override
    protected int metaOf(int code, int meta) {
        return code == LAMP ? 6 : code == SLAB ? 6 : meta; // фиолетовая лампа Divine; полублок из адского кирпича
    }

    @Override
    protected boolean lit(Block b) {
        return b == Blocks.glowstone || b == Blocks.lava || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
    }

    /** Верх зала на расстоянии r от центра: купол, у стен ниже. */
    public static int top(double r) {
        return r < R - 6 ? TOP : TOP - (int) Math.ceil((r - (R - 6)) * 1.1);
    }

    /** Внутри зала (там, где завал и бой; оболочка и тоннель - нет). */
    public static boolean interior(int dx, int y, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        return r < R && y > FLOOR && y <= top(r);
    }

    /** Центр кармана босса (над центром пола). */
    public static int bossY() {
        return FLOOR + 9;
    }

    // ---------------------------------------------------------------- шум завала

    private double lattice(int x, int y, int z) {
        return (hash(x * 73 + salt, z * 151 + y * 977) % 10000) / 10000.0;
    }

    /** Гладкий шум 0..1 (ячейка cell). */
    private double noise(double x, double y, double z, double cell) {
        double fx = x / cell, fy = y / cell, fz = z / cell;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy), z0 = (int) Math.floor(fz);
        double tx = s(fx - x0), ty = s(fy - y0), tz = s(fz - z0);
        double v = 0;
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) for (int k = 0; k < 2; k++)
            v += lattice(x0 + i, y0 + j, z0 + k) * (i == 0 ? 1 - tx : tx) * (j == 0 ? 1 - ty : ty) * (k == 0 ? 1 - tz : tz);
        return v;
    }

    private static double s(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Код блока в точке зала (с завалом или без - {@link #rubble}). */
    int fill(int dx, int y, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        // карман босса и помост с оком под ним
        double bx = dx, by = y - bossY(), bz = dz;
        if (bx * bx + by * by * 1.4 + bz * bz < 5.5 * 5.5) return AIR;
        if (r < 3 && y == FLOOR + 1) return r < 1 ? LAMP : QUARTZ;
        // прихожая у тоннеля (+X)
        double ax = dx - (R - 4), ay = y - (FLOOR + 2);
        if (ax * ax + ay * ay * 2 + dz * dz < 3.2 * 3.2 || dx >= R - 4 && Math.abs(dz) <= 1 && y <= FLOOR + 3) return AIR;
        // обломанные колонны старой обсерватории (стоят и без боя)
        double a = Math.atan2(dz, dx), step = Math.PI / 4, k = Math.round((a - Math.PI / 8) / step), ca = k * step + Math.PI / 8;
        double pd = Math.hypot(dx - Math.cos(ca) * 13, dz - Math.sin(ca) * 13);
        if (pd < 1.3) {
            int broken = FLOOR + 5 + hash((int) k + 11, salt) % 12;
            if (y <= broken) return (y - FLOOR) % 5 == 0 ? LAMP : QUARTZ;
        }
        if (!rubble) return AIR;
        double n = noise(dx, y * 1.3, dz, 6.0) * 0.7 + noise(dx + 101, y * 1.3, dz - 57, 2.7) * 0.3;
        if (n < 0.43) return AIR;
        if (y == FLOOR + 1 && n < 0.5) return AIR; // проходы по полу чаще
        int h = hash(dx * 13 + y * 7919 + salt, dz * 29 - y * 31);
        if (h % 1000 < 7) return GLOWSTONE;
        double m = noise(dx - 37, y, dz + 211, 4.0);
        if (m > 0.68) return SOUL;
        if (m < 0.25) return BRICK;
        return NETHERRACK;
    }

    @Override
    protected boolean column(int dx, int dz, int[] col) {
        double r = Math.sqrt(dx * dx + dz * dz);
        boolean tunnel = dx >= R - 4 && Math.abs(dz) <= 2 && dx <= tunnelEnd() + 3;
        if (r > R + 4 && !tunnel) return false;
        if (r <= R + 4) {
            int t = top(Math.min(r, R - 0.5));
            if (r < R) {
                put(col, FLOOR, OBSIDIAN, 0);
                put(col, FLOOR - 1, OBSIDIAN, 0);
                for (int y = FLOOR + 1; y <= t; y++) {
                    int c = fill(dx, y, dz);
                    put(col, y, c, 0);
                }
                put(col, t + 1, OBSIDIAN, 0);
                put(col, t + 2, OBSIDIAN, 0);
                for (int y = t + 3; y <= LAVA_TOP; y++) put(col, y, LAVA, 0); // лавовое озеро над сводом
            } else if (r < R + 2) {
                for (int y = FLOOR - 1; y <= t + 2; y++) put(col, y, OBSIDIAN, 0); // стена
                for (int y = t + 3; y <= LAVA_TOP; y++) put(col, y, LAVA, 0);
            } else {
                for (int y = FLOOR - 1; y <= LAVA_TOP; y++) put(col, y, NETHERRACK, 0); // берег озера
            }
        }
        if (tunnel) tunnel(dx, dz, col);
        return true;
    }

    /** Последний столбец тоннеля (дальше причал). */
    public static int tunnelEnd() {
        return R - 1 + (TUNNEL_Y - (FLOOR + 1)) * 2 + TUNNEL_FLAT;
    }

    /** Наклонный тоннель 3 x 3 в обсидиане, ступени-полублоки, лампы; на выходе - причал 5 x 5 над лавой. */
    private void tunnel(int dx, int dz, int[] col) {
        int t = dx - (R - 1);
        if (t < 0) {
            return; // внутри зала - прихожая (завал)
        }
        int rise = Math.min(t, (TUNNEL_Y - (FLOOR + 1)) * 2);
        int yb = FLOOR + 1 + rise / 2;
        boolean half = rise % 2 == 1;
        int end = tunnelEnd();
        if (dx > end) { // причал
            put(col, TUNNEL_Y - 1, BRICK, 0);
            for (int y = TUNNEL_Y; y <= TUNNEL_Y + 3; y++) put(col, y, AIR, 0);
            if (Math.abs(dz) == 2 && dx == end + 3) put(col, TUNNEL_Y, LAMP, 0);
            return;
        }
        if (Math.abs(dz) == 2) {
            for (int y = yb - 1; y <= yb + 4; y++) put(col, y, OBSIDIAN, 0);
            if (t % 6 == 3) put(col, yb + 1, LAMP, 0);
            return;
        }
        put(col, yb - 1, BRICK, 0);
        put(col, yb, half ? SLAB : AIR, 0);
        for (int y = yb + 1; y <= yb + 3; y++) put(col, y, AIR, 0);
        put(col, yb + 4, OBSIDIAN, 0);
    }

    // ---------------------------------------------------------------- восстановление завала

    /**
     * Завал заново (при вызове босса): каждый блок зала - как в чертеже; у игроков в зале - карман, чтобы никого не
     * замуровало. Оболочку и тоннель не трогает.
     */
    public int refill(World w, int cx, int cz, List<EntityPlayer> keepFree) {
        return apply(w, cx, cz, keepFree, true);
    }

    /**
     * Завал уходит (босс погиб или исчез): зал снова пуст, сбитые колонны встают. Трогает только воздух, огонь и блоки
     * завала - поставленное игроками остаётся; у игроков в зале - как было, чтобы колонной никого не замуровало.
     */
    public int clear(World w, int cx, int cz, List<EntityPlayer> keepFree) {
        return apply(w, cx, cz, keepFree, false);
    }

    static boolean debris(Block b) {
        return b == Blocks.air || b == Blocks.fire || b == Blocks.netherrack || b == Blocks.soul_sand || b == Blocks.nether_brick || b == Blocks.glowstone
            || b == Blocks.quartz_block || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
    }

    private int apply(World w, int cx, int cz, List<EntityPlayer> keepFree, boolean withRubble) {
        rubble = withRubble;
        int[] col = new int[256 * 2];
        int changed = 0;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (dx * dx + dz * dz >= R * R) continue;
                for (int i = 0; i < col.length; i += 2) col[i] = KEEP;
                column(dx, dz, col);
                int x = cx + dx, z = cz + dz;
                for (int y = FLOOR + 1; y <= TOP; y++) {
                    if (!interior(dx, y, dz) || col[y * 2] == KEEP) continue;
                    if (near(keepFree, x, y, z)) continue;
                    Block b = blockAt(col[y * 2], col[y * 2 + 1]);
                    int meta = metaAt(col[y * 2], col[y * 2 + 1]);
                    Block was = w.getBlock(x, y, z);
                    if (was == b && w.getBlockMetadata(x, y, z) == meta) continue;
                    if (was.hasTileEntity(w.getBlockMetadata(x, y, z))) continue; // чужие сундуки не трогаем
                    if (!withRubble && !debris(was)) continue;
                    w.setBlock(x, y, z, b, meta, 2);
                    changed++;
                }
            }
        }
        rubble = false;
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
