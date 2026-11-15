package com.divinerpgextended.item;

import java.util.List;

import net.divinerpg.utils.TooltipLocalizer;
import net.minecraft.item.ItemStack;

/** Tooltip lines for the addon's tools in the format of Divine pickaxes: efficiency and durability (or infinity). */
public final class ToolInfo {

    private ToolInfo() {}

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void add(List list, ItemStack s, float efficiency) {
        list.add(TooltipLocalizer.efficiency(efficiency));
        list.add(s.getMaxDamage() > 0 ? TooltipLocalizer.usesRemaining(s.getMaxDamage() - s.getItemDamage()) : TooltipLocalizer.infiniteUses());
    }
}
