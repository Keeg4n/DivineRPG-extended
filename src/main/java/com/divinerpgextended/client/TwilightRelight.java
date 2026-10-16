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
 * Фризы при исследовании Эдема и Вайлдвуда. Получив чанк по сети, клиент для любого мира, кроме обычного, перепроверяет
 * в нём свет (воздух рядом с блоками) - сервер при генерации досчитывает свет у краёв не до конца, клиент поправляет.
 * Minecraft делает это шагами по 128 пересчётов света подряд; у летающих островов и огромных крон один такой шаг
 * тянет на полсекунды, и прервать его нельзя.
 * <p>
 * Поэтому перепроверку делаем сами, той же логикой Minecraft, но по одному блоку: после каждого пересчёта смотрим на
 * часы и не тратим больше {@link #BUDGET_NS} за тик, ближние чанки первыми. Чанки прячем от Minecraft (счётчик
 * "проверено"), досчёт по перепадам высот (recheckGaps, тоже целыми столбами) отдаём сюда же. Свет выходит тот же.
 */
@SideOnly(Side.CLIENT)
public class TwilightRelight {

    private static final int DONE = 4096;
    /** Перепроверка Minecraft идёт только в активных чанках вокруг игрока (радиус 7) - берём с запасом. */
    private static final int RADIUS = 8;
    private static final long BUDGET_NS = 2000000L;

    private Field queued, gaps;
    /** Чанк -> позиция перепроверки: шаг Minecraft (0..4095) * 16 + блок в столбике шага (0..15). */
    private final java.util.Map<Chunk, Integer> pending = new java.util.WeakHashMap<Chunk, Integer>();

    /**
     * Тик своего игрока: он идёт после приёма пакетов (новые чанки уже сброшены Minecraft), но до тика мира, где
     * Minecraft проверил бы их пачкой - перехватываем ровно между ними.
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
            // новые (сброшенные Minecraft) чанки забираем себе
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
            // досчёт по перепадам высот - той же перепроверкой (во всей дальности прорисовки)
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
                    pending.remove(c); // выгружен - придёт заново, тогда и проверим
                    continue;
                }
                int pos = relight(mc.theWorld, c, pending.get(c), deadline);
                if (pos >= DONE * 16) pending.remove(c);
                else {
                    pending.put(c, pos);
                    break; // время тика кончилось
                }
            }
        } catch (Throwable t) {
            queued = null; // другой клиент (поля не нашлись) - не мешаем, Minecraft проверит сам
            pending.clear();
        }
    }

    /**
     * Перепроверка света чанка с позиции pos до срока deadline - как Chunk.enqueueRelightChecks, но по блоку.
     * Возвращает позицию, где остановились (DONE * 16 - чанк проверен).
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
