package com.divinerpgextended.asm;

/** Вызовы из Morph ({@link MorphModelTransformer}); только на клиенте. */
public final class MorphHooks {

    private MorphHooks() {}

    /** Удалить display list части модели; если его уже нет в учёте GLAllocation (удалён раньше) - ничего. */
    public static void deleteList(int list) {
        try {
            net.minecraft.client.renderer.GLAllocation.deleteDisplayLists(list);
        } catch (NullPointerException ignored) {}
    }
}
