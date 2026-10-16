package com.divinerpgextended.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.chunk.Chunk;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Фризы при исследовании Эдема и Вайлдвуда. Получив чанк по сети, клиент для любого мира, кроме обычного, перепроверяет
 * в нём свет (4096 шагов: воздух рядом с блоками) - сервер при генерации досчитывает свет у краёв не до конца, и
 * клиент его поправляет. Minecraft делает это сразу пачкой (до 10 чанков по 8 шагов за тик), а у летающих островов
 * много воздуха под небом - пачка небесного света тянет на 200-1000 мс.
 * <p>
 * Работу не выбрасываем, а растягиваем: прячем чанки от пачки Minecraft (счётчик "проверено") и сами продолжаем их
 * перепроверку с того же места, ближние первыми, не дольше {@link #BUDGET_NS} за тик. Так же поступаем с досчётом по
 * перепадам высот (recheckGaps): он целиком по столбу и над пустотой тянет до 500 мс - вместо него та же перепроверка.
 * Свет выходит тот же, без рывков.
 */
@SideOnly(Side.CLIENT)
public class TwilightRelight {

    private static final int DONE = 4096;
    /** Перепроверка Minecraft идёт только в активных чанках вокруг игрока (радиус 7) - берём с запасом. */
    private static final int RADIUS = 8;
    private static final long BUDGET_NS = 2000000L;

    private Field queued, gaps;
    /** Чанк, где остановилась его перепроверка (счётчик Minecraft прячем, своё значение храним здесь). */
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
                        pending.put(c, q);
                        queued.setInt(c, DONE);
                    }
                }
            // досчёт по перепадам высот (recheckGaps) Minecraft делает по столбу целиком - у острова над пустотой это до
            // 500 мс за раз. Снимаем его и отдаём той же растянутой перепроверке всего воздуха чанка (во всей дальности)
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
            long start = System.nanoTime();
            for (Chunk c : order) {
                if (!c.isChunkLoaded) {
                    pending.remove(c); // выгружен - придёт заново, тогда и проверим
                    continue;
                }
                int q = pending.get(c);
                while (q < DONE && System.nanoTime() - start < BUDGET_NS) {
                    queued.setInt(c, q);
                    c.enqueueRelightChecks(); // 8 шагов Minecraft с этого места
                    q = queued.getInt(c);
                }
                queued.setInt(c, DONE);
                if (q >= DONE) pending.remove(c);
                else {
                    pending.put(c, q);
                    break; // бюджет тика кончился
                }
            }
        } catch (Throwable t) {
            queued = null; // другой клиент (поля не нашлись) - не мешаем, Minecraft проверит сам
            pending.clear();
        }
    }

    private static int dist(Chunk c, int px, int pz) {
        int dx = c.xPosition - px, dz = c.zPosition - pz;
        return dx * dx + dz * dz;
    }
}
