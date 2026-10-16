package com.divinerpgextended.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Подсказки предметов аддона переносятся по словам: Minecraft 1.7.10 сам их не переносит, и длинная строка уезжает за
 * край экрана. Ширина - до 220 пикселей, на узком окне меньше; цвет строки сохраняется на переносах.
 */
@SideOnly(Side.CLIENT)
public class TooltipWrap {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent e) {
        if (e.itemStack == null) return;
        String id = Item.itemRegistry.getNameForObject(e.itemStack.getItem());
        if (id == null || !id.startsWith("divinerpgextended:")) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution r = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = Math.max(120, Math.min(220, r.getScaledWidth() / 2 - 20));
        List<String> out = new ArrayList<String>();
        for (String line : e.toolTip) {
            if (mc.fontRenderer.getStringWidth(line) <= width) {
                out.add(line);
                continue;
            }
            for (Object part : mc.fontRenderer.listFormattedStringToWidth(line, width)) out.add((String) part);
        }
        e.toolTip.clear();
        e.toolTip.addAll(out);
    }
}
