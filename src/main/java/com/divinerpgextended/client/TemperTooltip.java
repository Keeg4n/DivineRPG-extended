package com.divinerpgextended.client;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.item.TemperRecipe;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Закалённая вещь сумеречного мира (броня и луки Divine) - строка "Закалено" в подсказке. Отдельный публичный класс:
 * шина событий Forge вызывает обработчики только у публичных классов.
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
