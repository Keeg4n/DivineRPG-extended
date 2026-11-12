package com.divinerpgextended.asm;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.perk.ThrowStack;

/** Вызовы из бросков Divine, исправленных {@link ThrowableTransformer}. */
public final class ThrowHooks {

    private ThrowHooks() {}

    /** Вместо stack.stackSize-- при броске. */
    public static void consume(ItemStack s) {
        ThrowStack.consume(s);
    }

    /** Предел стопки метательного предмета. */
    public static int limit(Item item, ItemStack s) {
        return ThrowStack.limit(item, s);
    }
}
