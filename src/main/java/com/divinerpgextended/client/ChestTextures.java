package com.divinerpgextended.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.divinerpgextended.block.BlockLockedChest.Kind;

/**
 * Текстуры запертых сундуков (вариант В концептов). Собираются при запуске из ванильного сундука и, для сундуков Арканы, из
 * текстур стен Арканы установленного DivineRPG - в jar их нет (лицензия Divine). Раскладка - как у ванильного сундука
 * (ModelChest, атлас 64 x 64):
 * <ul>
 * <li>Аркана: грани крышки и короба - камень стены Арканы своего ключа (каждая грань с угла блока), рёбра граней - тот же
 * камень темнее, замок - приглушённый цвет ключа со скважиной;</li>
 * <li>реликварии миров: дерево сундука в цвет мира, окантовка граней и замок - цвет мира.</li>
 * </ul>
 * Отдаёт их {@link DerivedTexturePack} по адресу {@link #path}. Без клиентских классов: автотест собирает их и на сервере.
 */
public final class ChestTextures {

    public static final String DOMAIN = "divinerpgextended", PREFIX = "textures/entity/locked_chest/";

    /** Грани коробки ModelBox (u, v, w, h, d): up, down, west, north, east, south - {x0, y0, x1, y1}. */
    static int[][] faces(int u, int v, int w, int h, int d) {
        return new int[][] { { u + d, v, u + d + w, v + d }, { u + d + w, v, u + 2 * w + d, v + d }, { u, v + d, u + d, v + d + h },
            { u + d, v + d, u + d + w, v + d + h }, { u + d + w, v + d, u + 2 * d + w, v + d + h }, { u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h } };
    }

    static final int UP = 0, DOWN = 1, WEST = 2, NORTH = 3, EAST = 4, SOUTH = 5;
    static final int[][] LID = faces(0, 0, 14, 5, 14), KNOB = faces(0, 0, 2, 4, 1), BOX = faces(0, 19, 14, 10, 14);

    private ChestTextures() {}

    public static String path(Kind k) {
        return PREFIX + k.key + ".png";
    }

    /** Вид по пути текстуры (домен:путь) или null - не наша. */
    public static Kind kind(String key) {
        if (!key.startsWith(DOMAIN + ":" + PREFIX) || !key.endsWith(".png")) return null;
        String name = key.substring((DOMAIN + ":" + PREFIX).length(), key.length() - 4);
        for (Kind k : Kind.values()) if (k.key.equals(name)) return k;
        return null;
    }

    private static BufferedImage read(String path) throws IOException {
        InputStream in = ChestTextures.class.getResourceAsStream("/" + path);
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

    /** Блок 16 x 16 (у анимированных - первый кадр, у текстур высокого разрешения - усреднение). */
    static int[] block16(BufferedImage im) {
        int w = im.getWidth(), f = Math.max(1, w / 16);
        int[] out = new int[256];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            long r = 0, g = 0, b = 0;
            for (int j = 0; j < f; j++) for (int i = 0; i < f; i++) {
                int c = im.getRGB(Math.min(w - 1, x * f + i), Math.min(im.getHeight() - 1, y * f + j));
                r += c >> 16 & 255;
                g += c >> 8 & 255;
                b += c & 255;
            }
            int n = f * f;
            out[y * 16 + x] = 0xff000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        }
        return out;
    }

