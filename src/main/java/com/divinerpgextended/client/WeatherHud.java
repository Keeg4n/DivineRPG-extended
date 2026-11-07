package com.divinerpgextended.client;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.world.twilight.WorldStateMessage;
import com.divinerpgextended.world.twilight.WorldWeather;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Погода сумеречных миров на экране: перегрев Эдема (градусник над полоской еды и марево по краям), дождь ливня в Диком лесу и гул
 * камнепада в Апалачии. Дождь включается на самом клиенте: сервер в этом мире о нём не знает, у него погода обычного мира.
 */
@SideOnly(Side.CLIENT)
public class WeatherHud extends Gui {

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        Minecraft mc = Minecraft.getMinecraft();
        float heat = WorldStateMessage.clientHeat;
        if (mc.thePlayer == null || mc.thePlayer.dimension != ConfigurationHelper.eden && heat <= 0) return;
        ScaledResolution res = event.resolution;
        int w = res.getScaledWidth(), h = res.getScaledHeight();
        GL11.glEnable(GL11.GL_BLEND);
        if (heat > 25) haze(w, h, (heat - 25) / 75F);
        int x = w / 2 + 10, y = h - (mc.thePlayer.isInWater() ? 59 : 49), len = 81;
        drawRect(x - 1, y - 1, x + len + 1, y + 4, 0xFF302010);
        drawRect(x, y, x + len, y + 3, 0xFF504030);
        int color = heat >= 100 ? 0xFFFFFFFF : heat >= 75 ? 0xFFFF5020 : heat >= 50 ? 0xFFFF9020 : 0xFFE8C040;
        drawRect(x, y, x + (int) (len * heat / 100F), y + 3, color);
        for (int t = 2; t < 4; t++) drawRect(x + len * t / 4, y, x + len * t / 4 + 1, y + 3, 0x80000000); // отметки 50 и 75
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Марево: тёплые полосы по краям, сходящие на нет к центру. */
    private void haze(int w, int h, float k) {
        int steps = 8, bandX = w / 6, bandY = h / 6;
        for (int s = 0; s < steps; s++) {
            int c = ((int) (k * 0.45F * (1 - s / (float) steps) * 255) << 24) | 0xFF8020;
            int dx = bandX * s / steps, dx2 = bandX * (s + 1) / steps, dy = bandY * s / steps, dy2 = bandY * (s + 1) / steps;
            drawRect(dx, 0, dx2, h, c);
            drawRect(w - dx2, 0, w - dx, h, c);
            drawRect(dx2, dy, w - dx2, dy2, c);
            drawRect(dx2, h - dy2, w - dx2, h - dy, c);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null || mc.isGamePaused()) return;
        int phase = WorldStateMessage.clientPhase;
        if (mc.thePlayer.dimension == ConfigurationHelper.wildwood) {
            mc.theWorld.getWorldInfo().setRaining(phase == WorldWeather.ACTIVE);
        } else if (mc.thePlayer.dimension == ConfigurationHelper.apalachia && phase != WorldWeather.CALM && mc.thePlayer.ticksExisted % 40 == 0) {
            mc.theWorld.playSound(mc.thePlayer.posX, mc.thePlayer.posY - 3, mc.thePlayer.posZ, "random.explode", phase == WorldWeather.ACTIVE ? 0.35F : 0.15F, 0.4F, false);
        }
    }
}
