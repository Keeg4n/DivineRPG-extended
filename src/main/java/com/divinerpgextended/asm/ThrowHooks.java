package com.divinerpgextended.asm;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.perk.ThrowStack;

/** Calls from Divine throws patched by {@link ThrowableTransformer}. */
public final class ThrowHooks {

    private ThrowHooks() {}

    /** Instead of stack.stackSize-- on a throw. */
    public static void consume(ItemStack s) {
        ThrowStack.consume(s);
    }

    /** Stack limit of a throwable item. */
    public static int limit(Item item, ItemStack s) {
        return ThrowStack.limit(item, s);
    }
}
