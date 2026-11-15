package com.divinerpgextended.nei;

import java.lang.reflect.Method;
import java.util.List;

import codechicken.nei.PositionedStack;

/**
 * Which NEI is installed: classic (ChickenBones 1.0.5) or from GTNH. Methods that classic lacks are called only via
 * reflection or from {@link NeiGtnh} - otherwise classic NEI crashed (NoSuchMethodError when opening the fishing tab).
 */
final class Nei {

    private static Boolean gtnh;
    private static Method setTooltip;
    private static boolean tooltipLooked;

    private Nei() {}

    /** NEI from GTNH (has HandlerInfo tab descriptions). */
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

    /** Slot tooltip handled by NEI itself (GTNH). false - it cannot, we show the tooltip ourselves ({@link DreHandler#handleItemTooltip}). */
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
