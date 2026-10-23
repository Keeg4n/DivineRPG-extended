package com.divinerpgextended.client;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.perk.MiningTiers;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Подсказки цепочки: у кирки - какую руду и камень она берёт; у сумеречной брони - перк полного сета. */
@SideOnly(Side.CLIENT)
public class PickTierTooltip {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        if (e.itemStack.getItem() instanceof net.minecraft.item.ItemArmor) {
            int slot = ((net.minecraft.item.ItemArmor) e.itemStack.getItem()).armorType;
            for (TwilightWorld w : TwilightWorld.values()) {
                if (slot < 4 && w.armor()[slot] == e.itemStack.getItem())
                    e.toolTip.add(Math.min(1, e.toolTip.size()), EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.set_perk." + w.key()));
            }
        }
        int rk = com.divinerpgextended.perk.RangedRoles.kind(e.itemStack);
        if (rk > 0) e.toolTip.add(Math.min(1, e.toolTip.size()), EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.ranged_role." + rk));
        String line = MiningTiers.tooltip(e.itemStack);
        if (line == null) return;
        e.toolTip.add(Math.min(1, e.toolTip.size()), EnumChatFormatting.AQUA + line);
    }
}
