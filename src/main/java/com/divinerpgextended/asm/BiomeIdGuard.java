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
 * Default Divine biome IDs (100-107) coincide with Biomes O' Plenty biomes (Tropics, Volcano, Mangrove, Alps
 * Forest...): whichever biome is registered later overwrites the other - biomes get mixed up in the Divine worlds and in the Overworld.
 * Before mods load (from the coremod) we look at biome IDs in the configs of other mods and, if the Divine IDs are taken, rewrite
 * the Divine config to free ones (starting from 170, as on RedServer).
 */
public final class BiomeIdGuard {

    static final Logger LOG = LogManager.getLogger("DivineRPG Extended");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    /** Divine config keys and default IDs. */
    static final String[] KEYS = { "Apalachia", "Arcana", "Eden", "Iceika", "Mortum", "Skythern", "Vethea", "Wildwood" };
    static final int[] DEFAULTS = { 102, 106, 100, 107, 104, 103, 105, 101 };
    /** Biomes O' Plenty without its own config (first launch): its default IDs. */
    static final int BOP_FROM = 40, BOP_TO = 123;
    private static final Pattern LINE = Pattern.compile("^(\\s*)I:\"(\\w+) biome ID\"=(\\d+)\\s*$");
    private static final Pattern ANY_INT = Pattern.compile("^\\s*I:(\"[^\"]*\"|\\S+)=(\\d+)\\s*$");

    private BiomeIdGuard() {}

    /** Called from the coremod; errors go only to the log - starting the game is more important. */
    public static void run(File gameDir) {
        try {
            Map<String, Integer> moved = resolve(new File(gameDir, "config"), new File(gameDir, "mods"));
            if (!moved.isEmpty()) LOG.warn("Divine biome IDs collided with biomes of other mods - reassigned: " + moved
                + ". Already generated chunks of Divine worlds may show foreign biomes.");
        } catch (Throwable t) {
            LOG.warn("Divine biome ID check failed: " + t);
        }
    }

    /** @return what was rearranged (key - new ID); empty - no conflicts */
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

        // taken by others or duplicated within Divine itself
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
            if (next > 255) throw new IllegalStateException("no free biome IDs");
            ids.put(k, next);
            used.add(next);
            moved.put(k, next);
            next++;
        }
        write(cfg, lines, ids);
        return moved;
    }

    /** Vanilla biomes: 0-39 and their M variants (+128). */
    static boolean vanilla(int id) {
        return id < 40 || id >= 128 && id <= 167;
    }

    /** IDs from config categories with "biome" in the name (Biomes O' Plenty, Twilight Forest and others), except Divine itself. */
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
                    // IDs only: biome categories also have weights ("biome weights")
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
