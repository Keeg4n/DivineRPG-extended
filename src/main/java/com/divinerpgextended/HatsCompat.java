package com.divinerpgextended;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Hats роняет игру на спавне моба рядом с наследником ванильного спавнера (наш спавнер логова в ледяной пещере): он
 * запоминает, что класс - спавнер, но не запоминает поле его логики, если до этого встречал обычный спавнер, и потом
 * берёт логику null. Заполняем его кэш сами: для каждого наследника TileEntityMobSpawner - поле логики ванильного
 * спавнера (оно у всех одно).
 */
public final class HatsCompat {

    private HatsCompat() {}

    @SuppressWarnings("unchecked")
    public static void apply() {
        if (!Loader.isModLoaded("Hats")) return;
        try {
            Class<?> handler = Class.forName("hats.common.core.HatHandler");
            Map<Class<?>, Field> logic = (Map<Class<?>, Field>) ReflectionHelper.findField(handler, "mobSpawnerLogic").get(null);
            Map<Class<?>, Boolean> spawners = (Map<Class<?>, Boolean>) ReflectionHelper.findField(handler, "mobSpawners").get(null);
            Field f = ReflectionHelper.findField(TileEntityMobSpawner.class, "field_145882_a");
            Map<String, Class<?>> tiles = ReflectionHelper.getPrivateValue(TileEntity.class, null, "field_145855_i", "nameToClassMap");
            int n = 0;
            for (Class<?> c : tiles.values()) {
                if (c == null || c == TileEntityMobSpawner.class || !TileEntityMobSpawner.class.isAssignableFrom(c)) continue; // у чужих модов бывает пустая запись
                logic.put(c, f);
                spawners.put(c, Boolean.TRUE);
                n++;
            }
            DivineRPGExtended.LOG.info("Hats: логика спавнера записана для наследников ванильного спавнера (" + n + ") - без вылета на спавне мобов рядом");
        } catch (Throwable t) {
            DivineRPGExtended.LOG.warn("Hats: не удалось поправить его кэш спавнеров", t);
        }
    }
}
