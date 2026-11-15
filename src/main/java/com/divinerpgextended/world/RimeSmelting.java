package com.divinerpgextended.world;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Rime Ore smelts only in the Iceika coal furnace (infinite heat). The smelting recipe is shared by all furnaces, so
 * in a vanilla furnace holding Rime Ore we reset progress every half second: the ore will never finish smelting in it.
 * Divine's coal furnace is not a descendant of the vanilla one, so it is unaffected.
 */
public class RimeSmelting {

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        World w = event.world;
        if (event.phase != TickEvent.Phase.END || w.isRemote || w.getTotalWorldTime() % 10 != 0) return;
        Item ore = Item.getItemFromBlock(ModBlocks.rimeOre);
        for (Object o : w.loadedTileEntityList) {
            if (!(o instanceof TileEntityFurnace)) continue;
            TileEntityFurnace f = (TileEntityFurnace) o;
            ItemStack in = f.getStackInSlot(0);
            if (in != null && in.getItem() == ore) f.furnaceCookTime = 0;
        }
    }
}
