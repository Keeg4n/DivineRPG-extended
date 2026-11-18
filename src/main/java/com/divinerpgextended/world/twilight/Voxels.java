package com.divinerpgextended.world.twilight;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.DoubleUnaryOperator;

/**
 * A volume decoration of a column blueprint (towers, a crystal roof, a tree over the arena): built once from shapes in coordinates relative to
 * the structure center and laid column by column together with the blueprint. Codes are the structure's own (with its skin and meta);
 * {@link ColumnStructure#AIR} carves. Around the decoration there is an air envelope ({@link #envelope}), so that what rises above the usual
 * clearing is seen from the arena and does not stand walled up in the cave rock.
 */
public final class Voxels {

    /** Material by position (a mix of two blocks, rows). */
    public interface Mat {
        int at(int x, int y, int z);
    }

    /** How far around a decoration column the air envelope reaches, and how high above its top. */
    public static final int ENV_R = 6, ENV_H = 3;

    private final Map<Long, TreeMap<Integer, Integer>> cols = new HashMap<Long, TreeMap<Integer, Integer>>();
    private Map<Long, Integer> env;
    private int radius;
    /** Meta of a code when it is laid into the column (each structure has its own codes). */
    private final java.util.function.IntUnaryOperator meta;

    /** Codes of a Mortum arena (meta from {@link MortumArenas.Building#meta}). */
    public Voxels() {
        this(MortumArenas.Building::meta);
    }

    /** Codes of any column structure: meta by code. */
    public Voxels(java.util.function.IntUnaryOperator meta) {
        this.meta = meta;
    }

    public static long key(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    public void put(int x, int y, int z, int code) {
        Long k = key(x, z);
        TreeMap<Integer, Integer> m = cols.get(k);
        if (m == null) cols.put(k, m = new TreeMap<Integer, Integer>());
        m.put(y, code);
        radius = Math.max(radius, Math.max(Math.abs(x), Math.abs(z)));
        env = null;
    }

    public boolean has(int dx, int dz) {
        return cols.containsKey(key(dx, dz));
    }

    /** The highest decoration block in the column (or Integer.MIN_VALUE). */
    public int top(int dx, int dz) {
        TreeMap<Integer, Integer> m = cols.get(key(dx, dz));
        if (m == null) return Integer.MIN_VALUE;
        for (Integer y : m.descendingKeySet()) if (m.get(y) != ColumnStructure.AIR) return y;
        return Integer.MIN_VALUE;
    }

    /** The lowest decoration block in the column (or Integer.MAX_VALUE). */
    public int bottom(int dx, int dz) {
        TreeMap<Integer, Integer> m = cols.get(key(dx, dz));
        return m == null || m.isEmpty() ? Integer.MAX_VALUE : m.firstKey();
    }

    /** How far from the center the decoration reaches (along x or z). */
    public int radius() {
        return radius;
    }

    /** Up to which height to clear air in the column so that the decoration stands in the open (Integer.MIN_VALUE - not needed). */
    public int envelope(int dx, int dz) {
        Map<Long, Integer> e = env;
        if (e == null) {
            e = new HashMap<Long, Integer>();
            for (Map.Entry<Long, TreeMap<Integer, Integer>> c : cols.entrySet()) {
                int x = (int) (c.getKey() >> 32), z = (int) (long) c.getKey();
                int t = Integer.MIN_VALUE;
                for (Map.Entry<Integer, Integer> b : c.getValue().entrySet()) if (b.getValue() != ColumnStructure.AIR) t = Math.max(t, b.getKey());
                if (t == Integer.MIN_VALUE) continue;
                for (int i = -ENV_R; i <= ENV_R; i++) for (int k = -ENV_R; k <= ENV_R; k++) {
                    if (i * i + k * k > ENV_R * ENV_R) continue;
                    Long kk = key(x + i, z + k);
                    Integer was = e.get(kk);
                    if (was == null || was < t + ENV_H) e.put(kk, t + ENV_H);
                }
            }
            env = e;
        }
        Integer v = e.get(key(dx, dz));
        return v == null ? Integer.MIN_VALUE : v;
    }

    /** Lay the decoration of the column into the blueprint column. */
    public void apply(int dx, int dz, int[] col) {
        TreeMap<Integer, Integer> m = cols.get(key(dx, dz));
        if (m == null) return;
        for (Map.Entry<Integer, Integer> b : m.entrySet()) {
            int code = b.getValue();
            ColumnStructure.put(col, b.getKey(), code, code == ColumnStructure.AIR ? 0 : meta.applyAsInt(code));
        }
    }

    // ---------------------------------------------------------------- shapes

    /** A cylinder from y0 to y1: solid (shell < 0) or a wall of the given thickness. */
    public void cyl(double cx, double cz, double r, int y0, int y1, Mat v, double shell) {
        for (int x = (int) Math.floor(cx - r) - 1; x <= (int) Math.ceil(cx + r) + 1; x++) {
            for (int z = (int) Math.floor(cz - r) - 1; z <= (int) Math.ceil(cz + r) + 1; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > r + 0.01 || shell >= 0 && d <= r - shell) continue;
                for (int y = y0; y <= y1; y++) put(x, y, z, v.at(x, y, z));
            }
        }
    }

