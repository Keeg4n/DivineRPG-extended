package com.divinerpgextended.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.accessory.Sockets;
import com.divinerpgextended.perk.Ammo;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Подсказки гнёзд брони и заряда оружия; счётчик заряда справа от хотбара, пока оружие в руке. */
@SideOnly(Side.CLIENT)
public class SocketTooltip {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.itemStack;
        if (s == null) return;
        if (Sockets.socketable(s)) {
            List<ItemStack> in = Sockets.contents(s);
            e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("dre.sockets.tooltip", in.size(), Sockets.maxCapacity(s)));
            for (ItemStack a : in) e.toolTip.add(EnumChatFormatting.LIGHT_PURPLE + " - " + a.getDisplayName());
            if (in.isEmpty()) e.toolTip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.sockets.empty"));
        }
        Item ammo = Ammo.of(s);
        if (ammo != null) {
            int n = Ammo.loaded(s);
            e.toolTip.add((n > 0 ? EnumChatFormatting.YELLOW : EnumChatFormatting.GRAY)
                + StatCollector.translateToLocalFormatted("dre.ammo.loaded", n, Ammo.capacity(s), new ItemStack(ammo).getDisplayName()));
            int per = Ammo.perShot(s, ammo);
            if (per > 1) e.toolTip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.ammo.per_shot", per, new ItemStack(ammo).getDisplayName()));
            e.toolTip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.ammo.how"));
        }
    }

    /** Справа от хотбара: заряжено / вместимость у оружия в руке. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null || !mc.playerController.shouldDrawHUD()) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || Ammo.of(held) == null) return;
        int n = Ammo.loaded(held);
        ScaledResolution r = e.resolution;
        String text = n + "/" + Ammo.capacity(held);
        mc.fontRenderer.drawStringWithShadow(text, r.getScaledWidth() / 2 + 91 + 6, r.getScaledHeight() - 15, n > 0 ? 0xFFFFE070 : 0xFF909090);
    }
}
