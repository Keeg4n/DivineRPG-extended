package com.divinerpgextended.client;

import net.divinerpg.entities.vethea.EntityLadyLuna;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.vethea.LunaFight;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Крылья Леди Луны во второй фазе (полёт): два машущих светящихся крыла за спиной. */
@SideOnly(Side.CLIENT)
public class LunaWings {

    private static final ResourceLocation TEX = new ResourceLocation("divinerpgextended", "textures/entity/luna_wings.png");

    @SubscribeEvent
    public void onPost(RenderLivingEvent.Post e) {
        if (!(e.entity instanceof EntityLadyLuna) || LunaFight.phase((EntityLadyLuna) e.entity) != 2) return;
        EntityLadyLuna l = (EntityLadyLuna) e.entity;
        float yaw = l.renderYawOffset;
        float flap = 25 + 35 * (float) Math.sin(l.ticksExisted * 0.45);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX);
        GL11.glPushMatrix();
        GL11.glTranslated(e.x, e.y + l.height * 0.72, e.z);
        GL11.glRotatef(180 - yaw, 0, 1, 0);
        GL11.glTranslatef(0, 0, 0.3F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glColor4f(1, 1, 1, 0.95F);
        for (int side = -1; side <= 1; side += 2) {
            GL11.glPushMatrix();
            GL11.glRotatef(side * flap, 0, 1, 0);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            float w = 1.8F * side, h = 1.4F;
            float u0 = side < 0 ? 1 : 0, u1 = side < 0 ? 0 : 1;
            t.addVertexWithUV(0, h * 0.6, 0, u0, 0);
            t.addVertexWithUV(w, h * 0.6, 0, u1, 0);
            t.addVertexWithUV(w, -h * 0.4, 0, u1, 1);
            t.addVertexWithUV(0, -h * 0.4, 0, u0, 1);
            t.draw();
            GL11.glPopMatrix();
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }
}
