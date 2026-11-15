package com.divinerpgextended.world;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.EntityList;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Whitelist of natural spawning in Divine worlds (Iceika, the twilight dimensions, Arcana, Vethea): only Divine and addon
 * mobs and whatever is listed in {@link Config#divineSpawnWhitelist} appear on their own there. Tinkers' Construct slimes, Grimoire of Gaia
 * mobs and mobs of other mods that register "in all biomes" stay out - no need to suppress them with lamps.
 * Spawners and summons are not affected (they do not fire the natural spawn check event).
 */
public class DivineSpawns {

    /** A Divine world. */
    public static boolean divine(int dim) {
        return dim == ConfigurationHelper.iceika || dim == ConfigurationHelper.eden || dim == ConfigurationHelper.wildwood
            || dim == ConfigurationHelper.apalachia || dim == ConfigurationHelper.skythern || dim == ConfigurationHelper.mortum
            || dim == ConfigurationHelper.arcana || dim == ConfigurationHelper.vethea;
    }

    /** Whether this mob may appear on its own in a Divine world: a Divine/addon class or from the whitelist (class, entity name or modid). */
    public static boolean allowed(Class<?> c) {
        String n = c.getName();
        if (n.startsWith("net.divinerpg.") || n.startsWith("com.divinerpgextended.")) return true;
        String id = (String) EntityList.classToStringMapping.get(c);
        for (String w : Config.divineSpawnWhitelist) {
            w = w.trim();
            if (w.isEmpty()) continue;
            if (n.equals(w) || n.startsWith(w + ".") || w.equals(id) || id != null && id.startsWith(w + ".")) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onCheck(LivingSpawnEvent.CheckSpawn e) {
        if (!Config.divineSpawnFilter || e.world == null || e.world.isRemote || !divine(e.world.provider.dimensionId)) return;
        if (allowed(e.entityLiving.getClass())) return;
        e.setResult(Event.Result.DENY);
    }
}
