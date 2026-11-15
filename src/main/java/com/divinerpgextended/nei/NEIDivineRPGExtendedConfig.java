package com.divinerpgextended.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

import com.divinerpgextended.Tags;

/**
 * NEI hookup (NEI finds the NEI*Config classes itself): fishing, crate and merchant tabs. Works on both classic
 * NEI (ChickenBones 1.0.5) and NEI from GTNH: everything classic lacks (catalysts, tab descriptions) lives in
 * {@link NeiGtnh}, this class is loaded only with NEI from GTNH.
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
        InfusionHandler infusion = new InfusionHandler();
        API.registerRecipeHandler(infusion);
        API.registerUsageHandler(infusion);
        BrewingHandler brewing = new BrewingHandler();
        API.registerRecipeHandler(brewing);
        API.registerUsageHandler(brewing);
        AnvilHandler anvil = new AnvilHandler();
        API.registerRecipeHandler(anvil);
        API.registerUsageHandler(anvil);
        // service rail blocks (placed by rail items) and the removed Divine rails
        for (net.minecraft.block.Block b : new net.minecraft.block.Block[] { com.divinerpgextended.registry.ModBlocks.skyRail,
            com.divinerpgextended.registry.ModBlocks.apalachiaRail, com.divinerpgextended.registry.ModBlocks.skyBoostRail,
            com.divinerpgextended.registry.ModBlocks.letheFlowing, com.divinerpgextended.registry.ModBlocks.dreamCarrots,
            com.divinerpgextended.registry.ModBlocks.lureTorchLit,
            com.divinerpgextended.registry.ModBlocks.dreamMelonStem,
            net.divinerpg.utils.blocks.TwilightBlocks.apalachiaRails, net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails })
            if (b != null) API.hideItem(new net.minecraft.item.ItemStack(b));
        try {
            NeiSort.install();
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("NEI: addon item order not connected - " + t);
        }
        if (!Nei.gtnh()) return;
        try {
            NeiGtnh.setup(fishing, brewing);
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("NEI: catalysts and tab descriptions not connected - " + t);
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
