package com.divinerpgextended.client;

import java.lang.reflect.Field;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.chunk.Chunk;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Фризы при исследовании Эдема и Вайлдвуда. Получив чанк по сети, клиент для любого мира, кроме обычного, сбрасывает
 * счётчик перепроверки света, и потом по 8 шагов за тик заново проверяет свет у каждого воздуха рядом с блоками (4096
 * шагов на чанк). У летающих островов воздуха и неба много - перепроверка небесного света даёт рывки на 200-1000 мс.
 * В обычном мире клиент этого не делает и доверяет свету сервера; здесь так же: чанкам Эдема и Вайлдвуда возвращаем
 * "проверено" (4096, как у нового чанка). Небо и тени остаются - свет по-прежнему считает сервер.
 */
@SideOnly(Side.CLIENT)
public class TwilightRelight {

    private static final int DONE = 4096;
    /** Перепроверка идёт только в активных чанках вокруг игрока (радиус 7) - берём с запасом. */
    private static final int RADIUS = 8;
    private Field queued;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        int dim = mc.theWorld.provider.dimensionId;
        if (dim != ConfigurationHelper.eden && dim != ConfigurationHelper.wildwood) return;
        try {
            if (queued == null) queued = ReflectionHelper.findField(Chunk.class, "queuedLightChecks", "field_76649_t");
            int px = (int) Math.floor(mc.thePlayer.posX) >> 4, pz = (int) Math.floor(mc.thePlayer.posZ) >> 4;
            for (int x = px - RADIUS; x <= px + RADIUS; x++)
                for (int z = pz - RADIUS; z <= pz + RADIUS; z++) {
                    Chunk c = mc.theWorld.getChunkFromChunkCoords(x, z);
                    if (queued.getInt(c) < DONE) queued.setInt(c, DONE);
                }
        } catch (Throwable t) {
            queued = null; // другой клиент (поля не нашлись) - не мешаем
        }
    }
}
