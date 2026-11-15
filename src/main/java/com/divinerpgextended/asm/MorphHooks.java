package com.divinerpgextended.asm;

/** Calls from Morph ({@link MorphModelTransformer}); client only. */
public final class MorphHooks {

    private MorphHooks() {}

    /** Delete the display list of a model part; if it is no longer tracked by GLAllocation (deleted earlier) - nothing. */
    public static void deleteList(int list) {
        try {
            net.minecraft.client.renderer.GLAllocation.deleteDisplayLists(list);
        } catch (NullPointerException ignored) {}
    }
}
