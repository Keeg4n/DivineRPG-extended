package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.divinerpg.entities.vanilla.EntityAridWarrior;
import net.divinerpg.entities.vanilla.EntityDesertCrawler;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.block.BlockBurrow;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The Ancient Tomb under the vanilla desert pyramid - the lair of the Ancient Entity.
 * <p>
 * We recognize the pyramid by the trap in the treasure room: a stone pressure plate with TNT under it through one block. The tomb
 * is not built at once, but when its whole area (about 120 x 60 blocks) is already populated: otherwise late parts of the pyramid and
 * lakes of neighboring chunks would break it (a buried staircase, hanging water). While waiting, the pyramid is in a list that
 * is stored with the world and checked every 5 seconds.
 * <p>
 * We disarm the pyramid trap, move the east chest of the room to the corner, and a wide staircase goes down from the center into the
 * maze about 25 blocks lower: straight corridors as in Egyptian tombs, sandstone walls with a strip of
 * "hieroglyphs" of orange and blue clay, Divine gold lamps in the ceiling. In dead ends there are sarcophagi with chests and
 * undead spawners; in the corridors - a crumbling floor indistinguishable from a normal one, and under it a pit with cobwebs. From the far
 * end of the maze a staircase leads to the Hall of the Clock (a round hall under a dome, a dial floor, an hourglass); on a dais by the east
 * wall - the altar for the Mysterious Clock.
 */
public class WorldGenTomb implements IWorldGenerator {

    private static final int CELLS = 9, PITCH = 6, W = CELLS * PITCH + 1, H = 5;
    /** The altar stands on a dais ALTAR_DX blocks east of the hall center: the summoned boss stands in the center. */
    public static final int ALTAR_DX = 10, ALTAR_DY = 2;

    // ---------------------------------------------------------------- waiting for the surroundings to be finished

    /** Pyramids under which the tomb is not yet built (x, y, z of the pressure plate). Stored with the world. */
    public static class Pending extends WorldSavedData {

        private static final String NAME = "divinerpgextended_tombs_pending";
        final List<int[]> list = new ArrayList<int[]>();

        public Pending(String name) {
            super(name);
        }

        static Pending get(World w) {
            Pending p = (Pending) w.perWorldStorage.loadData(Pending.class, NAME);
            if (p == null) {
                p = new Pending(NAME);
                w.perWorldStorage.setData(NAME, p);
            }
            return p;
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            list.clear();
            NBTTagList l = tag.getTagList("P", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound t = l.getCompoundTagAt(i);
                list.add(new int[] { t.getInteger("x"), t.getInteger("y"), t.getInteger("z") });
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            NBTTagList l = new NBTTagList();
            for (int[] p : list) {
                NBTTagCompound t = new NBTTagCompound();
                t.setInteger("x", p[0]);
                t.setInteger("y", p[1]);
                t.setInteger("z", p[2]);
                l.appendTag(t);
            }
            tag.setTag("P", l);
        }
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != 0) return;
        for (int x = chunkX * 16 + 8; x < chunkX * 16 + 24; x++) {
            for (int z = chunkZ * 16 + 8; z < chunkZ * 16 + 24; z++) {
                for (int y = 70; y >= 30; y--) {
                    if (world.getBlock(x, y, z) == Blocks.stone_pressure_plate && world.getBlock(x, y - 2, z) == Blocks.tnt) {
                        Pending p = Pending.get(world);
                        p.list.add(new int[] { x, y, z });
                        p.markDirty();
                    }
                }
            }
        }
    }

