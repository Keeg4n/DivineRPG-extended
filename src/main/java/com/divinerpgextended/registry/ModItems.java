package com.divinerpgextended.registry;

import net.minecraft.item.Item;

import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.entity.EntityCarcinus;
import com.divinerpgextended.entity.EntityFrostCharge;
import com.divinerpgextended.entity.EntityGreatCadillion;
import com.divinerpgextended.entity.EntitySnowGlacon;
import com.divinerpgextended.item.ItemCadillionCall;
import com.divinerpgextended.item.ItemDeepShell;
import com.divinerpgextended.item.ItemIcePick;
import com.divinerpgextended.item.ItemLairMap;
import com.divinerpgextended.entity.EntityLairCrab;
import com.divinerpgextended.item.ItemTimeGear;
import com.divinerpgextended.item.ItemTrophy;
import com.divinerpgextended.entity.EntityFrostColossus;
import com.divinerpgextended.entity.EntityIcicle;
import com.divinerpgextended.entity.EntityLichKing;
import com.divinerpgextended.item.ItemWandererArmor;
import com.divinerpgextended.item.ItemWandererClock;

import com.divinerpgextended.fishing.EntityDreHook;
import com.divinerpgextended.fishing.ItemCrate;
import com.divinerpgextended.fishing.ItemDreFish;
import com.divinerpgextended.fishing.ItemDreRod;
import com.divinerpgextended.fishing.Rod;

