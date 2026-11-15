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

/** Tooltips of Divine's arcane weapons: price, arcane damage (with stage growth) and cooldown by the addon's rules instead of Divine's lines. */
@SideOnly(Side.CLIENT)
public class ArcanaTooltips {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTooltip(ItemTooltipEvent e) {
        if (e.itemStack == null || e.itemStack.getItem() instanceof ItemMageStaff) return;
        Item i = e.itemStack.getItem();
        if (com.divinerpgextended.boss.EnderColors.ender(i)) { // End armor: against which Ayeraco and what the set gives
            com.divinerpgextended.boss.AyeracoHorde.Hue h = com.divinerpgextended.boss.EnderColors.hue(i);
            e.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(h == null ? "dre.tooltip.ender_gray"
                : "dre.tooltip.ender_vs." + h.name().toLowerCase()));
            e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.ender_set",
                com.divinerpgextended.boss.EnderGear.RESCUE_CD / 20, (int) com.divinerpgextended.boss.EnderGear.RESCUE_HURT / 2));
        }
        if (com.divinerpgextended.boss.EnderColors.sword(e.itemStack)) e.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("dre.tooltip.ender_sword"));
        ArcaneEffect ing = ArcaneEffect.byIngredient(i);
        if (ing != null) e.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocalFormatted("dre.tooltip.potion_ingredient",
            ItemArcanePotion.of(ing, false, false, false).getDisplayName()));
        if (i == ArcanaItems.weakArcanaPotion || i == ArcanaItems.strongArcanaPotion) { // not instant arcana, but an effect
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
        if (i == ArcanaItems.wraithbane) {
            e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.wraith", String.format("%.0f",
                EntityArcaneWraith.BASE_DAMAGE * ArcanaRules.stage(e.entityPlayer)), ArcaneSummons.CAP, ArcaneSummons.CAP_KORMA, (int) EntityArcaneWraith.UPKEEP));
            e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.summon_stats", String.format("%.0f",
                EntityArcaneWraith.BASE_DAMAGE * ArcanaRules.stage(e.entityPlayer)), (int) ArcaneSummons.AGGRO));
        }
    }

    /** Remove Divine's lines about arcana and damage (except the name - with F3+H it also has a number - and the mod name). */
    private static void strip(ItemTooltipEvent e) {
        Iterator<String> it = e.toolTip.iterator();
        if (it.hasNext()) it.next();
        while (it.hasNext()) {
            String raw = it.next();
            String l = EnumChatFormatting.getTextWithoutFormattingCodes(raw);
            if (l == null || raw.startsWith("§9§o")) continue;
            String s = l.toLowerCase();
            if (s.contains("arcana") || s.contains("аркан") || s.contains("damage") || s.contains("урон")) it.remove();
        }
    }
}
