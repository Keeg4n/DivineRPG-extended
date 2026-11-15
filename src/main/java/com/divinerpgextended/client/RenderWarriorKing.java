package com.divinerpgextended.client;

import net.divinerpg.client.render.EntityResourceLocation;
import net.divinerpg.client.render.entity.twilight.model.ModelEnchantedWarrior;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntityWarriorKing;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Warrior King: the Divine Apalachia warrior twice as tall, with an Apalachia blade; on one knee - crouched and darkened. */
@SideOnly(Side.CLIENT)
public class RenderWarriorKing extends RenderBiped {

    public RenderWarriorKing() {
        super(new ModelEnchantedWarrior(), 1.0F);
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partial) {
        DreBossBar.show(entity);
        super.doRender(entity, x, y, z, yaw, partial);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partial) {
        EntityWarriorKing k = (EntityWarriorKing) entity;
        float s = EntityWarriorKing.SCALE;
        if (k.kneeling() > 0) {
            GL11.glScalef(s, s * 0.8F, s);
            GL11.glColor4f(0.6F, 0.5F, 0.7F, 1.0F);
            return;
        }
        GL11.glScalef(s, s, s);
        // brighter and more golden with each encounter
        float r = 0.85F + 0.075F * k.round();
        GL11.glColor4f(r, 0.8F, 1.0F, 1.0F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return EntityResourceLocation.apalachiaWarrior;
    }
}
