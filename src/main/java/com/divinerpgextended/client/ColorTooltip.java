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
        melee(e);
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
        // вещи порчи - вещи тьмы ({@link com.divinerpgextended.perk.CorruptedGear})
        if (com.divinerpgextended.perk.CorruptedGear.tool(it)) e.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("dre.tooltip.corrupted_tool"));
        if (com.divinerpgextended.perk.CorruptedGear.weapon(it)) e.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("dre.tooltip.corrupted_weapon"));
        // против Короля скорчеров ({@link com.divinerpgextended.boss.KingRace})
        if (com.divinerpgextended.boss.KingRace.aquatic(it)) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.aquatic_vs_king"));
        if (it == net.divinerpg.utils.items.VanillaItemsArmor.infernoHelmet || it == net.divinerpg.utils.items.VanillaItemsArmor.infernoBody
            || it == net.divinerpg.utils.items.VanillaItemsArmor.infernoLegs || it == net.divinerpg.utils.items.VanillaItemsArmor.infernoBoots
            || it == net.divinerpg.utils.items.VanillaItemsArmor.netheriteHelmet || it == net.divinerpg.utils.items.VanillaItemsArmor.netheriteBody
            || it == net.divinerpg.utils.items.VanillaItemsArmor.netheriteLegs || it == net.divinerpg.utils.items.VanillaItemsArmor.netheriteBoots)
        {
            e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.heatproof_vs_king"));
            e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("dre.tooltip.nether_smelt", Math.round(com.divinerpgextended.Config.netherSmeltChance * 100)));
        }
        if (it == net.divinerpg.utils.items.VanillaItemsArmor.bedrockHelmet || it == net.divinerpg.utils.items.VanillaItemsArmor.bedrockBody
            || it == net.divinerpg.utils.items.VanillaItemsArmor.bedrockLegs || it == net.divinerpg.utils.items.VanillaItemsArmor.bedrockBoots)
            e.toolTip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.tooltip.bedrock_stance"));
        if (com.divinerpgextended.accessory.Goggles.is(e.itemStack)) e.toolTip.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("dre.tooltip.goggles_socket"));
        if (it == net.divinerpg.utils.items.VanillaItemsArmor.terranHelmet || it == net.divinerpg.utils.items.VanillaItemsArmor.terranBody
            || it == net.divinerpg.utils.items.VanillaItemsArmor.terranLegs || it == net.divinerpg.utils.items.VanillaItemsArmor.terranBoots)
            e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.terran_crit"));
        if (part(it, net.divinerpg.utils.items.VanillaItemsArmor.divineHelmet, net.divinerpg.utils.items.VanillaItemsArmor.divineBody, net.divinerpg.utils.items.VanillaItemsArmor.divineLegs, net.divinerpg.utils.items.VanillaItemsArmor.divineBoots))
            e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.divine_melee"));
        if (part(it, com.divinerpgextended.world.twilight.TwilightWorld.SKYTHERN.armor())) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.sky_float"));
        if (part(it, com.divinerpgextended.world.twilight.TwilightWorld.MORTUM.armor())) e.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("dre.tooltip.mortum_souls"));
        if (part(it, net.divinerpg.utils.items.VanillaItemsArmor.frozenHelmet, net.divinerpg.utils.items.VanillaItemsArmor.frozenBody, net.divinerpg.utils.items.VanillaItemsArmor.frozenLegs, net.divinerpg.utils.items.VanillaItemsArmor.frozenBoots))
            e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.sun_immune"));
        if (part(it, net.divinerpg.utils.items.VanillaItemsArmor.infernoHelmet, net.divinerpg.utils.items.VanillaItemsArmor.infernoBody, net.divinerpg.utils.items.VanillaItemsArmor.infernoLegs, net.divinerpg.utils.items.VanillaItemsArmor.infernoBoots)
            || part(it, net.divinerpg.utils.items.VanillaItemsArmor.netheriteHelmet, net.divinerpg.utils.items.VanillaItemsArmor.netheriteBody, net.divinerpg.utils.items.VanillaItemsArmor.netheriteLegs, net.divinerpg.utils.items.VanillaItemsArmor.netheriteBoots)
            || part(it, com.divinerpgextended.world.twilight.TwilightWorld.EDEN.armor()))
            e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.sun_half"));
        if (part(it, net.divinerpg.utils.items.VanillaItemsArmor.krakenHelmet, net.divinerpg.utils.items.VanillaItemsArmor.krakenBody, net.divinerpg.utils.items.VanillaItemsArmor.krakenLegs, net.divinerpg.utils.items.VanillaItemsArmor.krakenBoots)
            || part(it, net.divinerpg.utils.items.VanillaItemsArmor.aquastriveHelmet, net.divinerpg.utils.items.VanillaItemsArmor.aquastriveBody, net.divinerpg.utils.items.VanillaItemsArmor.aquastriveLegs, net.divinerpg.utils.items.VanillaItemsArmor.aquastriveBoots))
            e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.rain_ready"));
        if (part(it, com.divinerpgextended.world.twilight.TwilightWorld.WILDWOOD.armor())) e.toolTip.add(EnumChatFormatting.GREEN + StatCollector.translateToLocal("dre.tooltip.wild_rain"));
        if (part(it, com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA.armor())) e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.apalachia_rocks"));
        if (com.divinerpgextended.world.twilight.SeaWeapons.seaWeapon(it)) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.sea_wet"));
        if (com.divinerpgextended.world.twilight.SeaWeapons.anchor(it) >= 0) {
            int anchor = com.divinerpgextended.world.twilight.SeaWeapons.anchor(it);
            e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.harpoon", com.divinerpgextended.world.twilight.SeaWeapons.range(anchor)));
            if (anchor == 2) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.harpoon_stun"));
            if (anchor == 3) e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.harpoon_swarm"));
        }
        if (blastArmor(it)) e.toolTip.add(EnumChatFormatting.DARK_RED + StatCollector.translateToLocal("dre.tooltip.blast_after_watcher"));
    }

    private static boolean part(net.minecraft.item.Item it, net.minecraft.item.Item... set) {
        for (net.minecraft.item.Item s : set) if (it == s) return true;
        return false;
    }

    /** Подвид оружия обычного мира: числа сервера. Меч с обычными числами - без строки, у него ничего не меняется. */
    private static void melee(ItemTooltipEvent e) {
        com.divinerpgextended.perk.MeleeRoles.Sync s = com.divinerpgextended.perk.MeleeRoles.shown;
        com.divinerpgextended.perk.MeleeRoles.Kind k = s != null && s.on ? com.divinerpgextended.perk.MeleeRoles.kind(e.itemStack) : null;
        if (k == null) return;
        int i = k.ordinal();
        boolean plain = s.window[i] == com.divinerpgextended.perk.MeleeRoles.VANILLA_WINDOW && s.damage[i] == 1F;
        boolean plainPvp = !s.pvp || s.windowPvp[i] == com.divinerpgextended.perk.MeleeRoles.VANILLA_WINDOW && s.damagePvp[i] == 1F;
        if (plain && plainPvp) return;
        java.text.DecimalFormat f = new java.text.DecimalFormat("0.##");
        e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.melee", StatCollector.translateToLocal("dre.tooltip.melee." + k.key()),
            f.format(s.window[i] / 20F), f.format(s.damage[i])));
        if (s.pvp) e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.melee_pvp", f.format(s.windowPvp[i] / 20F), f.format(s.damagePvp[i])));
    }

    /** Броня, полный сет которой защищает от взрывов (Bedrock и Ender любых цветов). */
    static boolean blastArmor(net.minecraft.item.Item it) {
        if (!(it instanceof net.minecraft.item.ItemArmor)) return false;
        String n = it.getUnlocalizedName();
        return n != null && (n.toLowerCase().contains("bedrock") || n.toLowerCase().contains("ender")) && it.getClass().getName().startsWith("net.divinerpg");
    }
}