    /** Every 5 seconds we try to build the waiting tombs. */
    public static class Ticker {

        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            World w = event.world;
            if (event.phase != TickEvent.Phase.END || w.isRemote || w.provider.dimensionId != 0
                || w.getTotalWorldTime() % 100 != 0) return;
            Pending p = Pending.get(w);
            if (p.list.isEmpty()) return;
            for (Iterator<int[]> it = p.list.iterator(); it.hasNext();) {
                int[] t = it.next();
                if (!ready(w, t[0], t[1], t[2])) {
                    // a player is at the pyramid, and the tomb area (up to 110 blocks to the east) is not all populated: with a short
                    // view distance it would never get built - we load the missing chunks little by little
                    if (w.getClosestPlayer(t[0], t[1], t[2], 96) != null) fill(w, t[0], t[1], t[2], 12);
                    else release(w, t[0], t[1], t[2]); // the player left - we do not hold the loaded chunks (if they return - we load again)
                    continue;
                }
                it.remove();
                p.markDirty();
                if (w.getBlock(t[0], t[1], t[2]) == Blocks.stone_pressure_plate && build(w, w.rand, t[0], t[1], t[2])) {
                    DivineRPGExtended.LOG.info("Ancient Tomb: " + t[0] + " " + t[2]);
                }
                release(w, t[0], t[1], t[2]);
            }
        }
    }

    /** Length of the staircase from the treasure room to the maze floor. */
    private static int stairLength(int py) {
        int floor = py - 1;
        return floor - Math.max(20, floor - 26);
    }

    /** Chunks of the tomb area (staircase, maze, hall): {ax0, ax1, az0, az1}. */
    private static int[] area(int px, int py, int pz) {
        int d = stairLength(py);
        int x0 = px - 12, x1 = px + d + 2 + W + 40, z0 = pz - 32, z1 = pz + 32;
        return new int[] { (x0 - 8) >> 4, (x1 - 8) >> 4, (z0 - 8) >> 4, (z1 - 8) >> 4 };
    }

    /** The whole tomb area is loaded and populated. */
    private static boolean ready(World w, int px, int py, int pz) {
        int[] a = area(px, py, pz);
        for (int ax = a[0]; ax <= a[1]; ax++) {
            for (int az = a[2]; az <= a[3]; az++) {
                if (!w.getChunkProvider()
                    .chunkExists(ax, az) || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) return false;
            }
        }
        return true;
    }

    /** Load (create) up to max missing chunks of the area and the ring behind it - without the ring the outermost ones will not be populated. */
    private static void fill(World w, int px, int py, int pz, int max) {
        int[] a = area(px, py, pz);
        for (int ax = a[0]; ax <= a[1] + 1 && max > 0; ax++) for (int az = a[2]; az <= a[3] + 1 && max > 0; az++) {
            if (w.getChunkProvider().chunkExists(ax, az)) continue;
            w.getChunkProvider().loadChunk(ax, az);
            max--;
        }
    }

    /** After building: chunks of the area that no player sees (loaded by {@link #fill}) - go to the unload queue. */
    private static void release(World w, int px, int py, int pz) {
        if (!(w instanceof net.minecraft.world.WorldServer)) return;
        net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) w;
        if (ws.getPlayerManager() == null) return; // the world is still being created (searching for the spawn point) - too early to unload
        int[] a = area(px, py, pz);
        for (int ax = a[0]; ax <= a[1] + 1; ax++) for (int az = a[2]; az <= a[3] + 1; az++)
            if (ws.theChunkProviderServer.chunkExists(ax, az) && !ws.getPlayerManager().func_152621_a(ax, az)) ws.theChunkProviderServer.unloadChunksIfNotNearSpawn(ax, az);
    }

    /** Whether a tomb closer than r blocks is waiting to be built (the pyramid exists, the area is not ready yet). */
    static boolean pendingNear(World w, int x, int z, int r) {
        for (int[] t : Pending.get(w).list) if ((long) (t[0] - x) * (t[0] - x) + (long) (t[2] - z) * (t[2] - z) <= (long) r * r) return true;
        return false;
    }

    // ---------------------------------------------------------------- construction

    private static void set(World w, int x, int y, int z, Block b, int meta) {
        w.setBlock(x, y, z, b, meta, 2);
    }

    private static void sandstone(World w, int x, int y, int z, int meta) {
        set(w, x, y, z, Blocks.sandstone, meta);
    }

    /** Divine gold lamp (instead of glowstone and gold blocks: there is light, no free gold). */
    private static void lamp(World w, int x, int y, int z) {
        set(w, x, y, z, VanillaBlocks.lamp1, 10);
    }

    /** Move a chest with all its contents. */
    private static void moveChest(World w, int x, int y, int z, int nx, int ny, int nz) {
        if (!(w.getTileEntity(x, y, z) instanceof TileEntityChest)) return;
        TileEntityChest from = (TileEntityChest) w.getTileEntity(x, y, z);
        ItemStack[] items = new ItemStack[from.getSizeInventory()];
        for (int i = 0; i < items.length; i++) {
            items[i] = from.getStackInSlot(i);
            from.setInventorySlotContents(i, null);
        }
        set(w, x, y, z, Blocks.air, 0);
        set(w, nx, ny, nz, Blocks.chest, 0);
        if (w.getTileEntity(nx, ny, nz) instanceof TileEntityChest) {
            TileEntityChest to = (TileEntityChest) w.getTileEntity(nx, ny, nz);
            for (int i = 0; i < items.length && i < to.getSizeInventory(); i++) to.setInventorySlotContents(i, items[i]);
        }
    }

    /** px, py, pz - the pressure plate in the treasure room. */
    private static boolean build(World w, Random rand, int px, int py, int pz) {
        int floor = py - 1; // floor of the treasure room
        int d = stairLength(py);
        int t = floor - d; // floor of the maze
        if (d < 8) return false;

        // disarm the pyramid trap, move the east chest to the corner of the room (the staircase will pass in its place)
        set(w, px, py, pz, Blocks.air, 0);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) sandstone(w, px + dx, py - 2, pz + dz, 0);
        if (w.getBlock(px + 2, py, pz) == Blocks.chest) moveChest(w, px + 2, py, pz, px - 2, py, pz + 2);

        // staircase down to the east: 3 wide, a step one block down per block forward, walls and vault of sandstone
        for (int i = 0; i <= d + 1; i++) {
            int x = px + i, sy = Math.max(t, floor - i);
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = sy - 1; y <= sy + 5; y++) {
                    if (w.getBlock(x, y, pz + dz) == Blocks.chest) continue;
                    boolean inner = Math.abs(dz) <= 1 && y >= sy + 1 && y <= sy + 4;
                    if (inner) set(w, x, y, pz + dz, Blocks.air, 0);
                    else if (y < floor + 1 || i > 2) sandstone(w, x, y, pz + dz, y == sy ? 2 : 0);
                }
            }
            if (i > 2 && i % 5 == 2) lamp(w, x, sy + 5, pz);
        }

        // maze: cell (0, mid) meets the staircase from the west
        int mid = CELLS / 2;
        int mx0 = px + d + 2, mz0 = pz - 1 - mid * PITCH - 1;
        boolean[][] open = maze(rand, mid);
        open[0][mid * PITCH + 1] = open[0][mid * PITCH + 2] = open[0][mid * PITCH + 3] = true; // entrance from the staircase
        int exitZ = mid * PITCH + 2;
        for (int i = W - 3; i < W; i++) { // exit to the burial hall through a wall of 3 blocks
            open[i][exitZ - 1] = open[i][exitZ] = open[i][exitZ + 1] = true;
        }
        for (int i = 0; i < W; i++) {
            for (int k = 0; k < W; k++) {
                int x = mx0 + i, z = mz0 + k;
                if (open[i][k]) {
                    sandstone(w, x, t, z, (i + k) % 7 == 0 ? 1 : 2);
                    for (int y = t + 1; y < t + H; y++) set(w, x, y, z, Blocks.air, 0);
                    if (i % PITCH == 2 && k % PITCH == 2 && rand.nextInt(3) > 0) lamp(w, x, t + H, z);
                    else sandstone(w, x, t + H, z, 2);
                } else {
                    for (int y = t; y <= t + H; y++) {
                        // strip of "hieroglyphs" at eye level on the corridor walls
                        if (y == t + 3 && touchesOpen(open, i, k)) {
                            int c = (i + k) % 4;
                            if (c == 0) set(w, x, y, z, Blocks.stained_hardened_clay, 1); // orange
                            else if (c == 2) set(w, x, y, z, Blocks.stained_hardened_clay, 11); // blue
                            else sandstone(w, x, y, z, 1);
                        } else {
                            sandstone(w, x, y, z, 0);
                        }
                    }
                }
            }
        }

        // dead ends: sarcophagi with chests and undead spawners
        List<int[]> ends = deadEnds(open);
        Collections.shuffle(ends, rand);
        String[] undead = { (String) EntityList.classToStringMapping.get(EntityAridWarrior.class),
            (String) EntityList.classToStringMapping.get(EntityDesertCrawler.class), "Zombie", "Skeleton" };
        int chests = 0, spawners = 0;
        List<int[]> used = new ArrayList<int[]>();
        for (int[] e : ends) {
            int x = mx0 + e[0], z = mz0 + e[1];
            if (e[0] <= 3 && Math.abs(e[1] - exitZ) <= 3) continue; // not right at the entrance
            used.add(e);
            if (chests < 8) {
                LairLoot.chest(w, rand, x, t + 1, z, Blocks.chest, LairLoot.tomb());
                set(w, x, t + 2, z, Blocks.stone_slab, 1); // sarcophagus: a sandstone slab on top
                chests++;
            } else if (com.divinerpgextended.Config.dungeonSpawners && spawners < 6) {
                String mob = undead[rand.nextInt(undead.length)];
                if (mob == null) mob = "Zombie";
                set(w, x, t, z, Blocks.mob_spawner, 0);
                TileEntityMobSpawner s = (TileEntityMobSpawner) w.getTileEntity(x, t, z);
                if (s != null) s.func_145881_a()
                    .setEntityName(mob);
                spawners++;
            }
        }

        // traps: in crossroad cells the floor crumbles, with a 5-block pit with cobwebs under it
        for (int n = 0, tries = 0; n < 7 && tries < 100; tries++) {
            int ci = 1 + rand.nextInt(CELLS - 1), cj = rand.nextInt(CELLS);
            int x = mx0 + ci * PITCH + 2, z = mz0 + cj * PITCH + 2;
            boolean taken = false;
            for (int[] u : used) if (u[0] == ci * PITCH + 2 && u[1] == cj * PITCH + 2) taken = true;
            if (taken) continue;
            used.add(new int[] { ci * PITCH + 2, cj * PITCH + 2 });
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                for (int y = t - 6; y <= t - 1; y++) {
                    boolean pit = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && y >= t - 5;
                    if (pit) set(w, x + dx, y, z + dz, Blocks.air, 0);
                    else sandstone(w, x + dx, y, z + dz, 0);
                }
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    set(w, x + dx, t - 6, z + dz, Blocks.sand, 0);
                    set(w, x + dx, t, z + dz, ModBlocks.trapFloor, 0);
                }
            }
            set(w, x, t - 5, z, Blocks.web, 0);
            set(w, x + (rand.nextBoolean() ? 1 : -1), t - 5, z + (rand.nextBoolean() ? 1 : -1), Blocks.web, 0);
            n++;
        }

        // descent to the burial hall and the hall itself
        int ex = mx0 + W, ez = mz0 + exitZ;
        int tb = t - 7; // hall floor
        for (int i = 0; i <= 9; i++) {
            int x = ex + i, sy = Math.max(tb, t - Math.max(0, i - 1));
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = sy - 1; y <= sy + 5; y++) {
                    boolean inner = Math.abs(dz) <= 1 && y >= sy + 1 && y <= sy + 4;
                    if (inner) set(w, x, y, ez + dz, Blocks.air, 0);
                    else sandstone(w, x, y, ez + dz, y == sy ? 2 : 0);
                }
            }
        }
        hall(w, ex + 10 + 14, tb, ez); // west wall of the hall right behind the staircase

        LairData.get(w)
            .add(LairData.TOMB, px, w.getTopSolidOrLiquidBlock(px, pz), pz); // pyramid
        return true;
    }

    private static boolean touchesOpen(boolean[][] open, int i, int k) {
        return i > 0 && open[i - 1][k] || i < W - 1 && open[i + 1][k] || k > 0 && open[i][k - 1]
            || k < W - 1 && open[i][k + 1];
    }

    /** Hall of the Clock: the inner half size (the round hall reaches HALL_R + 1.5 from the center) and the dome top above the floor. */
    static final int HALL_R = 13, HALL_DOME = 18;

    /** Decor of the hall without drops (colored inlays, sand of the hourglass): the burrow decor block. */
    private static void decor(World w, int x, int y, int z, int meta) {
        set(w, x, y, z, ModBlocks.burrow, meta);
    }

    /**
     * Hall of the Clock (1.1.3) - the Mysterious Clock summons the boss here: a round hall under a stepped dome, the floor is a dial (a blue ring
     * of hours, a yellow ring with lamps, the hour hand points at the altar, the minute hand to the north-west), ten hour pillars with lamps,
     * an hourglass of glass and sand behind the altar. The entrance from the west (3 x 4), the altar on a dais by the east wall, the center is free:
     * the summoned boss stands there. Inlays and sand are decor without drops ({@link BlockBurrow#DECOR}), glass drops nothing anyway.
     */
    private static void hall(World w, int cx, int tb, int cz) {
        int r = HALL_R, hd = HALL_DOME;
        double rr = r + 1.5;
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                int x = cx + dx, z = cz + dz;
                double d = Math.sqrt(dx * dx + dz * dz);
                boolean inner = Math.abs(dx) <= r + 1 && Math.abs(dz) <= r + 1;
                sandstone(w, x, tb - 1, z, 0);
                for (int y = 1; y <= hd + 1; y++) {
                    if (!inner || y > hd) {
                        sandstone(w, x, tb + y, z, 0);
                        continue;
                    }
                    double lim = y <= 9 ? rr : rr - (y - 9) * 1.35; // the dome narrows in steps above the ninth row
                    if (d < lim) set(w, x, tb + y, z, Blocks.air, 0);
                    else sandstone(w, x, tb + y, z, y % 4 != 0 ? 2 : 1);
                }
                // the dial
                if (!inner || d >= rr) {
                    sandstone(w, x, tb, z, inner ? 2 : 0);
                    continue;
                }
                double ang = Math.atan2(dz, dx);
                if (d >= 1.5 && d <= 8 && dz == 0 && dx > 0) decor(w, x, tb, z, BlockBurrow.CLAY_ORANGE); // hour hand - at the altar
                else if (d >= 1.5 && d <= 9.5 && Math.abs(Math.IEEEremainder(ang + Math.PI * 0.62, Math.PI * 2)) * d < 0.6) decor(w, x, tb, z, BlockBurrow.CLAY_BROWN);
                else if (Math.abs(d - 12.3) < 0.6) decor(w, x, tb, z, BlockBurrow.CLAY_BLUE);
                else if (Math.abs(d - 10.2) < 0.5) decor(w, x, tb, z, BlockBurrow.CLAY_YELLOW);
                else if (d < 1.5) lamp(w, x, tb, z);
                else sandstone(w, x, tb, z, 2);
            }
        }
        // ten hour pillars (the east one is the altar wall, the west one is the passage) and lamps of the hours in the yellow ring
        for (int k = 0; k < 12; k++) {
            if (k == 0 || k == 6) continue;
            double a = k * Math.PI / 6;
            int px = cx + (int) Math.round(Math.cos(a) * 11.6), pz = cz + (int) Math.round(Math.sin(a) * 11.6);
            for (int y = 1; y <= 8; y++) sandstone(w, px, tb + y, pz, y == 1 || y == 8 ? 1 : 2);
            lamp(w, px, tb + 9, pz);
            lamp(w, cx + (int) Math.round(Math.cos(a) * 10.2), tb, cz + (int) Math.round(Math.sin(a) * 10.2));
        }
        // dais with the altar by the east wall: the boss stands in the center of the hall
        int ax = cx + ALTAR_DX;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -2; dz <= 2; dz++) sandstone(w, ax + dx, tb + 1, cz + dz, 2);
        set(w, ax, tb + ALTAR_DY, cz, ModBlocks.tombAltar, 0);
        // the hourglass behind the altar: two bulbs of sand in glass between carved plates, a neck of sand (the upper part goes into the dome)
        int hx = cx + 12;
        for (int y = 3; y <= 15; y++) {
            int t = Math.abs(y - 9), wd = t <= 1 ? 0 : Math.min(2, t - 1);
            for (int dz = -3; dz <= 3; dz++) {
                int z = cz + dz, adz = Math.abs(dz);
                if (y == 3 || y == 15) {
                    sandstone(w, hx, tb + y, z, 1);
                    sandstone(w, hx + 1, tb + y, z, 1);
                    continue;
                }
                if (adz == 3 && (y == 4 || y == 14)) sandstone(w, hx, tb + y, z, 1);
                if (adz <= wd) {
                    if (y <= 6 || y >= 12) decor(w, hx, tb + y, z, BlockBurrow.SAND);
                    else set(w, hx, tb + y, z, Blocks.glass, 0);
                } else if (adz == wd + 1) set(w, hx, tb + y, z, Blocks.glass, 0);
            }
            sandstone(w, hx + 1, tb + y, cz - 3, 1);
            sandstone(w, hx + 1, tb + y, cz + 3, 1);
        }
        for (int y = 7; y <= 11; y++) decor(w, hx, tb + y, cz, BlockBurrow.SAND);
        // passage from the west: the staircase comes in here
        for (int dz = -1; dz <= 1; dz++) for (int x = cx - r - 3; x <= cx - r - 1; x++) for (int y = tb + 1; y <= tb + 4; y++) set(w, x, y, cz + dz, Blocks.air, 0);
    }

    /** A maze by "recursive backtracking" on a cell grid: true - passage (a 3x3 cell and the openings between cells). */
    private static boolean[][] maze(Random rand, int mid) {
        boolean[][] open = new boolean[W][W];
        boolean[][] seen = new boolean[CELLS][CELLS];
        List<int[]> stack = new ArrayList<int[]>();
        stack.add(new int[] { 0, mid });
        seen[0][mid] = true;
        cell(open, 0, mid);
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!stack.isEmpty()) {
            int[] c = stack.get(stack.size() - 1);
            List<int[]> next = new ArrayList<int[]>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < CELLS && nz < CELLS && !seen[nx][nz]) next.add(new int[] { nx, nz });
            }
            if (next.isEmpty()) {
                stack.remove(stack.size() - 1);
                continue;
            }
            int[] n = next.get(rand.nextInt(next.size()));
            seen[n[0]][n[1]] = true;
            cell(open, n[0], n[1]);
            // opening between cells: the wall of thickness 3 between them is broken through across the whole corridor width
            int ax = Math.min(c[0], n[0]) * PITCH, az = Math.min(c[1], n[1]) * PITCH;
            if (c[0] != n[0]) {
                for (int s = 4; s <= 6; s++) for (int q = 1; q <= 3; q++) open[ax + s][c[1] * PITCH + q] = true;
            } else {
                for (int s = 4; s <= 6; s++) for (int q = 1; q <= 3; q++) open[c[0] * PITCH + q][az + s] = true;
            }
            stack.add(n);
        }
        return open;
    }

    private static void cell(boolean[][] open, int cx, int cz) {
        for (int a = 1; a <= 3; a++) for (int b = 1; b <= 3; b++) open[cx * PITCH + a][cz * PITCH + b] = true;
    }

    /** Centers of cells with one exit. */
    private static List<int[]> deadEnds(boolean[][] open) {
        List<int[]> out = new ArrayList<int[]>();
        for (int cx = 0; cx < CELLS; cx++) {
            for (int cz = 0; cz < CELLS; cz++) {
                int mx = cx * PITCH + 2, mz = cz * PITCH + 2, exits = 0;
                if (mx - 3 >= 0 && open[mx - 3][mz]) exits++;
                if (mx + 3 < W && open[mx + 3][mz]) exits++;
                if (mz - 3 >= 0 && open[mx][mz - 3]) exits++;
                if (mz + 3 < W && open[mx][mz + 3]) exits++;
                if (exits == 1) out.add(new int[] { mx, mz });
            }
        }
        return out;
    }
}
