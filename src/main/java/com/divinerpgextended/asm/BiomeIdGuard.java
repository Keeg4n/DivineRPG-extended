package com.divinerpgextended.asm;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ID биомов Divine по умолчанию (100-107) совпадают с биомами Biomes O' Plenty (Tropics, Volcano, Mangrove, Alps
 * Forest...): чей биом зарегистрирован позже, тот затирает другой - в мирах Divine и в обычном мире путаются биомы.
 * До загрузки модов (из coremod) смотрим ID биомов в конфигах других модов и, если ID Divine заняты, переписываем
 * конфиг Divine на свободные (начиная со 170, как на RedServer).
 */
public final class BiomeIdGuard {

    static final Logger LOG = LogManager.getLogger("DivineRPG Extended");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    /** Ключи конфига Divine и ID по умолчанию. */
    static final String[] KEYS = { "Apalachia", "Arcana", "Eden", "Iceika", "Mortum", "Skythern", "Vethea", "Wildwood" };
    static final int[] DEFAULTS = { 102, 106, 100, 107, 104, 103, 105, 101 };
    /** Biomes O' Plenty без своего конфига (первый запуск): его ID по умолчанию. */
    static final int BOP_FROM = 40, BOP_TO = 123;
    private static final Pattern LINE = Pattern.compile("^(\\s*)I:\"(\\w+) biome ID\"=(\\d+)\\s*$");
    private static final Pattern ANY_INT = Pattern.compile("^\\s*I:(\"[^\"]*\"|\\S+)=(\\d+)\\s*$");

    private BiomeIdGuard() {}

    /** Вызов из coremod; ошибки только в лог - запуск игры важнее. */
    public static void run(File gameDir) {
        try {
            Map<String, Integer> moved = resolve(new File(gameDir, "config"), new File(gameDir, "mods"));
            if (!moved.isEmpty()) LOG.warn("ID биомов Divine совпадали с биомами других модов - переставлены: " + moved
                + ". Уже сгенерированные чанки миров Divine могут показывать чужие биомы.");
        } catch (Throwable t) {
            LOG.warn("Проверка ID биомов Divine не удалась: " + t);
        }
    }

    /** @return что переставлено (ключ - новый ID); пусто - конфликтов нет */
    public static Map<String, Integer> resolve(File config, File mods) throws Exception {
        Set<Integer> taken = takenByOthers(config);
        if (hasMod(mods, "biomesoplenty") && !new File(config, "biomesoplenty/ids.cfg").isFile())
            for (int i = BOP_FROM; i <= BOP_TO; i++) taken.add(i);

        File cfg = new File(config, "DivineRPG.cfg");
        Map<String, Integer> ids = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < KEYS.length; i++) ids.put(KEYS[i], DEFAULTS[i]);
        List<String> lines = cfg.isFile() ? Files.readAllLines(cfg.toPath(), UTF8) : new ArrayList<String>();
        for (String l : lines) {
            Matcher m = LINE.matcher(l);
            if (m.matches() && ids.containsKey(m.group(2))) ids.put(m.group(2), Integer.parseInt(m.group(3)));
        }

        // занятые чужими или повторяющиеся у самой Divine
        Set<Integer> own = new TreeSet<Integer>();
        boolean clash = false;
        for (int id : ids.values()) {
            if (taken.contains(id) || !own.add(id) || vanilla(id)) clash = true;
        }
        Map<String, Integer> moved = new LinkedHashMap<String, Integer>();
        if (!clash) return moved;

        Set<Integer> used = new TreeSet<Integer>(taken);
        int next = 170;
        for (String k : KEYS) {
            while (next <= 255 && (used.contains(next) || vanilla(next))) next++;
            if (next > 255) throw new IllegalStateException("нет свободных ID биомов");
            ids.put(k, next);
            used.add(next);
            moved.put(k, next);
            next++;
        }
        write(cfg, lines, ids);
        return moved;
    }

    /** Ванильные биомы: 0-39 и их варианты M (+128). */
    static boolean vanilla(int id) {
        return id < 40 || id >= 128 && id <= 167;
    }

    /** ID из категорий конфигов с "biome" в названии (Biomes O' Plenty, Twilight Forest и др.), кроме самой Divine. */
    static Set<Integer> takenByOthers(File config) throws Exception {
        Set<Integer> out = new TreeSet<Integer>();
        List<File> files = new ArrayList<File>();
        collect(config, files);
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (n.equals("divinerpg.cfg") || n.startsWith("divinerpgextended")) continue;
            String cat = "";
            for (String l : Files.readAllLines(f.toPath(), UTF8)) {
                String t = l.trim();
                if (t.endsWith("{")) cat = t.toLowerCase();
                else if (t.equals("}")) cat = "";
                else if (cat.contains("biome")) {
                    Matcher m = ANY_INT.matcher(l);
                    // только ID: в категориях биомов бывают и веса ("biome weights")
                    if (m.matches() && (cat.contains("id") || m.group(1).toLowerCase().contains("id"))) {
                        int v = Integer.parseInt(m.group(2));
                        if (v >= 0 && v <= 255) out.add(v);
                    }
                }
            }
        }
        return out;
    }

    private static void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".cfg")) out.add(f);
        }
    }

    static boolean hasMod(File dir, String name) {
        File[] fs = dir.listFiles();
        if (fs == null) return false;
        for (File f : fs) {
            if (f.isDirectory() ? hasMod(f, name) : f.getName().toLowerCase().contains(name)) return true;
        }
        return false;
    }

    private static void write(File cfg, List<String> lines, Map<String, Integer> ids) throws Exception {
        List<String> out = new ArrayList<String>();
        Set<String> done = new TreeSet<String>();
        int dim = -1;
        for (String l : lines) {
            Matcher m = LINE.matcher(l);
            if (m.matches() && ids.containsKey(m.group(2))) {
                out.add(m.group(1) + "I:\"" + m.group(2) + " biome ID\"=" + ids.get(m.group(2)));
                done.add(m.group(2));
            } else {
                if (l.trim().equals("dimension {")) dim = out.size();
                out.add(l);
            }
        }
        List<String> add = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : ids.entrySet())
            if (!done.contains(e.getKey())) add.add("    I:\"" + e.getKey() + " biome ID\"=" + e.getValue());
        if (!add.isEmpty()) {
            if (dim < 0) {
                if (out.isEmpty()) out.add("# Configuration file");
                out.add("");
                out.add("dimension {");
                out.addAll(add);
                out.add("}");
            } else out.addAll(dim + 1, add);
        }
        cfg.getParentFile().mkdirs();
        Files.write(cfg.toPath(), out, UTF8);
    }
}
