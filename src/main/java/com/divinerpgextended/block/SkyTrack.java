package com.divinerpgextended.block;

import java.util.ArrayList;
import java.util.List;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Путь вагонетки Апалачии по рельсам (навесным и напольным - аддона и старым Divine): соседи рельса, внутренние углы-горки
 * (пол - стена, стена - потолок), точка касания колёс, нормаль и стык с соседом. Вагонетка едет по ломаной
 * "середина рельса - стык - середина следующего", на горке - по диагонали блока, как ванильная по рельсу на блок выше.
 */
public final class SkyTrack {

    /** Высота рельса над гранью опоры. */
    public static final double LIFT = 1 / 16.0;
    private static final ForgeDirection NONE = ForgeDirection.UNKNOWN;

    private SkyTrack() {}

    /** Напольный рельс: любой (Апалачии, арканы, ванильные и чужие) - вагонетка Апалачии ездит и по обычным рельсам. */
    static boolean floor(Block b) {
        return b instanceof net.minecraft.block.BlockRailBase;
    }

    /**
     * Во что превращается напольный рельс у подножия стены с навесным рельсом (горка): рельсы Апалачии - в навесной
     * Апалачии, рельсы арканы - в навесной арканы; остальные не превращаются (иначе ванильный рельс ломался бы в рельс Апалачии).
     */
    public static Block rampOf(Block b) {
        if (b == TwilightBlocks.apalachiaRails || b == com.divinerpgextended.registry.ModBlocks.apalachiaRail) return com.divinerpgextended.registry.ModBlocks.skyRail;
        if (b == net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails) return com.divinerpgextended.registry.ModBlocks.skyBoostRail;
        return null;
    }

    /** Разгоняет ли рельс вагонетку Апалачии: рельсы арканы (на полу и навесные) и запитанные золотые. */
    public static boolean boost(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b instanceof BlockSkyBoostRail || b == net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails
            || b == net.minecraft.init.Blocks.golden_rail && (w.getBlockMetadata(x, y, z) & 8) != 0;
    }

    /** Форма напольного рельса (0-9, как у ванильных): без бита "запитан" у рельсов Divine. */
    private static int shapeMeta(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b instanceof net.minecraft.block.BlockRailBase ? ((net.minecraft.block.BlockRailBase) b).getBasicRailMetadata(w, null, x, y, z) : 0;
    }

