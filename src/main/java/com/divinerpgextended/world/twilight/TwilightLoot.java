package com.divinerpgextended.world.twilight;

import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

import com.divinerpgextended.item.ItemLairMap;
import com.divinerpgextended.registry.ModItems;

/**
 * Chests of twilight dimension structures. Ruins - what is needed on the spot: souls and shards of the world (Call of the Cadillion, maps),
 * world stones, food, occasionally the Call itself and a map to the lair. Dungeons in islands are richer: more stones and souls, world pieces,
 * golden apples, diamonds.
 */
public final class TwilightLoot {

    private TwilightLoot() {}

    private static WeightedRandomChestContent e(Item item, int meta, int min, int max, int weight) {
        return new WeightedRandomChestContent(new ItemStack(item, 1, meta), min, max, weight);
    }

    private static WeightedRandomChestContent e(Item item, int min, int max, int weight) {
        return e(item, 0, min, max, weight);
    }

    private static Item chunk(TwilightWorld w) {
        switch (w) {
            case EDEN: return TwilightItemsOther.edenChunk;
            case WILDWOOD: return TwilightItemsOther.wildwoodChunk;
            case APALACHIA: return TwilightItemsOther.apalachiaChunk;
            case SKYTHERN: return TwilightItemsOther.skythernChunk;
            default: return TwilightItemsOther.mortumChunk;
        }
    }

    /** The map to the lair of this world: a nest or the Demonic Circle. */
    private static WeightedRandomChestContent map(TwilightWorld w, int weight) {
        return e(ModItems.lairMap, w == TwilightWorld.MORTUM ? ItemLairMap.CIRCLE : ItemLairMap.NEST, 1, 1, weight);
    }

    public static WeightedRandomChestContent[] ruins(TwilightWorld w) {
        WeightedRandomChestContent seal = w == TwilightWorld.MORTUM ? e(w.gem(), 1, 1, 1) : e(ModItems.sanctumSeal, w.ordinal(), 1, 1, 1);
        return new WeightedRandomChestContent[] { e(w.soul(), 1, 3, 10), e(w.fragments(), 2, 6, 10), e(w.gem(), 1, 2, 5),
            e(chunk(w), 1, 1, 3), e(Items.bread, 2, 5, 6), e(Items.apple, 1, 3, 5), e(Items.arrow, 4, 12, 4),
            e(VanillaItemsOther.arlemiteIngot, 1, 2, 3), e(Items.gold_ingot, 1, 3, 3), map(w, 3), seal };
    }

    public static WeightedRandomChestContent[] dungeon(TwilightWorld w) {
        WeightedRandomChestContent call = e(w.gem(), 2, 3, 2);
        return new WeightedRandomChestContent[] { e(w.soul(), 2, 5, 10), e(w.gem(), 2, 4, 8), e(w.fragments(), 4, 8, 8),
            e(chunk(w), 1, 2, 6), e(Items.golden_apple, 1, 1, 2), e(Items.diamond, 1, 2, 3),
            e(VanillaItemsOther.rupeeIngot, 1, 2, 3), e(VanillaItemsOther.arlemiteIngot, 1, 3, 4), e(Items.experience_bottle, 2, 5, 4),
            map(w, 2), call };
    }
}
