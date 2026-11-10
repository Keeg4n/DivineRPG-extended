package com.divinerpgextended.accessory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Посадка аксессуаров на теле (tools/concepts/acc_export.py -> {@link #FILE}): для каждого аксессуара и профиля брони - детали
 * на костях ModelBiped в пикселях модели (y вниз). Коробка (c) - цветной куб, возможно повёрнутый вокруг точки; плоский
 * значок (f) - спрайт 16x16 с толщиной, пиксели одного цвета подряд сливаются в полоску (как в концептах). Здесь только
 * данные и геометрия (без OpenGL): рисует {@link com.divinerpgextended.client.AccessoryWear}.
 * <p>
 * Профили: bare (без брони), divine (любая не-Странника броня), r1..r7 и r1_shadow/r1_bedrock/r1_ender/r1_corrupted (облики
 * брони Странника): посадка считается от поверхности брони, на тонкой броне Странника вещь садится ближе к телу.
 */
public final class WearData {

    public static final String FILE = "/assets/divinerpgextended/wear/accessories.txt";
    public static final String[] BONES = { "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg" };
    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** Грани одной кости: по 4 вершины (x, y, z), нормаль и цвет (r, g, b 0..1) на грань. */
    public static final class Mesh {
        public final List<float[]> quads = new ArrayList<float[]>();

        /** [x0 y0 z0 x1 y1 z1 x2 y2 z2 x3 y3 z3 nx ny nz r g b] */
        void add(double[][] v, double[] n, float[] col) {
            float[] q = new float[18];
            for (int i = 0; i < 4; i++)
                for (int k = 0; k < 3; k++) q[i * 3 + k] = (float) v[i][k];
            q[12] = (float) n[0];
            q[13] = (float) n[1];
            q[14] = (float) n[2];
            q[15] = col[0];
            q[16] = col[1];
            q[17] = col[2];
            quads.add(q);
        }
    }

    private static Map<String, Map<String, Map<String, Mesh>>> data;

    private WearData() {}

    /** ключ аксессуара -> профиль -> кость -> грани. */
    public static synchronized Map<String, Map<String, Map<String, Mesh>>> all() {
        if (data == null) data = load();
        return data;
    }

    /** Грани аксессуара на кости для профиля (запасной профиль - divine); null - нет. */
    public static Mesh mesh(String key, String profile, String bone) {
        Map<String, Map<String, Mesh>> a = all().get(key);
        if (a == null) return null;
        Map<String, Mesh> p = a.get(profile);
        if (p == null) p = a.get("divine");
        return p == null ? null : p.get(bone);
    }

    private static Map<String, Map<String, Map<String, Mesh>>> load() {
        Map<String, Map<String, Map<String, Mesh>>> out = new LinkedHashMap<String, Map<String, Map<String, Mesh>>>();
        InputStream in = WearData.class.getResourceAsStream(FILE);
        if (in == null) return out;
        Map<String, int[]> sprites = new HashMap<String, int[]>();
        List<String[]> rows = new ArrayList<String[]>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            for (String line; (line = r.readLine()) != null;) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.trim().split(" ");
                if ("s".equals(f[0])) sprites.put(f[1], sprite(f[2], f[3]));
                else rows.add(f);
            }
        } catch (IOException ex) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Посадка аксессуаров не прочитана: " + ex);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
        Map<String, Map<String, Mesh>> acc = null;
        Map<String, Mesh> prof = null;
        for (String[] f : rows) {
            if ("a".equals(f[0])) {
                acc = new LinkedHashMap<String, Map<String, Mesh>>();
                out.put(f[1], acc);
            } else if ("p".equals(f[0])) {
                prof = new HashMap<String, Mesh>();
                acc.put(f[1], prof);
            } else {
                Mesh m = prof.get(f[1]);
                if (m == null) prof.put(f[1], m = new Mesh());
                if ("c".equals(f[0])) cube(m, f);
                else flat(m, f, sprites.get(f[11]));
            }
        }
        return out;
    }

    /** Спрайт: палитра AARRGGBB и 256 знаков-индексов ('.' - прозрачно) -> ARGB (0 - прозрачно). */
    static int[] sprite(String pal, String idx) {
        String[] p = pal.split(",");
        int[] c = new int[p.length];
        for (int i = 0; i < p.length; i++) c[i] = (int) Long.parseLong(p[i], 16);
        int[] px = new int[256];
        for (int i = 0; i < 256 && i < idx.length(); i++) {
            char ch = idx.charAt(i);
            px[i] = ch == '.' ? 0 : c[DIGITS.indexOf(ch)];
        }
        return px;
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    private static float[] color(int rgb) {
        return new float[] { ((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F };
    }

    /** Поворот как у ModelRenderer (и meshkit.rot_matrix): v' = Rz * Ry * Rx * v, градусы. */
    static double[][] rot(double rx, double ry, double rz) {
        double a = Math.toRadians(rx), b = Math.toRadians(ry), c = Math.toRadians(rz);
        double[][] X = { { 1, 0, 0 }, { 0, Math.cos(a), -Math.sin(a) }, { 0, Math.sin(a), Math.cos(a) } };
        double[][] Y = { { Math.cos(b), 0, Math.sin(b) }, { 0, 1, 0 }, { -Math.sin(b), 0, Math.cos(b) } };
        double[][] Z = { { Math.cos(c), -Math.sin(c), 0 }, { Math.sin(c), Math.cos(c), 0 }, { 0, 0, 1 } };
        return mul(Z, mul(Y, X));
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                for (int k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    private static double[] apply(double[][] m, double[] v) {
        return new double[] { m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2] };
    }

    /** c кость x y z w h d RRGGBB [rx ry rz px py pz] - те же 6 граней, что acc_kit.cube. */
    static void cube(Mesh m, String[] f) {
        double x = d(f[2]), y = d(f[3]), z = d(f[4]), x2 = x + d(f[5]), y2 = y + d(f[6]), z2 = z + d(f[7]);
        float[] col = color(Integer.parseInt(f[8], 16));
        double[][] faces = { { x2, y, z2, x2, y, z, x2, y2, z, x2, y2, z2, 1, 0, 0 }, { x, y, z, x, y, z2, x, y2, z2, x, y2, z, -1, 0, 0 },
            { x2, y, z2, x, y, z2, x, y, z, x2, y, z, 0, -1, 0 }, { x2, y2, z, x, y2, z, x, y2, z2, x2, y2, z2, 0, 1, 0 },
            { x2, y, z, x, y, z, x, y2, z, x2, y2, z, 0, 0, -1 }, { x, y, z2, x2, y, z2, x2, y2, z2, x, y2, z2, 0, 0, 1 } };
        double[][] R = f.length > 14 ? rot(d(f[9]), d(f[10]), d(f[11])) : null;
        double[] pv = R != null ? new double[] { d(f[12]), d(f[13]), d(f[14]) } : null;
        for (double[] fc : faces) {
            double[][] v = new double[4][];
            for (int i = 0; i < 4; i++) {
                double[] p = { fc[i * 3], fc[i * 3 + 1], fc[i * 3 + 2] };
                if (R != null) {
                    double[] q = apply(R, new double[] { p[0] - pv[0], p[1] - pv[1], p[2] - pv[2] });
                    p = new double[] { q[0] + pv[0], q[1] + pv[1], q[2] + pv[2] };
                }
                v[i] = p;
            }
            double[] n = { fc[12], fc[13], fc[14] };
            if (R != null) n = apply(R, n);
            m.add(v, n, col);
        }
    }

    /** f кость ox oy oz uxx uxy uxz uyx uyy uyz спрайт px t - плоский значок с толщиной, как acc_kit.flat. */
    static void flat(Mesh m, String[] f, int[] sp) {
        if (sp == null) return;
        double[] o = { d(f[2]), d(f[3]), d(f[4]) }, ux = { d(f[5]), d(f[6]), d(f[7]) }, uy = { d(f[8]), d(f[9]), d(f[10]) };
        double px = d(f[12]), t = d(f[13]);
        double[] n = { ux[1] * uy[2] - ux[2] * uy[1], ux[2] * uy[0] - ux[0] * uy[2], ux[0] * uy[1] - ux[1] * uy[0] };
        double[] mn = { -n[0], -n[1], -n[2] }, mux = { -ux[0], -ux[1], -ux[2] }, muy = { -uy[0], -uy[1], -uy[2] };
        for (int y = 0; y < 16; y++) {
            int x = 0;
            while (x < 16) {
                int c = sp[y * 16 + x];
                if (c == 0) {
                    x++;
                    continue;
                }
                int x2 = x;
                while (x2 + 1 < 16 && sp[y * 16 + x2 + 1] == c) x2++;
                float[] col = color(c);
                double a0 = (x - 8) * px, a1 = (x2 + 1 - 8) * px, b0 = (y - 8) * px, b1 = (y + 1 - 8) * px;
                m.add(new double[][] { P(o, ux, uy, n, a1, b0, -1, t), P(o, ux, uy, n, a0, b0, -1, t), P(o, ux, uy, n, a0, b1, -1, t), P(o, ux, uy, n, a1, b1, -1, t) }, mn, col);
                m.add(new double[][] { P(o, ux, uy, n, a0, b0, 1, t), P(o, ux, uy, n, a1, b0, 1, t), P(o, ux, uy, n, a1, b1, 1, t), P(o, ux, uy, n, a0, b1, 1, t) }, n, col);
                m.add(new double[][] { P(o, ux, uy, n, a0, b0, -1, t), P(o, ux, uy, n, a1, b0, -1, t), P(o, ux, uy, n, a1, b0, 1, t), P(o, ux, uy, n, a0, b0, 1, t) }, muy, col);
                m.add(new double[][] { P(o, ux, uy, n, a1, b1, -1, t), P(o, ux, uy, n, a0, b1, -1, t), P(o, ux, uy, n, a0, b1, 1, t), P(o, ux, uy, n, a1, b1, 1, t) }, uy, col);
                m.add(new double[][] { P(o, ux, uy, n, a0, b1, -1, t), P(o, ux, uy, n, a0, b0, -1, t), P(o, ux, uy, n, a0, b0, 1, t), P(o, ux, uy, n, a0, b1, 1, t) }, mux, col);
                m.add(new double[][] { P(o, ux, uy, n, a1, b0, -1, t), P(o, ux, uy, n, a1, b1, -1, t), P(o, ux, uy, n, a1, b1, 1, t), P(o, ux, uy, n, a1, b0, 1, t) }, ux, col);
                x = x2 + 1;
            }
        }
    }

    private static double[] P(double[] o, double[] ux, double[] uy, double[] n, double a, double b, double s, double t) {
        double k = s * t / 2;
        return new double[] { o[0] + ux[0] * a + uy[0] * b + n[0] * k, o[1] + ux[1] * a + uy[1] * b + n[1] * k, o[2] + ux[2] * a + uy[2] * b + n[2] * k };
    }
}
