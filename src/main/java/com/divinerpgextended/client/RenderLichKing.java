package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Lich King: a humanoid about 2.4 blocks tall with Divine's frost blade in hand and a boss health bar. */
@SideOnly(Side.CLIENT)
public class RenderLichKing extends RenderBiped {

    public RenderLichKing() {
        super(new ModelBiped(), 0.6F);
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partial) {
        if (entity instanceof IBossDisplayData) DreBossBar.show(entity); // bar in the Divine style
        super.doRender(entity, x, y, z, yaw, partial);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partial) {
        GL11.glScalef(1.35F, 1.35F, 1.35F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return RenderFrostColossus.LICH;
    }
}
