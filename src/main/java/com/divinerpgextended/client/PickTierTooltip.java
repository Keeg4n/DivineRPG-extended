package com.divinerpgextended.client;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.Config;
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
        int t = MiningTiers.tier(e.itemStack);
        if (t < 0 || !Config.miningTiers) return;
        String stone = StatCollector.translateToLocal("dre.world." + TwilightWorld.values()[Math.min(t, 4)].key());
        String line = t == 0 ? StatCollector.translateToLocalFormatted("dre.tooltip.pick_stone", stone)
            : StatCollector.translateToLocalFormatted("dre.tooltip.pick_tier",
                StatCollector.translateToLocal("dre.world." + TwilightWorld.values()[t - 1].key()), stone);
        e.toolTip.add(Math.min(1, e.toolTip.size()), EnumChatFormatting.AQUA + line);
    }
}
