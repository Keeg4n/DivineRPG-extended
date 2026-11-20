package com.divinerpgextended.client;

import java.math.BigDecimal;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.perk.DreEnchants;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Under each of the addon's enchantments on an item or a book - what it does, with the server's numbers. */
@SideOnly(Side.CLIENT)
public class EnchantTooltip {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.itemStack;
        if (s == null || s.getItem() == null) return;
        boolean book = s.getItem() == Items.enchanted_book;
        NBTTagList l = book ? Items.enchanted_book.func_92110_g(s) : s.getEnchantmentTagList();
        if (l == null) return;
        boolean clash = !book && DreEnchants.clashes(s);
        DreEnchants.Sync n = DreEnchants.numbers();
        for (int i = 0; i < l.tagCount(); i++) {
            int id = l.getCompoundTagAt(i).getShort("id"), lvl = l.getCompoundTagAt(i).getShort("lvl");
            Enchantment en = id >= 0 && id < Enchantment.enchantmentsList.length ? Enchantment.enchantmentsList[id] : null;
            if (!(en instanceof DreEnchants.Ench)) continue;
            DreEnchants.Kind k = ((DreEnchants.Ench) en).kind;
            String name = en.getTranslatedName(lvl);
            int at = -1;
            for (int j = 0; j < e.toolTip.size() && at < 0; j++) if (EnumChatFormatting.getTextWithoutFormattingCodes(e.toolTip.get(j)).equals(name)) at = j;
            int pos = at < 0 ? e.toolTip.size() : at + 1;
            e.toolTip.add(pos, EnumChatFormatting.GRAY + "  " + describe(k, Math.max(1, Math.min(lvl, 3)), n));
            if (clash && k.strike) e.toolTip.add(pos + 1, EnumChatFormatting.RED + "  " + StatCollector.translateToLocal("enchantment.dre.clash"));
        }
    }

    static String describe(DreEnchants.Kind k, int lvl, DreEnchants.Sync n) {
        String key = "enchantment.dre." + k.key() + ".desc";
        switch (k) {
            case SWEEP:
                return StatCollector.translateToLocalFormatted(key, num(n.sweepDamage), n.sweepTargets, num(n.sweepRadius));
            case AGILITY:
                return StatCollector.translateToLocalFormatted(key, num(n.agilityWindow[lvl - 1]), num(n.agilityDamage[lvl - 1]));
            case MIGHT:
                return StatCollector.translateToLocalFormatted(key, num(n.mightDamage), num(n.mightWindow));
            case CROWN:
                return StatCollector.translateToLocalFormatted(key, n.crownCalm, num(n.crownDamage));
            default:
                return StatCollector.translateToLocalFormatted(key, n.spiteSeconds, num(n.spiteWindow));
        }
    }

    /** 0.75 - "0.75", 3.0 - "3". */
    static String num(float v) {
        return new BigDecimal(String.valueOf(v)).stripTrailingZeros().toPlainString();
    }
}
