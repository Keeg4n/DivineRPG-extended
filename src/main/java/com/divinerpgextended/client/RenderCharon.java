package com.divinerpgextended.client;

import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/** Харон: модель в балахоне ({@link ModelCharon}), на голову выше игрока. */
public class RenderCharon extends RenderBiped {

    private static final ResourceLocation TEX = new ResourceLocation("divinerpgextended", "textures/entity/charon.png");
    private static final float SCALE = 1.2F;

    public RenderCharon() {
        super(new ModelCharon(), 0.6F);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float pt) {
        GL11.glScalef(SCALE, SCALE, SCALE);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }
}
