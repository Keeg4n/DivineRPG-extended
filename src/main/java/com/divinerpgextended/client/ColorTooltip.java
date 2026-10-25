package com.divinerpgextended.client;

import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.perk.ColorGear;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Стихия цветных вещей Divine и особенность бедрокового оружия - в подсказке ({@link ColorGear}). */
@SideOnly(Side.CLIENT)
public class ColorTooltip {

    private static final EnumChatFormatting[] COLOR = { EnumChatFormatting.RED, EnumChatFormatting.BLUE, EnumChatFormatting.GREEN, EnumChatFormatting.YELLOW,
        EnumChatFormatting.GRAY };

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        if (e.itemStack == null) return;
        ColorGear.Hue h = ColorGear.hue(e.itemStack);
        if (h != null) {
            String kind = ColorGear.isWeapon(e.itemStack) ? "weapon" : "armor";
            e.toolTip.add(COLOR[h.ordinal()] + StatCollector.translateToLocal("dre.tooltip.hue." + h.key() + "." + kind));
            return;
        }
        net.minecraft.item.Item it = e.itemStack.getItem();
        if (it == net.divinerpg.utils.items.VetheaItems.dreamCarrot || it == net.divinerpg.utils.items.VetheaItems.dreamMelon
            || it == com.divinerpgextended.registry.ModItems.dreamMelonSeeds)
            e.toolTip.add(EnumChatFormatting.GREEN + StatCollector.translateToLocal("dre.tooltip.farmland"));
        if (it == net.divinerpg.utils.items.IceikaItems.snowGlobe) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.snow_globe"));
        if (it == VanillaItemsWeapons.scorchingSword) e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.scorching_sword"));
        if (com.divinerpgextended.perk.ColdMobs.isHellWeapon(it)) e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.hell_vs_cold"));
        if (e.itemStack.getItem() == VanillaItemsWeapons.bedrockSword) e.toolTip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.tooltip.bedrock_sword"));
        if (e.itemStack.getItem() == VanillaItemsWeapons.bedrockMaul) e.toolTip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.tooltip.bedrock_maul"));
    }
}
