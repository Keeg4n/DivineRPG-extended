package com.divinerpgextended.registry;

import net.minecraft.block.Block;

import com.divinerpgextended.block.BlockBossAltar;
import com.divinerpgextended.block.BlockBrazier;
import com.divinerpgextended.block.BlockFrostIce;
import com.divinerpgextended.block.BlockBubbleCurtain;
import com.divinerpgextended.block.BlockBurrow;
import com.divinerpgextended.block.BlockCarcinusHeart;
import com.divinerpgextended.block.BlockHearth;
import com.divinerpgextended.block.BlockRimeOre;
import com.divinerpgextended.block.BlockSharkNest;
import com.divinerpgextended.block.BlockTrapFloor;
import com.divinerpgextended.station.BlockWandererAnvil;
import com.divinerpgextended.station.TileWandererAnvil;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlocks {

    public static Block wandererAnvil;
    public static Block carcinusAltar;
    public static Block glaconAltar;
    public static Block burrow;
    public static Block carcinusHeart;
    public static Block bubbleCurtain;
    public static Block sharkNest;
    public static Block hearth;
    public static Block frostIce;
    public static Block brazier;
    public static Block throneAltar;
    public static Block rimeOre;
    public static Block tombAltar;
    public static Block nestAltar;
    public static Block trapFloor;
    /** Факелы сумеречных миров по номеру TwilightWorld. */
    public static final Block[] worldTorch = new Block[5];
    /** Камни врат по номеру CadillionWorld: рамка портала в следующий мир. */
    public static final Block[] gateStone = new Block[4];
    public static Block windTotem;
    public static Block cloud;
    /** Лампы сумеречных миров по номеру TwilightWorld; [EDEN] пусто - у Эдема лампа Divine. */
    public static final Block[] worldLamp = new Block[5];
    private static final int[] TORCH_COLOR = { 0xFFD84A, 0x5AA8FF, 0xC070FF, 0xE8ECF4, 0xE04040 };

    private ModBlocks() {}

    public static void register() {
        wandererAnvil = new BlockWandererAnvil();
        GameRegistry.registerBlock(wandererAnvil, "wandererAnvil");
        GameRegistry.registerTileEntity(TileWandererAnvil.class, "divinerpgextended:wandererAnvil");
        carcinusAltar = new BlockBossAltar(BlockBossAltar.CARCINUS);
        GameRegistry.registerBlock(carcinusAltar, "carcinusAltar");
        glaconAltar = new BlockBossAltar(BlockBossAltar.SNOW_GLACON);
        GameRegistry.registerBlock(glaconAltar, "glaconAltar");
        burrow = new BlockBurrow();
        GameRegistry.registerBlock(burrow, "burrow");
        bubbleCurtain = new BlockBubbleCurtain();
        GameRegistry.registerBlock(bubbleCurtain, "bubbleCurtain");
        carcinusHeart = new BlockCarcinusHeart();
        GameRegistry.registerBlock(carcinusHeart, "carcinusHeart");
        GameRegistry.registerTileEntity(BlockCarcinusHeart.Tile.class, "divinerpgextended:carcinusHeart");
        sharkNest = new BlockSharkNest();
        GameRegistry.registerBlock(sharkNest, "sharkNest");
        GameRegistry.registerTileEntity(BlockSharkNest.Tile.class, "divinerpgextended:sharkNest");
        hearth = new BlockHearth();
        GameRegistry.registerBlock(hearth, "hearth");
        frostIce = new BlockFrostIce();
        GameRegistry.registerBlock(frostIce, "frostIce");
        brazier = new BlockBrazier();
        GameRegistry.registerBlock(brazier, "brazier");
        throneAltar = new BlockBossAltar(BlockBossAltar.THRONE);
        GameRegistry.registerBlock(throneAltar, "throneAltar");
        rimeOre = new BlockRimeOre();
        GameRegistry.registerBlock(rimeOre, "rimeOre");
        tombAltar = new BlockBossAltar(BlockBossAltar.TOMB);
        GameRegistry.registerBlock(tombAltar, "tombAltar");
        nestAltar = new BlockBossAltar(BlockBossAltar.NEST);
        GameRegistry.registerBlock(nestAltar, "nestAltar");
        trapFloor = new BlockTrapFloor();
        GameRegistry.registerBlock(trapFloor, "trapFloor");
        for (com.divinerpgextended.world.twilight.TwilightWorld w : com.divinerpgextended.world.twilight.TwilightWorld.values()) {
            worldTorch[w.ordinal()] = new com.divinerpgextended.block.BlockWorldTorch(w, TORCH_COLOR[w.ordinal()]);
            GameRegistry.registerBlock(worldTorch[w.ordinal()], "worldTorch_" + w.key());
        }
        for (com.divinerpgextended.world.twilight.TwilightWorld w : com.divinerpgextended.world.twilight.TwilightWorld.values()) {
            if (w == com.divinerpgextended.world.twilight.TwilightWorld.EDEN) continue;
            worldLamp[w.ordinal()] = new com.divinerpgextended.block.BlockWorldLamp(w);
            GameRegistry.registerBlock(worldLamp[w.ordinal()], "worldLamp_" + w.key());
        }
        cloud = new com.divinerpgextended.block.BlockCloud();
        GameRegistry.registerBlock(cloud, "cloud");
        windTotem = new com.divinerpgextended.block.BlockWindTotem();
        GameRegistry.registerBlock(windTotem, "windTotem");
        for (com.divinerpgextended.world.CadillionWorld w : com.divinerpgextended.world.CadillionWorld.values()) {
            gateStone[w.ordinal()] = new com.divinerpgextended.block.BlockGateStone(w);
            GameRegistry.registerBlock(gateStone[w.ordinal()], "gateStone_" + w.key());
        }
    }
}
