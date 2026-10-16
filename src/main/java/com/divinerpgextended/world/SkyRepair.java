package com.divinerpgextended.world;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.event.world.ChunkDataEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Чанки Эдема и Вайлдвуда, сохранённые версией 0.44.0 (она выключала там небесный свет), записаны без небесного света
 * (а пересохранённые 0.44.1 - с нулевым) и после возврата неба грузятся чёрными. Признак: в самой верхней секции нет
 * ни одного блока с небесным светом - в мире с небом над островами так не бывает. При загрузке такого чанка считаем небесный свет заново сверху вниз и отдаём
 * досчёт по горизонтали серверу; чанк сохранится уже со светом.
 */
public class SkyRepair {

    @SubscribeEvent
    public void onLoad(ChunkDataEvent.Load e) {
        if (e.world.isRemote || e.world.provider.hasNoSky) return;
        int dim = e.world.provider.dimensionId;
        if (dim != ConfigurationHelper.eden && dim != ConfigurationHelper.wildwood) return;
        NBTTagCompound level = e.getData().getCompoundTag("Level");
        NBTTagList sections = level.getTagList("Sections", 10);
        if (sections.tagCount() == 0 || !dark(sections)) return;
        e.getChunk().generateSkylightMap();
        e.getChunk().isLightPopulated = false;
        e.getChunk().setChunkModified();
    }

    /** Верхняя секция без небесного света вовсе (нет массива или он весь нулевой). */
    public static boolean dark(NBTTagList sections) {
        NBTTagCompound top = null;
        for (int i = 0; i < sections.tagCount(); i++) {
            NBTTagCompound s = sections.getCompoundTagAt(i);
            if (top == null || s.getByte("Y") > top.getByte("Y")) top = s;
        }
        if (!top.hasKey("SkyLight")) return true;
        for (byte b : top.getByteArray("SkyLight")) if (b != 0) return false;
        return true;
    }
}
