package com.divinerpgextended.magic;

import java.util.Iterator;

import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.item.Item;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Подсказки арканного оружия Divine: цена, арканный урон (с ростом по этапу) и откат по правилам аддона вместо строк Divine. */
@SideOnly(Side.CLIENT)
public class ArcanaTooltips {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTooltip(ItemTooltipEvent e) {
        if (e.itemStack == null || e.itemStack.getItem() instanceof ItemMageStaff) return;
        Item i = e.itemStack.getItem();
        ArcaneEffect ing = ArcaneEffect.byIngredient(i);
        if (ing != null) e.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocalFormatted("dre.tooltip.potion_ingredient",
            StatCollector.translateToLocal("item.dre.arcanePotion." + ing.key() + ".name")));
        if (i == ArcanaItems.weakArcanaPotion || i == ArcanaItems.strongArcanaPotion) { // не мгновенная аркана, а эффект
            boolean strong = i == ArcanaItems.strongArcanaPotion;
            strip(e);
            e.toolTip.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("dre.tooltip.arcana_potion", strong ? 3 : 2,
                (strong ? ArcanaRules.BUFF_TICKS_STRONG : ArcanaRules.BUFF_TICKS) / 20));
            return;
        }
        String owner = i.getClass().getName(), simple = ArcanaRules.simple(owner);
        Float base = ArcanaCombat.base(e.itemStack);
        ArcanaRules.Spec sp = ArcanaRules.SPECS.get(simple);
        if (base == null && (sp == null || sp.cost < 0)) return;
        float cost = i == ArcanaItems.staffStarlight ? ArcanaRules.STARLIGHT_STAFF : i == ArcanaItems.starlight ? ArcanaRules.STARLIGHT : sp.cost;
        strip(e);
        e.toolTip.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("dre.tooltip.arcana_cost", (int) cost));
        if (base != null) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.arcana_damage",
            String.format("%.0f", base * ArcanaRules.stage(e.entityPlayer))));
        if (sp != null && sp.cooldown > 0) e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.arcana_cooldown",
            String.format("%.2f", sp.cooldown / 20.0)));
    }

    /** Убрать строки Divine про аркану и урон (кроме названия). */
    private static void strip(ItemTooltipEvent e) {
        String name = EnumChatFormatting.getTextWithoutFormattingCodes(e.itemStack.getDisplayName());
        for (Iterator<String> it = e.toolTip.iterator(); it.hasNext();) {
            String l = EnumChatFormatting.getTextWithoutFormattingCodes(it.next());
            if (l == null || l.equals(name)) continue;
            String s = l.toLowerCase();
            if (s.contains("arcana") || s.contains("аркан") || s.contains("damage") || s.contains("урон")) it.remove();
        }
    }
}
