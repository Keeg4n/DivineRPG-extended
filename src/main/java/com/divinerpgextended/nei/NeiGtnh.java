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
 * Только для NEI из GTNH (в классическом NEI этих классов и методов нет - этот класс там не загружается): удочки как
 * катализаторы рыбалки и описания вкладок (иконка, высота страницы).
 */
public class NeiGtnh {

    static void setup(FishingHandler fishing) {
        // удочки - катализаторы рыбалки: в NEI видно, чем ловить, и по удочке открывается вкладка
        API.addRecipeCatalyst(new ItemStack(Items.fishing_rod), fishing);
        for (Item rod : ModItems.rods) if (rod != null) API.addRecipeCatalyst(new ItemStack(rod), fishing);
        infos();
        MinecraftForge.EVENT_BUS.register(new NeiGtnh());
    }

    /** NEI перечитывает описания вкладок при загрузке, наши кладём каждый раз. */
    @SubscribeEvent
    public void onHandlerInfos(NEIRegisterHandlerInfosEvent e) {
        infos();
    }

    private static void infos() {
        put(FishingHandler.class, new ItemStack(ModItems.rods[Rod.ARCANA.ordinal()]), FishingHandler.maxHeight());
        put(CrateHandler.class, new ItemStack(ModItems.crate, 1, Zone.OCEAN.ordinal()), CrateHandler.maxHeight());
        put(TradeHandler.class, new ItemStack(ModItems.rods[Rod.GOLDEN.ordinal()]), TradeHandler.maxHeight());
    }

    private static void put(Class<? extends IRecipeHandler> c, ItemStack icon, int height) {
        HandlerInfo info = new HandlerInfo.Builder(c, NEIDivineRPGExtendedConfig.MOD, NEIDivineRPGExtendedConfig.MODID).setDisplayStack(icon).setHeight(height)
            .setWidth(166).setMultipleWidgetsAllowed(true).setShowOverlayButton(false).build();
        GuiRecipeTab.handlerMap.put(info.getHandlerName(), info);
    }
}
