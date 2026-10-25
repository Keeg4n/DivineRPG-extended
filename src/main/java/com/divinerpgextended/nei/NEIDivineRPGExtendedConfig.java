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
        // служебные блоки рельсов (ставятся предметами-рельсами) и убранные рельсы Divine
        for (net.minecraft.block.Block b : new net.minecraft.block.Block[] { com.divinerpgextended.registry.ModBlocks.skyRail,
            com.divinerpgextended.registry.ModBlocks.apalachiaRail, com.divinerpgextended.registry.ModBlocks.skyBoostRail,
            com.divinerpgextended.registry.ModBlocks.letheFlowing, com.divinerpgextended.registry.ModBlocks.dreamWater, com.divinerpgextended.registry.ModBlocks.dreamCarrots,
            com.divinerpgextended.registry.ModBlocks.dreamMelonStem,
            net.divinerpg.utils.blocks.TwilightBlocks.apalachiaRails, net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails })
            if (b != null) API.hideItem(new net.minecraft.item.ItemStack(b));
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
