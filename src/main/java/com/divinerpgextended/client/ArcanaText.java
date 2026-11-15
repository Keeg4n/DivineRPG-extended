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
 * A number over the Divine arcana bar: "current/maximum". The bar has the same length at any maximum (full = 100 pixels),
 * and without the number the growth of the maximum from Vatticus's research is not visible.
 */
public class ArcanaText {

    /**
     * The Divine client adds +1 ahead on its own every tick, while the server sends the exact value - the number jumps by
     * one. We show the largest over the last ticks (spending is visible with a delay of a couple of ticks), not above the maximum.
     */
    private final float[] last = new float[4];
    private int at;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        last[at = (at + 1) % last.length] = Math.min(ArcanaRenderer.value, ArcanaHooks.clientMax());
    }

    private float shown() {
        float v = Math.min(ArcanaRenderer.value, ArcanaHooks.clientMax());
        // real spending (more than 2) is visible at once; jitter of one is suppressed
        for (float x : last) if (x > v && x - v <= 2) v = x;
        return v;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRender(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase != TickEvent.Phase.END || mc.currentScreen != null || mc.thePlayer == null || mc.gameSettings.hideGUI) return;
        ScaledResolution r = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int x = r.getScaledWidth() - ConfigurationHelper.arcanaX, y = r.getScaledHeight() - ConfigurationHelper.arcanaY;
        String t = (int) shown() + "/" + (int) ArcanaHooks.clientMax();
        FontRenderer f = mc.fontRenderer;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        f.drawStringWithShadow(t, x + 50 - f.getStringWidth(t) / 2, y + 1, 0xFFFFFF);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
