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
 * Iceika blizzard: a lull of 10-20 minutes, 30 seconds of warning ("the wind is rising"), then 2-3 minutes of blizzard. Where
 * the wind reaches ({@link #exposed}), the cold grows three times faster and sweeps you away; in a closed house and deep in a cave - as usual.
 * The state is stored in the Iceika world save.
 */
public class Blizzard extends WorldSavedData {

    public static final int CALM = 0, WARNING = 1, STORM = 2;
    /** How many steps through the air the wind goes in from the open sky. */
    public static final int WIND_REACH = 12;
    private static final int CHECK_EVERY = 10;
    private static final int[][] SIDES = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
    // the server and client of one game are different threads, each has its own memory
    private static final Map<EntityPlayer, long[]> SERVER_SEEN = new WeakHashMap<EntityPlayer, long[]>(), CLIENT_SEEN = new WeakHashMap<EntityPlayer, long[]>();
    private static final String NAME = "divinerpgextended_blizzard";

    public int phase = CALM, timer = 12000;
    public float windX = 1, windZ = 0;
    /** The Blizzard Spruce has already come for this storm. */
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
     * The wind reaches the player: the storm hits from the side, so a roof or a spruce overhead do not save you. It is asked every tick and every
     * frame, and the air traversal is not free - the answer lives for {@link #CHECK_EVERY} ticks.
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
     * Whether you can get from this place through the air to the open sky in no more than {@link #WIND_REACH} steps. A house with a closed door and
     * the depth of a cave shelter, an awning, a canopy and a cave entrance do not.
     */
    public static boolean windReaches(World w, int x, int y, int z) {
        if (y >= w.getHeight()) return true;
        if (y < 0 || w.getBlock(x, y, z).getMaterial().isLiquid()) return false; // head underwater
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

    /** Sky above the place: the game treats glass as transparent to the sky, but it stops the wind. */
    private static boolean openSky(World w, int x, int y, int z) {
        if (!w.canBlockSeeTheSky(x, y, z)) return false;
        for (int up = y + 1; up < w.getHeight(); up++) if (!windPasses(w, x, up, z)) return false;
        return true;
    }

    /** The wind passes through air, foliage and everything that can be walked through; water, walls and closed doors and trapdoors stop it. */
    private static boolean windPasses(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        Material m = b.getMaterial();
        if (m.isLiquid()) return false;
        if (m == Material.leaves) return true;
        // at a trapdoor the game answers the opposite: for pathfinding a closed trapdoor is a floor that can be walked on
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

    /** Phase change - once a second in the Iceika world. */
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
