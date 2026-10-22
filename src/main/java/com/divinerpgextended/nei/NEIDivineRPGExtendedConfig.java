package com.divinerpgextended.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

import com.divinerpgextended.Tags;

/**
 * Подключение к NEI (NEI сам находит классы NEI*Config): вкладки рыбалки, ящиков и торговцев. Работает и на классическом
 * NEI (ChickenBones 1.0.5), и на NEI из GTNH: всё, чего нет в классическом (катализаторы, описания вкладок), - в
 * {@link NeiGtnh}, этот класс загружается только при NEI из GTNH.
 */
public class NEIDivineRPGExtendedConfig implements IConfigureNEI {

    static final String MOD = "DivineRPG Extended", MODID = "divinerpgextended";

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
        if (!Nei.gtnh()) return;
        try {
            NeiGtnh.setup(fishing);
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("NEI: катализаторы и описания вкладок не подключены - " + t);
        }
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
