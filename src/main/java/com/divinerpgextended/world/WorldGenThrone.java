package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * The Frozen Throne in Iceika - an underground arena down to bedrock.
 * <p>
 * The location is computed from the world seed: one Throne per throneSpacing x throneSpacing square. The structure is huge (300 blocks
 * of arena, spires of 60 blocks), so it is not placed all at once: each chunk, when populated, builds its own part from
 * the function "which block is at this point", directly in the chunk storage (without recalculating light for every block).
 * <ul>
 * <li>On top - the citadel (the top of the arena dome): a platform with a parapet, a ring of ice columns, a domed hall of radius
 * 20 with a gate to the south, and two 60-block spires at the gate. In the hall - warm braziers and two hatches of spiral staircases.</li>
 * <li>Below - a dome of radius 150 blocks from the floor at bedrock (y=5) to ~66: ribs of ice brick, lamps in the vault,
 * icicles, an outer ring of columns up to the vault and an inner ring of pylons of 18 blocks with a platform (the
 * Lich King jumps on them), 12 braziers around the center, a giant throne in the north with a summoning altar.</li>
 * <li>Two spiral staircases descend from the citadel hall to the arena floor around ice pillars.</li>
 * </ul>
 */
public class WorldGenThrone implements IWorldGenerator {

    public static final int R = 150, FLOOR = 6, TOP = 72, CITADEL = 34;
    /** The altar stands on the throne dais: ALTAR_DZ to the north of the arena center, ALTAR_DY above the floor. */
    public static final int ALTAR_DZ = 37, ALTAR_DY = 3;
    private static final int HALL_R = 20, HALL_H = 17, TOWER_X = 14;
    private static final int KEEP = -1;

    // ---------------------------------------------------------------- location

    /** Center of the Throne in area (rx, rz) or null if Thrones are disabled. */
    public static int[] center(long seed, int rx, int rz) {
        int g = Config.throneSpacing;
        if (g <= 0) return null;
        Random r = new Random(seed ^ (rx * 341873128712L + rz * 132897987541L) ^ 0x7E3A11L);
        int margin = Math.min(g / 3, R + 60);
        return new int[] { rx * g + margin + r.nextInt(Math.max(1, g - 2 * margin)), TOP,
            rz * g + margin + r.nextInt(Math.max(1, g - 2 * margin)) };
    }

