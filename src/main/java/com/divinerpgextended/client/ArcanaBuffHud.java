package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Эффект "Регенерация арканы": надпись с множителем и оставшимся временем над полоской арканы Divine (справа внизу). */
@SideOnly(Side.CLIENT)
public class ArcanaBuffHud {

    private static int ticks, mult;

    public static void set(int t, int m) {
        ticks = t;
        mult = m;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ticks > 0 && !Minecraft.getMinecraft().isGamePaused()) ticks--;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || ticks <= 0 || mult <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        int s = ticks / 20;
        String text = EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("dre.hud.arcana_regen", mult,
            String.format("%d:%02d", s / 60, s % 60));
        int w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();
        mc.fontRenderer.drawStringWithShadow(text, w - 6 - mc.fontRenderer.getStringWidth(text), h - 36, 0xFFFFFF);
    }
}
