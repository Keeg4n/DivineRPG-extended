package com.divinerpgextended.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Описания предметов Divine и аддона - под Shift. Без Shift видны название, ванильные строки (чары, атрибуты), имя мода
 * (Waila и др.) и строки с меткой {@link #KEEP}; остальное - описания из самих предметов (вместе с цифрами Divine: урон,
 * применения, эффекты брони) и подсказки, дописанные событиями, - прячется, вместо них строка "Shift - описание".
 */
@SideOnly(Side.CLIENT)
public class TooltipShift {

    /** Метка строки, которая видна и без Shift (невидимые коды сброса цвета). */
    public static final String KEEP = "§r§r";
    /** Начало строки с именем мода (Waila, NEI). */
    private static final String MOD_NAME = "§9§o";
    private static ItemTooltipEvent seen;
    private static List<String> base;

    /** Ванильная часть подсказки - до всех обработчиков. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void first(ItemTooltipEvent e) {
        seen = e;
        base = new ArrayList<String>(e.toolTip);
    }

    public static boolean divine(Item i) {
        String id = Item.itemRegistry.getNameForObject(i);
        return id != null && (id.startsWith("divinerpg:") || id.startsWith("divinerpgextended:"));
    }

    /** Спрятать описания (из {@link TooltipWrap}, после всех обработчиков). */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void apply(ItemTooltipEvent e) {
        if (e.itemStack == null || e.toolTip.size() < 2 || GuiScreen.isShiftKeyDown()) return;
        Item item = e.itemStack.getItem();
        boolean own = divine(item);
        Map<String, Integer> hide = new HashMap<String, Integer>();
        if (own) { // строки самого предмета
            List info = new ArrayList();
            try {
                item.addInformation(e.itemStack, e.entityPlayer, info, e.showAdvancedItemTooltips);
            } catch (Throwable ignored) {}
            for (Object o : info) bump(hide, String.valueOf(o), 1);
        }
        if (seen == e) { // дописанное событиями (без ванильных строк)
            Map<String, Integer> was = new HashMap<String, Integer>();
            for (String l : base) bump(was, l, 1);
            for (String l : e.toolTip) {
                Integer n = was.get(l);
                if (n != null && n > 0) was.put(l, n - 1);
                else bump(hide, l, 1);
            }
        }
        boolean hidden = false;
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < e.toolTip.size(); i++) {
            String l = e.toolTip.get(i);
            Integer n = hide.get(l);
            if (i > 0 && n != null && n > 0 && !l.startsWith(KEEP) && !l.startsWith(MOD_NAME)) {
                hide.put(l, n - 1);
                if (EnumChatFormatting.getTextWithoutFormattingCodes(l).trim().length() > 0) hidden = true;
                continue;
            }
            out.add(l);
        }
        if (!hidden) return;
        while (out.size() > 1 && EnumChatFormatting.getTextWithoutFormattingCodes(out.get(out.size() - 1)).trim().isEmpty()) out.remove(out.size() - 1);
        int at = out.size(); // над именем мода, если оно в конце
        while (at > 1 && out.get(at - 1).startsWith(MOD_NAME)) at--;
        out.add(at, EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("dre.tooltip.shift"));
        e.toolTip.clear();
        e.toolTip.addAll(out);
    }

    private static void bump(Map<String, Integer> m, String k, int d) {
        Integer n = m.get(k);
        m.put(k, (n == null ? 0 : n) + d);
    }
}
