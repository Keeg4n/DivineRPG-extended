package com.divinerpgextended;

import java.util.Iterator;

import net.divinerpg.utils.blocks.VanillaBlocks;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.RecipeSorter;

import com.divinerpgextended.cold.Blizzard;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.boss.BossDamage;
import com.divinerpgextended.boss.BossLoot;
import com.divinerpgextended.boss.ProgressSpawns;
import com.divinerpgextended.boss.BossTracker;
import com.divinerpgextended.item.ClockUpgradeRecipe;
import com.divinerpgextended.item.ItemIcePick;
import com.divinerpgextended.item.ItemWandererClock;
import com.divinerpgextended.perk.ColdMobs;
import com.divinerpgextended.perk.IceikaCombat;
import com.divinerpgextended.perk.PerkEffects;
import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.station.GuiHandler;
import com.divinerpgextended.world.CadillionWorld;
import com.divinerpgextended.world.IceikaDecor;
import com.divinerpgextended.world.WorldGenThrone;
import com.divinerpgextended.world.WorldGenLairs;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        com.divinerpgextended.world.ScatterGen.register();
        Config.load(event.getSuggestedConfigurationFile());
        ModItems.register();
        ModBlocks.register();
        ColdHandler.init();
        com.divinerpgextended.arcana.MerchantMessages.register();
        com.divinerpgextended.accessory.AccessoryMessages.register();
        com.divinerpgextended.boss.ProgressSync.register();
        com.divinerpgextended.trinket.DoubleJump.register();
        com.divinerpgextended.boss.EnderGear.register();
        com.divinerpgextended.perk.RangedRoles.registerMessages();
        com.divinerpgextended.perk.MeleeRoles.registerMessages();
        com.divinerpgextended.perk.AmmoLoad.register();
        com.divinerpgextended.debug.Debug.register();
        com.divinerpgextended.perk.FrostBite.register(); // ice block of a frozen mob - to the clients
    }

    public void init(FMLInitializationEvent event) {
        net.minecraft.init.Items.snowball.setMaxStackSize(64);
        NetworkRegistry.INSTANCE.registerGuiHandler(DivineRPGExtended.instance, new GuiHandler());

        com.divinerpgextended.magic.ArcanaCombat.init();
        com.divinerpgextended.magic.ArcanaPrices.init();
        com.divinerpgextended.magic.ArcanaCombat arcanaCombat = new com.divinerpgextended.magic.ArcanaCombat();
        MinecraftForge.EVENT_BUS.register(arcanaCombat);
        FMLCommonHandler.instance().bus().register(arcanaCombat);
        FMLCommonHandler.instance().bus().register(new com.divinerpgextended.magic.ArcaneHoming());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.magic.ArcanaPrices());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.magic.ArcanePhases());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.magic.ArcaneSummons());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.magic.AnchorSummons()); // summon anchors: crab and shark
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.boss.AyeracoScales());
        com.divinerpgextended.perk.ArrowCleanup arrows = new com.divinerpgextended.perk.ArrowCleanup(); // arrows nobody can pick up vanish in 5 s
        MinecraftForge.EVENT_BUS.register(arrows);
        FMLCommonHandler.instance().bus().register(arrows);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.AquatoothEffects()); // toothed weapons, maul cooldown
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.FrostBite()); // frostbite from ice weapons
        com.divinerpgextended.perk.EarlyWeapons early = new com.divinerpgextended.perk.EarlyWeapons(); // Overworld weapons: roles, serenades, anvil
        MinecraftForge.EVENT_BUS.register(early);
        FMLCommonHandler.instance().bus().register(early);
        com.divinerpgextended.boss.AyeracoHorde horde = new com.divinerpgextended.boss.AyeracoHorde();
        MinecraftForge.EVENT_BUS.register(horde);
        FMLCommonHandler.instance().bus().register(horde);
        FMLCommonHandler.instance().bus().register(new com.divinerpgextended.boss.EnderGear());
        com.divinerpgextended.boss.AltarGuard altarGuard = new com.divinerpgextended.boss.AltarGuard();
        MinecraftForge.EVENT_BUS.register(altarGuard);
        FMLCommonHandler.instance().bus().register(altarGuard);
        com.divinerpgextended.magic.ArcaneEffects arcaneEffects = new com.divinerpgextended.magic.ArcaneEffects();
        MinecraftForge.EVENT_BUS.register(arcaneEffects);
        FMLCommonHandler.instance().bus().register(arcaneEffects);

        PerkEffects perks = new PerkEffects();
        MinecraftForge.EVENT_BUS.register(perks);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.Satiety());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.Scythe());
        FMLCommonHandler.instance()
            .bus()
            .register(perks);
        MinecraftForge.EVENT_BUS.register(new BossTracker());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.boss.LairSummons());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.HomeArmor());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.MiningTiers());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.SmeltNoFortune());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.CorruptedGear());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.SetPerks());
        com.divinerpgextended.world.twilight.SkythernWinds skythernWinds = new com.divinerpgextended.world.twilight.SkythernWinds();
        MinecraftForge.EVENT_BUS.register(skythernWinds);
        FMLCommonHandler.instance().bus().register(skythernWinds);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.world.twilight.MortumAfterlife());
        com.divinerpgextended.entity.mini.MiniBosses miniBosses = new com.divinerpgextended.entity.mini.MiniBosses();
        MinecraftForge.EVENT_BUS.register(miniBosses);
        FMLCommonHandler.instance().bus().register(miniBosses);
        com.divinerpgextended.world.twilight.WorldWeather.Events worldWeather = new com.divinerpgextended.world.twilight.WorldWeather.Events();
        MinecraftForge.EVENT_BUS.register(worldWeather);
        FMLCommonHandler.instance().bus().register(worldWeather);
        FMLCommonHandler.instance().bus().register(new com.divinerpgextended.world.twilight.EdenSun());
        com.divinerpgextended.world.twilight.SeaWeapons seaWeapons = new com.divinerpgextended.world.twilight.SeaWeapons();
        MinecraftForge.EVENT_BUS.register(seaWeapons);
        FMLCommonHandler.instance().bus().register(seaWeapons);
        com.divinerpgextended.boss.MortumBosses mortumBosses = new com.divinerpgextended.boss.MortumBosses();
        MinecraftForge.EVENT_BUS.register(mortumBosses);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(mortumBosses);
        com.divinerpgextended.boss.MortumArenaBosses arenaBosses = new com.divinerpgextended.boss.MortumArenaBosses();
        MinecraftForge.EVENT_BUS.register(arenaBosses);
        com.divinerpgextended.world.twilight.MortumWorld mortum = new com.divinerpgextended.world.twilight.MortumWorld();
        MinecraftForge.EVENT_BUS.register(mortum);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(mortum);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(arenaBosses);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.vethea.HiveTimer());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.boss.ArenaCalm());
        com.divinerpgextended.perk.RangedRoles ranged = new com.divinerpgextended.perk.RangedRoles();
        MinecraftForge.EVENT_BUS.register(ranged);
        com.divinerpgextended.perk.MeleeRoles meleeRoles = new com.divinerpgextended.perk.MeleeRoles();
        MinecraftForge.EVENT_BUS.register(meleeRoles);
        FMLCommonHandler.instance().bus().register(meleeRoles);
        com.divinerpgextended.world.twilight.TwilightCrafts.RailDrops rails = new com.divinerpgextended.world.twilight.TwilightCrafts.RailDrops();
        MinecraftForge.EVENT_BUS.register(rails);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.EnderGear());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.portal.PortalFrames.Drops());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.item.ItemLetheBucket.Fill());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.item.RecipeWandererDye.Wash());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.boss.TorchWards());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.item.ItemLairMap.Unfold());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.item.ExplorerMaps.Marks());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.boss.BossNoFly());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.dummy.EntityTrainingDummy.Pickup());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.item.ItemPetWhistle.Events());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.world.LairDomes.NoSpawns());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.world.WorldGenOuterLairs.NoSpawns());
        com.divinerpgextended.boss.WatcherHunt watcherHunt = new com.divinerpgextended.boss.WatcherHunt();
        MinecraftForge.EVENT_BUS.register(watcherHunt);
        FMLCommonHandler.instance().bus().register(watcherHunt);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.boss.KingRace());
        com.divinerpgextended.boss.NetherArsenal arsenal = new com.divinerpgextended.boss.NetherArsenal();
        MinecraftForge.EVENT_BUS.register(arsenal);
        FMLCommonHandler.instance().bus().register(arsenal);
        com.divinerpgextended.easter.Easter.enabled = com.divinerpgextended.Config.easterEggs;
        com.divinerpgextended.easter.Easter.registerRecipes();
        com.divinerpgextended.easter.Easter.registerMessages();
        com.divinerpgextended.easter.Easter.Events easter = new com.divinerpgextended.easter.Easter.Events();
        MinecraftForge.EVENT_BUS.register(easter);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(easter);
        com.divinerpgextended.boss.BossArena arena = new com.divinerpgextended.boss.BossArena();
        MinecraftForge.EVENT_BUS.register(arena);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(arena);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.ColorGear());
        FMLCommonHandler.instance().bus().register(rails);
        FMLCommonHandler.instance().bus().register(ranged);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.TwilightDrops());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.WholeGear());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.boss.BossMinions());
        com.divinerpgextended.block.BlockSkyRail.Adopt railAdopt = new com.divinerpgextended.block.BlockSkyRail.Adopt();
        MinecraftForge.EVENT_BUS.register(railAdopt);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(railAdopt);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.world.twilight.MoonWolfHealth());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.block.BlockDreamWater.Swim());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.block.BlockDreamWater.Loose());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.block.BlockBurrow.Convert());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.block.DreamCrops.Planting());
        com.divinerpgextended.perk.HomingArrows homing = new com.divinerpgextended.perk.HomingArrows();
        MinecraftForge.EVENT_BUS.register(homing);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(homing);
        com.divinerpgextended.world.twilight.Campfire campfire = new com.divinerpgextended.world.twilight.Campfire();
        MinecraftForge.EVENT_BUS.register(campfire);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(campfire);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.world.PopulationResend.Installer());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.world.SkyRepair());
        MinecraftForge.TERRAIN_GEN_BUS.register(new com.divinerpgextended.world.ScatterGen.Init());
        MinecraftForge.TERRAIN_GEN_BUS.register(new com.divinerpgextended.world.VanillaClearance());
        MinecraftForge.EVENT_BUS.register(new BossDamage());
        MinecraftForge.EVENT_BUS.register(new BossLoot());
        MinecraftForge.EVENT_BUS.register(new ProgressSpawns());
        com.divinerpgextended.boss.WorldGates worldGates = new com.divinerpgextended.boss.WorldGates();
        MinecraftForge.EVENT_BUS.register(worldGates);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.halite.HaliteDrops());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.block.BlockJungleOre.Handler());
        FMLCommonHandler.instance()
            .bus()
            .register(worldGates);
        MinecraftForge.EVENT_BUS.register(new ColdMobs());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.perk.ArmorSets());
        com.divinerpgextended.perk.OverHealth overHealth = new com.divinerpgextended.perk.OverHealth();
        MinecraftForge.EVENT_BUS.register(overHealth);
        FMLCommonHandler.instance()
            .bus()
            .register(overHealth);
        IceikaCombat iceikaCombat = new IceikaCombat();
        MinecraftForge.EVENT_BUS.register(iceikaCombat);
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.world.RimeSmelting());
        FMLCommonHandler.instance()
            .bus()
            .register(iceikaCombat);
        MinecraftForge.EVENT_BUS.register(new ItemIcePick.Handler());
        ColdHandler cold = new ColdHandler();
        MinecraftForge.EVENT_BUS.register(cold);
        FMLCommonHandler.instance()
            .bus()
            .register(cold);
        FMLCommonHandler.instance()
            .bus()
            .register(new Blizzard.Ticker());

        GameRegistry.addRecipe(new ItemStack(ModBlocks.wandererAnvil), "RRR", " A ", "III", 'R',
            VanillaBlocks.realmiteBlock, 'A', Blocks.anvil, 'I', Items.iron_ingot);

        // Wanderer's Clock I: like the Divine twilight clock, from 9 ordinary clocks
        GameRegistry.addRecipe(ItemWandererClock.withTier(ModItems.wandererClock, 1), "CCC", "CCC", "CCC", 'C',
            Items.clock);
        RecipeSorter.register("divinerpgextended:clock_upgrade", ClockUpgradeRecipe.class,
            RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new ClockUpgradeRecipe());
        RecipeSorter.register("divinerpgextended:temper", com.divinerpgextended.item.TemperRecipe.class,
            RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new com.divinerpgextended.item.TemperRecipe());
        RecipeSorter.register("divinerpgextended:wanderer_dye", com.divinerpgextended.item.RecipeWandererDye.class,
            RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new com.divinerpgextended.item.RecipeWandererDye());
        RecipeSorter.register("divinerpgextended:nether_reload", com.divinerpgextended.boss.NetherArsenal.Reload.class,
            RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new com.divinerpgextended.boss.NetherArsenal.Reload());

        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.fishing.FishingEvents());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.arcana.MerikForge());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.arcana.PetTraining());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.arcana.ItemPetCarrier.Handler());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.trinket.JackOMan());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.trinket.AccessorySources());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.bestiary.Bestiary());
        com.divinerpgextended.bestiary.Bestiary.registerMessages();
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.boss.ProgressSync());
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.trinket.DoubleJump());
        com.divinerpgextended.trinket.TrinketEffects trinkets = new com.divinerpgextended.trinket.TrinketEffects();
        MinecraftForge.EVENT_BUS.register(trinkets);
        com.divinerpgextended.perk.RelicEffects relics = new com.divinerpgextended.perk.RelicEffects(); // items from the world reliquaries
        MinecraftForge.EVENT_BUS.register(relics);
        FMLCommonHandler.instance().bus().register(relics);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.fishing.GoldenHearts()); // golden fish: +1 heart permanently
        FMLCommonHandler.instance().bus().register(new com.divinerpgextended.fishing.FishSchools()); // schools of fish
        FMLCommonHandler.instance()
            .bus()
            .register(trinkets);
        // Wanderer Anvil: leaving the server and death close the window - items from the slots are returned to the player
        com.divinerpgextended.station.AnvilWork.Events anvilWork = new com.divinerpgextended.station.AnvilWork.Events();
        MinecraftForge.EVENT_BUS.register(anvilWork);
        FMLCommonHandler.instance()
            .bus()
            .register(anvilWork);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.station.AltarLogic.Open());
        com.divinerpgextended.accessory.SocketGuard socketGuard = new com.divinerpgextended.accessory.SocketGuard();
        MinecraftForge.EVENT_BUS.register(socketGuard);
        FMLCommonHandler.instance()
            .bus()
            .register(socketGuard);
        com.divinerpgextended.arcana.FurnaceRoles furnaces = new com.divinerpgextended.arcana.FurnaceRoles();
        MinecraftForge.EVENT_BUS.register(furnaces);
        FMLCommonHandler.instance()
            .bus()
            .register(furnaces);
        com.divinerpgextended.arcana.Vatticus vatticus = new com.divinerpgextended.arcana.Vatticus();
        MinecraftForge.EVENT_BUS.register(vatticus);
        FMLCommonHandler.instance()
            .bus()
            .register(vatticus);
        com.divinerpgextended.arcana.WarGeneral general = new com.divinerpgextended.arcana.WarGeneral();
        MinecraftForge.EVENT_BUS.register(general);
        FMLCommonHandler.instance()
            .bus()
            .register(general);
        com.divinerpgextended.arcana.ArcanaMerchants merchants = new com.divinerpgextended.arcana.ArcanaMerchants();
        MinecraftForge.EVENT_BUS.register(merchants);
        FMLCommonHandler.instance()
            .bus()
            .register(merchants);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.fishing.ItemWormShovel.Handler());
        com.divinerpgextended.vethea.Nightmare nightmare = new com.divinerpgextended.vethea.Nightmare();
        MinecraftForge.EVENT_BUS.register(nightmare);
        FMLCommonHandler.instance()
            .bus()
            .register(nightmare);
        com.divinerpgextended.vethea.NightmareHands hands = new com.divinerpgextended.vethea.NightmareHands(); // other people's stuff on right click and area effects during sleep
        MinecraftForge.EVENT_BUS.register(hands);
        FMLCommonHandler.instance()
            .bus()
            .register(hands);
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.vethea.NightmareBaubles()); // other people's accessories during sleep
        com.divinerpgextended.vethea.VetheaFloors floors = new com.divinerpgextended.vethea.VetheaFloors();
        MinecraftForge.EVENT_BUS.register(floors);
        FMLCommonHandler.instance()
            .bus()
            .register(floors);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.vethea.VetheaCombat());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.world.DivineSpawns());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.item.ItemArcanePick.Handler());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.vethea.LunaFight());
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.vethea.VetheaStash());
        com.divinerpgextended.debug.DebugEvents debug = new com.divinerpgextended.debug.DebugEvents();
        MinecraftForge.EVENT_BUS.register(debug);
        com.divinerpgextended.debug.DebugWatch watch = new com.divinerpgextended.debug.DebugWatch();
        MinecraftForge.EVENT_BUS.register(watch);
        FMLCommonHandler.instance()
            .bus()
            .register(watch);
        FMLCommonHandler.instance()
            .bus()
            .register(debug);
        fishingRecipes();
        DreAchievements.register();
        DreAchievements.Handler achievements = new DreAchievements.Handler();
        MinecraftForge.EVENT_BUS.register(achievements);
        FMLCommonHandler.instance()
            .bus()
            .register(achievements);

        GameRegistry.registerWorldGenerator(new WorldGenLairs(), 50);

        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.WorldGenJungleLair(), 50);
        GameRegistry.registerWorldGenerator(new IceikaDecor(), 100);
        MinecraftForge.EVENT_BUS.register(new IceikaDecor.Events());
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.vethea.VetheaLakes(), 100);
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.ArcanaFountain(), 100);
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.IceikaXmasTree(), 101);
        GameRegistry.registerWorldGenerator(new WorldGenThrone(), 60);
        GameRegistry.registerWorldGenerator(new WorldGenThrone.Seal(), 100000); // last, after the generators of other mods
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.LairDomes(), 99000); // log domes - on top of chunk population
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new WorldGenThrone.Relight());
        MinecraftForge.EVENT_BUS.register(new WorldGenThrone.NoSpawns());
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.twilight.WorldGenNests(), 62);
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.twilight.WorldGenTwilightFeatures(), 64);
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.WorldGenOuterLairs(), 99000); // Nether and End lairs - after lakes and other things from other mods
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.ArcanaDoors(), 70);
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.ArcanaChests(), 110); // after the fountain halls: the hall rebuilds the room
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.twilight.Reliquaries.Ships(), 66); // Skythern ships with the Skythern reliquary
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.twilight.CloudIslands(), 67); // cloud islands with a pond (fishing in the clouds)
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.WorldGenTomb(), 55);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.boss.ClockGuard());
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.world.WorldGenTomb.Ticker());

        // Lair maps: paper surrounded by the biome's materials
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, 0), "CCC", "CPC", "CCC", 'C',
            VanillaItemsOther.crabClaw, 'P', Items.paper);
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, 1), "SIS", "IPI", "SIS", 'S', Items.snowball, 'I',
            Blocks.packed_ice, 'P', Items.paper);

        // Rime ore smelts into an ingot only in the Iceika coal furnace (the vanilla furnace is blocked by RimeSmelting); Rime Pickaxe
        GameRegistry.addSmelting(ModBlocks.rimeOre, new ItemStack(ModItems.rimeIngot), 1.0F);
        GameRegistry.addRecipe(new ItemStack(ModItems.rimePick), "III", " S ", " S ", 'I', ModItems.rimeIngot, 'S',
            com.divinerpgextended.world.twilight.TwilightCrafts.stick(com.divinerpgextended.world.twilight.WorldWood.FROZEN));

        // Suspicious Sandstone (tomb trap): smooth sandstone with a helmet
        GameRegistry.addRecipe(new ItemStack(ModBlocks.trapFloor, 5), "PPP", "P P", 'P', new ItemStack(Blocks.sandstone, 1, 2));

        // Map to the Jungle Temple (Ancient Dramcryx lair): paper surrounded by cocoa beans
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, com.divinerpgextended.item.ItemLairMap.JUNGLE), "SSS", "SPS", "SSS", 'S',
            new ItemStack(Items.dye, 1, 3), 'P', Items.paper);

        // Map to the Ancient Tomb: paper surrounded by chiseled sandstone
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, 3), "SSS", "SPS", "SSS", 'S', new ItemStack(Blocks.sandstone,
            1, 1), 'P', Items.paper);

        // Map to the Great Cadillion nest: paper surrounded by Eden shards; to the Demonic Circle - by Mortum shards
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, com.divinerpgextended.item.ItemLairMap.NEST), "SSS", "SPS", "SSS", 'S',
            net.divinerpg.utils.items.TwilightItemsOther.edenFragments, 'P', Items.paper);
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, com.divinerpgextended.item.ItemLairMap.CIRCLE), "SSS", "SPS", "SSS", 'S',
            net.divinerpg.utils.items.TwilightItemsOther.mortumFragments, 'P', Items.paper);

        // Maps to the Nether and End lairs: paper surrounded by nether brick (Watcher), blaze powder (Scorchers), end stone (Ayeraco)
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, com.divinerpgextended.item.ItemLairMap.WATCHER), "SSS", "SPS", "SSS", 'S',
            Items.netherbrick, 'P', Items.paper);
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, com.divinerpgextended.item.ItemLairMap.SCORCHERS), "SSS", "SPS", "SSS", 'S',
            Items.blaze_powder, 'P', Items.paper);
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, com.divinerpgextended.item.ItemLairMap.AYERACO), "SSS", "SPS", "SSS", 'S',
            Blocks.end_stone, 'P', Items.paper);

        // Frozen Throne Map: paper surrounded by ice shards
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, 2), "SSS", "SPS", "SSS", 'S', IceikaItems.iceShards,
            'P', Items.paper);

        // Ice Pick: ice stones (dropped by Frosts in the snow cave) on sticks
        GameRegistry.addRecipe(new ItemStack(ModItems.icePick), "III", " S ", " S ", 'I', IceikaItems.iceStone,
            'S', com.divinerpgextended.world.twilight.TwilightCrafts.stick(com.divinerpgextended.world.twilight.WorldWood.FROZEN));

        // Call of the Great Cadillion: world souls, world stone, a horn (for Skythern - an essence) from the world's mobs, and a sanctum seal
        // from the island dungeon - the boss cannot be reached without exploring the world
        for (CadillionWorld w : CadillionWorld.values()) {
            com.divinerpgextended.world.twilight.TwilightWorld tw = com.divinerpgextended.world.twilight.TwilightWorld.values()[w.ordinal()];
            GameRegistry.addRecipe(new ItemStack(ModItems.cadillionCall, 1, w.ordinal()), "SHS", "SGS", "SPS", 'S', w.soul(), 'G', w.gem(),
                'H', new ItemStack(ModItems.twilightMaterial, 1, com.divinerpgextended.world.twilight.TwilightMaterial.call(tw).ordinal()),
                'P', new ItemStack(ModItems.sanctumSeal, 1, w.ordinal()));
        }
    }

    /**
     * Fishing rods like Divine swords: the base ones from ingots, the twilight ones from pieces of their own world, and each next one from the previous one
     * (handle). The Eden one requires Leorna's Arcana fishing rod, then the chain goes up to Mortum; Jack, the Hunger and Golden are not craftable.
     */
    private static void fishingRecipes() {
        // worm shovel: iron shovel + string + bowl (worms drop only from it)
        GameRegistry.addShapelessRecipe(new ItemStack(ModItems.wormShovel), Items.iron_shovel, Items.string, Items.bowl);
        rodRecipe(com.divinerpgextended.fishing.Rod.REALMITE, VanillaItemsOther.realmiteIngot, Items.stick);
        rodRecipe(com.divinerpgextended.fishing.Rod.ARLEMITE, VanillaItemsOther.arlemiteIngot,
            rodOf(com.divinerpgextended.fishing.Rod.REALMITE));
        rodRecipe(com.divinerpgextended.fishing.Rod.RUPEE, VanillaItemsOther.rupeeIngot,
            rodOf(com.divinerpgextended.fishing.Rod.ARLEMITE));
        rodRecipe(com.divinerpgextended.fishing.Rod.EDEN, TwilightItemsOther.edenChunk,
            rodOf(com.divinerpgextended.fishing.Rod.ARCANA));
        rodRecipe(com.divinerpgextended.fishing.Rod.WILDWOOD, TwilightItemsOther.wildwoodChunk,
            rodOf(com.divinerpgextended.fishing.Rod.EDEN));
        rodRecipe(com.divinerpgextended.fishing.Rod.APALACHIA, TwilightItemsOther.apalachiaChunk,
            rodOf(com.divinerpgextended.fishing.Rod.WILDWOOD));
        rodRecipe(com.divinerpgextended.fishing.Rod.SKYTHERN, TwilightItemsOther.skythernChunk,
            rodOf(com.divinerpgextended.fishing.Rod.APALACHIA));
        rodRecipe(com.divinerpgextended.fishing.Rod.MORTUM, TwilightItemsOther.mortumChunk,
            rodOf(com.divinerpgextended.fishing.Rod.SKYTHERN));
    }

    private static Item rodOf(com.divinerpgextended.fishing.Rod r) {
        return ModItems.rods[r.ordinal()];
    }

    private static void rodRecipe(com.divinerpgextended.fishing.Rod r, Item material, Item handle) {
        GameRegistry.addRecipe(new ItemStack(rodOf(r)), "  M", " MS", "H S", 'M', material, 'S', Items.string, 'H',
            handle);
    }

    /** The player of this client (null on the server). */
    public net.minecraft.entity.player.EntityPlayer clientPlayer() {
        return null;
    }

    public void postInit(FMLPostInitializationEvent event) {
        HatsCompat.apply();
        com.divinerpgextended.magic.PotionArcane.register(Config.arcanePotionIds); // after all mods: their effects are already in place
        com.divinerpgextended.world.twilight.TwilightClimate.apply();
        com.divinerpgextended.vethea.LunaFight.buffEverlight();
        com.divinerpgextended.item.ItemArcanePick.makeBreakable();
        com.divinerpgextended.perk.MiningGuard.guardOres();
        if (Config.replaceTwilightClock) removeRecipes(TwilightItemsOther.twilightClock);
        if (Config.snowGlobeOnlyFromBoss) removeRecipes(IceikaItems.snowGlobe);
        if (Config.snowflakesInSummons) {
            // Order: first Iceika, then Ancient Entity and Watcher - snowflakes were added to the summoners (bottom corners)
            removeRecipes(VanillaItemsOther.mysteriousClock);
            // Mysterious Clock (Ancient Entity summon) - from Frozen Throne Shards: Lich King first
            GameRegistry.addRecipe(new ItemStack(VanillaItemsOther.mysteriousClock), " H ", "SCS", "FSF", 'H',
                VanillaItemsOther.hellstoneIngot, 'S', VanillaItemsOther.corruptedStone, 'C', Items.clock, 'F',
                ModItems.throneShard);
            removeRecipes(VanillaItemsOther.callWatcher);
            GameRegistry.addRecipe(new ItemStack(VanillaItemsOther.callWatcher), " H ", "SES", "FSF", 'H',
                VanillaItemsOther.hellstoneIngot, 'S', VanillaItemsOther.moltenStone, 'E', VanillaItemsOther.watchingEye,
                'F', IceikaItems.snowflake);
        }
        // Arcana portal frames - only with the Eye of the Watcher (the blue flame stones of the King of Scorchers stay in the recipe)
        removeRecipes(Item.getItemFromBlock(net.divinerpg.utils.blocks.ArcanaBlocks.arcanaPortalFrame));
        GameRegistry.addRecipe(new ItemStack(net.divinerpg.utils.blocks.ArcanaBlocks.arcanaPortalFrame, 12), " c ", "iEi",
            " c ", 'c', VanillaItemsOther.bluefireStone, 'i', Blocks.iron_block, 'E', ModItems.watcherEye);
        // altar of doom - with the Eye of the Watcher in the center instead of dark stone: after the Watcher (its 56 levels are just right for a 50 enchant)
        if (Config.corruptAltar) {
            removeRecipes(Item.getItemFromBlock(net.divinerpg.utils.blocks.VanillaBlocks.altarOfCorruption));
            GameRegistry.addRecipe(new ItemStack(net.divinerpg.utils.blocks.VanillaBlocks.altarOfCorruption), "sss", "dEd", "ddd", 's',
                VanillaItemsOther.corruptedStone, 'd', net.divinerpg.utils.blocks.VanillaBlocks.darkStone, 'E', ModItems.watcherEye);
        }

        // summoners: the Scorcher Brand from Fury Fire, the Ayeraco Prism from the six Ender swords around an Ayeraco Scale
        GameRegistry.addRecipe(new ItemStack(ModItems.scorcherBrand), "BMB", "MFM", "BMB", 'B', Items.blaze_rod, 'M', Items.magma_cream, 'F',
            VanillaItemsOther.furyFire);
        GameRegistry.addRecipe(new ItemStack(ModItems.ayeracoPrism), "RYG", "BSP", "EAE", 'R', net.divinerpg.utils.items.VanillaItemsWeapons.redEnderSword,
            'Y', net.divinerpg.utils.items.VanillaItemsWeapons.yellowEnderSword, 'G', net.divinerpg.utils.items.VanillaItemsWeapons.greenEnderSword,
            'B', net.divinerpg.utils.items.VanillaItemsWeapons.blueEnderSword, 'P', net.divinerpg.utils.items.VanillaItemsWeapons.enderSword,
            'A', net.divinerpg.utils.items.VanillaItemsWeapons.grayEnderSword, 'S', ModItems.ayeracoScale, 'E', Items.ender_eye);

        com.divinerpgextended.world.twilight.TwilightCrafts.postInit();
        com.divinerpgextended.perk.AquatoothEffects.postInit(); // toothed weapon damage, summon anchors without recipes
        com.divinerpgextended.perk.EarlyWeapons.postInit(); // Serenade of Death from Serenade of Health, cyclops rod - summon
        com.divinerpgextended.perk.RangedRoles.setupDust();
        com.divinerpgextended.perk.ColorGear.setup();
        com.divinerpgextended.perk.MeleeRoles.setup();
        com.divinerpgextended.world.ForeignTweaks.apply(); // bedrock armor, Tinkers islands
        // Divine checks for updates with a network request in the server thread without a timeout when a player joins: no answer - the server hangs
        try {
            net.divinerpg.utils.config.ConfigurationHelper.class.getField("updateChecker").setBoolean(null, false);
        } catch (Exception ignored) {
            // no such setting - nothing to disable
        }
        // entrance to Vethea - the Nightmare Bed only with the Spirit of the Universe
        removeRecipes(net.divinerpg.utils.items.VetheaItems.nightmareBed);
        com.divinerpgextended.halite.HaliteRecipes.register();
        com.divinerpgextended.bestiary.Bestiary.build(); // chapters - when all mobs and spawns are already recorded

        com.divinerpgextended.vethea.VetheaStash.disableDivineSwap();
        if (Config.warnVetheaInventory && com.divinerpgextended.vethea.VetheaStash.divineSwap()) {
            DivineRPGExtended.LOG.warn(
                "Divine config has the separate Vethea inventory enabled (Vethea Inventory = true). "
                    + "It must be turned off for DivineRPG Extended progression.");
        }
    }

    /** Removes all crafting table recipes that produce this item. */
    private static void removeRecipes(net.minecraft.item.Item item) {
        int removed = 0;
        for (Iterator<?> it = CraftingManager.getInstance()
            .getRecipeList()
            .iterator(); it.hasNext();) {
            IRecipe r = (IRecipe) it.next();
            ItemStack out = r.getRecipeOutput();
            if (out != null && out.getItem() == item) {
                it.remove();
                removed++;
            }
        }
        DivineRPGExtended.LOG.info("Recipes removed: " + item.getUnlocalizedName() + ": " + removed);
    }
}
