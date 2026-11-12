package com.divinerpgextended.client;

import java.util.ArrayList;
import java.util.List;

import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.perk.EarlyWeapons;
import com.divinerpgextended.perk.ThrowStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Подсказки оружия обычного мира ({@link EarlyWeapons}); устаревшие строки Divine (выстрел жезла, яд Серенады смерти) убираются. */
@SideOnly(Side.CLIENT)
public class EarlyWeaponsTooltip {

    private static String t(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("dre.tooltip.ew." + key, args);
    }

    private static String pct(float f) {
        return String.format("%.0f", f * 100);
    }

    private static String x(float f) {
        return String.format("%.2f", f).replaceAll("0$", "");
    }

    /** Убрать строки, которые пишет сам предмет Divine (первые n; n < 0 - все). */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void strip(ItemTooltipEvent e, int n) {
        List info = new ArrayList();
        try {
            e.itemStack.getItem().addInformation(e.itemStack, e.entityPlayer, info, e.showAdvancedItemTooltips);
        } catch (Throwable ignored) {
            return;
        }
        for (int i = 0; i < info.size() && (n < 0 || i < n); i++) e.toolTip.remove(String.valueOf(info.get(i)));
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.itemStack;
        if (s == null) return;
        Item i = s.getItem();
        List<String> l = e.toolTip;
        String c = EnumChatFormatting.AQUA.toString();
        if (i == VanillaItemsWeapons.cyclopsianStaff) strip(e, -1);
        else if (i == VanillaItemsWeapons.serenadeOfDeath) {
            strip(e, 2);
            l.add(c + t("death", EarlyWeapons.DEATH_REGEN_TICKS / 20));
        } else if (i == VanillaItemsWeapons.corruptedCannon) {
            l.add(c + t("cannon", EarlyWeapons.CANNON_TOP, x(EarlyWeapons.CANNON_DEEP), x(EarlyWeapons.cannonDepth(e.entityPlayer))));
        } else if (i == VanillaItemsWeapons.goldenFury) l.add(c + t("fury", pct(EarlyWeapons.FURY_NUGGET), pct(EarlyWeapons.FURY_COIN)));
        else if (i == VanillaItemsWeapons.bloodgemSword) l.add(c + t("bloodgem", pct(EarlyWeapons.VAMP), pct(EarlyWeapons.VAMP_LOW)));
        else if (i == VanillaItemsWeapons.poisonSaber) l.add(c + t("saber", EarlyWeapons.NO_HEAL_TICKS / 20));
        else if (i == VanillaItemsWeapons.palavence || i == VanillaItemsWeapons.massivence || i == IceikaItems.frossivence)
            l.add(c + t("healsword", String.format("%.0f", EarlyWeapons.swordHeal(s)), String.format("%.1f", EarlyWeapons.HEAL_EVERY / 20F)));
        else if (i == VanillaItemsWeapons.hunterBow) l.add(c + t("hunter", (int) EarlyWeapons.SNIPE_FROM, (int) EarlyWeapons.SNIPE_FULL, x(EarlyWeapons.SNIPE_MAX)));
        else if (i == VanillaItemsWeapons.shadowBow) l.add(c + t("shadow", EarlyWeapons.RUN_FULL / 20, pct(EarlyWeapons.RUN_MAX - 1F)));
        else if (i == VanillaItemsWeapons.shuriken)
            l.add(c + t("shuriken", EarlyWeapons.SERIES_WINDOW / 20, pct(EarlyWeapons.SERIES_STEP), pct(EarlyWeapons.SERIES_STEP * EarlyWeapons.SERIES_MAX)));
        else if (i == VanillaItemsWeapons.vileStorm) l.add(c + t("vile", EarlyWeapons.CLOUD_TICKS / 20));
        else if (i == VanillaItemsWeapons.serenadeOfInfusion) l.add(c + t("infusion", (int) EarlyWeapons.INFUSION_ARCANA, EarlyWeapons.INFUSION_CLARITY / 20));
        else if (i == VanillaItemsWeapons.serenadeStriker || i == net.divinerpg.utils.items.ArcanaItems.stormSword) l.add(c + t("lightning"));
        if (ThrowStack.is(i)) {
            if (ThrowStack.count(s) > 0) l.add(EnumChatFormatting.GRAY + t("stack_count", ThrowStack.count(s), ThrowStack.MAX));
            l.add(EnumChatFormatting.GRAY + t("stack", ThrowStack.MAX));
        }
        if (EarlyWeapons.divineRanged(s)) l.add(EnumChatFormatting.GRAY + t("ranged_ench"));
    }
}
