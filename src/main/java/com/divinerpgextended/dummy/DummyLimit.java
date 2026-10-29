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
 * Манекенов на сервере - не больше {@link #MAX}: список стоящих (по всем мирам, в том числе в выгруженных чанках)
 * хранится вместе с основным миром. Манекен попадает в список, когда его ставят (или при первом тике - стоявшие до
 * 0.99.33), и уходит из него, когда его убирают.
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

    /** Забыть все записи (для автотеста); вернуть - {@link #add}. */
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
