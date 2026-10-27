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
    public static Block glaconAltar, lairSpawner;
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
    public static Block arenaAltar;
    public static Block jungleOre, jungleAltar;
    public static Block trapFloor;
    /** Факелы сумеречных миров по номеру TwilightWorld. */
    public static final Block[] worldTorch = new Block[5];
    /** Камни врат по номеру CadillionWorld: рамка портала в следующий мир. */
    public static final Block[] gateStone = new Block[4];
    public static Block windTotem;
    public static Block cloud;
    public static Block skyRail, apalachiaRail, skyBoostRail, lethe, letheFlowing;
    public static Block dreamCarrots, dreamMelon, dreamMelonStem, netherTorch, dreamWater, iceikaTorch, vetheaTorch, lureTorch, lureTorchLit, arcaneConcentrate;
    public static net.minecraftforge.fluids.Fluid arcaneFluid;
    public static net.minecraftforge.fluids.Fluid dreamWaterFluid;
    public static net.minecraftforge.fluids.Fluid letheFluid;
    /** Лампы сумеречных миров по номеру TwilightWorld; [EDEN] пусто - у Эдема лампа Divine. */
    public static final Block[] worldLamp = new Block[5];
    /** Доски дерева Ветеи (по {@link com.divinerpgextended.block.BlockVetheaPlanks.Wood}). */
    public static final Block[] vetheaPlanks = new Block[4];
    private static final int[] TORCH_COLOR = { 0xFFD23F, 0x7FC4E6, 0xFF4FD8, 0xA8DCFF, 0xE01010 }; // искры под эмблемы: солнце, луна, разлом, небо, кровь

    private ModBlocks() {}

    public static void register() {
        wandererAnvil = new BlockWandererAnvil();
        GameRegistry.registerBlock(wandererAnvil, "wandererAnvil");
        GameRegistry.registerTileEntity(TileWandererAnvil.class, "divinerpgextended:wandererAnvil");
        carcinusAltar = new BlockBossAltar(BlockBossAltar.CARCINUS);
        GameRegistry.registerBlock(carcinusAltar, "carcinusAltar");
        glaconAltar = new BlockBossAltar(BlockBossAltar.SNOW_GLACON);
        GameRegistry.registerBlock(glaconAltar, "glaconAltar");
        lairSpawner = new com.divinerpgextended.block.BlockLairSpawner();
        GameRegistry.registerBlock(lairSpawner, "lairSpawner");
        GameRegistry.registerTileEntity(com.divinerpgextended.block.BlockLairSpawner.Tile.class, "divinerpgextended:lairSpawner");
        burrow = new BlockBurrow();
        GameRegistry.registerBlock(burrow, "burrow");
        bubbleCurtain = new BlockBubbleCurtain();
        GameRegistry.registerBlock(bubbleCurtain, "bubbleCurtain");
        carcinusHeart = new BlockCarcinusHeart();
        GameRegistry.registerBlock(carcinusHeart, "carcinusHeart");
        GameRegistry.registerTileEntity(com.divinerpgextended.block.TileSkyRail.class, "divinerpgextended:skyRail");
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
        arenaAltar = new com.divinerpgextended.block.BlockArenaAltar();
        GameRegistry.registerBlock(arenaAltar, "arenaAltar");
        jungleOre = new com.divinerpgextended.block.BlockJungleOre();
        GameRegistry.registerBlock(jungleOre, "jungleOre");
        jungleAltar = new BlockBossAltar(BlockBossAltar.JUNGLE);
        GameRegistry.registerBlock(jungleAltar, "jungleAltar");
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
        for (com.divinerpgextended.block.BlockVetheaPlanks.Wood w : com.divinerpgextended.block.BlockVetheaPlanks.Wood.values()) {
            vetheaPlanks[w.ordinal()] = new com.divinerpgextended.block.BlockVetheaPlanks(w);
            GameRegistry.registerBlock(vetheaPlanks[w.ordinal()], "vetheaPlanks_" + w.key());
            net.minecraft.init.Blocks.fire.setFireInfo(vetheaPlanks[w.ordinal()], 5, 20); // горят как доски (после регистрации - нужен id)
            net.minecraftforge.oredict.OreDictionary.registerOre("plankWood", vetheaPlanks[w.ordinal()]);
            GameRegistry.addShapelessRecipe(new net.minecraft.item.ItemStack(vetheaPlanks[w.ordinal()], 4), w.log());
        }
        skyRail = new com.divinerpgextended.block.BlockSkyRail();
        GameRegistry.registerBlock(skyRail, "skyRail");
        apalachiaRail = new com.divinerpgextended.block.BlockApalachiaRail();
        GameRegistry.registerBlock(apalachiaRail, "apalachiaRail");
        skyBoostRail = new com.divinerpgextended.block.BlockSkyBoostRail();
        GameRegistry.registerBlock(skyBoostRail, "skyBoostRail");
        letheFluid = new net.minecraftforge.fluids.Fluid("dre_lethe").setLuminosity(4).setDensity(1100).setViscosity(1600);
        net.minecraftforge.fluids.FluidRegistry.registerFluid(letheFluid);
        letheFluid = net.minecraftforge.fluids.FluidRegistry.getFluid("dre_lethe");
        lethe = new com.divinerpgextended.block.BlockLethe(letheFluid, false);
        GameRegistry.registerBlock(lethe, "lethe");
        if (letheFluid.getBlock() == null) letheFluid.setBlock(lethe);
        letheFlowing = new com.divinerpgextended.block.BlockLethe(letheFluid, true); // из ведра - течёт
        GameRegistry.registerBlock(letheFlowing, "letheFlowing");
        ModItems.letheBucket = new com.divinerpgextended.item.ItemLetheBucket();
        GameRegistry.registerItem(ModItems.letheBucket, "letheBucket");
        net.minecraftforge.fluids.FluidContainerRegistry.registerFluidContainer(letheFluid, new net.minecraft.item.ItemStack(ModItems.letheBucket),
            new net.minecraft.item.ItemStack(net.minecraft.init.Items.bucket));
        netherTorch = new com.divinerpgextended.block.BlockWorldTorch(0xFF6A1A);
        GameRegistry.registerBlock(netherTorch, "worldTorch_nether");
        for (int coal = 0; coal < 2; coal++) // уголь или древесный уголь над стержнем ифрита (вместо палки)
            GameRegistry.addRecipe(new net.minecraft.item.ItemStack(netherTorch, 4), "C", "B", 'C', new net.minecraft.item.ItemStack(net.minecraft.init.Items.coal, 1, coal),
                'B', net.minecraft.init.Items.blaze_rod);
        // факелы Iceika (ледяные осколки на морозной палке) и Ветеи (серый жемчуг на палке): по 4
        iceikaTorch = new com.divinerpgextended.block.BlockWorldTorch("iceika", 0x9FE0FF);
        GameRegistry.registerBlock(iceikaTorch, "worldTorch_iceika");
        GameRegistry.addRecipe(new net.minecraft.item.ItemStack(iceikaTorch, 4), "C", "S", 'C', net.divinerpg.utils.items.IceikaItems.iceShards, 'S',
            com.divinerpgextended.world.twilight.TwilightCrafts.stick(com.divinerpgextended.world.twilight.WorldWood.FROZEN));
        vetheaTorch = new com.divinerpgextended.block.BlockWorldTorch("vethea", 0xB070FF);
        GameRegistry.registerBlock(vetheaTorch, "worldTorch_vethea");
        GameRegistry.addRecipe(new net.minecraft.item.ItemStack(vetheaTorch, 4), "C", "S", 'C', net.divinerpg.utils.items.VetheaItems.dirtyPearls, 'S',
            net.minecraft.init.Items.stick);
        // арканный концентрат: жидкость фонтанных залов Арканы
        arcaneFluid = new net.minecraftforge.fluids.Fluid("dre_arcane").setLuminosity(9);
        net.minecraftforge.fluids.FluidRegistry.registerFluid(arcaneFluid);
        arcaneFluid = net.minecraftforge.fluids.FluidRegistry.getFluid("dre_arcane");
        arcaneConcentrate = new com.divinerpgextended.block.BlockArcaneConcentrate(arcaneFluid);
        GameRegistry.registerBlock(arcaneConcentrate, "arcaneConcentrate");
        // водный факел зова: водный слиток над водным стержнем ифрита, 1 штука; горит на арканиуме
        lureTorch = new com.divinerpgextended.block.BlockLureTorch(false);
        GameRegistry.registerBlock(lureTorch, "lureTorch");
        lureTorchLit = new com.divinerpgextended.block.BlockLureTorch(true);
        GameRegistry.registerBlock(lureTorchLit, "lureTorchLit");
        GameRegistry.registerTileEntity(com.divinerpgextended.block.BlockLureTorch.Tile.class, "dreLureTorch");
        GameRegistry.addRecipe(new net.minecraft.item.ItemStack(lureTorch), "I", "R", 'I', net.divinerpg.utils.items.VanillaItemsOther.aquaticIngot, 'R',
            net.divinerpg.utils.items.VanillaItemsOther.aquaticBlazeRod);
        dreamCarrots = new com.divinerpgextended.block.DreamCrops.Carrot();
        GameRegistry.registerBlock(dreamCarrots, "dreamCarrots");
        dreamMelon = new com.divinerpgextended.block.DreamCrops.Melon();
        GameRegistry.registerBlock(dreamMelon, "dreamMelon");
        dreamMelonStem = new com.divinerpgextended.block.DreamCrops.Stem(dreamMelon);
        GameRegistry.registerBlock(dreamMelonStem, "dreamMelonStem");
        ModItems.dreamMelonSeeds = new net.minecraft.item.ItemSeeds(dreamMelonStem, net.minecraft.init.Blocks.farmland).setUnlocalizedName("dre.dreamMelonSeeds")
            .setTextureName("divinerpgextended:dream_melon_seeds");
        GameRegistry.registerItem(ModItems.dreamMelonSeeds, "dreamMelonSeeds");
        GameRegistry.addShapelessRecipe(new net.minecraft.item.ItemStack(ModItems.dreamMelonSeeds), net.divinerpg.utils.items.VetheaItems.dreamMelon);
        GameRegistry.addRecipe(new net.minecraft.item.ItemStack(dreamMelon), "MMM", "MMM", "MMM", 'M', net.divinerpg.utils.items.VetheaItems.dreamMelon);
        // вода мечтаний: плотность меньше нуля - течёт вверх
        dreamWaterFluid = new net.minecraftforge.fluids.Fluid("dre_dreamwater").setLuminosity(6).setDensity(-1000).setViscosity(1000);
        net.minecraftforge.fluids.FluidRegistry.registerFluid(dreamWaterFluid);
        dreamWaterFluid = net.minecraftforge.fluids.FluidRegistry.getFluid("dre_dreamwater");
        dreamWater = new com.divinerpgextended.block.BlockDreamWater(dreamWaterFluid);
        GameRegistry.registerBlock(dreamWater, "dreamWater");
        if (dreamWaterFluid.getBlock() == null) dreamWaterFluid.setBlock(dreamWater);
        ModItems.dreamWaterBucket = new net.minecraft.item.ItemBucket(dreamWater).setContainerItem(net.minecraft.init.Items.bucket).setMaxStackSize(1)
            .setUnlocalizedName("dre.dreamWaterBucket").setTextureName("divinerpgextended:dreamwater_bucket").setCreativeTab(com.divinerpgextended.DreTab.TAB);
        GameRegistry.registerItem(ModItems.dreamWaterBucket, "dreamWaterBucket");
        net.minecraftforge.fluids.FluidContainerRegistry.registerFluidContainer(dreamWaterFluid, new net.minecraft.item.ItemStack(ModItems.dreamWaterBucket),
            new net.minecraft.item.ItemStack(net.minecraft.init.Items.bucket));
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
