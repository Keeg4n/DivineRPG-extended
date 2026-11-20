package com.divinerpgextended.station;

import net.minecraft.item.ItemStack;

/**
 * An enchantment that the corrupt altar rolls (and puts into its books) although the enchanting table never offers it:
 * {@link net.minecraft.enchantment.Enchantment#canApplyAtEnchantingTable} stays false, the altar asks {@link #canApplyAtAltar} instead.
 */
public interface AltarEnchant {

    /** Whether the altar may roll this enchantment for the item (for a book - for the item the book is rolled for). */
    boolean canApplyAtAltar(ItemStack s);

    /** Price tier on the altar: 0 - best, 1 - medium, 2 - weak (see {@link AltarLogic#tier}). */
    int altarTier();
}
