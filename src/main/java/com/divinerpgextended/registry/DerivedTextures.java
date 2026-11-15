package com.divinerpgextended.registry;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Textures made from DivineRPG textures (recolors and rework) are not in the jar: the DivineRPG license does not allow
 * moving its resources into other projects. Instead of images there are {@link #FILE} tables: from which texture of the installed
 * DivineRPG to build ours, which color to replace with which, and which individual pixels to draw ourselves. The client builds them at
 * startup ({@link com.divinerpgextended.client.DerivedTexturePack}).
 * <p>
 * The tables are exact for DivineRPG 1.4.2.3. In another version (1.4.1.5 from the Magic modpack) some textures are redrawn: then the
 * source does not match by md5, and a fallback source is used (if given) or the same one, recolored to our palette
 * by brightness ({@link #recolor}): the darkest pixel to our darkest color and so on.
 */
public final class DerivedTextures {

    public static final String FILE = "/assets/divinerpgextended/derived_textures.txt";

    /**
     * Recipe: output (domain:path), Divine sources (paths in the jar: main and fallbacks), md5 of the exact result and of the main
     * source, color and pixel replacements (ARGB), our palette by brightness (color and pixel count).
     */
    public static final class Recipe {
        public final String out, md5, srcMd5;
        public final String[] src;
        final Map<Integer, Integer> colors = new HashMap<Integer, Integer>(), pixels = new HashMap<Integer, Integer>();
        int[] palette = new int[0], weight = new int[0];

        Recipe(String out, String src, String md5, String srcMd5) {
            this.out = out;
            this.src = src.split(";");
            this.md5 = md5;
            this.srcMd5 = srcMd5;
        }
    }

    /** How the last texture was built: exactly, by recoloring the main source or a fallback (for the selftest). */
    public static String lastWay = "";

    private static Map<String, Recipe> recipes;

    private DerivedTextures() {}

    /** All recipes by output "domain:path". */
    public static synchronized Map<String, Recipe> recipes() {
        if (recipes != null) return recipes;
        Map<String, Recipe> m = new LinkedHashMap<String, Recipe>();
        InputStream in = DerivedTextures.class.getResourceAsStream(FILE);
        if (in != null) {
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                for (String line; (line = r.readLine()) != null;) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] f = line.split("\\|", -1);
                    Recipe rc = new Recipe(f[0], f[1], f[2], f[3]);
                    put(rc.colors, f[4], 16);
                    put(rc.pixels, f[5], 10);
                    if (!f[6].isEmpty()) {
                        String[] pal = f[6].split(",");
                        rc.palette = new int[pal.length];
                        rc.weight = new int[pal.length];
                        for (int i = 0; i < pal.length; i++) {
                            int x = pal[i].indexOf('x');
                            rc.palette[i] = (int) Long.parseLong(pal[i].substring(0, x), 16);
                            rc.weight[i] = Integer.parseInt(pal[i].substring(x + 1));
                        }
                    }
                    m.put(rc.out, rc);
                }
            } catch (IOException ex) {
                com.divinerpgextended.DivineRPGExtended.LOG.warn("Could not read texture recipes: " + ex);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
        }
        return recipes = m;
    }

    private static void put(Map<Integer, Integer> to, String list, int keyRadix) {
        if (list.isEmpty()) return;
        for (String pair : list.split(",")) {
            int at = pair.indexOf('>');
            to.put((int) Long.parseLong(pair.substring(0, at), keyRadix), (int) Long.parseLong(pair.substring(at + 1), 16));
        }
    }

    /** A texture from the DivineRPG jar (null - there is none). */
    static BufferedImage source(String path) throws IOException {
        InputStream in = DerivedTextures.class.getResourceAsStream("/" + path);
        if (in == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) in = cl.getResourceAsStream(path);
        }
        if (in == null) return null;
        try {
            return ImageIO.read(in);
        } finally {
            in.close();
        }
    }

    /**
     * Build a texture by recipe; null - no source exists. If the main source is the same as in the table, it is an exact build;
     * otherwise a recolor by palette of the fallback source (the first one found), and if there are no fallbacks - of the main one.
     */
    public static BufferedImage build(Recipe r) throws IOException {
        BufferedImage main = source(r.src[0]);
        if (main != null && md5(main).equals(r.srcMd5)) {
            lastWay = "exact";
            return exact(main, r);
        }
        for (int k = 1; k < r.src.length; k++) {
            BufferedImage alt = source(r.src[k]);
            if (alt != null) {
                lastWay = "fallback source";
                return recolor(alt, r);
            }
        }
        if (main == null) return null;
        lastWay = "recolor";
        return recolor(main, r);
    }

    /**
     * Our palette applied to another version of the texture: pixels are distributed by brightness among our colors in the same proportions. Colored and gray
     * pixels are distributed separately (colored ones among our colored, gray among our gray) if both have a noticeable share: otherwise
     * in an ore on stone the specks would blend with the stone (the jungle ore on Divine 1.4.1.5 lost its green).
     */
    static BufferedImage recolor(BufferedImage s, Recipe r) {
        int w = s.getWidth(), h = s.getHeight();
        final int[] px = s.getRGB(0, 0, w, h, null, 0, w);
        java.util.List<Integer> op = new java.util.ArrayList<Integer>(), opC = new java.util.ArrayList<Integer>(), opG = new java.util.ArrayList<Integer>();
        for (int i = 0; i < px.length; i++) {
            if (px[i] >>> 24 == 0) px[i] = 0;
            else {
                op.add(i);
                (colored(px[i]) ? opC : opG).add(i);
            }
        }
        if (r.palette.length == 0 || op.isEmpty()) return image(px, w, h);
        int[] out = new int[px.length];
        java.util.List<Integer> palC = new java.util.ArrayList<Integer>(), palG = new java.util.ArrayList<Integer>();
        long wC = 0, wAll = 0;
        for (int i = 0; i < r.palette.length; i++) {
            wAll += r.weight[i];
            if (colored(r.palette[i])) {
                palC.add(i);
                wC += r.weight[i];
            } else palG.add(i);
        }
        double share = 0.05;
        boolean split = opC.size() >= share * op.size() && opG.size() >= share * op.size() && wC >= share * wAll && wAll - wC >= share * wAll;
        if (split) {
            rank(px, opC, r, palC, out);
            rank(px, opG, r, palG, out);
        } else {
            java.util.List<Integer> all = new java.util.ArrayList<Integer>();
            for (int i = 0; i < r.palette.length; i++) all.add(i);
            rank(px, op, r, all, out);
        }
        return image(out, w, h);
    }

    /** A colored pixel: noticeable saturation and not almost black. */
    public static boolean colored(int c) {
        int rr = c >> 16 & 255, gg = c >> 8 & 255, bb = c & 255, mx = Math.max(rr, Math.max(gg, bb)), mn = Math.min(rr, Math.min(gg, bb));
        return mx >= 40 && (mx - mn) / (double) mx >= 0.3;
    }

    /** Distribute the pixels op by brightness among the colors of palette pal (indices in the recipe) in proportion to their weights. */
    private static void rank(final int[] px, java.util.List<Integer> op, Recipe r, java.util.List<Integer> pal, int[] out) {
        if (op.isEmpty() || pal.isEmpty()) return;
        java.util.Collections.sort(op, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(lum(px[a]), lum(px[b]));
            }
        });
        long total = 0;
        for (int i : pal) total += r.weight[i];
        double[] cum = new double[pal.size()];
        long acc = 0;
        for (int i = 0; i < cum.length; i++) {
            acc += r.weight[pal.get(i)];
            cum[i] = acc / (double) total;
        }
        int n = op.size();
        for (int i = 0; i < n;) {
            int j = i;
            double l = lum(px[op.get(i)]);
            while (j + 1 < n && lum(px[op.get(j + 1)]) == l) j++;
            double p = (i + j + 1) / 2.0 / n;
            int k = 0;
            while (k < cum.length - 1 && cum[k] < p - 1e-9) k++;
            for (int t = i; t <= j; t++) {
                int at = op.get(t);
                out[at] = (px[at] & 0xff000000) | (r.palette[pal.get(k)] & 0xffffff);
            }
            i = j + 1;
        }
    }

    private static double lum(int c) {
        return 0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255);
    }

    private static BufferedImage image(int[] px, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    /** Exact build: replacing colors and our own pixels. Transparent pixels stay transparent. */
    static BufferedImage exact(BufferedImage s, Recipe r) {
        int w = s.getWidth(), h = s.getHeight();
        int[] px = s.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if (c >>> 24 == 0) {
                px[i] = 0;
                continue;
            }
            Integer o = r.pixels.get(i);
            if (o == null) o = r.colors.get(c);
            if (o != null) px[i] = o;
        }
        return image(px, w, h);
    }

    /** md5 of ARGB pixels (as in the recipe): a match means it was built exactly as intended. */
    public static String md5(BufferedImage img) {
        try {
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(px.length * 4);
            for (int c : px) b.putInt(c >>> 24 == 0 ? 0 : c);
            byte[] d = java.security.MessageDigest.getInstance("MD5").digest(b.array());
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x & 0xff));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            return "";
        }
    }
}
