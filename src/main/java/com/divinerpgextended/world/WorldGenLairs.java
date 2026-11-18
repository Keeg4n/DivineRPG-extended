package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.divinerpg.entities.vanilla.EntityFrost;
import net.divinerpg.entities.vanilla.EntityGlacon;
import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.block.BlockBurrow;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Generation of early boss lairs in the overworld:
 * <ul>
 * <li>the Carcinus lair at a sandy shore (of a continent or island): a funnel at the water's edge, a long slanting shaft into a
 * flooded labyrinth of burrows under the shore and seabed, behind a bubble curtain - a dry grotto with the Carcinus Altar;</li>
 * <li>an ice cave under a snowy biome: an entrance through an igloo, winding passages with Glacons and Frosts, a hall with the
 * Snow Glacon Altar.</li>
 * </ul>
 */
public class WorldGenLairs implements IWorldGenerator {

    /** Burrow labyrinth: a grid of N x N nodes with step STEP (about 42 blocks across). */
    private static final int N = 11, STEP = 7;

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != 0) return;
        // lair sites - from the world seed by the biome map ({@link LairSites}): the lair map knows them in advance
        int[] crab = LairSites.siteInChunk(world, LairSites.CARCINUS, chunkX, chunkZ);
        if (crab != null) {
            if (crabLair(world, rand, crab[0], crab[1])) DivineRPGExtended.LOG.info("Carcinus lair: " + crab[0] + " " + crab[1]);
            else LairData.get(world).fail(LairData.CARCINUS, crab[0], crab[1]); // the terrain did not fit - the map will skip this site
        }
        int[] snow = LairSites.siteInChunk(world, LairSites.SNOW, chunkX, chunkZ);
        if (snow != null) {
            if (snowDungeon(world, rand, snow[0], snow[1]) != null) DivineRPGExtended.LOG.info("Glacon ice dome, entrance: " + snow[0] + " " + snow[1]);
            else LairData.get(world).fail(LairData.SNOW, snow[0], snow[1]);
        }
    }

    // ---------------------------------------------------------------- Carcinus lair

    private static boolean isWater(World w, int x, int y, int z) {
        return w.getBlock(x, y, z)
            .getMaterial() == Material.water;
    }

    /** The top solid block of the bottom under water. */
    private static int seabed(World w, int x, int z) {
        int y = 62;
        while (y > 5 && isWater(w, x, y, z)) y--;
        return y;
    }

    /** Neighbors at distance 3: there must be land somewhere near the entrance point. */
    private static final int[][] SHORE = { { 3, 0 }, { -3, 0 }, { 0, 3 }, { 0, -3 }, { 2, 2 }, { -2, -2 }, { 2, -2 },
        { -2, 2 } };

    /**
     * A crab burrow at the shore: at the very water's edge an uneven funnel, from it a long shaft goes diagonally down into a
     * flooded burrow labyrinth at height 30-40 under the shore and seabed.
     * The passages are crooked, of different widths, with loops, dead-end pockets and widenings - as if the crab itself dug them. The walls,
     * floor and ceiling are burrow blocks: if broken, the passage is filled with sand. Under the floor are hidden Burrow Crab Nests, on the
     * ceiling glow Kraken lamps and aqua lamps. In the east behind a bubble curtain is a dry grotto with the Carcinus summoning altar - a ship
     * graveyard (1.1.3: the bow of a wreck, a fallen mast, cargo, a fish skeleton); over the funnel lies a wrecked ship aground.
     */
    private boolean crabLair(World world, Random rand, int cx, int cz) {
        // entrance: sea-level water right at land (the beach edge). Several candidates: the labyrinth goes from the entrance
        // east, and a thickness of bottom is needed above it - at a sharp drop (Biomes O' Plenty oceans) the first entrance may not fit
        int span = (N - 1) * STEP;
        int ex = 0, ez = 0, ym = 0, entrances = 0, bestBed = -1;
        for (int t = 0; t < 160 && entrances < 12 && ym == 0; t++) {
            int x = cx + rand.nextInt(48) - 24, z = cz + rand.nextInt(48) - 24;
            if (!isWater(world, x, 62, z) || seabed(world, x, z) < 55) continue;
            boolean shore = false;
            for (int[] d : SHORE) {
                if (!isWater(world, x + d[0], 62, z + d[1]) && world.getBlock(x + d[0], 62, z + d[1]).getMaterial().isSolid()) shore = true;
            }
            if (!shore) continue;
            entrances++;
            // the deepest bottom above the future passages; the passages sink under it, but not below 20
            int bed = 62;
            for (int xx = x - 4; xx <= x + 95 + 2 * DEN_R + 4 && bed >= 26; xx += 4) {
                for (int zz = z - (N / 2) * STEP - 8; zz <= z + (N / 2) * STEP + 11; zz += 4) bed = Math.min(bed, seabed(world, xx, zz));
            }
            bestBed = Math.max(bestBed, bed);
            if (bed - 6 >= 20) {
                ex = x;
                ez = z;
                ym = Math.min(34, bed - 6); // the burrow walls hold water: at least 6 blocks of bottom above the passages
            }
        }
        if (ym == 0) {
            DivineRPGExtended.LOG.info("Carcinus lair did not generate at " + cx + " " + cz + ": entrances at the waterline " + entrances
                + (entrances > 0 ? ", bed above the tunnels too deep (best " + bestBed + ")" : ""));
            return false;
        }

        int x0 = ex + 20, z0 = ez - (N / 2) * STEP; // node (0, 0); the entrance is 20 blocks west of the first node
        int gx0 = x0 + span + 5, gcx = gx0 + DEN_R, gcz = z0 + (N / 2) * STEP; // the grotto wall and its center
        int ax0 = ex - 10, ax1 = gcx + DEN_R + 2, az0 = z0 - 8, az1 = z0 + span + 8;

        Carver c = new Carver(ax0, ym - 8, az0, ax1 - ax0 + 1, 72 - (ym - 8), az1 - az0 + 1, gx0 - 1);

        // nodes with a shift so that the passages do not run along a ruler
        double[][][] node = new double[N][N][];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                node[i][j] = new double[] { x0 + i * STEP + rand.nextInt(3) - 1 + 0.5, ym + rand.nextInt(5) - 2 + 0.5,
                    z0 + j * STEP + rand.nextInt(3) - 1 + 0.5 };
            }
        }
        double[] exit = node[N - 1][N / 2] = new double[] { x0 + span + 0.5, ym + 0.5, gcz + 0.5 };
        double[] start = node[0][N / 2];

        // the labyrinth skeleton by "recursive backtracker" + a few loops so there is somewhere to get lost
        List<int[]> edges = mazeEdges(rand, N, N, 0, N / 2);
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int n = 0; n < 6; n++) {
            int i = rand.nextInt(N), j = rand.nextInt(N);
            int[] d = dirs[rand.nextInt(4)];
            if (i + d[0] >= 0 && i + d[0] < N && j + d[1] >= 0 && j + d[1] < N) {
                edges.add(new int[] { i, j, i + d[0], j + d[1] });
            }
        }
        for (int[] e : edges) c.line(node[e[0]][e[1]], node[e[2]][e[3]], rand, true);

        // pit widenings and dead-end pockets (some pockets have chests)
        for (int n = 0; n < 7; n++) {
            double[] p = node[1 + rand.nextInt(N - 2)][rand.nextInt(N)];
            c.blob(p[0], p[1] + 0.5, p[2], 2.8 + rand.nextDouble(), 1.9);
        }
        List<double[]> pockets = new ArrayList<double[]>();
        for (int n = 0; n < 14; n++) {
            double[] p = node[rand.nextInt(N - 1)][rand.nextInt(N)];
            double a = rand.nextDouble() * Math.PI * 2, len = 4 + rand.nextInt(4);
            double[] q = { p[0] + Math.cos(a) * len, p[1] + rand.nextInt(3) - 1, p[2] + Math.sin(a) * len };
            c.line(p, q, rand, true);
            c.blob(q[0], q[1], q[2], 2.0, 1.4);
            pockets.add(q);
        }

        // the passage to the grotto: ends at the east wall, then the bubble curtain
        c.line(exit, new double[] { gx0 - 0.5, ym + 0.5, gcz + 0.5 }, rand, false);
        for (int y = ym - 1; y <= ym + 1; y++) c.add(gx0 - 1, y, gcz);

        // a slanting shaft from the funnel on the bottom to the first node
        int ey = seabed(world, ex, ez);
        c.line(new double[] { ex + 0.5, ey + 1.5, ez + 0.5 }, start, rand, false);

        // flood the passages with water and line them with burrow blocks (above the bottom is open water, we do not touch it)
        for (int[] p : c.cells) set(world, p[0], p[1], p[2], Blocks.water);
        for (int[] p : c.cells) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int x = p[0] + dx, y = p[1] + dy, z = p[2] + dz;
                if (c.has(x, y, z)) continue;
                Block b = world.getBlock(x, y, z);
                if (b.getMaterial() == Material.water || b == Blocks.bedrock || b == ModBlocks.burrow) continue;
                boolean sandy = c.has(x, y + 1, z) || c.has(x, y - 1, z);
                if (rand.nextInt(6) == 0) sandy = !sandy;
                burrow(world, x, y, z, sandy ? 1 : 0);
            }
        }

        // a funnel around the shaft and an uneven rampart of sand and sandstone
        double r0 = 3.2 + rand.nextDouble() * 1.5;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int x = ex + dx, z = ez + dz, top = seabed(world, x, z);
                if (!isWater(world, x, 62, z)) continue; // only underwater, not on the beach
                double dist = Math.sqrt(dx * dx + dz * dz), r = r0 + (rand.nextDouble() - 0.5) * 1.6;
                if (dist < r) {
                    int depth = dist < r * 0.5 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        if (world.getBlock(x, top - d, z) != Blocks.bedrock) set(world, x, top - d, z, Blocks.water);
                    }
                } else if (dist < r + 2.5 && rand.nextInt(100) < 60) {
                    int h = rand.nextInt(3) == 0 ? 2 : 1;
                    for (int d = 1; d <= h; d++) {
                        set(world, x, top + d, z, rand.nextBoolean() ? Blocks.sand : Blocks.sandstone);
                    }
                }
            }
        }

        // Kraken lamps and aqua lamps in the ceiling
        List<int[]> lamps = new ArrayList<int[]>();
        for (int[] p : c.path) {
            if (rand.nextInt(6) != 0 || near(lamps, p, 7)) continue;
            int y = p[1];
            while (c.has(p[0], y + 1, p[2]) && y - p[1] < 4) y++;
            if (world.getBlock(p[0], y + 1, p[2]) != ModBlocks.burrow) continue;
            lamp(world, rand, p[0], y + 1, p[2]);
            lamps.add(p);
        }

        // shark nests under the floor throughout the labyrinth, but not right at the entrance
        List<int[]> path = new ArrayList<int[]>(c.path);
        Collections.shuffle(path, rand);
        List<int[]> nests = new ArrayList<int[]>();
        for (int[] p : path) {
            if (nests.size() >= 10) break;
            double sdx = p[0] - start[0], sdz = p[2] - start[2];
            if (sdx * sdx + sdz * sdz < 100 || p[0] > gx0 - 5 || near(nests, p, 9)) continue;
            int y = p[1];
            while (c.has(p[0], y - 1, p[2]) && p[1] - y < 4) y--;
            if (world.getBlock(p[0], y - 1, p[2]) != ModBlocks.burrow) continue;
            world.setBlock(p[0], y - 1, p[2], ModBlocks.sharkNest, 0, 2);
            nests.add(p);
        }

        // chests at the ends of dead-end pockets, on the floor
        for (int n = 0; n < 7 && n < pockets.size(); n++) {
            double[] q = pockets.get(n);
            int x = MathHelper.floor_double(q[0]), z = MathHelper.floor_double(q[2]), y = MathHelper.floor_double(q[1]);
            if (!c.has(x, y, z)) continue;
            while (c.has(x, y - 1, z)) y--;
            LairLoot.chest(world, rand, x, y, z, false);
        }

        // the crab's dry grotto behind the east wall (1.1.3 - the ship graveyard): a rugged cave of radius ~DEN_R + 0.7 with a flat floor,
        // the vault higher in the middle (as much as the bottom above allows), stalactites, the broken bow of a ship at the north wall, a fallen
        // mast, crates of the sunken cargo, a fish skeleton half in the sand, sand drifts at the walls. Within GROTTO_CLEAR of the altar - nothing:
        // the boss rises on the altar and pulls everyone in with a vortex of radius 5.
        int yd = ym - 2;
        int minBed = 62;
        for (int dx = -DEN_R - 2; dx <= DEN_R + 2; dx += 2) for (int dz = -DEN_R - 2; dz <= DEN_R + 2; dz += 2) minBed = Math.min(minBed, seabed(world, gcx + dx, gcz + dz));
        int cap = Math.max(4, Math.min(8, minBed - yd - 4)); // two blocks of bottom stay above the vault
        int n = GROTTO_N;
        boolean[][][] den = new boolean[2 * n + 1][cap + 3][2 * n + 1];
        for (int dx = -n; dx <= n; dx++) for (int dz = -n; dz <= n; dz++) for (int y = 0; y <= cap; y++) den[dx + n][y][dz + n] = inGrotto(dx, y, dz, cap);
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                for (int y = -1; y <= cap + 1; y++) {
                    if (y >= 0 && den[dx + n][y][dz + n]) {
                        set(world, gcx + dx, yd + 1 + y, gcz + dz, Blocks.air);
                        continue;
                    }
                    boolean near = false;
                    for (int[] nb : NEIGHBORS) {
                        int ix = dx + nb[0] + n, iy = y + nb[1], iz = dz + nb[2] + n;
                        if (ix >= 0 && ix <= 2 * n && iz >= 0 && iz <= 2 * n && iy >= 0 && iy <= cap && den[ix][iy][iz]) near = true;
                    }
                    if (!near) continue;
                    boolean floor = y + 1 <= cap && y + 1 >= 0 && den[dx + n][y + 1][dz + n];
                    burrow(world, gcx + dx, yd + 1 + y, gcz + dz, floor || h32(dx, dz) % 4 == Math.floorMod(-y, 4) ? 1 : 0);
                }
            }
        }
        grottoDecor(world, gcx, yd + 1, gcz, den, cap);
        for (int[] g : new int[][] { { -3, -3 }, { -3, 3 }, { 3, -3 }, { 3, 3 }, { -8, 0 }, { 8, 0 }, { 0, -8 }, { 0, 8 },
            { -6, -6 }, { -6, 6 }, { 6, -6 }, { 6, 6 }, { -9, -2 }, { 8, 6 }, { 9, -6 }, { -7, -6 } }) {
            int y = 0;
            while (y <= cap && den[g[0] + n][y][g[1] + n]) y++; // the vault above this point
            if (y > 0) lamp(world, rand, gcx + g[0], yd + 1 + y, gcz + g[1]);
        }
        // the bubble curtain holds the water: the labyrinth water stands right in front of it, and you can walk straight through
        set(world, gx0, yd + 1, gcz, ModBlocks.bubbleCurtain);
        set(world, gx0, yd + 2, gcz, ModBlocks.bubbleCurtain);
        set(world, gcx, yd + 1, gcz, ModBlocks.carcinusAltar); // summoning: crab claws, dropped by Burrow Crabs

        wreck(world, ex, ez); // the ship aground over the funnel: the lair is seen from the shore

        LairData.get(world)
            .add(LairData.CARCINUS, ex, ey + 1, ez); // funnel on the bottom
        return true;
    }

    static boolean near(List<int[]> points, int[] p, int dist) {
        for (int[] q : points) {
            int dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
            if (dx * dx + dy * dy + dz * dz < dist * dist) return true;
        }
        return false;
    }

    /** A Kraken lamp or a Divine aqua lamp. */
    private static void lamp(World world, Random rand, int x, int y, int z) {
        if (rand.nextBoolean()) world.setBlock(x, y, z, VanillaBlocks.lamp2, 2, 2);
        else world.setBlock(x, y, z, VanillaBlocks.lamp1, 0, 2);
    }

    private static final int[][] NEIGHBORS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    /** Radius of the Carcinus arena grotto (was 6 - twice as cramped); the rugged edge reaches DEN_R + 0.7. */
    static final int DEN_R = 12;
    /** Half size of the grotto box, and the radius around the altar where the decor does not go (the boss vortex is 5). */
    static final int GROTTO_N = 14;
    static final double GROTTO_CLEAR = 6.5;

    /** A hash of the grotto and wreck shapes (the same as in the concept renders). */
    static long h32(int... v) {
        long x = 0x9E3779B9L;
        for (int a : v) {
            x = ((x ^ (a & 0xffffffffL)) * 0x85EBCA6BL) & 0xffffffffL;
            x ^= x >>> 13;
        }
        return x;
    }

    /**
     * The grotto void (dx, dz from the altar, dy from the floor top): radius ~DEN_R with a rugged edge, the vault from 4-6 at the walls to 6-8
     * in the middle, not above cap; the wall with the bubble curtain (dx = -DEN_R) stays whole, it holds the labyrinth water.
     */
    static boolean inGrotto(int dx, int dy, int dz, int cap) {
        double r = Math.sqrt(dx * dx + dz * dz) + h32(dx, dz, 2) % 3 * 0.25;
        if (r >= DEN_R + 0.7 || dy < 0 || dx <= -DEN_R) return false;
        double top = 5 + 2 * Math.cos(r / 12.6 * Math.PI / 2) + h32(Math.floorDiv(dx, 3), Math.floorDiv(dz, 3)) % 3 - 1;
        return dy <= Math.min(top, cap);
    }

    /** Wreck decor in the grotto: only into the void and not closer than GROTTO_CLEAR to the altar. */
    private static void grottoPut(World w, int gx, int gy, int gz, boolean[][][] den, int cap, int dx, int dy, int dz, int meta) {
        int n = GROTTO_N;
        if (Math.abs(dx) > n || Math.abs(dz) > n || dy < 0 || dy > cap || !den[dx + n][dy][dz + n] || Math.sqrt(dx * dx + dz * dz) < GROTTO_CLEAR) return;
        burrow(w, gx + dx, gy + dy, gz + dz, meta);
    }

    /** gx, gy, gz - the altar (gy - the first row above the floor). */
    private static void grottoDecor(World w, int gx, int gy, int gz, boolean[][][] den, int cap) {
        int n = GROTTO_N;
        for (int k = 0; k < 14; k++) { // stalactites from the vault, above head height
            int x = (int) (h32(k, 1) % 19) - 9, z = (int) (h32(k, 2) % 19) - 9;
            if (Math.sqrt(x * x + z * z) < 4) continue;
            int top = -1;
            for (int y = 0; y <= cap; y++) if (den[x + n][y][z + n]) top = y;
            for (int y = top; y >= top - 1 - (int) (h32(k) % 2) && y > 4; y--) burrow(w, gx + x, gy + y, gz + z, 0);
        }
        for (int x = -8; x <= 5; x++) { // the bow: a curved hull of dark planks at the north wall, a broken deck
            double t = (x + 8) / 13.0, half = 3.2 * Math.sin(Math.min(1.0, t * 1.4) * Math.PI / 2) + 0.4;
            for (int z = -12; z <= -5; z++) {
                double dz = z + 8.5;
                for (int y = 0; y <= 5; y++) {
                    if (Math.abs(Math.abs(dz) - half * (0.55 + 0.45 * (y / 5.0))) < 0.7 && !(h32(x, y, z) % 7 == 0 && y > 2))
                        grottoPut(w, gx, gy, gz, den, cap, x, y, z, Math.floorMod(x, 4) != 0 ? BlockBurrow.PLANKS : BlockBurrow.LOG);
                }
                if (Math.abs(dz) < half && Math.floorMod(x, 3) == 0 && h32(x) % 2 == 0) grottoPut(w, gx, gy, gz, den, cap, x, 4, z, BlockBurrow.PLANKS_LIGHT);
            }
        }
        for (int y = 0; y <= 6; y++) grottoPut(w, gx, gy, gz, den, cap, 6, y, -9, BlockBurrow.LOG); // stem post
        for (int i = 0; i < 12; i++) grottoPut(w, gx, gy, gz, den, cap, -10 + i, 1 + i / 4, 8 - i / 2, BlockBurrow.LOG); // fallen mast
        for (int[] c : new int[][] { { 8, 0, -3 }, { 9, 0, -2 }, { 8, 0, -2 }, { 9, 0, -3 }, { -6, 0, 9 }, { -5, 0, 10 }, { -6, 1, 9 } })
            grottoPut(w, gx, gy, gz, den, cap, c[0], c[1], c[2], BlockBurrow.PLANKS_LIGHT); // crates of the sunken cargo
        for (int i = 0; i < 7; i++) { // fish skeleton
            int x = 3 + i;
            grottoPut(w, gx, gy, gz, den, cap, x, 0, 7, BlockBurrow.CLAY_WHITE);
            if (i % 2 == 0) for (int y = 1; y <= 3 - Math.abs(i - 3) / 2; y++) {
                grottoPut(w, gx, gy, gz, den, cap, x, y, 6, BlockBurrow.CLAY_WHITE);
                grottoPut(w, gx, gy, gz, den, cap, x, y, 8, BlockBurrow.CLAY_WHITE);
            }
        }
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) { // sand drifts at the walls (not in front of the curtain)
            double r = Math.sqrt(x * x + z * z);
            if (r > 10.5 && r < 12.5 && h32(x, z, 4) % 2 == 0 && !(x < -9 && Math.abs(z) <= 1)) grottoPut(w, gx, gy, gz, den, cap, x, 0, z, 1);
        }
    }

    /** Puts a wreck block only into water or air: the ship lies aground, the beach stays; never over the funnel shaft. */
    private static void wreckPut(World w, int ex, int ez, int x, int y, int z, Block b, int meta) {
        if ((x - ex) * (x - ex) + (z - ez) * (z - ez) <= 6) return;
        Block was = w.getBlock(x, y, z);
        if (!was.isAir(w, x, y, z) && was.getMaterial() != Material.water) return;
        w.setBlock(x, y, z, b, meta, 2);
    }

    /**
     * The wreck at the entrance (1.1.3): a ship aground over the funnel - a hull of dark planks heeled over and broken in the middle, a mast
     * with a torn sail and a kraken lantern, lanterns on posts around the funnel. The funnel itself stays open: there is a hole in the bottom
     * of the hull over it. Planks, logs and sail are burrow decor - they drop nothing.
     */
    private static void wreck(World w, int ex, int ez) {
        int sea = 62, len = 22, x0 = ex - 2;
        for (int i = 0; i < len; i++) {
            if (i == 9 || i == 10) continue; // broken in the middle
            int x = x0 + i;
            double t = i / (double) (len - 1), half = 3.4 * Math.sin(Math.PI * Math.min(1.0, 0.15 + t * 0.9)) + 0.3;
            for (int z = -7; z <= 7; z++) {
                for (int y = sea - 3; y <= sea + 5; y++) {
                    double lz = z - (y - sea) * 0.35, k = (y - (sea - 3)) / 8.0;
                    boolean shell = Math.abs(Math.abs(lz) - half * (0.45 + 0.55 * k)) < 0.65 || y == sea - 3 && Math.abs(lz) < half * 0.45;
                    if (shell && !(h32(x - ex, y, z) % 6 == 0 && y > sea)) wreckPut(w, ex, ez, x, y, ez + z, ModBlocks.burrow, i % 5 != 0 ? BlockBurrow.PLANKS : BlockBurrow.LOG);
                }
                if (Math.abs(z) < half * 0.9 && i % 2 == 0 && i > 11) wreckPut(w, ex, ez, x, sea + 4, ez + z - 1, ModBlocks.burrow, BlockBurrow.PLANKS_LIGHT);
            }
        }
        int mx = x0 + 15;
        Block base = w.getBlock(mx, sea, ez); // the mast stands in the hull over water; on a high bank it would hang in the air - then no mast
        if ((base == ModBlocks.burrow || base.getMaterial() == Material.water) && w.getBlock(mx, sea + 1, ez).isAir(w, mx, sea + 1, ez)) {
            for (int y = sea + 1; y <= sea + 14; y++) wreckPut(w, ex, ez, mx, y, ez, ModBlocks.burrow, BlockBurrow.LOG);
            for (int y = sea + 7; y <= sea + 13; y++) {
                for (int z = -4; z <= 4; z++) {
                    if (h32(z, y, 3) % 4 != 0 && y - sea - 7 < 7 - Math.abs(z) / 2)
                        wreckPut(w, ex, ez, mx + 1, y, ez + z, ModBlocks.burrow, Math.floorMod(y + z, 3) != 0 ? BlockBurrow.SAIL : BlockBurrow.SAIL_GRAY);
                }
            }
            wreckPut(w, ex, ez, mx, sea + 15, ez, VanillaBlocks.lamp2, 2); // kraken lantern on the mast
        }
        for (int[] p : new int[][] { { -4, -3 }, { -4, 3 }, { -1, -5 }, { -1, 5 } }) { // lanterns on posts around the funnel
            int x = ex + p[0], z = ez + p[1];
            int b = sea + 6;
            while (b > 1 && (w.getBlock(x, b, z).isAir(w, x, b, z) || w.getBlock(x, b, z).getMaterial() == Material.water)) b--;
            wreckPut(w, ex, ez, x, b + 1, z, ModBlocks.burrow, BlockBurrow.LOG);
            wreckPut(w, ex, ez, x, b + 2, z, ModBlocks.burrow, BlockBurrow.LOG);
            wreckPut(w, ex, ez, x, b + 3, z, VanillaBlocks.lamp2, 2);
        }
    }

    private static void burrow(World world, int x, int y, int z, int meta) {
        world.setBlock(x, y, z, ModBlocks.burrow, meta, 2);
    }

    /** Labyrinth skeleton by "recursive backtracker" on a grid of cols x rows nodes from (si, sj): edges {i1, j1, i2, j2}. */
    static List<int[]> mazeEdges(Random rand, int cols, int rows, int si, int sj) {
        List<int[]> edges = new ArrayList<int[]>();
        boolean[][] seen = new boolean[cols][rows];
        List<int[]> stack = new ArrayList<int[]>();
        stack.add(new int[] { si, sj });
        seen[si][sj] = true;
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!stack.isEmpty()) {
            int[] c = stack.get(stack.size() - 1);
            List<int[]> next = new ArrayList<int[]>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < cols && nz < rows && !seen[nx][nz]) next.add(new int[] { nx, nz });
            }
            if (next.isEmpty()) {
                stack.remove(stack.size() - 1);
                continue;
            }
            int[] n = next.get(rand.nextInt(next.size()));
            seen[n[0]][n[1]] = true;
            edges.add(new int[] { c[0], c[1], n[0], n[1] });
            stack.add(n);
        }
        return edges;
    }

    /** Cuts crooked passages in the volume: a set of "dug out" blocks and points along the passage axis. */
    static final class Carver {

        final int bx, by, bz, sx, sy, sz, maxX;
        final boolean[] grid;
        final List<int[]> cells = new ArrayList<int[]>();
        final List<int[]> path = new ArrayList<int[]>();

        Carver(int bx, int by, int bz, int sx, int sy, int sz, int maxX) {
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.maxX = maxX;
            grid = new boolean[sx * sy * sz];
        }

        private int idx(int x, int y, int z) {
            x -= bx;
            y -= by;
            z -= bz;
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return -1;
            return (x * sy + y) * sz + z;
        }

        boolean has(int x, int y, int z) {
            int i = idx(x, y, z);
            return i >= 0 && grid[i];
        }

        void add(int x, int y, int z) {
            if (x > maxX || y < 6) return;
            int i = idx(x, y, z);
            if (i < 0 || grid[i]) return;
            grid[i] = true;
            cells.add(new int[] { x, y, z });
        }

        /** An ellipsoid with horizontal radius rh and vertical radius rv. */
        void blob(double px, double py, double pz, double rh, double rv) {
            for (int x = (int) Math.floor(px - rh); x <= (int) Math.ceil(px + rh); x++) {
                for (int y = (int) Math.floor(py - rv); y <= (int) Math.ceil(py + rv); y++) {
                    for (int z = (int) Math.floor(pz - rh); z <= (int) Math.ceil(pz + rh); z++) {
                        double dx = (x + 0.5 - px) / rh, dy = (y + 0.5 - py) / rv, dz = (z + 0.5 - pz) / rh;
                        if (dx * dx + dy * dy + dz * dz <= 1.0) add(x, y, z);
                    }
                }
            }
        }

        /** A crooked passage from a to b: bending through a shifted midpoint, width and height wander along the way. */
        void line(double[] a, double[] b, Random rand, boolean record) {
            line(a, b, rand, record, 1.3, 2.3, 1.1, 1.7);
        }

        /** The same with given limits of horizontal (rh) and vertical (rv) radius. */
        void line(double[] a, double[] b, Random rand, boolean record, double rhMin, double rhMax, double rvMin,
            double rvMax) {
            double mx = (a[0] + b[0]) / 2 + (rand.nextDouble() - 0.5) * 5;
            double my = (a[1] + b[1]) / 2 + (rand.nextDouble() - 0.5) * 2;
            double mz = (a[2] + b[2]) / 2 + (rand.nextDouble() - 0.5) * 5;
            double len = Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1])
                + (a[2] - b[2]) * (a[2] - b[2]));
            int steps = (int) (len * 2) + 2;
            double rh = rhMin + (rhMax - rhMin) * (0.2 + rand.nextDouble() * 0.5);
            double rv = rvMin + (rvMax - rvMin) * (0.2 + rand.nextDouble() * 0.5);
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps, u = 1 - t;
                double px = u * u * a[0] + 2 * u * t * mx + t * t * b[0];
                double py = u * u * a[1] + 2 * u * t * my + t * t * b[1];
                double pz = u * u * a[2] + 2 * u * t * mz + t * t * b[2];
                rh = Math.max(rhMin, Math.min(rhMax, rh + (rand.nextDouble() - 0.5) * (rhMax - rhMin) * 0.35));
                rv = Math.max(rvMin, Math.min(rvMax, rv + (rand.nextDouble() - 0.5) * (rvMax - rvMin) * 0.4));
                blob(px, py, pz, rh, rv);
                if (record && s % 2 == 0) path.add(new int[] { (int) Math.floor(px), (int) Math.floor(py),
                    (int) Math.floor(pz) });
            }
        }
    }

    // ---------------------------------------------------------------- ice cave

    /**
     * The entrance to the Snow Glacon ice dome ({@link LairDomes.Glacon}): an igloo on the surface, from it down a 3x3 shaft with a
     * spiral staircase to the tunnel into the dome (the shaft bottom and the tunnel are built by the dome itself). @return {x, igloo floor, z} or null - the place did not fit
     */
    public int[] buildSnowDungeon(World world, Random rand, int cx, int cz) {
        return snowDungeon(world, rand, cx, cz);
    }

    private int[] snowDungeon(World world, Random rand, int cx, int cz) {
        int surface = world.getTopSolidOrLiquidBlock(cx, cz);
        if (surface < LairDomes.G_FL + 24 || world.getBlock(cx, surface - 1, cz).getMaterial().isLiquid()) return null;
        int g = surface - 1; // the ground the igloo stands on
        igloo(world, new Carver(cx, 0, cz, 0, 0, 0, Integer.MAX_VALUE), cx, g, cz);
        // shaft: steps along the 3x3 ring (as in the dome: at height y - cell RING[y & 7]), walls of ice and frozen stone
        for (int y = LairDomes.G_FL + 9; y <= g; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = cx + dx, z = cz + dz;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                int k = LairDomes.ringIndex(dx, dz);
                set(world, x, y, z, k >= 0 && (y & 7) == k ? IceikaBlocks.icyBricks : Blocks.air);
            } else if (y < g - 1) set(world, x, y, z, iceShell(x, y, z, false));
        }
        snowCover(world, rand, cx, cz);
        LairData.get(world)
            .add(LairData.SNOW, cx - 7, g + 1, cz); // the igloo entrance
        return new int[] { cx, g, cz };
    }

    /** Cave wall: patches of packed ice and frozen stone; the floor is more often stone. */
    private static Block iceShell(int x, int y, int z, boolean floor) {
        int h = ((x >> 1) * 734287) ^ ((y >> 1) * 912271) ^ ((z >> 1) * 438289);
        int v = (h >>> 8) % 10;
        if (floor) return v < 6 ? IceikaBlocks.frozenStone : Blocks.packed_ice;
        return v < 7 ? Blocks.packed_ice : IceikaBlocks.frozenStone;
    }

    /** A column from floor to ceiling: thick at the base and at the vault, thin in the middle. */
    private static void pillar(World world, Carver c, int x, int yMid, int z, Random rand) {
        if (!c.has(x, yMid, z)) return;
        int lo = yMid, hi = yMid;
        while (c.has(x, lo - 1, z)) lo--;
        while (c.has(x, hi + 1, z)) hi++;
        int h = hi - lo + 1;
        if (h < 3) return;
        for (int y = lo; y <= hi; y++) {
            double t = (y - lo) / (double) (h - 1), r = 0.9 + 1.5 * Math.abs(t - 0.5) * 2;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > r * r || !c.has(x + dx, y, z + dz)) continue;
                set(world, x + dx, y, z + dz, rand.nextInt(4) == 0 ? IceikaBlocks.frozenStone : Blocks.packed_ice);
            }
        }
    }

    /** An igloo of snow blocks with an entrance tunnel to the west; in the floor - a pit descending into the cave. */
    /** Radius of snow around the igloo if snow does not lie there on its own (a cold but not snowy biome). */
    static final int SNOW_COVER = 18;

    /** Snow around the igloo: a layer of snow on the ground and leaves, water - ice; sparser toward the edge. */
    static void snowCover(World w, Random r, int cx, int cz) {
        if (Biomes.snowsAt(w.getBiomeGenForCoords(cx, cz), cx, cz)) return;
        for (int dx = -SNOW_COVER; dx <= SNOW_COVER; dx++) for (int dz = -SNOW_COVER; dz <= SNOW_COVER; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz) / SNOW_COVER;
            if (d > 1 || d > 0.7 && r.nextDouble() < (d - 0.7) / 0.3) continue;
            int x = cx + dx, z = cz + dz, y = w.getTopSolidOrLiquidBlock(x, z);
            Block top = w.getBlock(x, y - 1, z);
            if (top.getMaterial() == Material.water) w.setBlock(x, y - 1, z, Blocks.ice, 0, 2);
            else if (w.isAirBlock(x, y, z) && (top.isOpaqueCube() || top.isLeaves(w, x, y - 1, z))) w.setBlock(x, y, z, Blocks.snow_layer, 0, 2);
        }
    }

    private static void igloo(World world, Carver c, int cx, int g, int cz) {
        for (int dx = -8; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int x = cx + dx, z = cz + dz;
                double r = Math.sqrt(dx * dx + dz * dz);
                boolean tunnel = dx <= -4 && Math.abs(dz) <= 1;
                if (r > 5.5 && !(tunnel && dx >= -8)) continue;
                // foundation: the floor and everything empty under it down to the ground
                for (int y = g; y > g - 6; y--) {
                    if (c.has(x, y, z)) break;
                    if (y < g && world.getBlock(x, y, z)
                        .getMaterial()
                        .isSolid()) break;
                    set(world, x, y, z, Blocks.snow);
                }
                // dome: 5 outside, 4 inside
                for (int dy = 1; dy <= 7; dy++) {
                    if (c.has(x, g + dy, z)) continue;
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy);
                    Block b = d < 4.0 ? Blocks.air : d < 5.0 ? Blocks.snow : Blocks.air;
                    if (tunnel && r >= 3.5) {
                        // entrance tunnel: 1 x 2 inside, walls and roof of snow
                        boolean inside = dz == 0 && dy <= 2;
                        b = inside ? Blocks.air : dy <= 3 && dx >= -7 ? Blocks.snow : Blocks.air;
                    }
                    set(world, x, g + dy, z, b);
                }
            }
        }
    }

    // ---------------------------------------------------------------- common

    static void set(World world, int x, int y, int z, Block b) {
        world.setBlock(x, y, z, b, 0, 2);
    }

    /** How many steps through air to a lamp are allowed: lamp light 15, after 5 steps still 10, with margin for snow and columns - monsters do not spawn (7 and below needed). */
    static final int LIGHT_STEPS = 5;

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private static boolean clear(World w, int x, int y, int z) {
        return !w.getBlock(x, y, z).isOpaqueCube();
    }

    /**
     * Light the whole cave: breadth-first search through cave air from glowing blocks; where the light is farther than
     * {@link #LIGHT_STEPS} steps - an ice lamp in the ceiling above that spot. Steps through air, not in a straight line: light
     * bends around corners the same way.
     */
    static int lightUp(World world, Carver c, int maxY) {
        java.util.Map<Long, Integer> dist = new java.util.HashMap<Long, Integer>();
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<int[]>();
        int[][] nb = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] p : c.cells) {
            if (p[1] >= maxY || !clear(world, p[0], p[1], p[2])) continue;
            for (int[] d : nb) {
                if (world.getBlock(p[0] + d[0], p[1] + d[1], p[2] + d[2]).getLightValue() >= 14) {
                    dist.put(key(p[0], p[1], p[2]), 1);
                    q.add(p);
                    break;
                }
            }
        }
        spread(world, c, dist, q, maxY, nb);
        int added = 0;
        for (int[] p : c.cells) {
            if (p[1] >= maxY || !clear(world, p[0], p[1], p[2])) continue;
            Integer d = dist.get(key(p[0], p[1], p[2]));
            if (d != null && d <= LIGHT_STEPS) continue;
            // a lamp in the ceiling; at the entrance the ceiling is already the surface, then in the floor or a wall nearby
            int[] lamp = null, s0 = null;
            int y = p[1];
            while (c.has(p[0], y + 1, p[2]) && y + 1 < maxY) y++;
            if (y + 1 < maxY - 3 && !c.has(p[0], y + 1, p[2])) {
                lamp = new int[] { p[0], y + 1, p[2] };
                s0 = new int[] { p[0], y, p[2] };
            } else {
                for (int[] o : nb) {
                    int x = p[0] + o[0], yy = p[1] + o[1], z = p[2] + o[2];
                    if (o[1] > 0 || c.has(x, yy, z) || !world.getBlock(x, yy, z).isOpaqueCube()) continue;
                    lamp = new int[] { x, yy, z };
                    s0 = p;
                    if (o[1] < 0) break; // floor is better than wall
                }
            }
            if (lamp == null) continue;
            world.setBlock(lamp[0], lamp[1], lamp[2], VanillaBlocks.lamp2, 0, 2);
            added++;
            if (s0 == null) continue;
            dist.put(key(s0[0], s0[1], s0[2]), 1);
            q.add(s0);
            spread(world, c, dist, q, maxY, nb);
        }
        // check against the world's real light: the step computation and the game's light may diverge (snow, columns, icicles)
        for (int round = 0; round < 6; round++) {
            int fixed = 0;
            for (int[] p : c.cells) {
                if (p[1] >= maxY || !clear(world, p[0], p[1], p[2]) || clear(world, p[0], p[1] - 1, p[2])) continue;
                if (world.getSavedLightValue(net.minecraft.world.EnumSkyBlock.Block, p[0], p[1], p[2]) >= 8) continue;
                int[] lamp = lampSpot(world, c, p, maxY, nb);
                if (lamp == null) continue;
                world.setBlock(lamp[0], lamp[1], lamp[2], VanillaBlocks.lamp2, 0, 2);
                fixed++;
            }
            added += fixed;
            if (fixed == 0) break;
        }
        return added;
    }

    /** Where to place a lamp for spot p: in the ceiling above it, and if the ceiling is already the surface, in the floor or a wall nearby. */
    private static int[] lampSpot(World world, Carver c, int[] p, int maxY, int[][] nb) {
        int y = p[1];
        while (c.has(p[0], y + 1, p[2]) && y + 1 < maxY && clear(world, p[0], y + 1, p[2])) y++;
        // ceiling - only below the surface (maxY - 3 = the ground under the igloo), above - floor or wall
        if (y + 1 < maxY - 3 && y - p[1] <= 4 && world.getBlock(p[0], y + 1, p[2]).isOpaqueCube()) return new int[] { p[0], y + 1, p[2] };
        int[] best = null;
        for (int[] o : nb) {
            int x = p[0] + o[0], yy = p[1] + o[1], z = p[2] + o[2];
            if (o[1] > 0 || !world.getBlock(x, yy, z).isOpaqueCube() || world.getBlock(x, yy, z) == com.divinerpgextended.registry.ModBlocks.lairSpawner) continue;
            best = new int[] { x, yy, z };
            if (o[1] < 0) break;
        }
        return best;
    }

    private static void spread(World world, Carver c, java.util.Map<Long, Integer> dist, java.util.ArrayDeque<int[]> q, int maxY, int[][] nb) {
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int d = dist.get(key(p[0], p[1], p[2]));
            if (d >= LIGHT_STEPS) continue;
            for (int[] o : nb) {
                int x = p[0] + o[0], y = p[1] + o[1], z = p[2] + o[2];
                if (y >= maxY || !c.has(x, y, z) || !clear(world, x, y, z)) continue;
                Integer was = dist.get(key(x, y, z));
                if (was != null && was <= d + 1) continue;
                dist.put(key(x, y, z), d + 1);
                q.add(new int[] { x, y, z });
            }
        }
    }

    /** The lair spawner ({@link com.divinerpgextended.block.BlockLairSpawner}): spawns mobs in any light. */
    static void lairSpawner(World world, int x, int y, int z, String entity) {
        world.setBlock(x, y, z, com.divinerpgextended.registry.ModBlocks.lairSpawner, 0, 2);
        TileEntityMobSpawner te = (TileEntityMobSpawner) world.getTileEntity(x, y, z);
        if (te != null) te.func_145881_a()
            .setEntityName(entity);
    }

    static void spawner(World world, int x, int y, int z, String entity) {
        world.setBlock(x, y, z, Blocks.mob_spawner, 0, 2);
        TileEntityMobSpawner te = (TileEntityMobSpawner) world.getTileEntity(x, y, z);
        if (te != null) te.func_145881_a()
            .setEntityName(entity);
    }
}
