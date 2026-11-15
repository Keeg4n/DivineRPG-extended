package com.divinerpgextended.world;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Tally of guarantees by world region (in its dimension's data): how many were placed in the region by kind and how many suitable
 * places were skipped in a row. Used by Arcana locked chests ({@link ArcanaChests}) and world reliquaries
 * ({@link com.divinerpgextended.world.twilight.Reliquaries}): if the region has nothing yet, the N-th suitable place gets
 * a structure without a chance roll.
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

    /** Key of region (rx, rz) and kind (0..4095). */
    public static long key(int rx, int rz, int kind) {
        return ((long) rx & 0xffffffL) << 36 | ((long) rz & 0xffffffL) << 12 | (kind & 0xfff);
    }

    /** Region by coordinate at region size size (not less than 256). */
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
     * Decision for a suitable place: a 1-in-chance roll or the guarantee (the region has none yet, this is the pity-th place in a row). Records
     * the outcome. 0 - do not place, 1 - by roll, 2 - by guarantee.
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

    /** For the selftest: forget the region. */
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
