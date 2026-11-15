package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Where the mod built boss lairs: saved with the world, needed for lair maps. */
public class LairData extends WorldSavedData {

    public static final String NAME = "divinerpgextended_lairs";
    public static final int CARCINUS = 0, SNOW = 1, THRONE = 2, TOMB = 3;

    /** [type, x, y, z] */
    private final List<int[]> lairs = new ArrayList<int[]>();

    public LairData(String name) {
        super(name);
    }

    public static LairData get(World world) {
        LairData data = (LairData) world.mapStorage.loadData(LairData.class, NAME);
        if (data == null) {
            data = new LairData(NAME);
            world.mapStorage.setData(NAME, data);
        }
        return data;
    }

    public void add(int type, int x, int y, int z) {
        lairs.add(new int[] { type, x, y, z });
        markDirty();
    }

    /** A lair site from the seed where building failed (the terrain did not fit): type + FAILED. */
    public static final int FAILED = 100;

    public void fail(int type, int x, int z) {
        add(type + FAILED, x, 0, z);
    }

    /** Whether there is an entry of this type closer than r blocks. */
    public boolean near(int type, int x, int z, int r) {
        for (int[] l : lairs) if (l[0] == type && (long) (l[1] - x) * (l[1] - x) + (long) (l[3] - z) * (l[3] - z) <= (long) r * r) return true;
        return false;
    }

    /** Remove entries of this type closer than r blocks (for the selftest); put back - {@link #add}. */
    public java.util.List<int[]> forget(int type, int x, int z, int r) {
        java.util.List<int[]> gone = new java.util.ArrayList<int[]>();
        for (java.util.Iterator<int[]> it = lairs.iterator(); it.hasNext();) {
            int[] l = it.next();
            if (l[0] == type && (long) (l[1] - x) * (l[1] - x) + (long) (l[3] - z) * (l[3] - z) <= (long) r * r) {
                gone.add(l);
                it.remove();
            }
        }
        if (!gone.isEmpty()) markDirty();
        return gone;
    }

    /** All entries of this type. */
    public java.util.List<int[]> all(int type) {
        java.util.List<int[]> out = new java.util.ArrayList<int[]>();
        for (int[] l : lairs) if (l[0] == type) out.add(l);
        return out;
    }

    /** The nearest lair of this type or null. */
    public int[] nearest(int type, double px, double pz) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] l : lairs) {
            if (l[0] != type) continue;
            double d = (l[1] - px) * (l[1] - px) + (l[3] - pz) * (l[3] - pz);
            if (d < bestD) {
                bestD = d;
                best = l;
            }
        }
        return best;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        lairs.clear();
        NBTTagList list = tag.getTagList("Lairs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            lairs.add(new int[] { t.getInteger("t"), t.getInteger("x"), t.getInteger("y"), t.getInteger("z") });
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int[] l : lairs) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("t", l[0]);
            t.setInteger("x", l[1]);
            t.setInteger("y", l[2]);
            t.setInteger("z", l[3]);
            list.appendTag(t);
        }
        tag.setTag("Lairs", list);
    }
}
