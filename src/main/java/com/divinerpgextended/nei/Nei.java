package com.divinerpgextended.nei;

import java.lang.reflect.Method;
import java.util.List;

import codechicken.nei.PositionedStack;

/**
 * Какой NEI стоит: классический (ChickenBones 1.0.5) или из GTNH. Методы, которых в классическом нет, вызываем только
 * отражением или из {@link NeiGtnh} - иначе классический NEI падал (NoSuchMethodError при открытии вкладки рыбалки).
 */
final class Nei {

    private static Boolean gtnh;
    private static Method setTooltip;
    private static boolean tooltipLooked;

    private Nei() {}

    /** NEI из GTNH (есть описания вкладок HandlerInfo). */
    static boolean gtnh() {
        if (gtnh == null) {
            try {
                Class.forName("codechicken.nei.recipe.HandlerInfo", false, Nei.class.getClassLoader());
                gtnh = true;
            } catch (Throwable t) {
                gtnh = false;
            }
        }
        return gtnh;
    }

    /** Подсказка ячейки силами NEI (GTNH). false - не умеет, подсказку показываем сами ({@link DreHandler#handleItemTooltip}). */
    static boolean setTooltip(PositionedStack ps, List<String> tip) {
        if (!tooltipLooked) {
            tooltipLooked = true;
            try {
                setTooltip = PositionedStack.class.getMethod("setTooltip", List.class);
            } catch (Throwable t) {
                setTooltip = null;
            }
        }
        if (setTooltip == null) return false;
        try {
            setTooltip.invoke(ps, tip);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
