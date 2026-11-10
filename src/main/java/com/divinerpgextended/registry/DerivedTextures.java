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
 * Текстуры, сделанные из текстур DivineRPG (перекраски и переделки), в jar не лежат: лицензия DivineRPG не разрешает
 * переносить её ресурсы в другие проекты. Вместо картинок - таблицы {@link #FILE}: из какой текстуры установленного
 * DivineRPG собрать нашу, какой цвет на какой заменить и какие отдельные пиксели нарисовать своими. Клиент собирает их при
 * запуске ({@link com.divinerpgextended.client.DerivedTexturePack}).
 * <p>
 * Таблицы точные для DivineRPG 1.4.2.3. В другой версии (1.4.1.5 из сборки Magic) часть текстур перерисована: тогда
 * исходник не совпадает по md5, и берётся запасной исходник (если указан) или тот же, перекрашенный в нашу палитру по
 * яркости ({@link #recolor}): самый тёмный пиксель - в самый тёмный наш цвет и так далее.
 */
public final class DerivedTextures {

    public static final String FILE = "/assets/divinerpgextended/derived_textures.txt";

    /**
     * Рецепт: выход (домен:путь), исходники Divine (пути в jar: основной и запасные), md5 точного результата и основного
     * исходника, замены цветов и пикселей (ARGB), наша палитра по яркости (цвет и число пикселей).
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

    /** Как собрана последняя текстура: точно, перекраской основного исходника или запасного (для автотеста). */
    public static String lastWay = "";

    private static Map<String, Recipe> recipes;

    private DerivedTextures() {}

    /** Все рецепты по выходу "домен:путь". */
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
                com.divinerpgextended.DivineRPGExtended.LOG.warn("Не прочитаны рецепты текстур: " + ex);
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

    /** Текстура из jar DivineRPG (null - нет такой). */
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
     * Собрать текстуру по рецепту; null - ни одного исходника нет. Основной исходник тот же, что в таблице, - точная сборка;
     * иначе перекраска по палитре запасного исходника (первого найденного), а нет запасных - основного.
     */
    public static BufferedImage build(Recipe r) throws IOException {
        BufferedImage main = source(r.src[0]);
        if (main != null && md5(main).equals(r.srcMd5)) {
            lastWay = "точно";
            return exact(main, r);
        }
        for (int k = 1; k < r.src.length; k++) {
            BufferedImage alt = source(r.src[k]);
            if (alt != null) {
                lastWay = "запасной исходник";
                return recolor(alt, r);
            }
        }
        if (main == null) return null;
        lastWay = "перекраска";
        return recolor(main, r);
    }

    /** Наша палитра на чужую версию текстуры: пиксели по яркости раскладываются по нашим цветам в тех же долях. */
    static BufferedImage recolor(BufferedImage s, Recipe r) {
        int w = s.getWidth(), h = s.getHeight();
        final int[] px = s.getRGB(0, 0, w, h, null, 0, w);
        java.util.List<Integer> op = new java.util.ArrayList<Integer>();
        for (int i = 0; i < px.length; i++) {
            if (px[i] >>> 24 == 0) px[i] = 0;
            else op.add(i);
        }
        if (r.palette.length == 0 || op.isEmpty()) return image(px, w, h);
        java.util.Collections.sort(op, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(lum(px[a]), lum(px[b]));
            }
        });
        long total = 0;
        for (int wgt : r.weight) total += wgt;
        double[] cum = new double[r.palette.length];
        long acc = 0;
        for (int i = 0; i < cum.length; i++) {
            acc += r.weight[i];
            cum[i] = acc / (double) total;
        }
        int[] out = new int[px.length];
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
                out[at] = (px[at] & 0xff000000) | (r.palette[k] & 0xffffff);
            }
            i = j + 1;
        }
        return image(out, w, h);
    }

    private static double lum(int c) {
        return 0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255);
    }

    private static BufferedImage image(int[] px, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    /** Точная сборка: замена цветов и своих пикселей. Прозрачные пиксели остаются прозрачными. */
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

    /** md5 пикселей ARGB (как в рецепте): совпал - собрано точно как задумано. */
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