    /** Грань опоры рельса: навесной - по мете, напольный - пол; не рельс - UNKNOWN. */
    public static ForgeDirection support(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b instanceof BlockSkyRail) return BlockSkyRail.support(w.getBlockMetadata(x, y, z));
        if (floor(b)) return ForgeDirection.DOWN;
        return NONE;
    }

    /** Наклонный напольный рельс (мета 2-5, как у ванильных): куда поднимается; иначе UNKNOWN. */
    public static ForgeDirection ascending(IBlockAccess w, int x, int y, int z) {
        if (!floor(w.getBlock(x, y, z))) return NONE;
        switch (shapeMeta(w, x, y, z)) {
            case 2: return ForgeDirection.EAST;
            case 3: return ForgeDirection.WEST;
            case 4: return ForgeDirection.NORTH;
            case 5: return ForgeDirection.SOUTH;
            default: return NONE;
        }
    }

    private static boolean track(IBlockAccess w, int x, int y, int z, ForgeDirection d) {
        return BlockSkyRail.track(w.getBlock(x + d.offsetX, y + d.offsetY, z + d.offsetZ));
    }

    /**
     * Внутренний угол: рельс стоит у стены, а над ним (со стороны от опоры) на этой стене висит рельс - пол переходит в
     * стену (так же стена - в потолок). Возвращает, куда уходит горка (сторона стены); не угол - UNKNOWN. Рельсов в
     * своей плоскости у угла не больше одного - и тот с обратной стороны (иначе это развилка вдоль стены, а не горка).
     */
    public static ForgeDirection corner(IBlockAccess w, int x, int y, int z) {
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE || ascending(w, x, y, z) != NONE) return NONE;
        ForgeDirection a = n.getOpposite();
        ForgeDirection d = support(w, x + a.offsetX, y + a.offsetY, z + a.offsetZ);
        if (d == NONE || d == n || d == a || track(w, x, y, z, d)) return NONE;
        for (ForgeDirection o : ForgeDirection.VALID_DIRECTIONS)
            if (o != n && o != a && o != d.getOpposite() && track(w, x, y, z, o)) return NONE;
        return d;
    }

    /** Куда с этого рельса можно уехать (смещения к соседним рельсам, у наклонных Divine - и на блок выше/ниже). */
    public static List<int[]> exits(IBlockAccess w, int x, int y, int z) {
        List<int[]> out = new ArrayList<int[]>();
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE) return out;
        if (floor(w.getBlock(x, y, z)) && corner(w, x, y, z) == NONE) {
            ForgeDirection asc = ascending(w, x, y, z);
            for (ForgeDirection d : shape(shapeMeta(w, x, y, z))) {
                int[][] tries = d == asc ? new int[][] { { 1 }, { 0 } } : new int[][] { { 0 }, { -1 }, { 1 } };
                for (int[] t : tries) {
                    int dy = t[0];
                    if (BlockSkyRail.track(w.getBlock(x + d.offsetX, y + dy, z + d.offsetZ))) {
                        out.add(new int[] { d.offsetX, dy, d.offsetZ });
                        break;
                    }
                }
            }
            // навесные рельсы рядом: форма рельса Divine о них не знает
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                if (d == ForgeDirection.DOWN || contains(out, d)) continue;
                Block b = w.getBlock(x + d.offsetX, y + d.offsetY, z + d.offsetZ);
                if (!(b instanceof BlockSkyRail)) continue;
                ForgeDirection s = BlockSkyRail.support(w.getBlockMetadata(x + d.offsetX, y + d.offsetY, z + d.offsetZ));
                if (d == ForgeDirection.UP ? s.offsetY == 0 : s == ForgeDirection.DOWN || s == d) out.add(off(d));
            }
            return out;
        }
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS)
            if (d != n && d != n.getOpposite() && track(w, x, y, z, d)) out.add(off(d));
        ForgeDirection c = corner(w, x, y, z);
        if (c != NONE) out.add(off(n.getOpposite()));
        // внешнее ребро: рельса дальше в своей плоскости нет, а за ребром опоры на соседней грани того же блока - есть
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (d == n || d == n.getOpposite() || track(w, x, y, z, d)) continue;
            if (outerTo(w, x, y, z, d)) out.add(new int[] { d.offsetX + n.offsetX, d.offsetY + n.offsetY, d.offsetZ + n.offsetZ });
        }
        return out;
    }

    /** За ребром опоры в сторону d (вдоль своей плоскости) - рельс на соседней грани того же блока. */
    private static boolean outerTo(IBlockAccess w, int x, int y, int z, ForgeDirection d) {
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE || d == n || d == n.getOpposite()) return false;
        return support(w, x + d.offsetX + n.offsetX, y + d.offsetY + n.offsetY, z + d.offsetZ + n.offsetZ) == d.getOpposite();
    }

    /** Пара a -> b через внешнее ребро: направление от a по его плоскости (UNKNOWN - не ребро). */
    private static ForgeDirection outer(IBlockAccess w, int ax, int ay, int az, int bx, int by, int bz) {
        ForgeDirection n = support(w, ax, ay, az);
        if (n == NONE) return NONE;
        ForgeDirection d = dir(bx - ax - n.offsetX, by - ay - n.offsetY, bz - az - n.offsetZ);
        return d != NONE && outerTo(w, ax, ay, az, d) ? d : NONE;
    }

    private static boolean contains(List<int[]> l, ForgeDirection d) {
        for (int[] o : l) if (o[0] == d.offsetX && o[2] == d.offsetZ && (d.offsetY == 0 || o[1] == d.offsetY)) return true;
        return false;
    }

    private static int[] off(ForgeDirection d) {
        return new int[] { d.offsetX, d.offsetY, d.offsetZ };
    }

    /** Два направления ванильной формы рельса по мете. */
    private static ForgeDirection[] shape(int meta) {
        ForgeDirection N = ForgeDirection.NORTH, S = ForgeDirection.SOUTH, E = ForgeDirection.EAST, W = ForgeDirection.WEST;
        switch (meta) {
            case 1: case 2: case 3: return new ForgeDirection[] { E, W };
            case 6: return new ForgeDirection[] { S, E };
            case 7: return new ForgeDirection[] { S, W };
            case 8: return new ForgeDirection[] { N, W };
            case 9: return new ForgeDirection[] { N, E };
            default: return new ForgeDirection[] { N, S };
        }
    }

    private static double[] v(ForgeDirection d, double k) {
        return new double[] { d.offsetX * k, d.offsetY * k, d.offsetZ * k };
    }

    private static double[] add(double[]... vs) {
        double[] r = new double[3];
        for (double[] a : vs) for (int i = 0; i < 3; i++) r[i] += a[i];
        return r;
    }

    private static double[] center(int x, int y, int z) {
        return new double[] { x + 0.5, y + 0.5, z + 0.5 };
    }

    /** Точка касания колёс в середине рельса. */
    public static double[] contact(IBlockAccess w, int x, int y, int z) {
        double[] c = center(x, y, z);
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE || corner(w, x, y, z) != NONE || ascending(w, x, y, z) != NONE) return c; // горка и наклон - середина блока
        return add(c, v(n, 0.5 - LIFT));
    }

    /** Нормаль пути (от опоры в сторону вагонетки), единичная. */
    public static double[] normal(IBlockAccess w, int x, int y, int z) {
        ForgeDirection n = support(w, x, y, z);
        if (n == NONE) return new double[] { 0, 1, 0 };
        ForgeDirection c = corner(w, x, y, z), asc = ascending(w, x, y, z);
        double k = Math.sqrt(0.5);
        if (c != NONE) return add(v(n, -k), v(c, -k));
        if (asc != NONE) return add(v(ForgeDirection.UP, k), v(asc, -k));
        return v(n, -1);
    }

    /** Точка стыка рельса с соседом в сторону dir (горизонтальная часть смещения у наклонных). */
    public static double[] port(IBlockAccess w, int x, int y, int z, ForgeDirection dir) {
        double[] c = center(x, y, z);
        ForgeDirection n = support(w, x, y, z);
        ForgeDirection cr = corner(w, x, y, z), asc = ascending(w, x, y, z);
        if (cr != NONE) {
            if (dir == cr.getOpposite()) return add(c, v(n, 0.5), v(cr, -0.5)); // нижний край горки
            if (dir == n.getOpposite()) return add(c, v(n, -0.5), v(cr, 0.5)); // верхний край - у рельса на стене
            return add(c, v(dir, 0.5));
        }
        if (asc != NONE) {
            if (dir == asc) return add(c, v(dir, 0.5), v(ForgeDirection.UP, 0.5));
            if (dir == asc.getOpposite()) return add(c, v(dir, 0.5), v(ForgeDirection.DOWN, 0.5));
            return add(c, v(dir, 0.5));
        }
        if (n != NONE && dir != n && dir != n.getOpposite()) return add(c, v(n, 0.5 - LIFT), v(dir, 0.5));
        return add(c, v(dir, 0.5));
    }

    /** Стык двух соседних рельсов (середина между их краями - сглаживает неточные пары). */
    public static double[] joint(IBlockAccess w, int ax, int ay, int az, int bx, int by, int bz) {
        // через внешнее ребро - стык на самом ребре опоры (чуть снаружи), вагонетка перекатывается через него
        ForgeDirection od = outer(w, ax, ay, az, bx, by, bz);
        if (od != NONE) {
            ForgeDirection n = support(w, ax, ay, az);
            return add(center(ax, ay, az), v(n, 0.5 - LIFT), v(od, 0.5 + LIFT));
        }
        int dx = bx - ax, dy = by - ay, dz = bz - az;
        boolean diag = (dx != 0 || dz != 0) && dy != 0;
        ForgeDirection to = dir(dx, diag ? 0 : dy, dz);
        double[] pa = port(w, ax, ay, az, to), pb = port(w, bx, by, bz, to.getOpposite());
        return new double[] { (pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2, (pa[2] + pb[2]) / 2 };
    }

    public static ForgeDirection dir(int dx, int dy, int dz) {
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) if (d.offsetX == dx && d.offsetY == dy && d.offsetZ == dz) return d;
        return NONE;
    }
}
