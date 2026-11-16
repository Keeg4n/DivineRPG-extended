package com.divinerpgextended.portal;

import net.divinerpg.dimensions.base.DivineTeleporter;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Teleport of Divine portals (twilight worlds and exit from them). Divine looks for the paired portal by brute force over 257x257 blocks across the whole
 * world height (about 17 million world accesses) and creates the teleporter anew on every entry - the cache is empty, brute force every time,
 * and along the way it generates all 289 chunks around. Here is the same search (nearest portal block, the lowest in the column, the same
 * radius), but over chunk arrays skipping empty sections and only in already existing chunks: there cannot be a
 * portal in an ungenerated one. The found spot is put into Divine's cache and handed to its own code - the player is placed as before.
 * Substituted in BlockModPortal ({@link PortalTransformer}).
 */
public class FastTeleporter extends DivineTeleporter {

    private static final int RADIUS = 128;

    public FastTeleporter(WorldServer world, int dimensionID, Block portal, Block frameBlock) {
        super(world, dimensionID, portal, frameBlock);
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, double x, double y, double z, float rotation) {
        int ex = MathHelper.floor_double(entity.posX), ez = MathHelper.floor_double(entity.posZ);
        long key = ChunkCoordIntPair.chunkXZ2Int(ex, ez);
        if (!field_85191_c.containsItem(key)) {
            int[] found;
            try {
                found = find(entity, ex, ez);
            } catch (Throwable t) {
                return super.placeInExistingPortal(entity, x, y, z, rotation); // something is wrong - Divine's search
            }
            if (found == null) return false;
            field_85191_c.add(key, this.new PortalPosition(found[0], found[1], found[2], field_85192_a.getTotalWorldTime()));
            field_85190_d.add(key);
        }
        return super.placeInExistingPortal(entity, x, y, z, rotation);
    }

    /** How far to look for an island for a new portal in island worlds. */
    static final int ISLAND_SEARCH = 112;

    /**
     * A new portal on this side: its frame (and platform) is marked - when mined, it drops nothing ({@link PortalFrames}).
     * In island worlds Divine looks for ground only within 16 blocks and over the void builds the portal with a platform in the sky - first
     * we move the point to the nearest flat area of an island.
     */
    @Override
    public boolean makePortal(Entity entity) {
        net.minecraft.entity.player.EntityPlayer pl = entity instanceof net.minecraft.entity.player.EntityPlayer ? (net.minecraft.entity.player.EntityPlayer) entity : null;
        com.divinerpgextended.world.twilight.TwilightWorld tw = com.divinerpgextended.world.twilight.TwilightWorld.of(field_85192_a.provider.dimensionId);
        if (tw != null && tw != com.divinerpgextended.world.twilight.TwilightWorld.MORTUM) {
            int[] land = island(tw, MathHelper.floor_double(entity.posX), MathHelper.floor_double(entity.posZ), pl);
            if (land != null) entity.setLocationAndAngles(land[0] + 0.5, land[1] + 1, land[2] + 0.5, entity.rotationYaw, entity.rotationPitch);
        }
        // Divine builds the frame, the platform and clears space within 16 blocks of the point without asking anyone: on the other side the
        // point may fall into someone's base (the way back to the Overworld lands by the same coordinates) - we move it to where the player may build
        if (pl != null && !free(pl, MathHelper.floor_double(entity.posX), MathHelper.floor_double(entity.posY), MathHelper.floor_double(entity.posZ))) {
            int[] spot = freeSpot(pl, MathHelper.floor_double(entity.posX), MathHelper.floor_double(entity.posY), MathHelper.floor_double(entity.posZ));
            if (spot != null) entity.setLocationAndAngles(spot[0] + 0.5, spot[1], spot[2] + 0.5, entity.rotationYaw, entity.rotationPitch);
        }
        int ex = MathHelper.floor_double(entity.posX), ez = MathHelper.floor_double(entity.posZ), r = 20;
        java.util.Set<Long> before = PortalFrames.snapshot(field_85192_a, frameBlock, ex, ez, r);
        boolean made = super.makePortal(entity);
        PortalFrames.mark(field_85192_a, frameBlock, before, ex, ez, r);
        return made;
    }

    /**
     * The nearest flat area of an island (5 x 5, height difference no more than 1, at least 3 blocks of ground under it) within the radius
     * {@link #ISLAND_SEARCH}: {x, ground y, z}; null - none (then as in Divine). Under the entry point itself there is an island - that is it.
     */
    int[] island(com.divinerpgextended.world.twilight.TwilightWorld tw, int x0, int z0) {
        return island(tw, x0, z0, null);
    }

