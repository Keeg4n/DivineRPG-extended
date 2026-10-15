package com.divinerpgextended.client;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.cold.Blizzard;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.cold.ColdMessage;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Холод на экране: изморозь со снежинками по краям (гуще с ростом холода) и градусник над полоской еды. Пока
 * действует "Согрет", градусник обведён оранжевым. Во вьюгу под открытым небом - белая пелена, густой туман, снег
 * по ветру и вой ветра; перед вьюгой снег уже начинает мести.
 */
@SideOnly(Side.CLIENT)
public class ColdHud extends Gui {

    private static final int FLAKES = 60;
    private final float[][] flakes = new float[FLAKES][];

    public ColdHud() {
        Random r = new Random(1234);
        for (int i = 0; i < FLAKES; i++) {
            // точки у краёв экрана: x, y в долях экрана, размер, порог холода появления
            float edge = r.nextFloat() * 0.22F;
            int side = r.nextInt(4);
            float along = r.nextFloat();
            float x = side == 0 ? edge : side == 1 ? 1 - edge : along;
            float y = side == 2 ? edge : side == 3 ? 1 - edge : along;
            flakes[i] = new float[] { x, y, 1 + r.nextInt(3), 20 + r.nextFloat() * 80 };
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || !ColdHandler.inIceika(mc.thePlayer) && ColdMessage.clientCold <= 0) return;
        float cold = ColdMessage.clientCold;
        ScaledResolution res = event.resolution;
        int w = res.getScaledWidth(), h = res.getScaledHeight();

        GL11.glEnable(GL11.GL_BLEND);
        if (storm(mc)) drawRect(0, 0, w, h, argb(0.28F, 0xF0F6FF)); // белая пелена вьюги
        if (cold > 15) frost(w, h, (cold - 15) / 85F);
        thermometer(mc, w, h, cold, ColdMessage.clientWarm > 0);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Вьюга вокруг и игрок под открытым небом. */
    private static boolean storm(Minecraft mc) {
        return mc.thePlayer != null && ColdHandler.inIceika(mc.thePlayer) && ColdMessage.clientPhase == Blizzard.STORM
            && Blizzard.exposed(mc.thePlayer);
    }

    @SubscribeEvent
    public void onFog(EntityViewRenderEvent.FogDensity event) {
        if (!storm(Minecraft.getMinecraft())) return;
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
        event.density = 0.06F;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onFogColor(EntityViewRenderEvent.FogColors event) {
        if (!storm(Minecraft.getMinecraft())) return;
        event.red = 0.86F;
        event.green = 0.9F;
        event.blue = 0.96F;
    }

    /** Снег по ветру вокруг игрока и вой ветра. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null || mc.isGamePaused()
            || !ColdHandler.inIceika(mc.thePlayer) || ColdMessage.clientPhase == Blizzard.CALM) return;
        boolean full = ColdMessage.clientPhase == Blizzard.STORM;
        float wx = ColdMessage.clientWindX, wz = ColdMessage.clientWindZ;
        Random r = mc.theWorld.rand;
        int n = full ? 30 : 6;
        for (int i = 0; i < n; i++) {
            double x = mc.thePlayer.posX + (r.nextDouble() - 0.5) * 16 - wx * 6;
            double y = mc.thePlayer.posY + (r.nextDouble() - 0.3) * 8;
            double z = mc.thePlayer.posZ + (r.nextDouble() - 0.5) * 16 - wz * 6;
            if (!mc.theWorld.canBlockSeeTheSky((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) continue;
            mc.theWorld.spawnParticle("snowshovel", x, y, z, wx * (full ? 0.9 : 0.4), -0.05, wz * (full ? 0.9 : 0.4));
        }
        if (mc.thePlayer.ticksExisted % 50 == 0) {
            float vol = full ? (Blizzard.exposed(mc.thePlayer) ? 0.9F : 0.35F) : 0.25F;
            mc.theWorld.playSound(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, "ambient.weather.rain", vol,
                0.45F, false);
        }
    }

    private static int argb(float a, int rgb) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | rgb;
    }

    /** Изморозь: светло-голубые полосы по краям, сходящие на нет к центру, и снежинки. */
    private void frost(int w, int h, float k) {
        int steps = 10, bandX = w / 5, bandY = h / 5;
        for (int s = 0; s < steps; s++) {
            float a = k * 0.6F * (1 - s / (float) steps);
            int c = argb(a, 0xDDF2FF);
            int dx = bandX * s / steps, dx2 = bandX * (s + 1) / steps, dy = bandY * s / steps, dy2 = bandY * (s + 1) / steps;
            drawRect(dx, 0, dx2, h, c);
            drawRect(w - dx2, 0, w - dx, h, c);
            drawRect(dx2, dy, w - dx2, dy2, c);
            drawRect(dx2, h - dy2, w - dx2, h - dy, c);
        }
        float cold = k * 85 + 15;
        for (float[] f : flakes) {
            if (cold < f[3]) continue;
            int x = (int) (f[0] * w), y = (int) (f[1] * h), s = (int) f[2];
            int c = argb(0.85F, 0xFFFFFF);
            drawRect(x - 2 * s, y, x + 2 * s + 1, y + 1, c); // горизонталь
            drawRect(x, y - 2 * s, x + 1, y + 2 * s + 1, c); // вертикаль
            for (int d = 1; d <= s; d++) { // диагонали
                drawRect(x - d, y - d, x - d + 1, y - d + 1, c);
                drawRect(x + d, y - d, x + d + 1, y - d + 1, c);
                drawRect(x - d, y + d, x - d + 1, y + d + 1, c);
                drawRect(x + d, y + d, x + d + 1, y + d + 1, c);
            }
        }
    }

    /** Градусник справа над полоской еды (выше пузырьков воздуха под водой). */
    private void thermometer(Minecraft mc, int w, int h, float cold, boolean warm) {
        int x = w / 2 + 10, y = h - (mc.thePlayer.isInWater() ? 59 : 49), len = 81;
        drawRect(x - 1, y - 1, x + len + 1, y + 4, warm ? 0xFFFF9020 : 0xFF202830);
        drawRect(x, y, x + len, y + 3, 0xFF3A4450);
        int fill = (int) (len * cold / 100F);
        int color = cold >= 100 ? 0xFFFFFFFF : cold >= 75 ? 0xFFBFE8FF : cold >= 50 ? 0xFF7FC8F0 : 0xFF3F9AD8;
        drawRect(x, y, x + fill, y + 3, color);
        for (int t = 1; t < 4; t++) drawRect(x + len * t / 4, y, x + len * t / 4 + 1, y + 3, 0x80000000); // отметки 25/50/75
    }
}
