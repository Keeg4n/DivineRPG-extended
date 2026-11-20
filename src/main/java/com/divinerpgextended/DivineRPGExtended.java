package com.divinerpgextended;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.divinerpgextended.cold.CommandBlizzard;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = DivineRPGExtended.MODID,
    name = "DivineRPG Extended",
    version = Tags.VERSION,
    dependencies = "required-after:divinerpg",
    acceptedMinecraftVersions = "[1.7.10]")
public class DivineRPGExtended {

    public static final String MODID = "divinerpgextended";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static DivineRPGExtended instance;

    @SidedProxy(
        clientSide = "com.divinerpgextended.ClientProxy",
        serverSide = "com.divinerpgextended.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        com.divinerpgextended.perk.MiningGuard.apply();
        event.registerServerCommand(new CommandBlizzard());
        event.registerServerCommand(new com.divinerpgextended.world.twilight.CommandWorldWeather());
        event.registerServerCommand(new com.divinerpgextended.entity.mini.CommandMiniBoss());
        event.registerServerCommand(new com.divinerpgextended.boss.CommandDreBoss());
        event.registerServerCommand(new com.divinerpgextended.boss.CommandDreRank());
        event.registerServerCommand(new com.divinerpgextended.accessory.CommandDreInv());
        event.registerServerCommand(new com.divinerpgextended.debug.CommandDreDebug());
        // selftest - only when started with -Ddre.selftest=true (test server), not enabled in normal play
        if (com.divinerpgextended.selftest.SelfTest.enabled()) com.divinerpgextended.selftest.SelfTest.start();
        if (com.divinerpgextended.selftest.GenBench.enabled()) com.divinerpgextended.selftest.GenBench.start();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        // own melee enchantments (only from the corrupt altar): after every mod's init and postInit, so a later one never meets a taken ID
        com.divinerpgextended.perk.DreEnchants.register();
    }
}
