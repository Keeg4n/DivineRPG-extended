package com.divinerpgextended.halite;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.items.TwilightItemsArmor;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.TwilightItemsWeapons;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.RecipeSorter;

import com.divinerpgextended.halite.Halite.Part;
import com.divinerpgextended.halite.Halite.Spark;
import com.divinerpgextended.halite.Halite.Spirit;
import com.divinerpgextended.halite.Halite.Trophy;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.registry.GameRegistry;

/** Recipes of halite, spirits and the Nightmare Bed (call after Divine's recipes; Divine's old bed is removed before this). */
public final class HaliteRecipes {

    private HaliteRecipes() {}

    public static void register() {
        ItemStack I = Halite.part(Part.INGOT, 1), H = Halite.part(Part.HANDLE, 1);
        GameRegistry.addShapelessRecipe(Halite.part(Part.INGOT, 1), Halite.part(Part.SHARD, 1), Halite.part(Part.SHARD, 1), Halite.part(Part.SHARD, 1),
            Halite.part(Part.SHARD, 1), TwilightItemsOther.mortumSoul);
        GameRegistry.addRecipe(Halite.part(Part.HANDLE, 1), "I", "F", "I", 'I', I, 'F', TwilightItemsOther.mortumFragments);
        // armor: four pieces - four different bosses
        GameRegistry.addRecipe(new ItemStack(TwilightItemsArmor.haliteHelmet), "III", "ITI", 'I', I, 'T', Halite.trophy(Trophy.ARCHER));
        GameRegistry.addRecipe(new ItemStack(TwilightItemsArmor.haliteChestplate), "ITI", "III", "III", 'I', I, 'T', Halite.trophy(Trophy.TWINS));
        GameRegistry.addRecipe(new ItemStack(TwilightItemsArmor.haliteLeggings), "III", "ITI", "I I", 'I', I, 'T', Halite.trophy(Trophy.VAMACHERON));
        GameRegistry.addRecipe(new ItemStack(TwilightItemsArmor.haliteBoots), "I I", "ITI", 'I', I, 'T', Halite.trophy(Trophy.FIEND));
        // weapons
        GameRegistry.addRecipe(new ItemStack(TwilightItemsWeapons.haliteBlade), "IT", "I ", "H ", 'I', I, 'T', Halite.trophy(Trophy.TWINS), 'H', H);
        GameRegistry.addRecipe(new ItemStack(TwilightItemsWeapons.haliteBow), "IS ", "ITS", "IS ", 'I', I, 'S', Items.string, 'T', Halite.trophy(Trophy.ARCHER));
        GameRegistry.addRecipe(new ItemStack(TwilightItemsWeapons.halitePhaser), "III", "GT ", 'I', I, 'G', TwilightItemsOther.mortumGem, 'T',
            Halite.trophy(Trophy.DEMON));
        GameRegistry.addRecipe(new ItemStack(TwilightItemsWeapons.haliteBlitz), "III", "DT ", 'I', I, 'D', TwilightItemsOther.mortumDust, 'T',
            Halite.trophy(Trophy.DEMON));
        GameRegistry.addShapelessRecipe(new ItemStack(TwilightItemsWeapons.haliteSlicer, 4), I, I, Halite.trophy(Trophy.FIEND));
        // tools
        GameRegistry.addRecipe(new ItemStack(ModItems.halitePickaxe), "III", " H ", " T ", 'I', I, 'H', H, 'T', Halite.trophy(Trophy.VAMACHERON));
        GameRegistry.addRecipe(new ItemStack(ModItems.haliteAxe), "II", "IH", "T ", 'I', I, 'H', H, 'T', Halite.trophy(Trophy.VAMACHERON));
        GameRegistry.addRecipe(new ItemStack(ModItems.haliteShovel), "I", "H", "T", 'I', I, 'H', H, 'T', Halite.trophy(Trophy.VAMACHERON));
        // any wear: before 0.98 the tools broke, the old ones kept wear in their metadata
        int any = net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE;
        GameRegistry.addShapelessRecipe(new ItemStack(ModItems.haliteMultitool), new ItemStack(ModItems.halitePickaxe, 1, any), new ItemStack(ModItems.haliteAxe, 1, any),
            new ItemStack(ModItems.haliteShovel, 1, any), Halite.trophy(Trophy.KAROT));
        // spirits
        RecipeSorter.register("divinerpgextended:twilight_spirit", RecipeTwilightSpirit.class, RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new RecipeTwilightSpirit());
        GameRegistry.addShapelessRecipe(Halite.spirit(Spirit.ARCANA), Halite.spark(Spark.DRAMIX), Halite.spark(Spark.PARASECTA));
        GameRegistry.addShapelessRecipe(Halite.spirit(Spirit.WORLD), Halite.spark(Spark.ANCIENT_ENTITY), Halite.spark(Spark.JUNGLE));
        // Ancient Dramcryx totem: jungle gems (only with a halite pickaxe), shards and Divine's jungle stone
        GameRegistry.addRecipe(new ItemStack(ModItems.jungleTotem), "GSG", "SJS", "GSG", 'G', ModItems.jungleGem, 'S',
            net.divinerpg.utils.items.VanillaItemsOther.jungleShards, 'J', net.divinerpg.utils.items.VanillaItemsOther.jungleStone);
        GameRegistry.addShapelessRecipe(Halite.spirit(Spirit.NETHER), Halite.spark(Spark.SCORCHERS));
        GameRegistry.addShapelessRecipe(Halite.spirit(Spirit.END), Halite.spark(Spark.AYERACO));
        GameRegistry.addShapelessRecipe(Halite.spirit(Spirit.UNIVERSE), Halite.spirit(Spirit.TWILIGHT), Halite.spirit(Spirit.ARCANA), Halite.spirit(Spirit.WORLD),
            Halite.spirit(Spirit.NETHER), Halite.spirit(Spirit.END));
        // Nightmare Bed: the former blocks and Mortum logs + Spirit of the Universe
        GameRegistry.addRecipe(new ItemStack(VetheaItems.nightmareBed), "MSM", "WWW", 'M', TwilightBlocks.mortumBlock, 'S', Halite.spirit(Spirit.UNIVERSE), 'W',
            TwilightBlocks.mortumLogs);
    }
}
