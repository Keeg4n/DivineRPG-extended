package com.divinerpgextended.cold;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Вьюга Iceika: затишье 10-20 минут, 30 секунд предупреждения ("ветер усиливается"), затем 2-3 минуты вьюги. Там, куда
 * достаёт ветер ({@link #exposed}), холод растёт втрое быстрее и сносит; в закрытом доме и в глубине пещеры - как обычно.
 * Состояние хранится в сохранении мира Iceika.
 */
public class Blizzard extends WorldSavedData {

    public static final int CALM = 0, WARNING = 1, STORM = 2;
    /** На сколько шагов по воздуху ветер заходит с открытого неба. */
    public static final int WIND_REACH = 12;
    private static final int CHECK_EVERY = 10;
    private static final int[][] SIDES = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
    // сервер и клиент одной игры - разные потоки, память у каждого своя
    private static final Map<EntityPlayer, long[]> SERVER_SEEN = new WeakHashMap<EntityPlayer, long[]>(), CLIENT_SEEN = new WeakHashMap<EntityPlayer, long[]>();
    private static final String NAME = "divinerpgextended_blizzard";

    public int phase = CALM, timer = 12000;
    public float windX = 1, windZ = 0;
    /** За эту метель Вьюжная ель уже приходила. */
    public boolean spruceCame;

    public Blizzard(String name) {
        super(name);
    }

    public static Blizzard get(World world) {
        Blizzard b = (Blizzard) world.perWorldStorage.loadData(Blizzard.class, NAME);
        if (b == null) {
            b = new Blizzard(NAME);
            world.perWorldStorage.setData(NAME, b);
        }
        return b;
    }

    /**
     * Ветер достаёт игрока: метель бьёт сбоку, поэтому крыша или ёлка над головой не спасают. Спрашивают каждый тик и каждый
     * кадр, а обход воздуха не бесплатный - ответ живёт {@link #CHECK_EVERY} тиков.
     */
    public static boolean exposed(EntityPlayer p) {
        Map<EntityPlayer, long[]> seen = p.worldObj.isRemote ? CLIENT_SEEN : SERVER_SEEN;
        long now = p.worldObj.getTotalWorldTime();
        long[] s = seen.get(p);
        if (s == null) seen.put(p, s = new long[] { now - CHECK_EVERY, 0 });
        if (now - s[0] >= CHECK_EVERY || now < s[0]) {
            s[0] = now;
            s[1] = windReaches(p.worldObj, MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY) + 1, MathHelper.floor_double(p.posZ)) ? 1 : 0;
        }
        return s[1] != 0;
    }

    /**
     * Можно ли от этого места дойти по воздуху до открытого неба не дальше {@link #WIND_REACH} шагов. Дом с закрытой дверью и
     * глубина пещеры укрывают, навес, крона и вход в пещеру - нет.
     */
    public static boolean windReaches(World w, int x, int y, int z) {
        if (y >= w.getHeight()) return true;
        if (y < 0 || w.getBlock(x, y, z).getMaterial().isLiquid()) return false; // голова под водой
        int size = WIND_REACH * 2 + 1;
        boolean[] visited = new boolean[size * size * size];
        visited[(WIND_REACH * size + WIND_REACH) * size + WIND_REACH] = true;
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        queue.add(new int[] { x, y, z, 0 });
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (openSky(w, c[0], c[1], c[2])) return true;
            if (c[3] == WIND_REACH) continue;
            for (int[] d : SIDES) {
                int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
                if (ny < 0) continue;
                if (ny >= w.getHeight()) return true;
                int i = ((nx - x + WIND_REACH) * size + ny - y + WIND_REACH) * size + nz - z + WIND_REACH;
                if (visited[i]) continue;
                visited[i] = true;
                if (windPasses(w, nx, ny, nz)) queue.add(new int[] { nx, ny, nz, c[3] + 1 });
            }
        }
        return false;
    }

    /** Небо над местом: стекло игра считает прозрачным для неба, но ветер оно держит. */
    private static boolean openSky(World w, int x, int y, int z) {
        if (!w.canBlockSeeTheSky(x, y, z)) return false;
        for (int up = y + 1; up < w.getHeight(); up++) if (!windPasses(w, x, up, z)) return false;
        return true;
    }

    /** Ветер проходит сквозь воздух, листву и всё, сквозь что можно пройти; вода, стены и закрытые двери и люки его держат. */
    private static boolean windPasses(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        Material m = b.getMaterial();
        if (m.isLiquid()) return false;
        if (m == Material.leaves) return true;
        // у люка игра отвечает наоборот: для поиска пути закрытый люк - пол, по которому идут
        if (b instanceof BlockTrapDoor) return (w.getBlockMetadata(x, y, z) & 4) != 0;
        return b.getBlocksMovement(w, x, y, z);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        phase = tag.getInteger("Phase");
        timer = tag.getInteger("Timer");
        windX = tag.getFloat("WindX");
        windZ = tag.getFloat("WindZ");
        spruceCame = tag.getBoolean("SpruceCame");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Phase", phase);
        tag.setInteger("Timer", timer);
        tag.setFloat("WindX", windX);
        tag.setFloat("WindZ", windZ);
        tag.setBoolean("SpruceCame", spruceCame);
    }

    /** Смена фаз - раз в секунду в мире Iceika. */
    public static class Ticker {

        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            World w = event.world;
            if (event.phase != TickEvent.Phase.END || w.isRemote || w.getTotalWorldTime() % 20 != 0
                || !ColdHandler.isIceika(w)) return;
            Blizzard b = get(w);
            if (b.phase == STORM) com.divinerpgextended.entity.mini.MiniBosses.spruceStep(w, b);
            b.timer -= 20;
            if (b.timer > 0) {
                if (b.timer % 200 == 0) b.markDirty();
                return;
            }
            if (b.phase == CALM) {
                b.phase = WARNING;
                b.timer = 600;
                double a = w.rand.nextDouble() * Math.PI * 2;
                b.windX = (float) Math.cos(a);
                b.windZ = (float) Math.sin(a);
                tell(w, "dre.chat.blizzard_warning");
            } else if (b.phase == WARNING) {
                b.phase = STORM;
                b.spruceCame = false;
                b.timer = 2400 + w.rand.nextInt(1201);
                tell(w, "dre.chat.blizzard_start");
            } else {
                b.phase = CALM;
                b.timer = 12000 + w.rand.nextInt(12001);
                tell(w, "dre.chat.blizzard_end");
            }
            b.markDirty();
        }

        private static void tell(World w, String key) {
            for (Object o : w.playerEntities) ((EntityPlayer) o).addChatMessage(new ChatComponentTranslation(key));
        }
    }
}
