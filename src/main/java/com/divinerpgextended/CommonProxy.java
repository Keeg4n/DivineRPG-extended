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
        Config.load(event.getSuggestedConfigurationFile());
        ModItems.register();
        ModBlocks.register();
        ColdHandler.init();
        com.divinerpgextended.arcana.MerchantMessages.register();
        com.divinerpgextended.accessory.AccessoryMessages.register();
        com.divinerpgextended.boss.ProgressSync.register();
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(DivineRPGExtended.instance, new GuiHandler());

        PerkEffects perks = new PerkEffects();
        MinecraftForge.EVENT_BUS.register(perks);
        FMLCommonHandler.instance()
            .bus()
            .register(perks);
        MinecraftForge.EVENT_BUS.register(new BossTracker());
        MinecraftForge.EVENT_BUS.register(new BossDamage());
        MinecraftForge.EVENT_BUS.register(new BossLoot());
        MinecraftForge.EVENT_BUS.register(new ProgressSpawns());
        com.divinerpgextended.boss.ArcanaGate arcanaGate = new com.divinerpgextended.boss.ArcanaGate();
        MinecraftForge.EVENT_BUS.register(arcanaGate);
        FMLCommonHandler.instance()
            .bus()
            .register(arcanaGate);
        MinecraftForge.EVENT_BUS.register(new ColdMobs());
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

        // Часы Странника I: как сумеречные часы Divine, из 9 обычных часов
        GameRegistry.addRecipe(ItemWandererClock.withTier(ModItems.wandererClock, 1), "CCC", "CCC", "CCC", 'C',
            Items.clock);
        RecipeSorter.register("divinerpgextended:clock_upgrade", ClockUpgradeRecipe.class,
            RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new ClockUpgradeRecipe());

        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.fishing.FishingEvents());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.arcana.MerikForge());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.arcana.PetTraining());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.arcana.ItemPetCarrier.Handler());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.trinket.JackOMan());
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.boss.ProgressSync());
        com.divinerpgextended.trinket.TrinketEffects trinkets = new com.divinerpgextended.trinket.TrinketEffects();
        MinecraftForge.EVENT_BUS.register(trinkets);
        FMLCommonHandler.instance()
            .bus()
            .register(trinkets);
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
        fishingRecipes();

        GameRegistry.registerWorldGenerator(new WorldGenLairs(), 50);
        GameRegistry.registerWorldGenerator(new IceikaDecor(), 100);
        GameRegistry.registerWorldGenerator(new WorldGenThrone(), 60);
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.ArcanaDoors(), 70);
        GameRegistry.registerWorldGenerator(new com.divinerpgextended.world.WorldGenTomb(), 55);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.boss.ClockGuard());
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.world.WorldGenTomb.Ticker());

        // Карты логов: бумага в окружении материалов биома
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, 0), "CCC", "CPC", "CCC", 'C',
            VanillaItemsOther.crabClaw, 'P', Items.paper);
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, 1), "SIS", "IPI", "SIS", 'S', Items.snowball, 'I',
            Blocks.packed_ice, 'P', Items.paper);

        // руда инея плавится в слиток только в угольной печи Iceika (ванильную печь глушит RimeSmelting); кирка инея
        GameRegistry.addSmelting(ModBlocks.rimeOre, new ItemStack(ModItems.rimeIngot), 1.0F);
        GameRegistry.addRecipe(new ItemStack(ModItems.rimePick), "III", " S ", " S ", 'I', ModItems.rimeIngot, 'S',
            Items.stick);

        // Карта к Древней гробнице: бумага в резном песчанике
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, 3), "SSS", "SPS", "SSS", 'S', new ItemStack(Blocks.sandstone,
            1, 1), 'P', Items.paper);

        // Карта Ледяного Трона: бумага в ледяных осколках
        GameRegistry.addRecipe(new ItemStack(ModItems.lairMap, 1, 2), "SSS", "SPS", "SSS", 'S', IceikaItems.iceShards,
            'P', Items.paper);

        // Ледоруб: ледяные камни (падают с Фростов в снежной пещере) на палках
        GameRegistry.addRecipe(new ItemStack(ModItems.icePick), "III", " S ", " S ", 'I', IceikaItems.iceStone,
            'S', Items.stick);

        // Зов великого кадиллиона: 8 душ мира вокруг камня мира
        for (CadillionWorld w : CadillionWorld.values()) {
            GameRegistry.addRecipe(new ItemStack(ModItems.cadillionCall, 1, w.ordinal()), "SSS", "SGS", "SSS", 'S',
                w.soul(), 'G', w.gem());
        }
    }

    /**
     * Удочки как мечи Divine: базовые из слитков, сумеречные из кусков своего мира, и каждая следующая из предыдущей
     * (рукоять). Эдемская требует арканную удочку Леорны, дальше цепочка до Мортума; Джек, Голод и Золотая - не крафт.
     */
    private static void fishingRecipes() {
        // червекопалка: железная лопата + нитка + миска (черви падают только с неё)
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

    public void postInit(FMLPostInitializationEvent event) {
        if (Config.replaceTwilightClock) removeRecipes(TwilightItemsOther.twilightClock);
        if (Config.snowGlobeOnlyFromBoss) removeRecipes(IceikaItems.snowGlobe);
        if (Config.snowflakesInSummons) {
            // Порядок: сначала Iceika, потом Ancient Entity и Watcher - в призывалки добавлены снежинки (нижние углы)
            removeRecipes(VanillaItemsOther.mysteriousClock);
            // Таинственные часы (призыв Ancient Entity) - из Осколков Ледяного Трона: сначала Король-лич
            GameRegistry.addRecipe(new ItemStack(VanillaItemsOther.mysteriousClock), " H ", "SCS", "FSF", 'H',
                VanillaItemsOther.hellstoneIngot, 'S', VanillaItemsOther.corruptedStone, 'C', Items.clock, 'F',
                ModItems.throneShard);
            removeRecipes(VanillaItemsOther.callWatcher);
            GameRegistry.addRecipe(new ItemStack(VanillaItemsOther.callWatcher), " H ", "SES", "FSF", 'H',
                VanillaItemsOther.hellstoneIngot, 'S', VanillaItemsOther.moltenStone, 'E', VanillaItemsOther.watchingEye,
                'F', IceikaItems.snowflake);
        }
        // рамки портала Арканы - только с Оком Смотрителя (камни синего огня Короля Скорчеров остаются в рецепте)
        removeRecipes(Item.getItemFromBlock(net.divinerpg.utils.blocks.ArcanaBlocks.arcanaPortalFrame));
        GameRegistry.addRecipe(new ItemStack(net.divinerpg.utils.blocks.ArcanaBlocks.arcanaPortalFrame, 12), " c ", "iEi",
            " c ", 'c', VanillaItemsOther.bluefireStone, 'i', Blocks.iron_block, 'E', ModItems.watcherEye);

        if (Config.warnVetheaInventory && net.divinerpg.utils.config.ConfigurationHelper.vetheaInventory) {
            DivineRPGExtended.LOG.warn(
                "В конфиге Divine включён отдельный инвентарь Vethea (Vethea Inventory = true). "
                    + "Для прогрессии DivineRPG Extended его нужно выключить.");
        }
    }

    /** Убирает все рецепты верстака, которые выдают этот предмет. */
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
        DivineRPGExtended.LOG.info("Убрано рецептов " + item.getUnlocalizedName() + ": " + removed);
    }
}
