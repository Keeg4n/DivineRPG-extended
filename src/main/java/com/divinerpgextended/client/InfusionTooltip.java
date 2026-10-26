package com.divinerpgextended.client;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.vethea.Infusion;
import com.divinerpgextended.vethea.VetheaCombat;
import com.divinerpgextended.vethea.WeaponKind;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Подсказки Ветеи: на шаблоне (и на всём, что кладут в нижний слот алтаря) - что из него выходит и сколько глыб
 * нужно; на ветейском оружии - его вид (для слабостей мобов и ритма Пояса хейвы).
 */
@SideOnly(Side.CLIENT)
public class InfusionTooltip {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        if (e.itemStack == null) return;
        List<Infusion.Recipe> rs = Infusion.byTemplate(e.itemStack);
        if (!rs.isEmpty()) {
            e.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("dre.tooltip.infusion"));
            int shown = 0;
            for (Infusion.Recipe r : rs) {
                if (shown++ == 9) {
                    e.toolTip.add(EnumChatFormatting.DARK_GRAY + "...");
                    break;
                }
                e.toolTip.add(EnumChatFormatting.GRAY + " " + r.lump.getDisplayName() + " x" + r.count + " -> " + EnumChatFormatting.WHITE
                    + r.output.getDisplayName());
            }
        }
        WeaponKind k = WeaponKind.ofItem(e.itemStack);
        if (k != null) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.weapon_kind." + k.key()));
        if (com.divinerpgextended.accessory.Accessories.divineAccessory(e.itemStack))
            e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.accessory_slot"));
        if (e.itemStack.getItem() == net.divinerpg.utils.items.VetheaItems.heivaBelt)
            e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.heiva_rhythm",
                (int) (VetheaCombat.RHYTHM_STEP * 100), (int) (VetheaCombat.RHYTHM_STEP * VetheaCombat.RHYTHM_MAX * 100)));
        if (Infusion.tempered(e.itemStack)) e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.wings_tempered"));
    }
}
