package com.divinerpgextended.nei;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import codechicken.nei.api.API;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;

import com.divinerpgextended.fishing.Rod;
import com.divinerpgextended.fishing.Zone;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * For NEI from GTNH only (classic NEI has none of these classes and methods - this class is not loaded there): fishing rods as
 * fishing catalysts and tab descriptions (icon, page height).
 */
public class NeiGtnh {

    static void setup(FishingHandler fishing, BrewingHandler brewing) {
        // fishing rods are fishing catalysts: NEI shows what to catch with, and the tab opens from a rod
        API.addRecipeCatalyst(new ItemStack(Items.fishing_rod), fishing);
        for (Item rod : ModItems.rods) if (rod != null) API.addRecipeCatalyst(new ItemStack(rod), fishing);
        // the Arcane Brewing Stand is a brewing catalyst
        API.addRecipeCatalyst(new ItemStack(com.divinerpgextended.registry.ModBlocks.arcaneBrewer), brewing);
        infos();
        MinecraftForge.EVENT_BUS.register(new NeiGtnh());
    }

    /** NEI rereads tab descriptions on load, so we register ours every time. */
    @SubscribeEvent
    public void onHandlerInfos(NEIRegisterHandlerInfosEvent e) {
        infos();
    }

    private static void infos() {
        put(FishingHandler.class, new ItemStack(ModItems.rods[Rod.ARCANA.ordinal()]), FishingHandler.maxHeight());
        put(CrateHandler.class, new ItemStack(ModItems.crate, 1, Zone.OCEAN.ordinal()), CrateHandler.maxHeight());
        put(TradeHandler.class, new ItemStack(ModItems.rods[Rod.GOLDEN.ordinal()]), TradeHandler.maxHeight());
        put(BrewingHandler.class, new ItemStack(com.divinerpgextended.registry.ModBlocks.arcaneBrewer), BrewingHandler.maxHeight());
    }

    private static void put(Class<? extends IRecipeHandler> c, ItemStack icon, int height) {
        HandlerInfo info = new HandlerInfo.Builder(c, NEIDivineRPGExtendedConfig.MOD, NEIDivineRPGExtendedConfig.MODID).setDisplayStack(icon).setHeight(height)
            .setWidth(166).setMultipleWidgetsAllowed(true).setShowOverlayButton(false).build();
        GuiRecipeTab.handlerMap.put(info.getHandlerName(), info);
    }
}
