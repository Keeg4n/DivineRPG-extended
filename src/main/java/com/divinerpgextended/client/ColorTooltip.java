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
        // адское оружие дальнего боя - роль ({@link com.divinerpgextended.boss.NetherArsenal})
        if (it == VanillaItemsWeapons.ghastCannon) e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.ghast_cannon"));
        if (it == VanillaItemsWeapons.maelstrom) e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.maelstrom"));
        if (it == VanillaItemsWeapons.ghastCannon || it == VanillaItemsWeapons.maelstrom)
            e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(it == VanillaItemsWeapons.ghastCannon ? "dre.tooltip.reload_ghast" : "dre.tooltip.reload_maelstrom"));
        if (it == net.divinerpg.utils.items.IceikaItems.soundOfMusic) e.toolTip.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("dre.tooltip.sound_of_music"));
        if (it == net.divinerpg.utils.items.IceikaItems.soundOfCarols) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.sound_of_carols"));
        // против Короля скорчеров ({@link com.divinerpgextended.boss.KingRace})
        if (com.divinerpgextended.boss.KingRace.aquatic(it)) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.aquatic_vs_king"));
        if (it == net.divinerpg.utils.items.VanillaItemsArmor.infernoHelmet || it == net.divinerpg.utils.items.VanillaItemsArmor.infernoBody
            || it == net.divinerpg.utils.items.VanillaItemsArmor.infernoLegs || it == net.divinerpg.utils.items.VanillaItemsArmor.infernoBoots
            || it == net.divinerpg.utils.items.VanillaItemsArmor.netheriteHelmet || it == net.divinerpg.utils.items.VanillaItemsArmor.netheriteBody
            || it == net.divinerpg.utils.items.VanillaItemsArmor.netheriteLegs || it == net.divinerpg.utils.items.VanillaItemsArmor.netheriteBoots)
            e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.heatproof_vs_king"));
        if (blastArmor(it)) e.toolTip.add(EnumChatFormatting.DARK_RED + StatCollector.translateToLocal("dre.tooltip.blast_after_watcher"));
    }

    /** Броня, полный сет которой защищает от взрывов (Bedrock и Ender любых цветов). */
    static boolean blastArmor(net.minecraft.item.Item it) {
        if (!(it instanceof net.minecraft.item.ItemArmor)) return false;
        String n = it.getUnlocalizedName();
        return n != null && (n.toLowerCase().contains("bedrock") || n.toLowerCase().contains("ender")) && it.getClass().getName().startsWith("net.divinerpg");
    }
}
