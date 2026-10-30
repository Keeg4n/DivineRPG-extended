package com.divinerpgextended.easter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Звуки пасхалок прямо из установленных у игрока Half-Life и Half-Life 2 (Steam), как "монтирование" игр в Garry's Mod:
 * файлы Valve в мод не входят и никуда не копируются. Half-Life - россыпь WAV в valve/sound, Half-Life 2 - архивы VPK
 * (hl2_sound_misc). Любой WAV приводится к моно 16 бит PCM (Minecraft играет его кодеком WAV). Нет игр - звуки обычные.
 * <p>
 * Имена файлов для ресурсов: {@code hl1/<путь>} и {@code hl2/<путь>} без расширения (путь - от папки sound).
 */
public final class HalfLifeMount {

    private HalfLifeMount() {}

    /** Событие -> варианты; вариант - кандидаты по порядку (первый найденный). */
    public static final Map<String, String[][]> EVENTS = new LinkedHashMap<String, String[][]>();
    static {
        ev("hev.pain", seq("hl1/player/pl_pain%d", 2, 4, 5, 6, 7));
        ev("hev.fallpain", seq("hl1/player/pl_fallpain%d", 1, 2, 3));
        ev("hev.flatline", one("hl1/fvox/flatline"));
        ev("hev.logon", one("hl1/fvox/hev_logon"));
        ev("hev.morphine", one("hl1/fvox/morphine_shot"));
        ev("hev.major_fracture", one("hl1/fvox/major_fracture"));
        ev("hev.minor_fracture", one("hl1/fvox/minor_fracture"));
        ev("hev.health_critical", one("hl1/fvox/health_critical"));
        steps("concrete", "hl1/player/pl_step%d", 4);
        steps("wood", "hl1/player/pl_step%d", 4);
        steps("grass", "hl1/player/pl_dirt%d", 4);
        steps("dirt", "hl1/player/pl_dirt%d", 4);
        steps("gravel", "hl1/player/pl_dirt%d", 4);
        steps("sand", "hl1/player/pl_dirt%d", 4);
        steps("metal", "hl1/player/pl_metal%d", 4);
        steps("ladder", "hl1/player/pl_ladder%d", 4);
        steps("slosh", "hl1/player/pl_slosh%d", 4);
        String[][] wade = new String[8][];
        for (int i = 1; i <= 8; i++) wade[i - 1] = new String[] { "hl2/player/footsteps/wade" + i, "hl1/player/pl_wade" + ((i - 1) % 4 + 1) };
        ev("hev.step.wade", wade);
        ev("hev.sprint", one("hl2/player/suit_sprint"));
        ev("hev.cbar_hit", seq("hl1/weapons/cbar_hit%d", 1, 2));
        ev("hev.cbar_hitbod", seq("hl1/weapons/cbar_hitbod%d", 1, 2, 3));
        ev("hev.cbar_miss", one("hl1/weapons/cbar_miss1"));
        ev("hev.rocketfire", one("hl1/weapons/rocketfire1"));
    }

    private static void ev(String name, String[][] variants) {
        EVENTS.put(name, variants);
    }

    private static String[][] one(String f) {
        return new String[][] { { f } };
    }

    private static String[][] seq(String fmt, int... n) {
        String[][] out = new String[n.length][];
        for (int i = 0; i < n.length; i++) out[i] = new String[] { String.format(fmt, n[i]) };
        return out;
    }

    /** Шаги: Half-Life 2 (по поверхности), запасной - Half-Life. */
    private static void steps(String surface, String hl1, int n) {
        String[][] out = new String[n][];
        for (int i = 1; i <= n; i++) out[i - 1] = new String[] { "hl2/player/footsteps/" + surface + i, String.format(hl1, i) };
        ev("hev.step." + surface, out);
    }

    // ------------------------------------------------------------------ поиск игр

    static File hl1, hl2vpk;
    static Vpk vpk;
    static boolean searched;
    static final Map<String, byte[]> CACHE = new HashMap<String, byte[]>();

    /** Папка valve/sound у Half-Life (или null). */
    public static synchronized File halfLife() {
        search();
        return hl1;
    }

    /** hl2_sound_misc_dir.vpk у Half-Life 2 (или null). */
    public static synchronized File halfLife2() {
        search();
        return hl2vpk;
    }

    /** Заново искать игры (после смены пути в конфиге). */
    public static synchronized void reset() {
        searched = false;
        hl1 = hl2vpk = null;
        vpk = null;
        CACHE.clear();
    }

