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
import com.divinerpgextended.world.twilight.Voxels;

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
    private static final int HALL_R = 20, TOWER_X = 14;
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
            put(col, FLOOR - 1, floorCode(dx, dz, r), 0); // the cathedral floor (1.1.3): a snowflake, an avenue to the throne
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
            if (pb <= 4.5) pylon(dx, dz, col);
            avenue(dx, dz, col);
            throne(dx, dz, col);
            knights(dx, dz, col);
            Voxels v = deco();
            if (v.has(dx, dz)) v.apply(dx, dz, col);
            // braziers around the center (the one on the throne dais stands on its step)
            if (ringDist(dx, dz, 30, 12, 0) < 0.5) {
                int b = FLOOR;
                while (b < FLOOR + 4 && col[b * 2] != AIR) b++;
                if (b == FLOOR) put(col, b++, ICY, 0);
                put(col, b, BRAZIER, 1);
            }
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
            crown(dx, dz, col);
            hall(dx, dz, r, col);
        }
        spires(dx, dz, col);
        towers(dx, dz, col);
        return true;
    }

    /** The citadel hall (1.1.3): a pointed dome, its radius by height. */
    private static final int OGIVE_H = 26;

    private static double ogive(int dy) {
        double t = dy / (double) OGIVE_H;
        return dy > OGIVE_H ? -1 : HALL_R * Math.sqrt(Math.max(0, 1 - Math.pow(t, 1.6)));
    }

    /** Inner boundary of the dome shell at height dy: the shell reaches the next row's radius, so the steep top has no gaps. */
    private static double ogiveInner(int dy) {
        return Math.min(ogive(dy) - 1.4, ogive(dy + 1) - 0.3);
    }

    /**
     * The citadel's hall (1.1.3, the cathedral): a pointed dome of packed ice with an ice brick base, a band of frosted glass, a gate to the south,
     * on the top a lantern with a needle; inside lamps and icicles under the vault and four warm braziers.
     */
    private static void hall(int dx, int dz, double r, int[] col) {
        for (int dy = 1; dy <= OGIVE_H; dy++) {
            if (r > ogive(dy) || r <= ogiveInner(dy)) continue;
            boolean gate = Math.abs(dx) <= 2 && dy <= 7 && dz < -10;
            if (gate) continue;
            int sector = (int) ((Math.atan2(dz, dx) + Math.PI) * 6 / Math.PI);
            put(col, TOP + dy, dy <= 3 ? ICY : dy >= 6 && dy <= 8 && sector % 2 == 0 ? GLASS : PACKED, 0);
        }
        if (Math.abs(dx) == 3 && dz >= -21 && dz <= -18) for (int dy = 1; dy <= 8; dy++) put(col, TOP + dy, ICY, 0);
        if (dx == 0 && dz == 0) { // the lantern with a needle on the top
            for (int y = TOP + OGIVE_H + 1; y <= TOP + OGIVE_H + 17; y++) put(col, y, y < TOP + OGIVE_H + 14 ? ICY : PACKED, 0);
            put(col, TOP + OGIVE_H + 18, LAMP, 0);
        }
        if (r < HALL_R - 2) {
            int top = -1;
            for (int dy = OGIVE_H; dy >= 4; dy--) {
                if (r < ogiveInner(dy)) {
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

    /**
     * The crown of the citadel (1.1.3, in place of the ring of columns in the courtyard): twelve tapering spires of ice, every other one taller, a
     * lamp on each tip; none at the gate.
     */
    private static void crown(int dx, int dz, int[] col) {
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6 + Math.PI / 12, cx = Math.cos(a) * 27, cz = Math.sin(a) * 27;
            if (cz < -22 && Math.abs(cx) < 12) continue;
            int rcx = (int) Math.round(cx), rcz = (int) Math.round(cz), ox = dx - rcx, oz = dz - rcz;
            if (ox * ox + oz * oz > 9) continue;
            int hgt = k % 2 != 0 ? 22 : 32;
            double d = Math.sqrt((dx - cx) * (dx - cx) + (dz - cz) * (dz - cz));
            for (int y = 1; y <= hgt; y++) if (d <= 2.2 * (1 - y / (double) (hgt + 3)) + 0.4) put(col, TOP + y, y < 6 ? ICY : PACKED, 0);
            if (ox == 0 && oz == 0) put(col, TOP + hgt + 1, LAMP, 0);
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

    /** The throne dais center: THRONE_DZ toward the throne from the arena center. */
    private static final int THRONE_DZ = 40;

    /**
     * The cathedral floor (1.1.3): a six-armed snowflake of ice brick with side branches on snow brick, rings of packed ice at the brazier
     * and pylon circles, an avenue of ice brick to the throne. The lamp grid is laid over it in column().
     */
    private static int floorCode(int dx, int dz, double d) {
        if (Math.abs(dx) <= 3 && dz >= 10 && dz < 30) return Math.abs(dx) < 3 ? ICY : PACKED; // the avenue
        if (d < 12) return ICY;
        double a = Math.atan2(dz, dx) - Math.PI / 2;
        if (Math.abs(Math.IEEEremainder(a, Math.PI / 3)) * d < 1.6) return ICY;
        double k = Math.round(a / (Math.PI / 3)) * Math.PI / 3 + Math.PI / 2;
        for (int br = 40; br <= 120; br += 40) { // side branches at 60 degrees from each arm
            double vx = dx - Math.cos(k) * br, vz = dz - Math.sin(k) * br;
            for (int s = -1; s <= 1; s += 2) {
                double bx = Math.cos(k + s * Math.PI / 3), bz = Math.sin(k + s * Math.PI / 3);
                double along = vx * bx + vz * bz, perp = Math.abs(-vx * bz + vz * bx);
                if (along >= 0 && along <= br * 0.3 && perp < 1.2) return ICY;
            }
        }
        if (Math.abs(d - 30) < 0.8 || Math.abs(d - 70) < 0.8) return PACKED;
        return SNOW;
    }

    /** Obelisks along the avenue: packed ice with a lamp on top, in pairs. */
    private static void avenue(int dx, int dz, int[] col) {
        if (Math.abs(dx) != 5 || dz < 10 || dz >= 30 || Math.floorMod(dz, 6) != 4) return;
        put(col, FLOOR, ICY, 0);
        for (int y = FLOOR + 1; y < FLOOR + 6; y++) put(col, y, PACKED, 0);
        put(col, FLOOR + 6, LAMP, 0);
    }

    /**
     * A pylon - a crystal spire (1.1.3): a hexagonal prism of packed ice with ice brick belts, on top the same platform at FLOOR + 17..18 with
     * a lamp in the center (the Lich King jumps on it); leaning shards at the foot are in deco().
     */
    private static void pylon(int dx, int dz, int[] col) {
        double ang = Math.atan2(dz, dx) - Math.PI / 16;
        double ca = Math.round(ang / (Math.PI / 8)) * (Math.PI / 8) + Math.PI / 16, cx = Math.cos(ca) * 70, cz = Math.sin(ca) * 70;
        int rcx = (int) Math.round(cx), rcz = (int) Math.round(cz);
        if ((dx - rcx) * (dx - rcx) + (dz - rcz) * (dz - rcz) > 16) return;
        double hexd = Math.max(Math.abs(dx - cx), Math.abs(dz - cz) * 0.87 + Math.abs(dx - cx) * 0.5);
        double d = Math.sqrt((dx - cx) * (dx - cx) + (dz - cz) * (dz - cz));
        for (int y = FLOOR; y <= FLOOR + 18; y++) {
            if (y < FLOOR + 17 && hexd <= 2.3) put(col, y, y < FLOOR + 2 || y == FLOOR + 9 ? ICY : PACKED, 0);
            if (y >= FLOOR + 17 && d <= 3.4) put(col, y, ICY, 0); // platform on top
        }
        if (dx == rcx && dz == rcz) put(col, FLOOR + 18, LAMP, 0); // and a lamp in its center
    }

    /**
     * The giant throne (1.1.3): a dais of three steps, the seat and armrests with lamps, a tall back up to FLOOR + 33 narrowing at the top,
     * a fan of ice spikes behind it (deco()); the altar in front of the seat, as before.
     */
    private static void throne(int dx, int dz, int[] col) {
        int tz = dz - THRONE_DZ, ax = Math.abs(dx);
        double rd = Math.sqrt(dx * dx + tz * tz);
        if (rd <= 14) put(col, FLOOR, ICY, 0);
        if (rd <= 11.5) put(col, FLOOR + 1, SNOW, 0);
        if (rd <= 9) put(col, FLOOR + 2, ICY, 0);
        if (dx == 0 && dz == ALTAR_DZ) {
            put(col, FLOOR + ALTAR_DY, ALTAR, 0);
            put(col, FLOOR + ALTAR_DY + 1, AIR, 0);
        }
        if (ax <= 4 && tz >= 0 && tz <= 4) { // seat and armrests
            for (int y = FLOOR + 3; y <= FLOOR + 5; y++) put(col, y, ax <= 2 ? PACKED : ICY, 0);
            if (ax >= 3) for (int y = FLOOR + 6; y <= FLOOR + 8; y++) put(col, y, ICY, 0);
            if (ax == 4 && tz == 0) put(col, FLOOR + 9, LAMP, 0);
        }
        if (tz >= 5 && tz <= 7) for (int y = FLOOR + 3; y <= FLOOR + 33; y++) // the back
            if (ax <= 4 - Math.max(0, y - FLOOR - 26) / 2) put(col, y, (y + ax) % 6 != 0 ? ICY : PACKED, 0);
    }

    /**
     * Two giant ice knights guard the throne (1.1.3): a plinth, a body of packed ice with broad shoulders, a head with a lamp-visor, a sword
     * held point down in front.
     */
    private static void knights(int dx, int dz, int[] col) {
        for (int s = -1; s <= 1; s += 2) {
            int ox = dx - s * 19, oz = dz - (THRONE_DZ + 2);
            if (Math.abs(ox) > 3 || Math.abs(oz) > 3) continue;
            for (int y = FLOOR; y < FLOOR + 3; y++) put(col, y, ICY, 0);
            for (int y = FLOOR + 3; y < FLOOR + 24; y++) {
                int w = y < FLOOR + 12 ? 2 : y < FLOOR + 19 ? 3 : 1;
                if (Math.abs(ox) <= w && Math.abs(oz) <= 1) put(col, y, PACKED, 0);
            }
            if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) for (int y = FLOOR + 24; y < FLOOR + 27; y++) put(col, y, ICY, 0);
            if (ox == 0 && oz == -2) put(col, FLOOR + 25, LAMP, 0);
            if (ox == 0 && oz == -3) for (int y = FLOOR + 3; y < FLOOR + 30; y++) put(col, y, y >= FLOOR + 17 && y <= FLOOR + 18 ? ICY : PACKED, 0);
            if (oz == -3 && ox != 0 && Math.abs(ox) <= 2) put(col, FLOOR + 18, ICY, 0);
        }
    }

    private static Voxels deco;

    /** Volume parts of the cathedral: the fan of spikes behind the throne, three leaning shards at the foot of each pylon. */
    private static synchronized Voxels deco() {
        if (deco != null) return deco;
        Voxels v = new Voxels(code -> code == BRAZIER ? 1 : 0);
        for (int k = 0; k < 9; k++) {
            double a = Math.PI / 2 + (k - 4) * 0.28;
            prism(v, 0.5, FLOOR + 26, THRONE_DZ + 6.5, Math.cos(a) * 0.9, Math.sin(a), 0, 14 + (4 - Math.abs(k - 4)) * 4, 1.3, PACKED, k % 2 == 0 ? LAMP : 0);
        }
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8 + Math.PI / 16, cx = Math.cos(a) * 70, cz = Math.sin(a) * 70;
            for (int j = 0; j < 3; j++) {
                double b = a + j * 2 * Math.PI / 3 + 0.5;
                prism(v, cx + Math.cos(b) * 2.5, FLOOR, cz + Math.sin(b) * 2.5, Math.cos(b) * 0.6, 1, Math.sin(b) * 0.6, 6 + j * 2, 1.0, PACKED, 0);
            }
        }
        return deco = v;
    }

    /** A crystal: a thick segment from (x0, y0, z0) along the direction, tapering to a point in the last third (a tip block near the end). */
    private static void prism(Voxels v, double x0, double y0, double z0, double ux, double uy, double uz, double length, double rad, int shell, int tip) {
        double n = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= n;
        uy /= n;
        uz /= n;
        int r = (int) rad + 2;
        for (int i = 0; i <= (int) (length * 2); i++) {
            double t = i / 2.0, f = t / length, rr = f < 0.65 ? rad : rad * (1 - (f - 0.65) / 0.35) + 0.25;
            double cx = x0 + ux * t, cy = y0 + uy * t, cz = z0 + uz * t;
            for (int ddx = -r; ddx <= r; ddx++) for (int ddy = -r; ddy <= r; ddy++) for (int ddz = -r; ddz <= r; ddz++) {
                int px = (int) Math.floor(cx) + ddx, py = (int) Math.floor(cy) + ddy, pz = (int) Math.floor(cz) + ddz;
                if (py < FLOOR) continue; // not into the floor and its lamps
                double vx = px + 0.5 - x0, vy = py + 0.5 - y0, vz = pz + 0.5 - z0, along = vx * ux + vy * uy + vz * uz;
                if (Math.abs(along - t) > 0.5) continue;
                double ax = vx - ux * along, ay = vy - uy * along, az = vz - uz * along;
                if (Math.sqrt(ax * ax + ay * ay + az * az) > rr) continue;
                v.put(px, py, pz, tip != 0 && f > 0.8 ? tip : shell);
            }
        }
    }
}
