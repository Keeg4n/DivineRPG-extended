package com.divinerpgextended.asm;

/**
 * Правильная сторонняя логика "бесконечных" печей Divine (слот 0 - вход, слот 1 - результат): сверху и с боков
 * кладут во вход, снизу забирают результат. Вызывается из исправленного {@link FurnaceTransformer} класса Divine.
 */
public final class FurnaceHooks {

    private static final int[] INPUT = { 0 }, OUTPUT = { 1 };

    private FurnaceHooks() {}

    /** Какие слоты доступны со стороны side (0 - низ, 1 - верх, 2-5 - бока). */
    public static int[] slots(int side) {
        return side == 0 ? OUTPUT : INPUT;
    }

    /** Забирать можно только результат. */
    public static boolean canExtract(int slot) {
        return slot == 1;
    }
}
