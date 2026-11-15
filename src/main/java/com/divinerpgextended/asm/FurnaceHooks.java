package com.divinerpgextended.asm;

/**
 * Correct side logic for Divine "infinite" furnaces (slot 0 - input, slot 1 - result): from the top and sides
 * items go into the input, from the bottom the result is taken. Called from the Divine class patched by {@link FurnaceTransformer}.
 */
public final class FurnaceHooks {

    private static final int[] INPUT = { 0 }, OUTPUT = { 1 };

    private FurnaceHooks() {}

    /** Which slots are accessible from side (0 - bottom, 1 - top, 2-5 - sides). */
    public static int[] slots(int side) {
        return side == 0 ? OUTPUT : INPUT;
    }

    /** Only the result can be taken out. */
    public static boolean canExtract(int slot) {
        return slot == 1;
    }
}
