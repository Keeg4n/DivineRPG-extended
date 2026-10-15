package com.divinerpgextended.world;

import java.util.Random;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.ItemsFood;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Сундуки в норах боссов. Нора Карцинуса - обычные сундуки: материалы на сет Kraken (чернильные мешки, кожа
 * кракена), крабовое и водное, реалмит, изредка молот из клешней и карта к снежной пещере. Ледяная пещера Глакона -
 * морозные сундуки Divine: ледяные камни и осколки (снежная кирка, сет Frozen), горячая еда против холода Iceika,
 * адские слитки, изредка части Frozen, морозное оружие и снежинки.
 */
public final class LairLoot {

    private LairLoot() {}

    private static WeightedRandomChestContent e(Item item, int meta, int min, int max, int weight) {
        return new WeightedRandomChestContent(new ItemStack(item, 1, meta), min, max, weight);
    }

    private static WeightedRandomChestContent e(Item item, int min, int max, int weight) {
        return e(item, 0, min, max, weight);
    }

    private static WeightedRandomChestContent e(Block block, int min, int max, int weight) {
        return e(Item.getItemFromBlock(block), 0, min, max, weight);
    }

    private static WeightedRandomChestContent[] crab() {
        return new WeightedRandomChestContent[] { e(Items.dye, 0, 4, 10, 12), // чернильные мешки - на сет Kraken
            e(VanillaItemsOther.krakenSkin, 1, 3, 6), e(VanillaItemsOther.krakenScale, 1, 3, 6),
            e(VanillaItemsOther.crabClaw, 2, 5, 10), e(VanillaItemsOther.aquaticPellets, 2, 6, 10),
            e(VanillaItemsOther.aquaticIngot, 1, 3, 6), e(VanillaItemsOther.sharkFin, 1, 2, 5),
            e(VanillaItemsOther.realmiteIngot, 2, 6, 8), e(Items.iron_ingot, 2, 5, 8), e(Items.gold_ingot, 1, 4, 5),
            e(Items.fish, 2, 5, 6), e(Items.diamond, 1, 2, 2), e(VanillaItemsOther.arlemiteIngot, 1, 2, 3),
            e(VanillaItemsWeapons.crabclawMaul, 1, 1, 1), e(ModItems.lairMap, 1, 1, 1, 2) };
    }

    private static WeightedRandomChestContent[] snow() {
        return new WeightedRandomChestContent[] { e(IceikaItems.iceShards, 2, 6, 10), e(IceikaItems.iceStone, 1, 3, 8),
            e(Items.snowball, 4, 12, 8), e(Blocks.packed_ice, 2, 6, 6), e(ItemsFood.tomato, 2, 5, 8),
            e(ItemsFood.hotPie, 1, 2, 4), e(VanillaItemsOther.arlemiteIngot, 1, 3, 5),
            e(VanillaItemsOther.rupeeIngot, 1, 2, 3), e(VanillaItemsOther.netheriteIngot, 1, 2, 3),
            e(Items.diamond, 1, 2, 2), e(VanillaItemsArmor.frozenHelmet, 1, 1, 1), e(VanillaItemsArmor.frozenBody, 1, 1, 1),
            e(VanillaItemsArmor.frozenLegs, 1, 1, 1), e(VanillaItemsArmor.frozenBoots, 1, 1, 1),
            e(VanillaItemsWeapons.frostSword, 1, 1, 2), e(VanillaItemsWeapons.frostCannon, 1, 1, 1),
            e(IceikaItems.snowflake, 1, 2, 2), e(ModItems.icePick, 1, 1, 1) };
    }

    /** Сундуки в домах жителей Iceika: еда против холода, топливо и огниво, сладости, немного материалов. */
    public static WeightedRandomChestContent[] village() {
        return new WeightedRandomChestContent[] { e(IceikaItems.eggNog, 1, 3, 8), e(IceikaItems.fruitCake, 1, 2, 6),
            e(IceikaItems.chocolateLog, 1, 4, 6), e(ItemsFood.hotPie, 1, 2, 4), e(ItemsFood.tomato, 2, 4, 6),
            e(Items.coal, 2, 6, 8), e(Blocks.torch, 4, 12, 6), e(Items.flint_and_steel, 1, 1, 3),
            e(IceikaBlocks.coalstone, 2, 6, 4), e(IceikaBlocks.candyCane, 2, 4, 3), e(IceikaBlocks.presentBox, 1, 1, 2),
            e(IceikaItems.iceShards, 1, 4, 5), e(IceikaItems.iceStone, 1, 2, 3), e(IceikaItems.snowflake, 1, 2, 3),
            e(ModItems.rimeIngot, 1, 2, 3), e(ModItems.lairMap, 2, 1, 1, 1) };
    }

