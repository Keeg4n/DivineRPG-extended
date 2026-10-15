package com.divinerpgextended.world;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Руда инея плавится только в угольной печи Iceika (бесконечный жар). Рецепт плавки общий для всех печей, поэтому
 * ванильной печи, в которой лежит руда инея, раз в полсекунды сбрасываем прогресс: руда в ней не доплавится.
 * Угольная печь Divine - не наследник ванильной, её это не касается.
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
