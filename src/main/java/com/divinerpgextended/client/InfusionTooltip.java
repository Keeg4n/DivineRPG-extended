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
 * Vethea tooltips: on the template (and on everything put into the lower altar slot) - what comes out of it and how many chunks
 * are needed; on Vethea weapons - its kind (for mob weaknesses and the rhythm of the Heiva Belt).
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
            e.toolTip.add(EnumChatFormatting.GOLD + com.divinerpgextended.trinket.ItemTrinket.howTo(e.itemStack).getUnformattedText());
        if (e.itemStack.getItem() == net.divinerpg.utils.items.VetheaItems.heivaBelt)
            e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.heiva_rhythm",
                (int) (VetheaCombat.RHYTHM_STEP * 100), (int) (VetheaCombat.RHYTHM_STEP * VetheaCombat.RHYTHM_MAX * 100)));
        if (Infusion.tempered(e.itemStack)) e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.wings_tempered"));
    }
}
