package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.divinerpgextended.magic.ArcaneEffect;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Значки действующих арканных зелий над полоской арканы (справа внизу): склянка эффекта и сколько осталось. */
@SideOnly(Side.CLIENT)
public class ArcaneEffectsHud {

    private static final int[] TICKS = new int[ArcaneEffect.values().length], LEVELS = new int[ArcaneEffect.values().length];
    private static final RenderItem ITEMS = new RenderItem();

    public static void set(int[] ticks, int[] levels) {
        for (int i = 0; i < TICKS.length && i < ticks.length; i++) {
            TICKS[i] = ticks[i];
            LEVELS[i] = levels[i];
        }
    }

    public static int level(ArcaneEffect e) {
        return TICKS[e.ordinal()] > 0 ? LEVELS[e.ordinal()] : 0;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Minecraft.getMinecraft().isGamePaused()) return;
        for (int i = 0; i < TICKS.length; i++) if (TICKS[i] > 0) TICKS[i]--;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        int w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight(), x = w - 22, y = h - 52;
        for (ArcaneEffect ef : ArcaneEffect.values()) {
            int t = TICKS[ef.ordinal()];
            if (t <= 0) continue;
            ItemStack icon = new ItemStack(com.divinerpgextended.registry.ModItems.arcanePotion, 1, ef.ordinal() | (LEVELS[ef.ordinal()] > 1 ? 32 : 0));
            GL11.glPushMatrix();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            ITEMS.renderItemIntoGUI(mc.fontRenderer, mc.getTextureManager(), icon, x, y);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glPopMatrix();
            int s = t / 20;
            String time = s >= 60 ? s / 60 + "m" : s + "s";
            GL11.glPushMatrix();
            GL11.glScalef(0.5F, 0.5F, 1F);
            mc.fontRenderer.drawStringWithShadow(time, (x + 8) * 2 - mc.fontRenderer.getStringWidth(time) / 2, (y + 17) * 2, 0xFFFFFF);
            GL11.glPopMatrix();
            x -= 18;
            if (x < w / 2) {
                x = w - 22;
                y -= 24;
            }
        }
    }
}
