package com.divinerpgextended.dummy;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Dummies on the server - no more than {@link #MAX}: the list of standing ones (across all worlds, including unloaded chunks)
 * is stored together with the main world. A dummy enters the list when it is placed (or on the first tick - those placed before
 * 0.99.33), and leaves it when it is removed.
 */
public class DummyLimit extends WorldSavedData {

    public static final String NAME = "divinerpgextended_dummies";
    public static final int MAX = 3;

    private final Set<UUID> placed = new LinkedHashSet<UUID>();

    public DummyLimit(String name) {
        super(name);
    }

    public static DummyLimit get() {
        World w = MinecraftServer.getServer().worldServerForDimension(0);
        DummyLimit d = (DummyLimit) w.mapStorage.loadData(DummyLimit.class, NAME);
        if (d == null) {
            d = new DummyLimit(NAME);
            w.mapStorage.setData(NAME, d);
        }
        return d;
    }

    public boolean full() {
        return placed.size() >= MAX;
    }

    public int count() {
        return placed.size();
    }

    public void add(UUID id) {
        if (placed.add(id)) markDirty();
    }

    public void remove(UUID id) {
        if (placed.remove(id)) markDirty();
    }

    /** Forget all records (for selftest); restore with {@link #add}. */
    public Set<UUID> clear() {
        Set<UUID> was = new LinkedHashSet<UUID>(placed);
        placed.clear();
        markDirty();
        return was;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        placed.clear();
        NBTTagList l = t.getTagList("Dummies", 8);
        for (int i = 0; i < l.tagCount(); i++) {
            try {
                placed.add(UUID.fromString(l.getStringTagAt(i)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (UUID id : placed) l.appendTag(new NBTTagString(id.toString()));
        t.setTag("Dummies", l);
    }
}
