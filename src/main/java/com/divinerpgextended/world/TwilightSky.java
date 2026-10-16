package com.divinerpgextended.world;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Эдем и Вайлдвуд без небесного света, как у Divine уже сделаны Апалачия, Скайтерн и Мортум. У летающих островов под
 * верхним блоком сотня блоков пустоты: каждый пришедший клиенту чанк и каждый поставленный блок заставляют клиент
 * пересчитывать небесный свет по всей этой высоте - отсюда фризы при исследовании. Без неба этой работы нет.
 * <ul>
 * <li>Вид: свет берётся из таблицы яркости - делаем её яркой (солнечный полдень), иначе мир потемнел бы до Апалачии.
 * Тени под островами пропадают.</li>
 * <li>Спавн: без неба уровень света везде 0, и враждебные мобы полезли бы под солнце. Под открытым небом враждебный
 * спавн запрещаем, как и было при небесном свете 15.</li>
 * </ul>
 * Сервер и клиент обязаны считать одинаково (от этого зависит формат чанков), поэтому без настройки - всегда.
 */
public class TwilightSky {

    /** Нижняя яркость: 0 у ванили (темно), 0.1 у Апалачии; 0.45 - светло, но факел ещё заметен. */
    private static final float MIN = 0.45F;

    public static boolean applies(int dim) {
        return dim == ConfigurationHelper.eden || dim == ConfigurationHelper.wildwood;
    }

    @SubscribeEvent
    public void onLoad(WorldEvent.Load e) {
        WorldProvider p = e.world.provider;
        if (!applies(p.dimensionId) || p.hasNoSky) return;
        p.hasNoSky = true;
        for (int i = 0; i <= 15; i++) {
            float f = 1.0F - i / 15.0F;
            p.lightBrightnessTable[i] = (1.0F - f) / (f * 3.0F + 1.0F) * (1.0F - MIN) + MIN;
        }
    }

    @SubscribeEvent
    public void onSpawn(LivingSpawnEvent.CheckSpawn e) {
        if (!(e.entity instanceof IMob) || e.world.isRemote || !applies(e.world.provider.dimensionId)) return;
        World w = e.world;
        int x = MathHelper.floor_double(e.x), y = MathHelper.floor_double(e.y), z = MathHelper.floor_double(e.z);
        if (w.canBlockSeeTheSky(x, y, z)) e.setResult(Event.Result.DENY);
    }
}
