package com.divinerpgextended.client;

import net.divinerpg.client.render.EntityResourceLocation;
import net.divinerpg.client.render.entity.twilight.model.ModelSkythernFiend;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntityWindLord;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Повелитель ветров: изверг Скайтерна из Divine, крупный, бледно-голубой и полупрозрачный, как облако. */
@SideOnly(Side.CLIENT)
public class RenderWindLord extends RenderLiving {

    public RenderWindLord() {
        super(new ModelSkythernFiend(), 1.0F);
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partial) {
        DreBossBar.show(entity);
        super.doRender(entity, x, y, z, yaw, partial);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partial) {
        GL11.glScalef(EntityWindLord.SCALE * 0.92F, EntityWindLord.SCALE * 0.92F, EntityWindLord.SCALE * 0.92F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float pulse = 0.8F + 0.1F * (float) Math.sin((entity.ticksExisted + partial) * 0.1);
        GL11.glColor4f(0.8F, 0.92F, 1.0F, pulse);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return EntityResourceLocation.skythernFiend;
    }
}
