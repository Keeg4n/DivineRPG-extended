package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.perk.FrostBite;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Вмороженный моб ({@link FrostBite}) - в полупрозрачной ледяной глыбе по размеру тела, как замороженные ледогрибом
 * зомби; к концу глыба тает (прозрачнее). Плюс строка про обморожение в подсказке ледяного оружия.
 */
@SideOnly(Side.CLIENT)
public class FrostRender {

    @SubscribeEvent
    public void onPost(RenderLivingEvent.Post e) {
        Long end = FrostBite.CLIENT.get(e.entity.getEntityId());
        if (end == null) return;
        long left = end - System.currentTimeMillis();
        if (left <= 0 || e.entity.isDead) {
            FrostBite.CLIENT.remove(e.entity.getEntityId());
            return;
        }
        float alpha = 0.62F * Math.min(1F, left / 500F); // последние полсекунды тает
        double w = e.entity.width / 2 + 0.15, h = e.entity.height + 0.15;
        IIcon ice = Blocks.ice.getIcon(0, 0);
        double u0 = ice.getMinU(), u1 = ice.getMaxU(), v0 = ice.getMinV(), v1 = ice.getMaxV();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPushMatrix();
        GL11.glTranslated(e.x, e.y - 0.05, e.z);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glColor4f(0.85F, 0.95F, 1F, alpha);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        // четыре стены, крыша и дно
        t.addVertexWithUV(-w, 0, -w, u0, v1); t.addVertexWithUV(-w, h, -w, u0, v0); t.addVertexWithUV(w, h, -w, u1, v0); t.addVertexWithUV(w, 0, -w, u1, v1);
        t.addVertexWithUV(w, 0, w, u0, v1); t.addVertexWithUV(w, h, w, u0, v0); t.addVertexWithUV(-w, h, w, u1, v0); t.addVertexWithUV(-w, 0, w, u1, v1);
        t.addVertexWithUV(-w, 0, w, u0, v1); t.addVertexWithUV(-w, h, w, u0, v0); t.addVertexWithUV(-w, h, -w, u1, v0); t.addVertexWithUV(-w, 0, -w, u1, v1);
        t.addVertexWithUV(w, 0, -w, u0, v1); t.addVertexWithUV(w, h, -w, u0, v0); t.addVertexWithUV(w, h, w, u1, v0); t.addVertexWithUV(w, 0, w, u1, v1);
        t.addVertexWithUV(-w, h, -w, u0, v0); t.addVertexWithUV(-w, h, w, u0, v1); t.addVertexWithUV(w, h, w, u1, v1); t.addVertexWithUV(w, h, -w, u1, v0);
        t.addVertexWithUV(-w, 0, w, u0, v1); t.addVertexWithUV(-w, 0, -w, u0, v0); t.addVertexWithUV(w, 0, -w, u1, v0); t.addVertexWithUV(w, 0, w, u1, v1);
        t.draw();
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        if (e.itemStack == null || !FrostBite.iceWeapon(e.itemStack.getItem()) || !com.divinerpgextended.Config.iceFreeze) return;
        e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.frost", FrostBite.FREEZE / 20F));
    }
}
