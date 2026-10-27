package com.divinerpgextended.world;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.EntityList;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Белый список естественного спавна в мирах Divine (Iceika, сумеречные миры, Аркана, Ветея): сами по себе там появляются
 * только мобы Divine и аддона и то, что указано в {@link Config#divineSpawnWhitelist}. Слизни Tinkers' Construct, мобы
 * Grimoire of Gaia и прочих модов, которые прописаны "во все биомы", туда не лезут - и не нужно гасить их лампами.
 * Спавнеры и призыв не затронуты (событие проверки естественного спавна они не вызывают).
 */
public class DivineSpawns {

    /** Мир Divine. */
    public static boolean divine(int dim) {
        return dim == ConfigurationHelper.iceika || dim == ConfigurationHelper.eden || dim == ConfigurationHelper.wildwood
            || dim == ConfigurationHelper.apalachia || dim == ConfigurationHelper.skythern || dim == ConfigurationHelper.mortum
            || dim == ConfigurationHelper.arcana || dim == ConfigurationHelper.vethea;
    }

    /** Может ли этот моб сам появиться в мире Divine: класс Divine/аддона или из белого списка (класс, имя сущности или modid). */
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
