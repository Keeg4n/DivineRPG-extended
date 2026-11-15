package com.divinerpgextended.world.twilight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * A large structure defined by the function "what stands in the column (dx, dz) from the center" - like the Frozen Throne. Each chunk, when
 * populated, builds its piece directly in the chunk storage (without chain generation and without recalculating light for every block),
 * and then removes from neighboring finished pieces what Divine population placed there (trees, flowers) over the blueprint.
 */
public abstract class ColumnStructure {

    /** The code "do not touch the column here" and "air". */
    public static final int KEEP = -1, AIR = 0;
    /**
     * Foundation: from this height downward, while it is empty below (air, liquid, plants), {@link #foundation()} is laid - up to
     * {@link #FOUNDATION_DEPTH} blocks. The structure stands on the ground and does not hang over a cave.
     */
    public static final int FOUNDATION = -2, FOUNDATION_DEPTH = 48;

    /** What to fill the foundation with. */
    protected Block foundation() {
        return Blocks.stone;
    }

    /** Block and meta by code (codes > 0 are specific to each structure). */
    protected abstract Block block(int code, int meta);

    /** Fill col[y * 2] = code, col[y * 2 + 1] = meta; false - the column is not part of the structure. */
    protected abstract boolean column(int dx, int dz, int[] col);

    /** Block meta by code (Divine lamps are one block with many metadata values). */
    protected int metaOf(int code, int meta) {
        return meta;
    }

    /** Blocks after which light must be recalculated (they glow). */
    protected boolean lit(Block b) {
        return b.getLightValue() > 0;
    }

    /** The blueprint column from outside (for checks and sealing): the same as {@link #column}. */
    public boolean columnAt(int dx, int dz, int[] col) {
        return column(dx, dz, col);
    }

    public Block blockAt(int code, int meta) {
        return code == AIR ? Blocks.air : block(code, meta);
    }

    public int metaAt(int code, int meta) {
        return code == AIR ? 0 : metaOf(code, meta);
    }

    public static void put(int[] col, int y, int code, int meta) {
        if (y <= 0 || y >= 256) return;
        col[y * 2] = code;
        col[y * 2 + 1] = meta;
    }

