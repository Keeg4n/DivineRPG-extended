package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntityCarcinus;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Босс на модели моба Divine: увеличенная модель, своя подкраска и полоска здоровья. */
@SideOnly(Side.CLIENT)
public class RenderScaledBoss extends RenderLiving {

    private final float scale;
    private final float r, g, b;
    private final ResourceLocation texture;

    public RenderScaledBoss(ModelBase model, float scale, ResourceLocation texture, float r, float g, float b) {
        super(model, 0.5F * scale);
        this.scale = scale;
        this.texture = texture;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partial) {
        if (entity instanceof IBossDisplayData) DreBossBar.show(entity); // полоска в стиле Divine
        super.doRender(entity, x, y, z, yaw, partial);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partial) {
        GL11.glScalef(scale, scale, scale);
        // Карцинус в панцире темнеет: видно, что обычные удары не пройдут
        float k = entity instanceof EntityCarcinus && ((EntityCarcinus) entity).inShell() ? 0.45F : 1.0F;
        GL11.glColor4f(r * k, g * k, b * k, 1.0F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return texture;
    }
}
