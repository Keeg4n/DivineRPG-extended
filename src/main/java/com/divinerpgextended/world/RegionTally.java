package com.divinerpgextended.world;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Учёт гарантий по участкам мира (в данных своего измерения): сколько поставлено на участке по виду и сколько подходящих
 * мест пропущено подряд. Им пользуются запертые сундуки Арканы ({@link ArcanaChests}) и реликварии миров
 * ({@link com.divinerpgextended.world.twilight.Reliquaries}): если на участке ещё ничего нет, N-е подходящее место получает
 * постройку без броска шанса.
 */
public class RegionTally extends WorldSavedData {

    private final Map<Long, int[]> regions = new HashMap<Long, int[]>();

    public RegionTally(String name) {
        super(name);
    }

    public static RegionTally get(World w, String name) {
        RegionTally d = (RegionTally) w.perWorldStorage.loadData(RegionTally.class, name);
        if (d == null) {
            d = new RegionTally(name);
            w.perWorldStorage.setData(name, d);
        }
        return d;
    }

    /** Ключ участка (rx, rz) и вида (0..4095). */
    public static long key(int rx, int rz, int kind) {
        return ((long) rx & 0xffffffL) << 36 | ((long) rz & 0xffffffL) << 12 | (kind & 0xfff);
    }

    /** Участок по координате при размере участка size (не меньше 256). */
    public static int region(int c, int size) {
        return Math.floorDiv(c, Math.max(256, size));
    }

    private int[] row(long key) {
        int[] v = regions.get(key);
        if (v == null) regions.put(key, v = new int[2]);
        return v;
    }

    public int count(long key) {
        int[] v = regions.get(key);
        return v == null ? 0 : v[0];
    }

    public int miss(long key) {
        int[] v = regions.get(key);
        return v == null ? 0 : v[1];
    }

    public void missed(long key) {
        row(key)[1]++;
        markDirty();
    }

    public void placed(long key) {
        int[] v = row(key);
        v[0]++;
        v[1] = 0;
        markDirty();
    }

    /**
     * Решение для подходящего места: бросок 1 из chance или гарантия (на участке ещё нет, это pity-е место подряд). Записывает
     * исход. 0 - не ставить, 1 - по броску, 2 - по гарантии.
     */
    public int decide(long key, java.util.Random r, int chance, int pity) {
        boolean lucky = r.nextInt(Math.max(1, chance)) == 0;
        boolean forced = !lucky && count(key) == 0 && miss(key) + 1 >= Math.max(1, pity);
        if (!lucky && !forced) {
            missed(key);
            return 0;
        }
        placed(key);
        return lucky ? 1 : 2;
    }

    /** Для самопроверки: забыть участок. */
    public void forget(long key) {
        if (regions.remove(key) != null) markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        regions.clear();
        NBTTagList l = tag.getTagList("R", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            regions.put(t.getLong("k"), new int[] { t.getInteger("n"), t.getInteger("m") });
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<Long, int[]> e : regions.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("k", e.getKey());
            t.setInteger("n", e.getValue()[0]);
            t.setInteger("m", e.getValue()[1]);
            l.appendTag(t);
        }
        tag.setTag("R", l);
    }
}
