package com.divinerpgextended;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.divinerpgextended.cold.CommandBlizzard;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
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
        event.registerServerCommand(new CommandBlizzard());
        event.registerServerCommand(new com.divinerpgextended.boss.CommandDreBoss());
        event.registerServerCommand(new com.divinerpgextended.accessory.CommandDreInv());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
