package com.divinerpgextended.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Факелы Divine (водный, скелетный, Эдема, арканиевый) и факелы сумеречных миров аддона в обычном мире отгоняют мобов
 * Divine: в {@link Config#divineTorchRadius} блоках от такого факела мобы Divine сами не появляются (спавнеры и призыв
 * не трогаем). Факелы учитываются по чанкам - при загрузке чанка, установке и поломке, - а не перебором блоков на
 * каждую попытку спавна.
 */
public class TorchWards {

    /** Чанк -> факелы в нём (только обычный мир). */
    private static final Map<Long, Set<Long>> TORCHES = new HashMap<Long, Set<Long>>();

    public static boolean isTorch(Block b) {
        return b instanceof net.divinerpg.blocks.base.BlockModTorch || b instanceof com.divinerpgextended.block.BlockWorldTorch;
    }

    private static boolean overworld(World w) {
        return !w.isRemote && w.provider.dimensionId == 0;
    }

    private static long chunk(int cx, int cz) {
        return (long) cx << 32 | cz & 0xFFFFFFFFL;
    }

    private static long pos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | y & 0xFFF;
    }

    public static void add(int x, int y, int z) {
        long k = chunk(x >> 4, z >> 4);
        Set<Long> s = TORCHES.get(k);
        if (s == null) TORCHES.put(k, s = new HashSet<Long>());
        s.add(pos(x, y, z));
    }

    public static void remove(int x, int y, int z) {
        Set<Long> s = TORCHES.get(chunk(x >> 4, z >> 4));
        if (s != null) s.remove(pos(x, y, z));
    }

    /** Есть ли факел ближе радиуса (по горизонтали круг, по высоте - тот же радиус). Пропавшие факелы выбрасываем. */
    public static boolean warded(World w, int x, int y, int z) {
        int r = Config.divineTorchRadius;
        if (r <= 0) return false;
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
            Set<Long> s = TORCHES.get(chunk(cx, cz));
            if (s == null) continue;
            for (Iterator<Long> it = s.iterator(); it.hasNext();) {
                long p = it.next();
                int tx = (int) (p >> 38), tz = (int) (p << 26 >> 38), ty = (int) (p & 0xFFF);
                int dx = tx - x, dz = tz - z;
                if (dx * dx + dz * dz > r * r || Math.abs(ty - y) > r) continue;
                if (!isTorch(w.getBlock(tx, ty, tz))) {
                    it.remove(); // сломан не игроком (вода, взрыв) - забыть
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load e) {
        if (!overworld(e.world)) return;
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
        if (found == null) TORCHES.remove(k);
        else TORCHES.put(k, found);
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload e) {
        if (overworld(e.world)) TORCHES.remove(chunk(e.getChunk().xPosition, e.getChunk().zPosition));
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.PlaceEvent e) {
        if (overworld(e.world) && isTorch(e.placedBlock)) add(e.x, e.y, e.z);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (overworld(e.world) && isTorch(e.block)) remove(e.x, e.y, e.z);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn e) {
        if (!overworld(e.world) || !e.entityLiving.getClass().getName().startsWith("net.divinerpg.")
            || e.entityLiving instanceof net.minecraft.entity.boss.IBossDisplayData) return;
        if (warded(e.world, MathHelper.floor_double(e.x), MathHelper.floor_double(e.y), MathHelper.floor_double(e.z))) e.setResult(Event.Result.DENY);
    }
}