    static int rgb(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    static int scale(int c, double k) {
        int r = (int) Math.min(255, Math.round((c >> 16 & 255) * k)), g = (int) Math.min(255, Math.round((c >> 8 & 255) * k)),
            b = (int) Math.min(255, Math.round((c & 255) * k));
        return c & 0xff000000 | r << 16 | g << 8 | b;
    }

    /** Цвет с приглушённой насыщенностью и яркостью (HSV). */
    static int mute(int rgb, float sat, float val) {
        float[] hsv = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        return 0xff000000 | java.awt.Color.HSBtoRGB(hsv[0], hsv[1] * sat, hsv[2] * val) & 0xffffff;
    }

    /** Собрать текстуру вида; null - нет ванильного сундука (не на клиенте). */
    public static BufferedImage build(Kind k) throws IOException {
        return build(k, read("assets/minecraft/textures/entity/chest/normal.png"));
    }

    /** Собрать текстуру вида на основе сундука 64 x 64 (null - основы нет или она другого размера). */
    public static BufferedImage build(Kind k, BufferedImage base) throws IOException {
        if (base == null || base.getWidth() != 64 || base.getHeight() != 64) return null;
        int[] t = base.getRGB(0, 0, 64, 64, null, 0, 64);
        int trim = rgb(k.trim);
        if (k.arcana()) {
            BufferedImage wall = read("assets/divinerpg/textures/blocks/" + k.wall + ".png");
            int[] blk = wall != null ? block16(wall) : noise(mute(trim, 0.35F, 0.45F));
            for (int[][] fs : new int[][][] { LID, BOX }) {
                for (int f = 0; f < 6; f++) {
                    int[] r = fs[f];
                    double sh = f == UP ? 1.0 : 0.92;
                    for (int y = r[1]; y < r[3]; y++) for (int x = r[0]; x < r[2]; x++)
                        t[y * 64 + x] = scale(blk[((y - r[1]) % 16) * 16 + (x - r[0]) % 16], sh);
                    for (int x = r[0]; x < r[2]; x++) {
                        t[r[1] * 64 + x] = scale(t[r[1] * 64 + x], 0.55);
                        t[(r[3] - 1) * 64 + x] = scale(t[(r[3] - 1) * 64 + x], 0.55);
                    }
                    for (int y = r[1] + 1; y < r[3] - 1; y++) {
                        t[y * 64 + r[0]] = scale(t[y * 64 + r[0]], 0.55);
                        t[y * 64 + r[2] - 1] = scale(t[y * 64 + r[2] - 1], 0.55);
                    }
                }
            }
            int latch = mute(trim, 0.75F, 0.95F);
            for (int f = 0; f < 6; f++) {
                int[] r = KNOB[f];
                int c = f == EAST || f == WEST ? scale(latch, 0.8) : latch;
                for (int y = r[1]; y < r[3]; y++) for (int x = r[0]; x < r[2]; x++) t[y * 64 + x] = c;
            }
            int[] n = KNOB[NORTH];
            t[(n[1] + 1) * 64 + n[0]] = 0xff141414; // скважина
            t[(n[1] + 2) * 64 + n[0]] = 0xff141414;
        } else {
            float[] th = java.awt.Color.RGBtoHSB(rgb(k.wood) >> 16 & 255, rgb(k.wood) >> 8 & 255, rgb(k.wood) & 255, null);
            for (int i = 0; i < t.length; i++) {
                int c = t[i];
                if (c >>> 24 == 0) continue;
                float[] hsv = java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
                t[i] = c & 0xff000000 | java.awt.Color.HSBtoRGB(th[0], Math.min(1F, th[1] * 0.85F + 0.1F), Math.min(1F, hsv[2] * (0.55F + th[2] * 0.7F))) & 0xffffff;
            }
            for (int[][] fs : new int[][][] { LID, BOX }) {
                for (int[] r : fs) {
                    for (int x = r[0]; x < r[2]; x++) {
                        t[r[1] * 64 + x] = 0xff000000 | trim;
                        t[(r[3] - 1) * 64 + x] = 0xff000000 | trim;
                    }
                    for (int y = r[1]; y < r[3]; y++) {
                        t[y * 64 + r[0]] = 0xff000000 | trim;
                        t[y * 64 + r[2] - 1] = 0xff000000 | trim;
                    }
                }
            }
            for (int f = 0; f < 6; f++) {
                int[] r = KNOB[f];
                int c = f == EAST || f == WEST ? scale(0xff000000 | trim, 0.8) : 0xff000000 | trim;
                for (int y = r[1]; y < r[3]; y++) for (int x = r[0]; x < r[2]; x++) t[y * 64 + x] = c;
            }
        }
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, 64, 64, t, 0, 64);
        return out;
    }

    // ---------------- ключи миров ----------------

    public static final String KEY_PREFIX = "textures/items/worldKey_";

    /** Мир ключа по пути текстуры (домен:путь) или null. */
    public static com.divinerpgextended.world.twilight.TwilightWorld keyWorld(String key) {
        if (!key.startsWith(DOMAIN + ":" + KEY_PREFIX) || !key.endsWith(".png")) return null;
        String name = key.substring((DOMAIN + ":" + KEY_PREFIX).length(), key.length() - 4);
        for (com.divinerpgextended.world.twilight.TwilightWorld w : com.divinerpgextended.world.twilight.TwilightWorld.values()) if (w.key().equals(name)) return w;
        return null;
    }

    /**
     * Ключ мира: ключ разрушенного кирпича Divine (16 x 16 в обеих версиях), перекрашенный по яркости от тёмного дерева мира к
     * его яркому цвету. Нет текстуры Divine - простой ключ своей рисовки.
     */
    public static BufferedImage buildKey(com.divinerpgextended.world.twilight.TwilightWorld w) throws IOException {
        Kind k = Kind.of(w);
        int dark = scale(0xff000000 | rgb(k.wood), 0.55), light = 0xff000000 | rgb(k.trim);
        BufferedImage src = read("assets/divinerpg/textures/items/degradedKey.png");
        int W = src != null ? src.getWidth() : 16, H = src != null ? src.getHeight() : 16;
        int[] px = src != null ? src.getRGB(0, 0, W, H, null, 0, W) : ownKey();
        double max = 1;
        for (int c : px) if (c >>> 24 != 0) max = Math.max(max, lum(c));
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if (c >>> 24 == 0) continue;
            double t = Math.pow(lum(c) / max, 0.8);
            out[i] = c & 0xff000000 | mix(dark, light, t) & 0xffffff;
        }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, W, H, out, 0, W);
        return img;
    }

    static double lum(int c) {
        return 0.299 * (c >> 16 & 255) + 0.587 * (c >> 8 & 255) + 0.114 * (c & 255);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = (int) Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t),
            bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xff000000 | r << 16 | g << 8 | bl;
    }

    /** Свой ключ 16 x 16 на случай, если в Divine нет текстуры: кольцо внизу слева, стержень по диагонали, бородка вверху. */
    static int[] ownKey() {
        String[] rows = { "................", "...........##...", "..........#OO#..", ".........#O##O#.", "........#O#..#O#", ".......#O#..#O#.",
            "......#O#.##O#..", ".....#O#..#O#...", "....#O#...##....", "..##O#..........", ".#OO#...........", "#O##O#..........", "#O#.#O#.........",
            "#O##O#..........", ".#OO#...........", "..##............" };
        int[] out = new int[256];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            char ch = rows[y].charAt(x);
            out[y * 16 + x] = ch == '#' ? 0xff303030 : ch == 'O' ? 0xffd0d0d0 : 0;
        }
        return out;
    }

    /** Запасной камень, если в Divine нет текстуры стены: шум вокруг цвета. */
    static int[] noise(int c) {
        int[] out = new int[256];
        java.util.Random r = new java.util.Random(c);
        for (int i = 0; i < out.length; i++) out[i] = scale(c, 0.8 + r.nextDouble() * 0.4);
        return out;
    }
}
