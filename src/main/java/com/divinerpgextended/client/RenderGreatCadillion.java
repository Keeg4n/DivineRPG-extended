package com.divinerpgextended.client;

import net.divinerpg.client.render.EntityResourceLocation;
import net.divinerpg.client.render.entity.twilight.model.ModelCadillion;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntityGreatCadillion;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the Great Cadillion with the model and textures of Divine's cadillions (taken from Divine itself during the game).
 * Skythern has no cadillion of its own - we take the Eden texture and tint it a cold color.
 */
@SideOnly(Side.CLIENT)
public class RenderGreatCadillion extends RenderLiving {

    private static final float SCALE = 2.5F;

    public RenderGreatCadillion() {
        super(new ModelCadillion(), 1.5F);
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partial) {
        DreBossBar.show((EntityGreatCadillion) entity); // bar in the Divine style
        super.doRender(entity, x, y, z, yaw, partial);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partial) {
        GL11.glScalef(SCALE, SCALE, SCALE);
        if (((EntityGreatCadillion) entity).getVariant() == 3) GL11.glColor4f(0.75F, 0.85F, 1.0F, 1.0F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        switch (((EntityGreatCadillion) entity).getVariant()) {
            case 1:
                return EntityResourceLocation.wildwoodCadillion;
            case 2:
                return EntityResourceLocation.apalachiaCadillion;
            default:
                return EntityResourceLocation.edenCadillion;
        }
    }
}
