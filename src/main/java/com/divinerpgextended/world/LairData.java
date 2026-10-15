package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Где мод построил логова боссов: сохраняется вместе с миром, нужно для карт логов. */
public class LairData extends WorldSavedData {

    public static final String NAME = "divinerpgextended_lairs";
    public static final int CARCINUS = 0, SNOW = 1, THRONE = 2, TOMB = 3;

    /** [тип, x, y, z] */
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

    /** Ближайшее логово этого типа или null. */
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
