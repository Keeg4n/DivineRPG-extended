package com.divinerpgextended.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Freezes while exploring Eden and Wildwood. After receiving a chunk over the network, the client re-checks light in it
 * for any world except the Overworld (air next to blocks) - the server does not fully finish the light at the edges during generation, the client corrects it.
 * Minecraft does this in steps of 128 light recalculations in a row; for floating islands and huge canopies one such step
 * takes half a second, and it can't be interrupted.
 * <p>
 * So we do the re-check ourselves, with the same Minecraft logic, but one block at a time: after each recalculation we look at the
 * clock and spend no more than {@link #BUDGET_NS} per tick, nearby chunks first. We hide chunks from Minecraft (the "checked"
 * counter), and the finishing pass by height differences (recheckGaps, also in whole columns) is handed over here too. The light comes out the same.
 */
@SideOnly(Side.CLIENT)
public class TwilightRelight {

    private static final int DONE = 4096;
    /** Minecraft's re-check runs only in active chunks around the player (radius 7) - we take with a margin. */
    private static final int RADIUS = 8;
    private static final long BUDGET_NS = 2000000L;

    private Field queued, gaps;
    /** Chunk -> re-check position: Minecraft's step (0..4095) * 16 + the block in the step's column (0..15). */
    private final java.util.Map<Chunk, Integer> pending = new java.util.WeakHashMap<Chunk, Integer>();

    /**
     * Tick of the own player: it goes after packet reception (new chunks are already reset by Minecraft) but before the world tick, where
     * Minecraft would check them in a batch - we intercept exactly between them.
     */
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !e.player.worldObj.isRemote) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (e.player != mc.thePlayer || mc.theWorld == null) return;
        int dim = mc.theWorld.provider.dimensionId;
        if (dim != ConfigurationHelper.eden && dim != ConfigurationHelper.wildwood) {
            pending.clear();
            return;
        }
        try {
            if (queued == null) {
                queued = ReflectionHelper.findField(Chunk.class, "queuedLightChecks", "field_76649_t");
                gaps = ReflectionHelper.findField(Chunk.class, "isGapLightingUpdated", "field_76650_s");
            }
            final int px = (int) Math.floor(mc.thePlayer.posX) >> 4, pz = (int) Math.floor(mc.thePlayer.posZ) >> 4;
            // new chunks (reset by Minecraft) are taken for ourselves
            for (int x = px - RADIUS; x <= px + RADIUS; x++)
                for (int z = pz - RADIUS; z <= pz + RADIUS; z++) {
                    Chunk c = mc.theWorld.getChunkFromChunkCoords(x, z);
                    int q = queued.getInt(c);
                    if (q < DONE) {
                        Integer had = pending.get(c);
                        pending.put(c, Math.min(had == null ? q * 16 : had, q * 16));
                        queued.setInt(c, DONE);
                    }
                }
            // the finishing pass by height differences - with the same re-check (across the whole render distance)
            int far = mc.gameSettings.renderDistanceChunks + 1;
            for (int x = px - far; x <= px + far; x++)
                for (int z = pz - far; z <= pz + far; z++) {
                    Chunk c = mc.theWorld.getChunkFromChunkCoords(x, z);
                    if (!gaps.getBoolean(c)) continue;
                    gaps.setBoolean(c, false);
                    pending.put(c, 0);
                    queued.setInt(c, DONE);
                }
            if (pending.isEmpty()) return;
            List<Chunk> order = new ArrayList<Chunk>(pending.keySet());
            Collections.sort(order, new Comparator<Chunk>() {

                @Override
                public int compare(Chunk a, Chunk b) {
                    return dist(a, px, pz) - dist(b, px, pz);
                }
            });
            long deadline = System.nanoTime() + BUDGET_NS;
            for (Chunk c : order) {
                if (!c.isChunkLoaded) {
                    pending.remove(c); // unloaded - will come again, we will check then
                    continue;
                }
                int pos = relight(mc.theWorld, c, pending.get(c), deadline);
                if (pos >= DONE * 16) pending.remove(c);
                else {
                    pending.put(c, pos);
                    break; // tick time is over
                }
            }
        } catch (Throwable t) {
            queued = null; // another client (the fields were not found) - we do not interfere, Minecraft will check by itself
            pending.clear();
        }
    }

    /**
     * Re-check the chunk's light from position pos until deadline - like Chunk.enqueueRelightChecks, but per block.
     * Returns the position where we stopped (DONE * 16 - the chunk is checked).
     */
    static int relight(World w, Chunk c, int pos, long deadline) {
        ExtendedBlockStorage[] arr = c.getBlockStorageArray();
        while (pos < DONE * 16) {
            int step = pos >> 4, k1 = pos & 15;
            int j = step % 16, k = step / 16 % 16, l = step / 256;
            int x = (c.xPosition << 4) + k, z = (c.zPosition << 4) + l, y = (j << 4) + k1;
            ExtendedBlockStorage s = arr[j];
            boolean check = s == null ? (k1 == 0 || k1 == 15 || k == 0 || k == 15 || l == 0 || l == 15)
                : s.getBlockByExtId(k, k1, l).getMaterial() == Material.air;
            pos++;
            if (!check) continue;
            if (w.getBlock(x, y - 1, z).getLightValue() > 0) w.func_147451_t(x, y - 1, z);
            if (w.getBlock(x, y + 1, z).getLightValue() > 0) w.func_147451_t(x, y + 1, z);
            if (w.getBlock(x - 1, y, z).getLightValue() > 0) w.func_147451_t(x - 1, y, z);
            if (w.getBlock(x + 1, y, z).getLightValue() > 0) w.func_147451_t(x + 1, y, z);
            if (w.getBlock(x, y, z - 1).getLightValue() > 0) w.func_147451_t(x, y, z - 1);
            if (w.getBlock(x, y, z + 1).getLightValue() > 0) w.func_147451_t(x, y, z + 1);
            w.func_147451_t(x, y, z);
            if (System.nanoTime() > deadline) break;
        }
        return pos;
    }

    private static int dist(Chunk c, int px, int pz) {
        int dx = c.xPosition - px, dz = c.zPosition - pz;
        return dx * dx + dz * dz;
    }
}