    public static int hash(int x, int z) {
        int h = x * 734287 + z * 912271;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    /** Finish a piece of the structure with center (cx, cz) in the population area [x0, x0 + 16) x [z0, z0 + 16). */
    public void buildSlice(World w, int cx, int cz, int x0, int z0) {
        Set<Chunk> touched = new HashSet<Chunk>();
        List<int[]> lights = new ArrayList<int[]>();
        // what was changed directly in the chunk storage - the client won't find out on its own (the chunk may have already gone to the player): mark for sending
        List<int[]> changed = new ArrayList<int[]>();
        int[] col = new int[256 * 2];
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int i = 0; i < col.length; i += 2) col[i] = KEEP;
                if (!column(x - cx, z - cz, col)) continue;
                Chunk ch = w.getChunkFromBlockCoords(x, z);
                touched.add(ch);
                ExtendedBlockStorage[] arr = ch.getBlockStorageArray();
                for (int y = 1; y < 256; y++) {
                    int code = col[y * 2];
                    if (code == KEEP) continue;
                    if (code == FOUNDATION) {
                        Block f = foundation();
                        for (int yy = y; yy > Math.max(1, y - FOUNDATION_DEPTH); yy--) {
                            Block was = w.getBlock(x, yy, z);
                            if (!(was.isAir(w, x, yy, z) || was.getMaterial().isLiquid() || was.getMaterial().isReplaceable() || !was.isOpaqueCube())) break;
                            ExtendedBlockStorage fs = arr[yy >> 4];
                            if (fs == null) fs = arr[yy >> 4] = new ExtendedBlockStorage(yy >> 4 << 4, !w.provider.hasNoSky);
                            fs.func_150818_a(x & 15, yy & 15, z & 15, f);
                            fs.setExtBlockMetadata(x & 15, yy & 15, z & 15, 0);
                            changed.add(new int[] { x, yy, z });
                        }
                        continue;
                    }
                    int meta = col[y * 2 + 1];
                    Block b = code == AIR ? Blocks.air : block(code, meta);
                    if (code != AIR) meta = metaOf(code, meta);
                    if (w.getBlock(x, y, z).hasTileEntity(w.getBlockMetadata(x, y, z))) w.removeTileEntity(x, y, z);
                    ExtendedBlockStorage s = arr[y >> 4];
                    if (s == null) {
                        if (b == Blocks.air) continue;
                        s = arr[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !w.provider.hasNoSky);
                    }
                    s.func_150818_a(x & 15, y & 15, z & 15, b);
                    s.setExtBlockMetadata(x & 15, y & 15, z & 15, meta);
                    changed.add(new int[] { x, y, z });
                    if (lit(b)) lights.add(new int[] { x, y, z });
                }
            }
        }
        for (Chunk ch : touched) {
            ch.generateSkylightMap();
            // per-column light doesn't know the neighbors: under a big structure above the void a black column remains. Let
            // the server finish the light as for a normal island, before the chunk is sent to the player - otherwise the client does it
            ch.isLightPopulated = false;
            ch.setChunkModified();
        }
        for (int[] l : lights) w.func_147451_t(l[0], l[1], l[2]);
        resend(w, changed);
        decorate(w, cx, cz, x0, z0);
    }

    /**
     * Tell the players who see these chunks about blocks written around the world: without this a neighboring chunk already sent to the client
     * stays old in pieces (holes and "glass" until the first update nearby). More than 64 blocks in a chunk - the game itself
     * resends the changed sections whole.
     */
    static void resend(World w, List<int[]> changed) {
        if (!(w instanceof net.minecraft.world.WorldServer) || changed.isEmpty()) return;
        net.minecraft.server.management.PlayerManager pm = ((net.minecraft.world.WorldServer) w).getPlayerManager();
        if (pm == null) return; // the world is still being created (searching for the spawn point): there are no players, nobody to send to
        for (int[] c : changed) pm.markBlockForUpdate(c[0], c[1], c[2]);
    }

    /**
     * Blocks with data (statues, chests) - placed through the world, not into the chunk storage: only those that fall within the population
     * area [x0, x0 + 16) x [z0, z0 + 16) (see {@link #place}).
     */
    protected void decorate(World w, int cx, int cz, int x0, int z0) {}

    /** Place a block with data if (cx + dx, cz + dz) is in the population area; true - placed. */
    protected static boolean place(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, Block b, int meta) {
        int x = cx + dx, z = cz + dz;
        if (x < x0 || x >= x0 + 16 || z < z0 || z >= z0 + 16) return false;
        w.setBlock(x, y, z, b, meta, 2);
        return true;
    }

    /**
     * Restore, according to the blueprint, solid blocks of finished neighboring pieces that foreign generation spoiled after construction (lakes
     * of lava and blood carve out air and flood liquid, fire, natural stone on top) - within yFrom..yTo. Only air, liquid, fire
     * and natural blocks are replaced (what world generation places); blocks with data are not touched.
     */
    public void restore(World w, int cx, int cz, int xFrom, int zFrom, int size, int yFrom, int yTo, Block... natural) {
        int[] col = new int[256 * 2];
        for (int x = xFrom; x < xFrom + size; x++) {
            for (int z = zFrom; z < zFrom + size; z++) {
                if (!w.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
                int ax = (x - 8) >> 4, az = (z - 8) >> 4; // the piece the column belongs to is already built
                if (!w.getChunkProvider().chunkExists(ax, az) || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) continue;
                for (int i = 0; i < col.length; i += 2) col[i] = KEEP;
                if (!column(x - cx, z - cz, col)) continue;
                for (int y = Math.max(1, yFrom); y <= yTo; y++) {
                    int code = col[y * 2];
                    if (code == KEEP || code == AIR || code == FOUNDATION) continue;
                    Block want = block(code, col[y * 2 + 1]), was = w.getBlock(x, y, z);
                    int meta = metaOf(code, col[y * 2 + 1]);
                    if (was == want || want.getMaterial().isLiquid()) continue;
                    boolean spoiled = was.isAir(w, x, y, z) || was.getMaterial().isLiquid() || was == Blocks.fire;
                    for (Block n : natural) spoiled |= was == n;
                    if (!spoiled || was.hasTileEntity(w.getBlockMetadata(x, y, z))) continue;
                    w.setBlock(x, y, z, want, meta, 2);
                }
            }
        }
    }

    /**
     * Remove from finished neighboring pieces what Divine population placed over the air of the blueprint (within
     * yFrom..yTo). Unfinished and unloaded chunks are not touched.
     */
    public void clean(World w, int cx, int cz, int xFrom, int zFrom, int size, int yFrom, int yTo) {
        int[] col = new int[256 * 2];
        for (int x = xFrom; x < xFrom + size; x++) {
            for (int z = zFrom; z < zFrom + size; z++) {
                if (!w.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
                int ax = (x - 8) >> 4, az = (z - 8) >> 4; // the piece the column belongs to is already built
                if (!w.getChunkProvider().chunkExists(ax, az) || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) continue;
                for (int i = 0; i < col.length; i += 2) col[i] = KEEP;
                if (!column(x - cx, z - cz, col)) continue;
                for (int y = yFrom; y <= yTo; y++) {
                    if (col[y * 2] != AIR || w.isAirBlock(x, y, z)) continue;
                    if (w.getBlock(x, y, z).hasTileEntity(w.getBlockMetadata(x, y, z)) || w.getBlock(x, y, z) == com.divinerpgextended.registry.ModBlocks.arenaAltar) continue; // chests and altars of the structure ({@link #decorate})
                    w.removeTileEntity(x, y, z);
                    w.setBlock(x, y, z, Blocks.air, 0, 2);
                }
            }
        }
    }
}
