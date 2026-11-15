package com.divinerpgextended.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.divinerpg.utils.blocks.ArcanaBlocks;
import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.block.BlockWorldTorch;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Torches repel Divine monsters of their own world: within {@link Config#divineTorchRadius} blocks of a torch, Divine monsters do not
 * spawn on their own (spawners, summons and bosses are not affected).
 * <ul>
 * <li>skeleton (bone) torch - in the Overworld all Divine monsters; water torch - in the Overworld those that spawn in water;</li>
 * <li>Eden torch (Divine and addon), Wildwood, Apalachia, Skythern, Mortum - in their own Twilight world;</li>
 * <li>Arcanium torch - in Arcana; the addon's Nether torch (from an ifrit rod) - in the Nether.</li>
 * </ul>
 * Torches are tracked per chunk - on chunk load, placement and breaking - rather than by iterating blocks on every spawn attempt.
 */
public class TorchWards {

    /** Dimension -> chunk -> torches in it. */
    private static final Map<Integer, Map<Long, Set<Long>>> TORCHES = new HashMap<Integer, Map<Long, Set<Long>>>();

    public static boolean isTorch(Block b) {
        return b instanceof net.divinerpg.blocks.base.BlockModTorch || b instanceof BlockWorldTorch;
    }

    /** The dimension where the torch works (MIN_VALUE - nowhere). */
    public static int home(Block b) {
        if (b == VanillaBlocks.skeletonTorch || b == VanillaBlocks.aquaTorch) return 0;
        if (b == TwilightBlocks.edenTorch) return ConfigurationHelper.eden;
        if (b == ArcanaBlocks.arcaniumTorch) return ConfigurationHelper.arcana;
        if (b instanceof BlockWorldTorch) return ((BlockWorldTorch) b).dimension();
        return Integer.MIN_VALUE;
    }

    /** Whether this torch repels this monster at the spawn point. */
    static boolean wards(Block torch, World w, EntityLivingBase mob, int x, int y, int z) {
        if (home(torch) != w.provider.dimensionId) return false;
        if (torch == VanillaBlocks.aquaTorch) return w.getBlock(x, y, z).getMaterial() == Material.water;
        return true;
    }

    private static Map<Long, Set<Long>> dim(World w) {
        Map<Long, Set<Long>> m = TORCHES.get(w.provider.dimensionId);
        if (m == null) TORCHES.put(w.provider.dimensionId, m = new HashMap<Long, Set<Long>>());
        return m;
    }

    private static long chunk(int cx, int cz) {
        return (long) cx << 32 | cz & 0xFFFFFFFFL;
    }

    private static long pos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | y & 0xFFF;
    }

    public static void add(World w, int x, int y, int z) {
        Map<Long, Set<Long>> m = dim(w);
        long k = chunk(x >> 4, z >> 4);
        Set<Long> s = m.get(k);
        if (s == null) m.put(k, s = new HashSet<Long>());
        s.add(pos(x, y, z));
    }

    public static void remove(World w, int x, int y, int z) {
        Set<Long> s = dim(w).get(chunk(x >> 4, z >> 4));
        if (s != null) s.remove(pos(x, y, z));
    }

    /** Whether there is a torch within the radius that repels this mob (a circle horizontally, the same radius in height). */
    public static boolean warded(World w, EntityLivingBase mob, int x, int y, int z) {
        int r = Config.divineTorchRadius;
        if (r <= 0) return false;
        Map<Long, Set<Long>> m = TORCHES.get(w.provider.dimensionId);
        if (m == null) return false;
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
            Set<Long> s = m.get(chunk(cx, cz));
            if (s == null) continue;
            for (Iterator<Long> it = s.iterator(); it.hasNext();) {
                long p = it.next();
                int tx = (int) (p >> 38), tz = (int) (p << 26 >> 38), ty = (int) (p & 0xFFF);
                int dx = tx - x, dz = tz - z;
                if (dx * dx + dz * dz > r * r || Math.abs(ty - y) > r) continue;
                Block b = w.getBlock(tx, ty, tz);
                if (!isTorch(b)) {
                    it.remove(); // broken not by a player (water, explosion) - forget it
                    continue;
                }
                if (wards(b, w, mob, x, y, z)) return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load e) {
        if (e.world.isRemote) return;
        Chunk c = e.getChunk();
        Set<Long> found = null;
        for (ExtendedBlockStorage s : c.getBlockStorageArray()) {
            if (s == null || s.isEmpty()) continue;
            for (int ly = 0; ly < 16; ly++) for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                if (!isTorch(s.getBlockByExtId(lx, ly, lz))) continue;
                if (found == null) found = new HashSet<Long>();
                found.add(pos((c.xPosition << 4) + lx, s.getYLocation() + ly, (c.zPosition << 4) + lz));
            }
        }
        long k = chunk(c.xPosition, c.zPosition);
        if (found == null) dim(e.world).remove(k);
        else dim(e.world).put(k, found);
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload e) {
        if (!e.world.isRemote) dim(e.world).remove(chunk(e.getChunk().xPosition, e.getChunk().zPosition));
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.PlaceEvent e) {
        if (!e.world.isRemote && isTorch(e.placedBlock)) add(e.world, e.x, e.y, e.z);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (!e.world.isRemote && isTorch(e.block)) remove(e.world, e.x, e.y, e.z);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn e) {
        if (e.world.isRemote || !(e.entityLiving instanceof IMob) || !e.entityLiving.getClass().getName().startsWith("net.divinerpg.")
            || e.entityLiving instanceof net.minecraft.entity.boss.IBossDisplayData) return;
        if (warded(e.world, e.entityLiving, MathHelper.floor_double(e.x), MathHelper.floor_double(e.y), MathHelper.floor_double(e.z)))
            e.setResult(Event.Result.DENY);
    }
}