import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    /** Wanderer armor pieces: [0] helmet, [1] chestplate, [2] leggings, [3] boots (like armorType in Minecraft). */
    public static final Item[] wanderer = new Item[4];
    private static final String[] NAMES = { "wandererHelmet", "wandererChestplate", "wandererLeggings",
        "wandererBoots" };

    public static Item wandererClock;
    /** Lethe Bucket - registered together with Lethe ({@link ModBlocks#register}): the bucket needs the block at creation. */
    public static Item letheBucket;
    public static Item dreamMelonSeeds, dreamWaterBucket;
    public static Item bossEgg;
    public static Item timeGear;
    public static Item cadillionCall;
    public static Item deepShell;
    public static Item lairMap;
    public static Item twilightMaterial, sanctumSeal;
    public static Item icePick;
    public static Item rimePick;
    public static Item rimeIngot;
    public static Item watcherEye;
    public static Item scorcherBrand, ayeracoPrism, ayeracoScale;
    public static Item frozenHeart;
    public static Item throneShard;
    public static Item fish;
    public static Item crate;
    /** Fishing rods by index in {@link Rod}; [VANILLA] is empty - that is the vanilla rod. */
    public static final Item[] rods = new Item[Rod.values().length];
    public static Item worm;
    public static Item wormShovel;
    /** Angler set: [0] hat, [1] vest, [2] pants. */
    public static final Item[] angler = new Item[3];
    public static Item fishGuide;
    public static Item baitBag;
    public static Item nightmareDrill;
    public static Item memoryCrystal;
    public static Item furnaceModule;
    public static Item trinket;
    public static Item petCarrier;
    public static Item bestiary;
    public static Item jungleGem, jungleTotem;
    public static Item haliteMaterial, mortumTrophy, spiritSpark, worldSpirit, halitePickaxe, haliteAxe, haliteShovel, haliteMultitool;
    public static Item worldStick;
    public static Item guardianHeart;
    public static Item skyCart, apalachiaRails, arcanaRails, gondola;
    /** Addon Vethea items: wing templates, lunar shard ({@link com.divinerpgextended.vethea.DreamItem}). */
    public static Item dreamItem;
    /** Arcanite Pickaxe: breaks Arcana dungeon blocks and spawners; charged - 3x3. */
    public static Item arcanePick, arcanePickCharged;
    /** Spawn egg of the Wanderer. */
    public static Item wandererEgg;
    /** Mage staffs of the twilight worlds. */
    public static Item mageStaff;
    /** Arcane potions (from the Arcane Brewing Stand). */
    public static Item arcanePotion;
    /** Training Dummy. */
    public static Item trainingDummy, petWhistle;
    /** World reliquaries: world keys (meta - world) and items from reliquaries. */
    public static Item worldKey, stormBow, tomahawk, cloudStaff, soulHarvest;
    /** World fishing: Sun Lure (bait made of Eden sparks) and Wandering Soul (Mortum catch, charges Soul Harvest). */
    public static Item sunLure, wanderingSoul, cyclopsBait;
    /** Liopleurodon: the Tsunami bow (from the boss) and Bloody Bait (summon). */
    public static Item tsunami, bloodBait;

    private ModItems() {}

    public static void register() {
        for (int i = 0; i < 4; i++) {
            wanderer[i] = new ItemWandererArmor(i, NAMES[i]);
            GameRegistry.registerItem(wanderer[i], NAMES[i]);
        }
        wandererClock = new ItemWandererClock();
        GameRegistry.registerItem(wandererClock, "wandererClock");
        timeGear = new ItemTimeGear();
        GameRegistry.registerItem(timeGear, "timeGear");
        cadillionCall = new ItemCadillionCall();
        GameRegistry.registerItem(cadillionCall, "cadillionCall");
        deepShell = new ItemDeepShell();
        GameRegistry.registerItem(deepShell, "deepShell");
        lairMap = new ItemLairMap();
        GameRegistry.registerItem(lairMap, "lairMap");
        twilightMaterial = new com.divinerpgextended.item.ItemTwilightMaterial(false);
        GameRegistry.registerItem(twilightMaterial, "twilightMaterial");
        sanctumSeal = new com.divinerpgextended.item.ItemTwilightMaterial(true);
        GameRegistry.registerItem(sanctumSeal, "sanctumSeal");
        icePick = new ItemIcePick(false);
        GameRegistry.registerItem(icePick, "icePick");
        rimePick = new ItemIcePick(true);
        GameRegistry.registerItem(rimePick, "rimePick");
        rimeIngot = new ItemTrophy("dre.rimeIngot", "divinerpgextended:rimeIngot", "dre.tooltip.rime_ingot", false);
        GameRegistry.registerItem(rimeIngot, "rimeIngot");
        watcherEye = new ItemTrophy("dre.watcherEye", "divinerpgextended:watcherEye", "dre.tooltip.watcher_eye", true);
        GameRegistry.registerItem(watcherEye, "watcherEye");
        scorcherBrand = new Item().setUnlocalizedName("dre.scorcherBrand").setTextureName("divinerpgextended:scorcherBrand").setMaxStackSize(1)
            .setCreativeTab(com.divinerpgextended.DreTab.TAB).setFull3D();
        GameRegistry.registerItem(scorcherBrand, "scorcherBrand");
        ayeracoPrism = new Item().setUnlocalizedName("dre.ayeracoPrism").setTextureName("divinerpgextended:ayeracoPrism").setMaxStackSize(1)
            .setCreativeTab(com.divinerpgextended.DreTab.TAB).setFull3D();
        GameRegistry.registerItem(ayeracoPrism, "ayeracoPrism");
        ayeracoScale = new ItemTrophy("dre.ayeracoScale", "divinerpgextended:ayeracoScale", "dre.tooltip.ayeraco_scale", false);
        GameRegistry.registerItem(ayeracoScale, "ayeracoScale");
        frozenHeart = new ItemTrophy("dre.frozenHeart", "divinerpgextended:frozenHeart", "dre.tooltip.frozen_heart", true);
        GameRegistry.registerItem(frozenHeart, "frozenHeart");
        throneShard = new ItemTrophy("dre.throneShard", "divinerpgextended:throneShard", "dre.tooltip.throne_shard", false);
        GameRegistry.registerItem(throneShard, "throneShard");

        fish = new ItemDreFish();
        GameRegistry.registerItem(fish, "fish");
        crate = new ItemCrate();
        GameRegistry.registerItem(crate, "crate");
        for (Rod r : Rod.values()) {
            if (r == Rod.VANILLA) continue;
            rods[r.ordinal()] = new ItemDreRod(r);
            GameRegistry.registerItem(rods[r.ordinal()], "rod_" + r.key);
        }
        worm = new ItemTrophy("dre.worm", "divinerpgextended:worm", "dre.tooltip.worm", false);
        GameRegistry.registerItem(worm, "worm");
        wormShovel = new com.divinerpgextended.fishing.ItemWormShovel();
        GameRegistry.registerItem(wormShovel, "wormShovel");
        for (int i = 0; i < 3; i++) {
            angler[i] = new com.divinerpgextended.fishing.ItemAnglerArmor(i);
            GameRegistry.registerItem(angler[i], com.divinerpgextended.fishing.ItemAnglerArmor.name(i));
        }
        fishGuide = new com.divinerpgextended.fishing.ItemFishGuide();
        GameRegistry.registerItem(fishGuide, "fishGuide");
        baitBag = new ItemTrophy("dre.baitBag", "divinerpgextended:baitBag", "dre.tooltip.bait_bag", true).setMaxStackSize(1);
        GameRegistry.registerItem(baitBag, "baitBag");
        nightmareDrill = new com.divinerpgextended.fishing.ItemNightmareDrill();
        GameRegistry.registerItem(nightmareDrill, "nightmareDrill");
        memoryCrystal = new com.divinerpgextended.arcana.ItemMemoryCrystal();
        GameRegistry.registerItem(memoryCrystal, "memoryCrystal");
        furnaceModule = new com.divinerpgextended.arcana.ItemFurnaceModule();
        GameRegistry.registerItem(furnaceModule, "furnaceModule");
        trinket = new com.divinerpgextended.trinket.ItemTrinket();
        GameRegistry.registerItem(trinket, "trinket");
        bestiary = new com.divinerpgextended.bestiary.ItemBestiary();
        GameRegistry.registerItem(bestiary, "bestiary");
        jungleGem = new com.divinerpgextended.item.ItemTrophy("dre.jungleGem", "divinerpgextended:jungleGem", "dre.tooltip.jungle_gem", false);
        GameRegistry.registerItem(jungleGem, "jungleGem");
        jungleTotem = new com.divinerpgextended.item.ItemTrophy("dre.jungleTotem", "divinerpgextended:jungleTotem", "dre.tooltip.jungle_totem", true);
        GameRegistry.registerItem(jungleTotem, "jungleTotem");
        haliteMaterial = new com.divinerpgextended.halite.ItemMeta("haliteMaterial", keys(com.divinerpgextended.halite.Halite.Part.values()), 64, false);
        GameRegistry.registerItem(haliteMaterial, "haliteMaterial");
        mortumTrophy = new com.divinerpgextended.halite.ItemMeta("mortumTrophy", keys(com.divinerpgextended.halite.Halite.Trophy.values()), 16, true);
        GameRegistry.registerItem(mortumTrophy, "mortumTrophy");
        spiritSpark = new com.divinerpgextended.halite.ItemMeta("spiritSpark", keys(com.divinerpgextended.halite.Halite.Spark.values()), 16, true);
        GameRegistry.registerItem(spiritSpark, "spiritSpark");
        worldSpirit = new com.divinerpgextended.halite.ItemMeta("worldSpirit", keys(com.divinerpgextended.halite.Halite.Spirit.values()), 16, true);
        GameRegistry.registerItem(worldSpirit, "worldSpirit");
        halitePickaxe = new com.divinerpgextended.halite.HaliteTools.Pickaxe();
        GameRegistry.registerItem(halitePickaxe, "halitePickaxe");
        haliteAxe = new com.divinerpgextended.halite.HaliteTools.Axe();
        GameRegistry.registerItem(haliteAxe, "haliteAxe");
        haliteShovel = new com.divinerpgextended.halite.HaliteTools.Shovel();
        GameRegistry.registerItem(haliteShovel, "haliteShovel");
        haliteMultitool = new com.divinerpgextended.halite.HaliteTools.Multitool();
        GameRegistry.registerItem(haliteMultitool, "haliteMultitool");
        petCarrier = new com.divinerpgextended.arcana.ItemPetCarrier();
        GameRegistry.registerItem(petCarrier, "petCarrier");
        worldStick = new com.divinerpgextended.item.ItemWorldStick();
        GameRegistry.registerItem(worldStick, "worldStick");
        net.minecraftforge.oredict.OreDictionary.registerOre("stickWood", new net.minecraft.item.ItemStack(worldStick, 1, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE));
        guardianHeart = new com.divinerpgextended.item.ItemGuardianHeart();
        GameRegistry.registerItem(guardianHeart, "guardianHeart");
        skyCart = new com.divinerpgextended.item.ItemSkyCart();
        GameRegistry.registerItem(skyCart, "skyCart");
        apalachiaRails = new com.divinerpgextended.item.ItemApalachiaRails(false);
        GameRegistry.registerItem(apalachiaRails, "apalachiaRails");
        gondola = new com.divinerpgextended.item.ItemGondola();
        GameRegistry.registerItem(gondola, "gondola");
        arcanaRails = new com.divinerpgextended.item.ItemApalachiaRails(true);
        GameRegistry.registerItem(arcanaRails, "arcanaRails");
        dreamItem = new com.divinerpgextended.halite.ItemMeta("dreamItem", com.divinerpgextended.vethea.DreamItem.keys(), 64, false);
        GameRegistry.registerItem(dreamItem, "dreamItem");
        arcanePick = new com.divinerpgextended.item.ItemArcanePick(false);
        GameRegistry.registerItem(arcanePick, "arcanePick");
        arcanePickCharged = new com.divinerpgextended.item.ItemArcanePick(true);
        GameRegistry.registerItem(arcanePickCharged, "arcanePickCharged");
        wandererEgg = new com.divinerpgextended.item.ItemWandererEgg();
        GameRegistry.registerItem(wandererEgg, "wandererEgg");
        mageStaff = new com.divinerpgextended.magic.ItemMageStaff();
        GameRegistry.registerItem(mageStaff, "mageStaff");
        arcanePotion = new com.divinerpgextended.magic.ItemArcanePotion();
        GameRegistry.registerItem(arcanePotion, "arcanePotion");
        trainingDummy = new com.divinerpgextended.dummy.ItemTrainingDummy();
        GameRegistry.registerItem(trainingDummy, "trainingDummy");
        petWhistle = new com.divinerpgextended.item.ItemPetWhistle();
        GameRegistry.registerItem(petWhistle, "petWhistle");
        worldKey = new com.divinerpgextended.item.ItemWorldKey();
        GameRegistry.registerItem(worldKey, "worldKey");
        com.divinerpgextended.item.ItemWorldKey.recipes(worldKey);
        stormBow = new com.divinerpgextended.item.ItemStormBow();
        GameRegistry.registerItem(stormBow, "stormBow");
        tomahawk = new com.divinerpgextended.item.ItemTomahawk();
        GameRegistry.registerItem(tomahawk, "tomahawk");
        cloudStaff = new com.divinerpgextended.item.ItemCloudStaff();
        GameRegistry.registerItem(cloudStaff, "cloudStaff");
        soulHarvest = new com.divinerpgextended.item.ItemSoulHarvest();
        GameRegistry.registerItem(soulHarvest, "soulHarvest");
        sunLure = new Item().setUnlocalizedName("dre.sunLure").setTextureName("divinerpgextended:sun_lure").setCreativeTab(com.divinerpgextended.DreTab.TAB);
        GameRegistry.registerItem(sunLure, "sunLure");
        if (net.divinerpg.utils.items.TwilightItemsOther.edenSparkles != null) // 2 Eden sparks and a gold nugget - 2 lures
            GameRegistry.addShapelessRecipe(new net.minecraft.item.ItemStack(sunLure, 2), net.divinerpg.utils.items.TwilightItemsOther.edenSparkles,
                net.divinerpg.utils.items.TwilightItemsOther.edenSparkles, net.minecraft.init.Items.gold_nugget);
        cyclopsBait = new Item().setUnlocalizedName("dre.cyclopsBait").setTextureName("divinerpgextended:cyclops_bait").setCreativeTab(com.divinerpgextended.DreTab.TAB);
        GameRegistry.registerItem(cyclopsBait, "cyclopsBait");
        if (net.divinerpg.utils.items.VanillaItemsOther.cyclopsEyeShards != null) // cyclops eye shard - 2 eye baits
            GameRegistry.addShapelessRecipe(new net.minecraft.item.ItemStack(cyclopsBait, 2), net.divinerpg.utils.items.VanillaItemsOther.cyclopsEyeShards);
        wanderingSoul = new com.divinerpgextended.fishing.ItemWanderingSoul();
        GameRegistry.registerItem(wanderingSoul, "wanderingSoul");
        tsunami = new com.divinerpgextended.item.ItemTsunamiBow();
        GameRegistry.registerItem(tsunami, "tsunami");
        bloodBait = new ItemTrophy("dre.bloodBait", "divinerpgextended:blood_bait", "dre.tooltip.blood_bait", false);
        GameRegistry.registerItem(bloodBait, "bloodBait");
        if (net.divinerpg.utils.items.TwilightItemsOther.apalachiaChunk != null) // shark pup, raw beef and an Apalachia piece
            GameRegistry.addShapelessRecipe(new net.minecraft.item.ItemStack(bloodBait), new net.minecraft.item.ItemStack(fish, 1,
                com.divinerpgextended.fishing.Fish.SHARK_PUP.ordinal()), net.minecraft.init.Items.beef, net.divinerpg.utils.items.TwilightItemsOther.apalachiaChunk);
        // iron whistle on a string, gold mouthpiece
        GameRegistry.addShapelessRecipe(new net.minecraft.item.ItemStack(petWhistle), net.minecraft.init.Items.iron_ingot, net.minecraft.init.Items.gold_nugget,
            net.minecraft.init.Items.string);
        // pumpkin on a hay bale on a stick
        GameRegistry.addRecipe(new net.minecraft.item.ItemStack(trainingDummy), "P", "H", "S", 'P', net.minecraft.init.Blocks.pumpkin, 'H', net.minecraft.init.Blocks.hay_block, 'S',
            net.minecraft.init.Items.stick);

        EntityRegistry.registerModEntity(EntityGreatCadillion.class, "GreatCadillion", 0, DivineRPGExtended.instance,
            80, 3, true);
        EntityRegistry.registerModEntity(EntityCarcinus.class, "Carcinus", 1, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(EntitySnowGlacon.class, "SnowGlacon", 2, DivineRPGExtended.instance, 80, 3,
            true);
        EntityRegistry.registerModEntity(EntityFrostCharge.class, "FrostCharge", 3, DivineRPGExtended.instance, 64, 10,
            true);
        EntityRegistry.registerModEntity(EntityLairCrab.class, "LairCrab", 4, DivineRPGExtended.instance, 64, 3, true);
        EntityRegistry.registerModEntity(EntityLairCrab.King.class, "LairKingCrab", 5, DivineRPGExtended.instance, 64,
            3, true);
        EntityRegistry.registerModEntity(EntityFrostColossus.class, "FrostColossus", 6, DivineRPGExtended.instance,
            128, 3, true);
        EntityRegistry.registerModEntity(EntityLichKing.class, "LichKing", 7, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(EntityIcicle.class, "Icicle", 8, DivineRPGExtended.instance, 64, 10, true);
        EntityRegistry.registerModEntity(EntityDreHook.class, "DreHook", 9, DivineRPGExtended.instance, 64, 5, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityWanderer.class, "Wanderer", 10, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityCharon.class, "Charon", 16, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityGondola.class, "Gondola", 17, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityShadowBolt.class, "ShadowBolt", 18, DivineRPGExtended.instance, 64, 5, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityAncientGolem.class, "AncientGolem", 11, DivineRPGExtended.instance, 96, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityAncientDramcryx.class, "AncientDramcryx", 19, DivineRPGExtended.instance, 96, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityWarriorKing.class, "WarriorKing", 12, DivineRPGExtended.instance, 96, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityWindLord.class, "WindLord", 13, DivineRPGExtended.instance, 112, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityBoulder.class, "Boulder", 14, DivineRPGExtended.instance, 64, 5, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntitySkyCart.class, "SkyCart", 15, DivineRPGExtended.instance, 80, 1, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.dummy.EntityTrainingDummy.class, "TrainingDummy", 20, DivineRPGExtended.instance, 64, 20, false);
        EntityRegistry.registerModEntity(com.divinerpgextended.magic.EntityArcaneWraith.class, "ArcaneWraith", 21, DivineRPGExtended.instance, 64, 2, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.easter.EntitySeat.class, "SofaSeat", 22, DivineRPGExtended.instance, 64, 20, false);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityWatcherEye.class, "WatcherEye", 23, DivineRPGExtended.instance, 64, 2, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityLostSoul.class, "LostSoul", 24, DivineRPGExtended.instance, 64, 20, false);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityRockfall.class, "Rockfall", 25, DivineRPGExtended.instance, 64, 5, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntityNoonCori.class, "NoonCori", 26, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntityOldVerek.class, "OldVerek", 27, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntityStonebreaker.class, "Stonebreaker", 28, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntitySkyGrabber.class, "SkyGrabber", 29, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntityMourner.class, "Mourner", 30, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntitySleepwalker.class, "Sleepwalker", 31, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntityBlizzardSpruce.class, "BlizzardSpruce", 32, DivineRPGExtended.instance, 80, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntityBlizzardSnowman.class, "BlizzardSnowman", 33, DivineRPGExtended.instance, 64, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.mini.EntitySnowClump.class, "SnowClump", 34, DivineRPGExtended.instance, 64, 10, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityTomahawk.class, "Tomahawk", 35, DivineRPGExtended.instance, 64, 1, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityLiopleurodonBoss.class, "LioBoss", 36, DivineRPGExtended.instance, 96, 2, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.entity.EntityWaterJet.class, "WaterJet", 37, DivineRPGExtended.instance, 64, 2, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.magic.EntitySummonCrab.class, "SummonCrab", 38, DivineRPGExtended.instance, 64, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.magic.EntitySummonShark.class, "SummonShark", 39, DivineRPGExtended.instance, 64, 2, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.magic.EntitySummonSlime.class, "SummonSlime", 40, DivineRPGExtended.instance, 64, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.magic.EntitySummonEye.class, "SummonEye", 41, DivineRPGExtended.instance, 64, 2, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.magic.EntitySummonScorcher.class, "SummonScorcher", 42, DivineRPGExtended.instance, 64, 3, true);
        EntityRegistry.registerModEntity(com.divinerpgextended.magic.EntitySummonAyeraco.class, "SummonAyeraco", 43, DivineRPGExtended.instance, 64, 2, true);
        listForMinimap();
        bossEgg = new com.divinerpgextended.item.ItemBossEgg();
        GameRegistry.registerItem(bossEgg, "bossEgg");
    }

    private static String[] keys(Enum<?>[] v) {
        String[] k = new String[v.length];
        for (int i = 0; i < v.length; i++) k[i] = v[i].name().toLowerCase();
        return k;
    }

    /** Values in the name list for addon mobs: above 255, so as not to confuse them with real global ids (in 1.7.10 those go up to 255). */
    public static final int MINIMAP_ID_BASE = 1000;

    /**
     * Xaero's Minimap reads icon descriptions only for names from the global id list (EntityList.func_151515_b), and addon
     * mobs (registerModEntity) are not in it - their descriptions were not read, and the minimap showed a question mark. We append
     * our names to this list; besides Xaero it is used only by the /summon command hint.
     */
    @SuppressWarnings("unchecked")
    static void listForMinimap() {
        try {
            java.util.Map<String, Integer> ids = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.entity.EntityList.class, null, "stringToIDMapping",
                "field_75622_f");
            int n = 0;
            for (Object o : net.minecraft.entity.EntityList.classToStringMapping.entrySet()) {
                java.util.Map.Entry<Class<?>, String> e = (java.util.Map.Entry<Class<?>, String>) o;
                if (!e.getKey().getName().startsWith("com.divinerpgextended.") || !net.minecraft.entity.EntityLiving.class.isAssignableFrom(e.getKey())) continue;
                if (!ids.containsKey(e.getValue())) ids.put(e.getValue(), MINIMAP_ID_BASE + n++);
            }
        } catch (Throwable t) {
            DivineRPGExtended.LOG.warn("Minimap: addon mobs not added to the name list - " + t);
        }
    }
}
