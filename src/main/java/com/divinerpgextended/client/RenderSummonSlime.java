package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelSlime;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Слизень слизневого меча: ванильная модель слизня (ядро и полупрозрачная оболочка), в прыжке вытягивается. */
@SideOnly(Side.CLIENT)
public class RenderSummonSlime extends RenderLiving {

    private static final ResourceLocation TEX = new ResourceLocation("textures/entity/slime/slime.png");
    private final ModelBase shell = new ModelSlime(0);

    public RenderSummonSlime() {
        super(new ModelSlime(16), 0.3F);
    }

    @Override
    protected int shouldRenderPass(EntityLivingBase e, int pass, float pt) {
        if (e.isInvisible()) return 0;
        if (pass == 0) {
            setRenderPassModel(shell);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            return 1;
        }
        if (pass == 1) {
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
        return -1;
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float pt) {
        float stretch = e.onGround ? 1F : 1F + 0.25F * (float) Math.min(1, Math.abs(e.motionY) * 3);
        float s = 1.2F;
        GL11.glScalef(s / stretch, s * stretch, s / stretch);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }
}
