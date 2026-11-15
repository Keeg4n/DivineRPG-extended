package com.divinerpgextended;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Hats crashes the game on a mob spawn next to a descendant of the vanilla spawner (our lair spawner in the ice cave): it
 * remembers that the class is a spawner, but does not remember the field of its logic if it met an ordinary spawner before, and then
 * takes the logic as null. We fill its cache ourselves: for each descendant of TileEntityMobSpawner - the logic field of the vanilla
 * spawner (it is the same for all of them).
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
                if (c == null || c == TileEntityMobSpawner.class || !TileEntityMobSpawner.class.isAssignableFrom(c)) continue; // other mods sometimes have an empty entry
                logic.put(c, f);
                spawners.put(c, Boolean.TRUE);
                n++;
            }
            DivineRPGExtended.LOG.info("Hats: spawner logic is written for subclasses of the vanilla spawner (" + n + ") - no crash when mobs spawn nearby");
        } catch (Throwable t) {
            DivineRPGExtended.LOG.warn("Hats: could not fix its spawner cache", t);
        }
    }
}
