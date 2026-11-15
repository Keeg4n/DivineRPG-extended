package com.divinerpgextended.client;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.item.TemperRecipe;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A tempered Twilight world item (Divine armor and bows) - the "Tempered" line in the tooltip. A separate public class:
 * the Forge event bus calls handlers only on public classes.
 */
@SideOnly(Side.CLIENT)
public class TemperTooltip {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        if (TemperRecipe.tempered(e.itemStack)) {
            e.toolTip.add(Math.min(1, e.toolTip.size()), EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.tempered"));
        }
    }
}
