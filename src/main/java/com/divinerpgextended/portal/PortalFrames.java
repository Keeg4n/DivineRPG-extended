package com.divinerpgextended.portal;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.event.world.BlockEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Рамки порталов, которые построил сам телепорт Divine на той стороне (из блоков мира, откуда пришёл игрок: блок
 * Эдема, Дикого леса и т.д.). Добытые, они ничего не роняют - иначе каждый переход давал бы горсть дорогих блоков
 * даром. Портал, который игрок сложил сам, ломается как обычно. Хранится по миру (perWorldStorage).
 */
public class PortalFrames extends WorldSavedData {

    public static final String NAME = "divinerpgextended_portal_frames";
    private final Set<Long> frames = new HashSet<Long>();

    public PortalFrames(String name) {
        super(name);
    }

    public static PortalFrames get(World w) {
        PortalFrames d = (PortalFrames) w.perWorldStorage.loadData(PortalFrames.class, NAME);
        if (d == null) {
            d = new PortalFrames(NAME);
            w.perWorldStorage.setData(NAME, d);
        }
        return d;
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    public boolean has(int x, int y, int z) {
        return frames.contains(key(x, y, z));
    }

    /** Отметить блоки рамки frame вокруг (x, y, z), которых до постройки портала не было (before - снимок до). */
    static void mark(World w, Block frame, Set<Long> before, int cx, int cz, int r) {
        PortalFrames d = get(w);
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) for (int y = 1; y < w.getHeight(); y++) {
            if (w.getBlock(x, y, z) != frame) continue;
            long k = key(x, y, z);
            if (!before.contains(k) && d.frames.add(k)) d.markDirty();
        }
    }

    /** Снимок блоков frame вокруг до постройки портала. */
    static Set<Long> snapshot(World w, Block frame, int cx, int cz, int r) {
        Set<Long> s = new HashSet<Long>();
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) for (int y = 1; y < w.getHeight(); y++)
            if (w.getBlock(x, y, z) == frame) s.add(key(x, y, z));
        return s;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        frames.clear();
        for (long k : unpack(tag.getIntArray("hi"), tag.getIntArray("lo"))) frames.add(k);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        int[] hi = new int[frames.size()], lo = new int[frames.size()];
        int i = 0;
        for (long k : frames) {
            hi[i] = (int) (k >>> 32);
            lo[i++] = (int) k;
        }
        tag.setIntArray("hi", hi);
        tag.setIntArray("lo", lo);
    }

    private static long[] unpack(int[] hi, int[] lo) {
        long[] out = new long[Math.min(hi.length, lo.length)];
        for (int i = 0; i < out.length; i++) out[i] = (long) hi[i] << 32 | lo[i] & 0xFFFFFFFFL;
        return out;
    }

    /** Добыт блок рамки, построенной телепортом: дропа нет, отметка снимается. */
    public static class Drops {

        @SubscribeEvent
        public void onHarvest(BlockEvent.HarvestDropsEvent e) {
            if (e.world.isRemote) return;
            PortalFrames d = get(e.world);
            if (!d.frames.remove(key(e.x, e.y, e.z))) return;
            d.markDirty();
            e.drops.clear();
        }
    }
}