    /** The same, only where the player may build (null - anywhere). */
    int[] island(com.divinerpgextended.world.twilight.TwilightWorld tw, int x0, int z0, net.minecraft.entity.player.EntityPlayer pl) {
        for (int r = 0; r <= ISLAND_SEARCH; r += 4) {
            int[] best = null;
            double bd = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx += 4) for (int dz = -r; dz <= r; dz += 4) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // only the ring of radius r
                int x = x0 + dx, z = z0 + dz;
                int y = flat(tw, x, z);
                if (y < 0 || pl != null && !free(pl, x, y + 1, z)) continue;
                double d = dx * dx + dz * dz;
                if (d < bd) {
                    bd = d;
                    best = new int[] { x, y, z };
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Divine builds within 16 blocks of the point: the player may build at the point and 16 blocks around it (spawn protection, regions). */
    static boolean free(net.minecraft.entity.player.EntityPlayer p, int x, int y, int z) {
        int yy = Math.max(1, Math.min(250, y));
        for (int dx = -16; dx <= 16; dx += 16) for (int dz = -16; dz <= 16; dz += 16)
            if (!com.divinerpgextended.perk.Protection.canBuild(p, x + dx, yy, z + dz)) return false;
        return true;
    }

    /** The nearest point in rings of 8 blocks up to 96 where {@link #free} holds: {x, y, z} (y - above the top solid block if there is one); null - none. */
    int[] freeSpot(net.minecraft.entity.player.EntityPlayer p, int x0, int y0, int z0) {
        for (int r = 8; r <= 96; r += 8) {
            for (int dx = -r; dx <= r; dx += 8) for (int dz = -r; dz <= r; dz += 8) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                int x = x0 + dx, z = z0 + dz, top = field_85192_a.getTopSolidOrLiquidBlock(x, z);
                int y = top > 0 ? top : y0;
                if (free(p, x, y, z)) return new int[] { x, y, z };
            }
        }
        return null;
    }

    /** Height of flat 5 x 5 ground around (x, z) or -1. */
    private int flat(com.divinerpgextended.world.twilight.TwilightWorld tw, int x, int z) {
        int lo = 999, hi = -1;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int y = com.divinerpgextended.world.twilight.WorldGenTwilightFeatures.surface(field_85192_a, tw, x + dx, z + dz);
            if (y < 0) return -1;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        if (hi - lo > 1) return -1;
        for (int y = hi - 3; y < hi; y++) if (field_85192_a.isAirBlock(x, y, z)) return -1;
        for (int y = hi + 1; y <= hi + 5; y++) if (!field_85192_a.isAirBlock(x, y, z) && !field_85192_a.getBlock(x, y, z).isReplaceable(field_85192_a, x, y, z)) return -1;
        return hi;
    }

    /** The nearest portal block (the lowest in its column) in a square of radius 128, as in Divine; null - none. */
    int[] find(Entity entity, int ex, int ez) {
        ChunkProviderServer cps = field_85192_a.theChunkProviderServer;
        IChunkLoader loader = ReflectionHelper.getPrivateValue(ChunkProviderServer.class, cps, "currentChunkLoader", "field_73247_e");
        double best = -1;
        int[] at = null;
        for (int cx = (ex - RADIUS) >> 4; cx <= (ex + RADIUS) >> 4; cx++) {
            for (int cz = (ez - RADIUS) >> 4; cz <= (ez + RADIUS) >> 4; cz++) {
                if (!cps.chunkExists(cx, cz)
                    && !(loader instanceof AnvilChunkLoader && ((AnvilChunkLoader) loader).chunkExists(field_85192_a, cx, cz))) continue;
                Chunk c = field_85192_a.getChunkFromChunkCoords(cx, cz);
                ExtendedBlockStorage[] secs = c.getBlockStorageArray();
                for (int si = 0; si < secs.length; si++) {
                    ExtendedBlockStorage s = secs[si];
                    if (s == null || s.isEmpty()) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lx = 0; lx < 16; lx++) {
                            int bx = (cx << 4) + lx;
                            if (Math.abs(bx - ex) > RADIUS) continue;
                            for (int lz = 0; lz < 16; lz++) {
                                if (s.getBlockByExtId(lx, ly, lz) != portal) continue;
                                int bz = (cz << 4) + lz, by = s.getYLocation() + ly;
                                if (Math.abs(bz - ez) > RADIUS) continue;
                                if (by > 0 && c.getBlock(lx, by - 1, lz) == portal) continue; // not the lowest in the column
                                double dx = bx + 0.5 - entity.posX, dy = by + 0.5 - entity.posY, dz = bz + 0.5 - entity.posZ;
                                double d = dx * dx + dy * dy + dz * dz;
                                if (best < 0 || d < best) {
                                    best = d;
                                    at = new int[] { bx, by, bz };
                                }
                            }
                        }
                    }
                }
            }
        }
        return at;
    }
}
