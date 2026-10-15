package com.divinerpgextended.nei;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;

import com.divinerpgextended.Tags;
import com.divinerpgextended.fishing.Rod;
import com.divinerpgextended.fishing.Zone;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Подключение к NEI (NEI сам находит классы NEI*Config): вкладки рыбалки, ящиков и торговцев. */
public class NEIDivineRPGExtendedConfig implements IConfigureNEI {

    private static final String MOD = "DivineRPG Extended", MODID = "divinerpgextended";

    @Override
    public void loadConfig() {
        FishingHandler fishing = new FishingHandler();
        CrateHandler crates = new CrateHandler();
        TradeHandler trades = new TradeHandler();
        API.registerRecipeHandler(fishing);
        API.registerUsageHandler(fishing);
        API.registerRecipeHandler(crates);
        API.registerUsageHandler(crates);
        API.registerRecipeHandler(trades);
        API.registerUsageHandler(trades);

        // удочки - катализаторы рыбалки: в NEI видно, чем ловить, и по удочке открывается вкладка
        API.addRecipeCatalyst(new ItemStack(Items.fishing_rod), fishing);
        for (Item rod : ModItems.rods) if (rod != null) API.addRecipeCatalyst(new ItemStack(rod), fishing);

        infos();
        MinecraftForge.EVENT_BUS.register(this);
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
        HandlerInfo info = new HandlerInfo.Builder(c, MOD, MODID).setDisplayStack(icon).setHeight(height).setWidth(166)
            .setMultipleWidgetsAllowed(true).setShowOverlayButton(false).build();
        GuiRecipeTab.handlerMap.put(info.getHandlerName(), info);
    }

    @Override
    public String getName() {
        return MOD;
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