    /** A solid steep cone: radius r0 at y0, the apex height above; rows of band every `every` rows; a tip of blocks above the apex. */
    public void cone(double cx, double cz, double r0, int y0, int height, int mat, int band, int every, int... tip) {
        double k = height / r0;
        for (int x = (int) (cx - r0) - 1; x <= (int) (cx + r0) + 1; x++) {
            for (int z = (int) (cz - r0) - 1; z <= (int) (cz + r0) + 1; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > r0 + 0.25) continue;
                int top = y0 + (int) Math.round((r0 - d + 0.3) * k);
                for (int y = Math.max(y0, top - (int) Math.ceil(k) - 1); y <= top; y++)
                    put(x, y, z, band != 0 && (y - y0) % every == every - 1 ? band : mat);
            }
        }
        int apex = y0 + (int) Math.round((r0 + 0.3) * k);
        for (int i = 0; i < tip.length; i++) put((int) Math.round(cx), apex + 1 + i, (int) Math.round(cz), tip[i]);
    }

    /** A square stepped pyramid shell: the half size shrinks by 1 every `step` rows. */
    public void pyramid(int cx, int cz, int half, int y0, int mat, int step, int band, int every, int... tip) {
        int y = y0;
        for (int h = half; h >= 0; h--) {
            for (int s = 0; s < step; s++, y++) {
                for (int x = cx - h; x <= cx + h; x++) for (int z = cz - h; z <= cz + h; z++)
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) == h || h <= 1) put(x, y, z, band != 0 && (y - y0) % every == every - 1 ? band : mat);
            }
        }
        for (int i = 0; i < tip.length; i++) put(cx, y + i, cz, tip[i]);
    }

    /** A thick segment between two points; the thickness may change along it (t from 0 to 1). */
    public void line(double[] p0, double[] p1, DoubleUnaryOperator r, int code) {
        int n = (int) (Math.max(Math.abs(p1[0] - p0[0]), Math.max(Math.abs(p1[1] - p0[1]), Math.abs(p1[2] - p0[2]))) * 2) + 1;
        for (int s = 0; s <= n; s++) {
            double t = s / (double) n;
            double cx = p0[0] + (p1[0] - p0[0]) * t, cy = p0[1] + (p1[1] - p0[1]) * t, cz = p0[2] + (p1[2] - p0[2]) * t;
            double rr = r.applyAsDouble(t);
            for (int x = (int) (cx - rr) - 1; x <= (int) (cx + rr) + 1; x++)
                for (int y = (int) (cy - rr) - 1; y <= (int) (cy + rr) + 1; y++)
                    for (int z = (int) (cz - rr) - 1; z <= (int) (cz + rr) + 1; z++)
                        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz) <= rr * rr + 0.3) put(x, y, z, code);
        }
    }

    /** Which blocks of another decoration to take. */
    public interface Keep {
        boolean keep(int x, int y, int z, int code);
    }

    /** Copy the blocks of `from` that pass the filter (roots that must not lie on paths and graves). */
    public void addAll(Voxels from, Keep keep) {
        for (Map.Entry<Long, TreeMap<Integer, Integer>> c : from.cols.entrySet()) {
            int x = (int) (c.getKey() >> 32), z = (int) (long) c.getKey();
            for (Map.Entry<Integer, Integer> b : c.getValue().entrySet()) if (keep.keep(x, b.getKey(), z, b.getValue())) put(x, b.getKey(), z, b.getValue());
        }
    }

    /** A mix of two materials by a hash of the position: alt in about one of k. */
    public static Mat mix(int main, int alt, int k, int salt) {
        return (x, y, z) -> ColumnStructure.hash(x * 31 + y + salt, z * 17 - y) % k == 0 ? alt : main;
    }

    public static Mat of(int code) {
        return (x, y, z) -> code;
    }
}
