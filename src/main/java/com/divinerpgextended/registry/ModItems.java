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
    public static Item timeGear;
    public static Item cadillionCall;
    public static Item deepShell;
    public static Item lairMap;
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
        petCarrier = new com.divinerpgextended.arcana.ItemPetCarrier();
        GameRegistry.registerItem(petCarrier, "petCarrier");

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
    }
}