    /** Сундуки в подземных данжах Iceika и в пещерах к ним: снежинки, иней, ледяное оружие, редкости. */
    public static WeightedRandomChestContent[] iceikaDungeon() {
        return new WeightedRandomChestContent[] { e(IceikaItems.snowflake, 1, 2, 5), e(ModItems.rimeIngot, 1, 3, 6),
            e(IceikaItems.iceStone, 2, 4, 6), e(IceikaItems.iceShards, 3, 8, 8), e(VanillaItemsOther.arlemiteIngot, 1, 3, 4),
            e(VanillaItemsOther.rupeeIngot, 1, 2, 3), e(Items.diamond, 1, 2, 3), e(Items.emerald, 1, 2, 2),
            e(Items.golden_apple, 1, 1, 1), e(IceikaItems.snowSlash, 1, 1, 1), e(IceikaItems.glacierSword, 1, 1, 1),
            e(IceikaItems.frossivence, 1, 1, 1), e(IceikaItems.icicleBow, 1, 1, 1), e(VanillaItemsArmor.frozenHelmet, 1, 1, 1),
            e(VanillaItemsArmor.frozenBody, 1, 1, 1), e(VanillaItemsArmor.frozenLegs, 1, 1, 1),
            e(VanillaItemsArmor.frozenBoots, 1, 1, 1), e(ModItems.rimePick, 1, 1, 1), e(ModItems.lairMap, 2, 1, 1, 2) };
    }

    /** Саркофаги Древней гробницы: золото и самоцветы, кости фараонов, редкое оружие и яблоки. */
    public static WeightedRandomChestContent[] tomb() {
        return new WeightedRandomChestContent[] { e(Items.gold_ingot, 2, 6, 10), e(Items.gold_nugget, 4, 12, 8),
            e(Items.dye, 4, 4, 10, 6), e(Items.emerald, 1, 3, 5), e(Items.diamond, 1, 2, 3), e(Items.bone, 2, 6, 6),
            e(Items.rotten_flesh, 2, 5, 4), e(VanillaItemsOther.rupeeIngot, 1, 2, 4), e(VanillaItemsOther.arlemiteIngot, 1, 3, 5),
            e(VanillaItemsOther.shadowCoins, 2, 6, 4), e(Items.golden_apple, 1, 1, 2), e(Items.golden_apple, 1, 1, 1, 1),
            e(VanillaItemsWeapons.sandslash, 1, 1, 2), e(Items.name_tag, 1, 1, 2), e(Items.saddle, 1, 1, 2),
            e(Items.golden_horse_armor, 1, 1, 1), e(ModItems.lairMap, 3, 1, 1, 1) };
    }

    /** Добавить в уже стоящий сундук count стопок из таблицы. */
    public static void fill(TileEntityChest te, Random rand, WeightedRandomChestContent[] table, int count) {
        WeightedRandomChestContent.generateChestContents(rand, table, te, count);
    }

    /** Поставить сундук block и заполнить 4-7 стопками из таблицы. */
    public static void chest(World w, Random rand, int x, int y, int z, Block block, WeightedRandomChestContent[] table) {
        w.setBlock(x, y, z, block, 0, 2);
        if (w.getTileEntity(x, y, z) instanceof TileEntityChest) {
            fill((TileEntityChest) w.getTileEntity(x, y, z), rand, table, 4 + rand.nextInt(4));
        }
    }

    /** Поставить сундук и заполнить: 5-8 стопок. snow - морозный сундук Divine, иначе обычный. */
    public static void chest(World w, Random rand, int x, int y, int z, boolean snow) {
        w.setBlock(x, y, z, snow ? IceikaBlocks.decorativeFrostedChest : Blocks.chest, 0, 2);
        if (!(w.getTileEntity(x, y, z) instanceof TileEntityChest)) return;
        TileEntityChest te = (TileEntityChest) w.getTileEntity(x, y, z);
        WeightedRandomChestContent.generateChestContents(rand, snow ? snow() : crab(), te, 5 + rand.nextInt(4));
    }
}
