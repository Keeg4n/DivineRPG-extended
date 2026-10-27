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

    /** Части брони Странника: [0] шлем, [1] нагрудник, [2] поножи, [3] ботинки (как armorType в Minecraft). */
    public static final Item[] wanderer = new Item[4];
    private static final String[] NAMES = { "wandererHelmet", "wandererChestplate", "wandererLeggings",
        "wandererBoots" };

    public static Item wandererClock;
    /** Ведро Леты - регистрируется вместе с Летой ({@link ModBlocks#register}): блок нужен ведру при создании. */
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
    public static Item frozenHeart;
    public static Item throneShard;
    public static Item fish;
    public static Item crate;
    /** Удочки по номеру в {@link Rod}; [VANILLA] пусто - это ванильная удочка. */
    public static final Item[] rods = new Item[Rod.values().length];
    public static Item worm;
    public static Item wormShovel;
    /** Набор рыбака: [0] шляпа, [1] жилет, [2] штаны. */
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
    /** Вещи Ветеи аддона: шаблоны крыльев, лунный осколок ({@link com.divinerpgextended.vethea.DreamItem}). */
    public static Item dreamItem;
    /** Арканитовая кирка: ломает спавнеры Арканы. */
    public static Item arcanePick;

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
        arcanePick = new com.divinerpgextended.item.ItemArcanePick();
        GameRegistry.registerItem(arcanePick, "arcanePick");

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
        bossEgg = new com.divinerpgextended.item.ItemBossEgg();
        GameRegistry.registerItem(bossEgg, "bossEgg");
    }

    private static String[] keys(Enum<?>[] v) {
        String[] k = new String[v.length];
        for (int i = 0; i < v.length; i++) k[i] = v[i].name().toLowerCase();
        return k;
    }
}