    static void search() {
        if (searched) return;
        searched = true;
        for (File lib : libraries()) {
            File a = new File(lib, "steamapps/common/Half-Life/valve/sound");
            if (hl1 == null && a.isDirectory()) hl1 = a;
            File b = new File(lib, "steamapps/common/Half-Life 2/hl2/hl2_sound_misc_dir.vpk");
            if (hl2vpk == null && b.isFile()) hl2vpk = b;
        }
        if (hl2vpk != null) {
            try {
                vpk = new Vpk(hl2vpk);
            } catch (IOException e) {
                hl2vpk = null;
            }
        }
    }

    /** Библиотеки Steam: путь из настроек, обычные места Steam и все библиотеки из libraryfolders.vdf. */
    public static List<File> libraries() {
        Set<File> roots = new LinkedHashSet<File>();
        String custom = System.getProperty("dre.steamLibrary", com.divinerpgextended.Config.halfLifePath);
        if (custom != null && !custom.trim().isEmpty()) for (String s : custom.split(";")) roots.add(new File(s.trim()));
        String home = System.getProperty("user.home", "");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String reg = registrySteamPath();
            if (reg != null) roots.add(new File(reg));
            roots.add(new File("C:/Program Files (x86)/Steam"));
            roots.add(new File("C:/Program Files/Steam"));
            for (File r : File.listRoots()) {
                roots.add(new File(r, "SteamLibrary"));
                roots.add(new File(r, "Steam"));
                roots.add(new File(r, "Games/Steam"));
            }
        } else if (os.contains("mac")) {
            roots.add(new File(home, "Library/Application Support/Steam"));
        } else {
            roots.add(new File(home, ".steam/steam"));
            roots.add(new File(home, ".local/share/Steam"));
            roots.add(new File(home, ".var/app/com.valvesoftware.Steam/.local/share/Steam"));
            for (char c = 'c'; c <= 'z'; c++) roots.add(new File("/mnt/" + c + "/SteamLibrary")); // WSL
        }
        List<File> out = new ArrayList<File>();
        for (File r : roots) {
            if (!r.isDirectory()) continue;
            if (!out.contains(r)) out.add(r);
            for (File more : libraryFolders(new File(r, "steamapps/libraryfolders.vdf"))) if (more.isDirectory() && !out.contains(more)) out.add(more);
        }
        return out;
    }

    static final Pattern VDF_PATH = Pattern.compile("\"path\"\\s*\"([^\"]+)\"");

    static List<File> libraryFolders(File vdf) {
        List<File> out = new ArrayList<File>();
        if (!vdf.isFile()) return out;
        try {
            Matcher m = VDF_PATH.matcher(new String(readAll(new FileInputStream(vdf)), Charset.forName("UTF-8")));
            while (m.find()) out.add(new File(m.group(1).replace("\\\\", "\\")));
        } catch (IOException ignored) {}
        return out;
    }

    static String registrySteamPath() {
        try {
            Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath").redirectErrorStream(true).start();
            String s = new String(readAll(p.getInputStream()), Charset.forName("UTF-8"));
            Matcher m = Pattern.compile("SteamPath\\s+REG_SZ\\s+(.+)").matcher(s);
            return m.find() ? m.group(1).trim() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ файлы

    /** Есть ли файл (hl1/... или hl2/... без расширения). */
    public static synchronized boolean has(String name) {
        search();
        if (name.startsWith("hl1/")) return hl1 != null && new File(hl1, name.substring(4) + ".wav").isFile();
        if (name.startsWith("hl2/")) return vpk != null && vpk.has("sound/" + name.substring(4) + ".wav");
        return false;
    }

    /** Первый найденный кандидат варианта (или null). */
    public static String resolve(String[] candidates) {
        for (String c : candidates) if (has(c)) return c;
        return null;
    }

    /** WAV, приведённый к моно 16 бит (null - нет файла или формат не PCM). */
    public static synchronized byte[] wav(String name) {
        if (CACHE.containsKey(name)) return CACHE.get(name);
        byte[] out = null;
        try {
            byte[] raw = null;
            if (name.startsWith("hl1/") && hl1 != null) {
                File f = new File(hl1, name.substring(4) + ".wav");
                if (f.isFile()) raw = readAll(new FileInputStream(f));
            } else if (name.startsWith("hl2/") && vpk != null) {
                raw = vpk.read("sound/" + name.substring(4) + ".wav");
            }
            if (raw != null) out = normalize(raw);
        } catch (IOException ignored) {}
        CACHE.put(name, out);
        return out;
    }

    static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            for (int n; (n = in.read(b)) > 0;) bo.write(b, 0, n);
            return bo.toByteArray();
        } finally {
            in.close();
        }
    }

    /** RIFF WAV PCM 8/16 бит, моно/стерео -> моно 16 бит со своей частотой; лишние блоки (cue, LIST) отбрасываются. */
    public static byte[] normalize(byte[] d) {
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        if (d.length < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) return null; // RIFF....WAVE
        int tag = -1, ch = 0, rate = 0, bits = 0, dataOff = -1, dataLen = 0;
        for (int i = 12; i + 8 <= d.length;) {
            int id = b.getInt(i), sz = b.getInt(i + 4);
            if (sz < 0) break;
            if (id == 0x20746d66) { // "fmt "
                tag = b.getShort(i + 8) & 0xffff;
                ch = b.getShort(i + 10) & 0xffff;
                rate = b.getInt(i + 12);
                bits = b.getShort(i + 22) & 0xffff;
            } else if (id == 0x61746164) { // "data"
                dataOff = i + 8;
                dataLen = Math.min(sz, d.length - dataOff);
            }
            i += 8 + sz + (sz & 1);
        }
        if (tag != 1 || dataOff < 0 || ch < 1 || ch > 2 || (bits != 8 && bits != 16) || rate <= 0) return null;
        int frame = ch * bits / 8, n = dataLen / frame;
        ByteBuffer o = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        o.putInt(0x46464952).putInt(36 + n * 2).putInt(0x45564157);
        o.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        o.putInt(0x61746164).putInt(n * 2);
        for (int i = 0; i < n; i++) {
            int sum = 0;
            for (int c = 0; c < ch; c++) {
                int p = dataOff + i * frame + c * bits / 8;
                sum += bits == 8 ? ((d[p] & 0xff) - 128) << 8 : b.getShort(p);
            }
            o.putShort((short) (sum / ch));
        }
        return o.array();
    }

    /** Архив VPK (v1/v2): каталог в *_dir.vpk, данные - в *_NNN.vpk или в самом каталоге. */
    public static final class Vpk {

        final File dir;
        final String base;
        final Map<String, long[]> entries = new HashMap<String, long[]>(); // путь -> {архив, смещение, длина, начало preload, длина preload}
        final byte[] tree;
        final int dataStart;

        public Vpk(File dirFile) throws IOException {
            dir = dirFile;
            String n = dirFile.getPath();
            base = n.substring(0, n.length() - "_dir.vpk".length());
            byte[] all = readAll(new FileInputStream(dirFile));
            ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt(0) != 0x55aa1234) throw new IOException("не VPK");
            int ver = b.getInt(4), treeLen = b.getInt(8), hdr = ver == 2 ? 28 : 12;
            tree = all;
            dataStart = hdr + treeLen;
            int[] pos = { hdr };
            while (true) {
                String ext = cstr(all, pos);
                if (ext.isEmpty()) break;
                while (true) {
                    String path = cstr(all, pos);
                    if (path.isEmpty()) break;
                    while (true) {
                        String file = cstr(all, pos);
                        if (file.isEmpty()) break;
                        int p = pos[0];
                        int pre = b.getShort(p + 4) & 0xffff, arch = b.getShort(p + 6) & 0xffff;
                        long off = b.getInt(p + 8) & 0xffffffffL, len = b.getInt(p + 12) & 0xffffffffL;
                        entries.put((path.equals(" ") ? "" : path + "/") + file + "." + ext, new long[] { arch, off, len, p + 18, pre });
                        pos[0] = p + 18 + pre;
                    }
                }
            }
        }

        static String cstr(byte[] a, int[] pos) {
            int s = pos[0], e = s;
            while (a[e] != 0) e++;
            pos[0] = e + 1;
            return new String(a, s, e - s, Charset.forName("ISO-8859-1")).toLowerCase();
        }

        public boolean has(String path) {
            return entries.containsKey(path.toLowerCase());
        }

        public int size() {
            return entries.size();
        }

        public byte[] read(String path) throws IOException {
            long[] e = entries.get(path.toLowerCase());
            if (e == null) return null;
            int pre = (int) e[4], len = (int) e[2];
            byte[] out = new byte[pre + len];
            System.arraycopy(tree, (int) e[3], out, 0, pre);
            if (len == 0) return out;
            File f = e[0] == 0x7fff ? dir : new File(String.format("%s_%03d.vpk", base, e[0]));
            long off = e[0] == 0x7fff ? dataStart + e[1] : e[1];
            RandomAccessFile r = new RandomAccessFile(f, "r");
            try {
                r.seek(off);
                r.readFully(out, pre, len);
            } finally {
                r.close();
            }
            return out;
        }
    }
}