    /** The Throne nearest to the point (we look at its own and neighboring areas). */
    public static int[] nearest(long seed, double x, double z) {
        int g = Config.throneSpacing;
        if (g <= 0) return null;
        int rx = (int) Math.floor(x / g), rz = (int) Math.floor(z / g);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] c = center(seed, rx + i, rz + k);
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != ConfigurationHelper.iceika || Config.throneSpacing <= 0) return;
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8; // the population area of this chunk
        int[] c = nearest(world.getSeed(), x0 + 8, z0 + 8);
        if (c == null || Math.abs(c[0] - (x0 + 8)) > R + 16 || Math.abs(c[2] - (z0 + 8)) > R + 16) return;
        buildSlice(world, c[0], c[2], x0, z0);
        clean(world, c[0], c[2], x0 - 16, z0 - 16, 48);
    }

    /**
     * Divine populates chunks with its own dungeons and trees at an offset relative to ours, and they can intrude into the already finished
     * part of the arena (a dungeon with snowflakes hanging in the air). So after each piece we check the neighboring finished
     * pieces: where the blueprint has air but there is a block, we remove it. Unfinished and unloaded chunks are not touched.
     */
    private static void clean(World w, int cx, int cz, int xFrom, int zFrom, int size) {
        int[] col = new int[256 * 2];
        for (int x = xFrom; x < xFrom + size; x++) {
            for (int z = zFrom; z < zFrom + size; z++) {
                int dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz >= R * R) continue;
                int chx = x >> 4, chz = z >> 4;
                if (!w.getChunkProvider()
                    .chunkExists(chx, chz)) continue;
                // the piece the column belongs to is already built (its chunk is populated)
                int ax = (x - 8) >> 4, az = (z - 8) >> 4;
                if (!w.getChunkProvider()
                    .chunkExists(ax, az)
                    || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) continue;
                if (!column(dx, dz, col)) continue;
                for (int y = FLOOR; y < 80; y++) {
                    if (col[y * 2] != AIR || w.isAirBlock(x, y, z)) continue;
                    w.removeTileEntity(x, y, z);
                    w.setBlock(x, y, z, Blocks.air, 0, 2);
                }
            }
        }
    }

    /**
     * Seal the walls and vault: after all population generators (and other mods', there are many in the modpack) where the blueprint
     * has a wall, vault or floor but there is air, restore the block. Works only when populating new chunks nearby (player
     * edits in an already built Throne are not touched) and only in already built pieces.
     */
    public static class Seal implements IWorldGenerator {

        /** What world generation replaced Throne blocks with: liquids and natural blocks of Iceika and the overworld. */
        static boolean natural(Block b) {
            if (b.getMaterial().isLiquid()) return true;
            return b == net.divinerpg.utils.blocks.IceikaBlocks.frozenStone || b == net.divinerpg.utils.blocks.IceikaBlocks.frozenDirt
                || b == net.divinerpg.utils.blocks.IceikaBlocks.frozenGrass || b == Blocks.stone || b == Blocks.dirt || b == Blocks.gravel
                || b == Blocks.snow;
        }

        @Override
        public void generate(Random rand, int chunkX, int chunkZ, World w, IChunkProvider gen, IChunkProvider prov) {
            if (w.provider.dimensionId != ConfigurationHelper.iceika || Config.throneSpacing <= 0) return;
            int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8;
            int[] c = nearest(w.getSeed(), x0 + 8, z0 + 8);
            if (c == null || Math.abs(c[0] - (x0 + 8)) > R + 48 || Math.abs(c[2] - (z0 + 8)) > R + 48) return;
            int[] col = new int[256 * 2];
            for (int x = x0 - 16; x < x0 + 32; x++) for (int z = z0 - 16; z < z0 + 32; z++) {
                int dx = x - c[0], dz = z - c[2];
                if (!w.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
                int ax = (x - 8) >> 4, az = (z - 8) >> 4; // the column piece is already built
                if (!w.getChunkProvider().chunkExists(ax, az) || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated || !column(dx, dz, col)) continue;
                for (int y = FLOOR; y < 250; y++) {
                    int code = col[y * 2];
                    if (code == KEEP || code == LAMP || code == BRAZIER) continue;
                    Block now = w.getBlock(x, y, z);
                    // inside the hall - remove what has flowed in and grown (lava, water, frozen stone of Iceika caves)
                    if (code == AIR) {
                        if (natural(now)) w.setBlock(x, y, z, Blocks.air, 0, 2);
                        continue;
                    }
                    // wall and vault: fill a hole, restore a block replaced by generation according to the blueprint
                    Block want = block(code);
                    if (now != want && (w.isAirBlock(x, y, z) || natural(now))) w.setBlock(x, y, z, want, col[y * 2 + 1], 2);
                }
            }
        }
    }

    // ---------------------------------------------------------------- writing to chunks

    private static void buildSlice(World w, int cx, int cz, int x0, int z0) {
        Set<Chunk> touched = new HashSet<Chunk>();
        List<int[]> lights = new ArrayList<int[]>();
        int[] col = new int[256 * 2];
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int dx = x - cx, dz = z - cz;
                if (!column(dx, dz, col)) continue;
                Chunk ch = w.getChunkFromBlockCoords(x, z);
                touched.add(ch);
                ExtendedBlockStorage[] arr = ch.getBlockStorageArray();
                for (int y = 1; y < 250; y++) {
                    int code = col[y * 2];
                    if (code == KEEP) continue;
                    Block b = block(code);
                    int meta = col[y * 2 + 1];
                    if (w.getBlock(x, y, z)
                        .hasTileEntity(w.getBlockMetadata(x, y, z))) w.removeTileEntity(x, y, z);
                    ExtendedBlockStorage s = arr[y >> 4];
                    if (s == null) {
                        if (b == Blocks.air) continue;
                        s = arr[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !w.provider.hasNoSky);
                    }
                    s.func_150818_a(x & 15, y & 15, z & 15, b);
                    s.setExtBlockMetadata(x & 15, y & 15, z & 15, meta);
                    if (b.getLightValue() > 0 || b == ModBlocks.brazier) lights.add(new int[] { x, y, z });
                }
            }
        }
        for (Chunk ch : touched) {
            ch.generateSkylightMap();
            ch.setChunkModified();
        }
        for (int[] l : lights) relight(w, l[0], l[1], l[2]);
        // light of lamps of neighboring, already built pieces: here it was computed from the old blocks (stone where there is now a passage) - again
        for (int x = x0 - 15; x < x0 + 31; x++) {
            for (int z = z0 - 15; z < z0 + 31; z++) {
                if (x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16 || !w.blockExists(x, 64, z) || !column(x - cx, z - cz, col)) continue;
                for (int y = 1; y < 250; y++) {
                    int code = col[y * 2];
                    if ((code == LAMP || code == BRAZIER) && w.getBlock(x, y, z) == block(code)) relight(w, x, y, z);
                }
            }
        }
    }

    /**
     * Lamp light: the game silently does not compute it if there is an uncreated chunk within 17 blocks (at the edge of a Throne being built - always).
     * Such lamps wait in the world queue until the neighbors appear ({@link #flush}).
     */
    private static final java.util.Map<World, Set<Long>> PENDING = new java.util.WeakHashMap<World, Set<Long>>();

    private static void relight(World w, int x, int y, int z) {
        if (w.doChunksNearChunkExist(x, y, z, 17)) {
            spread(w, x, y, z);
            return;
        }
        Set<Long> q = PENDING.get(w);
        if (q == null) PENDING.put(w, q = new HashSet<Long>());
        q.add(((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | y & 0xFFF);
    }

    /**
     * Spread the lamp light again: the game recalculates only when the light at the point itself changes, and a burning lamp already has
     * 15 - so zero first.
     */
    private static void spread(World w, int x, int y, int z) {
        w.setLightValue(net.minecraft.world.EnumSkyBlock.Block, x, y, z, 0);
        w.updateLightByType(net.minecraft.world.EnumSkyBlock.Block, x, y, z);
    }

    /** How many lamps are waiting for a light recalculation (for measurements and selftest). */
    public static int pending(World w) {
        Set<Long> q = PENDING.get(w);
        return q == null ? 0 : q.size();
    }

    /** Finish the light of queued lamps around which the chunks already exist. */
    public static int flush(World w) {
        Set<Long> q = PENDING.get(w);
        if (q == null || q.isEmpty()) return 0;
        int n = 0;
        for (java.util.Iterator<Long> it = q.iterator(); it.hasNext();) {
            long k = it.next();
            int x = (int) (k >> 38), z = (int) (k << 26 >> 38), y = (int) (k & 0xFFF);
            if (!w.doChunksNearChunkExist(x, y, z, 17)) continue;
            it.remove();
            if (w.getBlock(x, y, z).getLightValue() > 0 || w.getBlock(x, y, z) == ModBlocks.brazier) {
                spread(w, x, y, z);
                n++;
            }
        }
        return n;
    }

    /** Once per second - the Throne light queue. */
    public static class Relight {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onTick(cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent e) {
            if (e.phase == cpw.mods.fml.common.gameevent.TickEvent.Phase.END && !e.world.isRemote && e.world.getTotalWorldTime() % 20 == 0) flush(e.world);
        }
    }

    /**
     * Mobs do not spawn by themselves in the Throne (neither Iceika's nor others' - Grimoire of Gaia and the rest): it does not depend on light, in the huge
     * dome it cannot be tracked in all corners. There are no spawners, bosses and their minions are summoned separately.
     */
    public static class NoSpawns {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onCheckSpawn(net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn e) {
            if (!e.world.isRemote && shields(e.world, (int) Math.floor(e.x), (int) Math.floor(e.y), (int) Math.floor(e.z)))
                e.setResult(cpw.mods.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    // ---------------------------------------------------------------- what stands where

    private static final int AIR = 0, ICY = 1, SNOW = 2, PACKED = 3, STONE = 4, GLASS = 5, LAMP = 6, BRAZIER = 7,
        ALTAR = 8;

    private static Block block(int code) {
        switch (code) {
            case ICY:
                return IceikaBlocks.icyBricks;
            case SNOW:
                return IceikaBlocks.snowBricks;
            case PACKED:
                return Blocks.packed_ice;
            case STONE:
                return IceikaBlocks.frozenStone;
            case GLASS:
                return IceikaBlocks.frostedGlass;
            case LAMP:
                return VanillaBlocks.lamp2;
            case BRAZIER:
                return ModBlocks.brazier;
            case ALTAR:
                return ModBlocks.throneAltar;
            default:
                return Blocks.air;
        }
    }

    private static void put(int[] col, int y, int code, int meta) {
        if (y <= 0 || y >= 250) return;
        col[y * 2] = code;
        col[y * 2 + 1] = meta;
    }

    private static int hash(int x, int z) {
        int h = x * 734287 + z * 912271;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    /**
     * A point inside the Throne or right next to it: the arena dome with the vault (with a margin for the size of a lake or dungeon) and the citadel
     * with spires. Iceika population generators do not place anything here ({@link com.divinerpgextended.asm.IceikaThroneHooks}).
     */
    /** How many blocks from their own point Iceika population generators reach. */
    static final int REACH = 32;

    public static boolean shields(World w, int x, int y, int z) {
        if (w.provider.dimensionId != ConfigurationHelper.iceika || Config.throneSpacing <= 0) return false;
        int[] c = nearest(w.getSeed(), x, z);
        double d = Math.sqrt((x - c[0]) * (x - c[0]) + (z - c[2]) * (z - c[2]));
        if (d <= CITADEL + 16) return y <= TOP + 64;
        // Divine structures (the Rollum dungeon etc.) stretch from the point up to ~24 blocks: we take the vault at the innermost
        // point they can reach (measured: a dungeon 172 blocks from the center gnawed through the wall at the edge of the dome)
        return d <= R + REACH && y <= ceil(Math.min(Math.max(d - REACH, 0), R - 1)) + 3 + 20;
    }

    /** A point in the Throne or closer than margin blocks to its edge (horizontally, at any height). */
    public static boolean near(World w, int x, int z, int margin) {
        if (w.provider.dimensionId != ConfigurationHelper.iceika || Config.throneSpacing <= 0) return false;
        int[] c = nearest(w.getSeed(), x, z);
        double dx = x - c[0], dz = z - c[2];
        return dx * dx + dz * dz <= (double) (R + 4 + margin) * (R + 4 + margin);
    }

    /** The lower boundary of the arena vault above a point at distance r from the center. */
    private static int ceil(double r) {
        if (r >= R) return FLOOR + 12;
        return FLOOR + 12 + (int) (48 * Math.sqrt(1 - (r / R) * (r / R)));
    }

    /** The nearest center of a column of the ring (radius ring, count pieces) and the distance to it. */
    private static double ringDist(double dx, double dz, double ring, int count, double phase) {
        double a = Math.atan2(dz, dx) - phase, step = Math.PI * 2 / count;
        double k = Math.round(a / step);
        double ca = k * step + phase;
        double px = Math.cos(ca) * ring, pz = Math.sin(ca) * ring;
        return Math.sqrt((dx - px) * (dx - px) + (dz - pz) * (dz - pz));
    }

    /** An "hourglass" column: thicker at the floor and at the vault. */
    private static double hourglass(int y, int lo, int hi, double rMid, double rEnd) {
        double t = (y - lo) / (double) Math.max(1, hi - lo);
        return rMid + (rEnd - rMid) * Math.abs(t - 0.5) * 2;
    }

    /**
     * Compare the built Throne with the blueprint (for selftest): {extra blocks where the blueprint has air; holes - air where
     * the blueprint has a wall or vault; replacements - a natural block or liquid instead of a wall}; out gets the first examples.
     * Only columns in loaded chunks.
     */
    public static int[] audit(World w, int cx, int cz, StringBuilder out) {
        int[] col = new int[256 * 2], n = new int[3];
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            int x = cx + dx, z = cz + dz;
            if (!w.getChunkProvider().chunkExists(x >> 4, z >> 4) || !column(dx, dz, col)) continue;
            for (int y = FLOOR; y < 250; y++) {
                int code = col[y * 2];
                if (code == KEEP) continue;
                boolean air = w.isAirBlock(x, y, z);
                if (code == AIR && !air) {
                    if (n[0]++ < 4) out.append("extra ").append(dx).append(",").append(y).append(",").append(dz).append(" ")
                        .append(Block.blockRegistry.getNameForObject(w.getBlock(x, y, z))).append("; ");
                } else if (code != AIR && air && code != LAMP && code != BRAZIER) {
                    if (n[1]++ < 4) out.append("hole ").append(dx).append(",").append(y).append(",").append(dz).append(" code ").append(code).append("; ");
                } else if (code != AIR && code != LAMP && code != BRAZIER && !air && w.getBlock(x, y, z) != block(code) && Seal.natural(w.getBlock(x, y, z))) {
                    if (n[2]++ < 4) out.append("substitution ").append(dx).append(",").append(y).append(",").append(dz).append(" ")
                        .append(Block.blockRegistry.getNameForObject(w.getBlock(x, y, z))).append("; ");
                }
            }
        }
        return n;
    }

    /** Fills col[y] = {block code, meta} for the column (dx, dz) from the center. false - the column is not touched. */
    private static boolean column(int dx, int dz, int[] col) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > R + 4 && !inSpire(dx, dz)) return false;
        for (int i = 0; i < col.length; i += 2) col[i] = KEEP;

        // ---- arena
        int ce = ceil(r);
        if (r < R) {
            boolean ring = ((int) r / 8) % 2 == 0;
            put(col, FLOOR - 1, r < 12 || ring ? ICY : SNOW, 0);
            // lamps in the floor in a grid shifted every 5 blocks: light on the whole floor is at least 8, mobs (including others', Grimoire of Gaia) do not spawn
            if (Math.floorMod(dx, 5) == 0 && Math.floorMod(dz + (Math.floorMod(Math.floorDiv(dx, 5), 2) == 0 ? 0 : 2), 5) == 0) put(col, FLOOR - 1, LAMP, 0);
            for (int y = FLOOR; y < ce; y++) put(col, y, AIR, 0);
            // vault: ribs of ice brick every 15 degrees, packed ice between them
            double ang = Math.toDegrees(Math.atan2(dz, dx)) + 360;
            boolean rib = ang % 15 < 1.6;
            for (int y = ce; y < ce + 3; y++) put(col, y, rib ? ICY : PACKED, 0);
            if (dx % 12 == 0 && dz % 12 == 0 && r > 6) put(col, ce, LAMP, 0);
            else if (hash(dx, dz) % 23 == 0 && ce - FLOOR > 14 && r > 6) {
                int len = 1 + hash(dz, dx) % 4;
                for (int y = ce - len; y < ce; y++) put(col, y, PACKED, 0);
            }
            // rings of columns
            double pa = ringDist(dx, dz, 115, 32, 0), pb = ringDist(dx, dz, 70, 16, Math.PI / 16);
            for (int y = FLOOR; y < ce; y++) {
                if (pa <= hourglass(y, FLOOR, ce, 2.5, 4.5)) put(col, y, y < FLOOR + 3 ? ICY : PACKED, 0);
            }
            int pylon = FLOOR + 18;
            for (int y = FLOOR; y <= pylon; y++) {
                if (pb <= 2.2 && y < pylon) put(col, y, y < FLOOR + 3 ? ICY : PACKED, 0);
                if (pb <= 3.4 && y >= pylon - 1) put(col, y, ICY, 0); // platform on top
            }
            if (pb < 0.75) put(col, pylon, LAMP, 0); // and a lamp in its center
            // braziers around the center
            if (ringDist(dx, dz, 30, 12, 0) < 0.5) {
                put(col, FLOOR, ICY, 0);
                put(col, FLOOR + 1, BRAZIER, 1);
            }
            throne(dx, dz, col);
        } else {
            for (int y = FLOOR - 1; y < FLOOR + 15; y++) put(col, y, ICY, 0); // wall at the edge
        }

        // ---- citadel on top
        if (r <= CITADEL) {
            for (int y = ce + 3; y < TOP; y++) put(col, y, STONE, 0); // the thickness between the vault and the platform
            boolean rim = r > CITADEL - 2;
            put(col, TOP, rim ? ICY : r < HALL_R - 1 ? ((dx + dz & 1) == 0 ? ICY : SNOW) : SNOW, 0);
            for (int y = TOP + 1; y <= TOP + 60; y++) put(col, y, AIR, 0);
            if (rim && r > CITADEL - 1 && (dx + dz) % 2 == 0) put(col, TOP + 1, ICY, 0);
            // ring of columns in the courtyard
            if (ringDist(dx, dz, 27, 12, Math.PI / 12) < 0.5 && !(dz < -24 && Math.abs(dx) < 9)) {
                put(col, TOP + 1, ICY, 0);
                for (int y = TOP + 2; y <= TOP + 8; y++) put(col, y, PACKED, 0);
                put(col, TOP + 9, ICY, 0);
                put(col, TOP + 10, LAMP, 0);
            }
            hall(dx, dz, r, col);
        }
        spires(dx, dz, col);
        towers(dx, dz, col);
        return true;
    }

    /** The citadel's domed hall with a gate to the south, lamps, icicles and four warm braziers. */
    private static void hall(int dx, int dz, double r, int[] col) {
        for (int dy = 1; dy <= HALL_H + 1; dy++) {
            double d = Math.sqrt((dx / (double) HALL_R) * (dx / (double) HALL_R) + (dy / (double) HALL_H) * (dy / (double) HALL_H)
                + (dz / (double) HALL_R) * (dz / (double) HALL_R));
            if (d > 1.0 || d < 0.93) continue;
            boolean gate = Math.abs(dx) <= 2 && dy <= 7 && dz < -14;
            if (gate) continue;
            int code = dy <= 3 ? ICY
                : dy >= 6 && dy <= 8 && (Math.round(Math.toDegrees(Math.atan2(dz, dx)) + 360) % 30) < 12 ? GLASS : PACKED;
            put(col, TOP + dy, code, 0);
        }
        if (Math.abs(dx) == 3 && dz >= -21 && dz <= -18) for (int dy = 1; dy <= 8; dy++) put(col, TOP + dy, ICY, 0);
        if (r < HALL_R - 2) {
            int top = -1;
            for (int dy = HALL_H; dy >= 4; dy--) {
                double d = Math.sqrt((dx / (double) HALL_R) * (dx / (double) HALL_R) + (dy / (double) HALL_H) * (dy / (double) HALL_H)
                    + (dz / (double) HALL_R) * (dz / (double) HALL_R));
                if (d < 0.93) {
                    top = dy;
                    break;
                }
            }
            if (top > 10 && dx % 6 == 0 && dz % 6 == 0) put(col, TOP + top, LAMP, 0);
            else if (top > 8 && hash(dx + 7, dz) % 9 == 0) {
                int len = 1 + hash(dz, dx + 3) % 3;
                for (int i = 0; i < len; i++) put(col, TOP + top - i, PACKED, 0);
            }
        }
        if (ringDist(dx, dz, 12, 4, Math.PI / 4) < 0.5) {
            put(col, TOP + 1, ICY, 0);
            put(col, TOP + 2, BRAZIER, 1);
        }
    }

    private static boolean inSpire(int dx, int dz) {
        for (int side = -1; side <= 1; side += 2) {
            int sx = dx - side * 9, sz = dz + 26;
            if (sx * sx + sz * sz <= 36) return true;
        }
        return false;
    }

    /** Two spires at the gate: 60 blocks, radius 5 at the base. */
    private static void spires(int dx, int dz, int[] col) {
        for (int side = -1; side <= 1; side += 2) {
            int sx = dx - side * 9, sz = dz + 26;
            double rr = Math.sqrt(sx * sx + sz * sz);
            if (rr > 5.5) continue;
            int h = 60;
            for (int dy = 1; dy <= h; dy++) {
                double rad = 4.8 * (1 - dy / (double) (h + 6)) + 0.4;
                if (rr <= rad) put(col, TOP + dy, dy < h / 3 ? ICY : PACKED, 0);
            }
            if (rr < 0.5) put(col, TOP + h + 1, LAMP, 0);
            for (int y = TOP - 6; y <= TOP; y++) if (rr <= 5) put(col, y, ICY, 0); // foundation
        }
    }

    /** Spiral staircases around ice pillars: from the citadel hall to the arena floor. */
    private static void towers(int dx, int dz, int[] col) {
        for (int side = -1; side <= 1; side += 2) {
            int tx = dx - side * TOWER_X, tz = dz;
            double rt = Math.sqrt(tx * tx + tz * tz);
            if (rt > 5.6) continue;
            double r = Math.sqrt(dx * dx + dz * dz);
            int ce = ceil(r);
            double a = Math.atan2(tz, tx);
            if (a < 0) a += Math.PI * 2;
            int sector = (int) (a / (Math.PI * 2 / 16));
            for (int y = FLOOR; y <= TOP; y++) {
                if (y >= ce) put(col, y, AIR, 0); // shaft through the vault and the thickness
                int stepSector = ((y - FLOOR) % 16 + 16) % 16;
                if (rt <= 1.5) put(col, y, rt > 0.9 && (y - FLOOR) % 2 == 0 ? LAMP : ICY, 0); // pillar, with belts of lamps above the steps in it
                else if (rt <= 4.6 && sector == stepSector) put(col, y, rt > 3.6 && (y - FLOOR) % 3 == 0 ? LAMP : ICY, 0); // step, a lamp at the edge of every third one
                else if (rt > 4.6 && rt <= 5.4 && sector == ((y - 1 - FLOOR) % 16 + 16) % 16 && y > FLOOR) {
                    put(col, y, PACKED, 0); // railing
                }
            }
            if (rt <= 1.5) put(col, TOP + 1, ICY, 0);
            if (rt <= 1.5) put(col, TOP + 2, LAMP, 0);
        }
    }

    /** The giant throne in the north of the arena: a dais of three steps, a seat, armrests, a back with spikes, an altar. */
    private static void throne(int dx, int dz, int[] col) {
        int tz = dz - 40;
        double rd = Math.sqrt(dx * dx + tz * tz);
        if (rd <= 9) put(col, FLOOR, ICY, 0);
        if (rd <= 7) put(col, FLOOR + 1, ICY, 0);
        if (rd <= 5) put(col, FLOOR + 2, ICY, 0);
        if (dx == 0 && dz == ALTAR_DZ) {
            put(col, FLOOR + ALTAR_DY, ALTAR, 0);
            put(col, FLOOR + ALTAR_DY + 1, AIR, 0);
        }
        int ax = Math.abs(dx);
        if (ax <= 2 && tz >= 0 && tz <= 3) for (int y = FLOOR + 3; y <= FLOOR + 4; y++) put(col, y, PACKED, 0); // seat
        if (ax >= 3 && ax <= 4 && tz >= 0 && tz <= 3) for (int y = FLOOR + 3; y <= FLOOR + 6; y++) put(col, y, ICY, 0);
        if (ax <= 4 && tz >= 4 && tz <= 5) {
            for (int y = FLOOR + 3; y <= FLOOR + 18; y++) put(col, y, ICY, 0); // back
            if (tz == 4 && ax % 2 == 0) {
                int spike = ax == 0 ? 7 : ax == 2 ? 5 : 3;
                for (int y = FLOOR + 19; y < FLOOR + 19 + spike; y++) put(col, y, PACKED, 0);
            }
        }
    }
}
