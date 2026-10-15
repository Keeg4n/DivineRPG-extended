package com.divinerpgextended.client;

import net.divinerpg.client.ArcanaRenderer;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.asm.ArcanaHooks;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Число поверх полоски арканы Divine: "сейчас/максимум". Полоска при любом максимуме одной длины (полная = 100 пикселей),
 * и без числа рост максимума от исследований Ваттикуса не виден.
 */
public class ArcanaText {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRender(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase != TickEvent.Phase.END || mc.currentScreen != null || mc.thePlayer == null || mc.gameSettings.hideGUI) return;
        ScaledResolution r = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int x = r.getScaledWidth() - ConfigurationHelper.arcanaX, y = r.getScaledHeight() - ConfigurationHelper.arcanaY;
        String t = (int) ArcanaRenderer.value + "/" + (int) ArcanaHooks.clientMax();
        FontRenderer f = mc.fontRenderer;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        f.drawStringWithShadow(t, x + 50 - f.getStringWidth(t) / 2, y + 1, 0xFFFFFF);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
