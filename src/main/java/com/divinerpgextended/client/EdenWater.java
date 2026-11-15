package com.divinerpgextended.client;

import net.minecraftforge.client.event.TextureStitchEvent;

import com.divinerpgextended.asm.WaterHooks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Eden's golden water: registers its textures in the block atlas (the replacement is {@link com.divinerpgextended.asm.WaterTransformer}). */
@SideOnly(Side.CLIENT)
public class EdenWater {

    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Pre e) {
        if (e.map.getTextureType() != 0) return;
        WaterHooks.still = e.map.registerIcon("divinerpgextended:eden_water_still");
        WaterHooks.flow = e.map.registerIcon("divinerpgextended:eden_water_flow");
    }
}
