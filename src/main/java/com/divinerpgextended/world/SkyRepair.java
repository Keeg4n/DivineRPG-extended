package com.divinerpgextended.world;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.event.world.ChunkDataEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Eden and Wildwood chunks saved by version 0.44.0 (it disabled sky light there) are written without sky light
 * (and those resaved by 0.44.1 - with zero) and after the sky returned load black. The sign: the topmost section has
 * no block with sky light at all - in a world with sky above the islands this does not happen. On loading such a chunk we recompute sky light top to bottom and hand
 * the horizontal spread to the server; the chunk then saves with light.
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

    /** The top section with no sky light at all (no array or it is all zero). */
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
