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

/** Король-лич: гуманоид ростом ~2.4 блока с морозным клинком Divine в руке и полоской здоровья босса. */
@SideOnly(Side.CLIENT)
public class RenderLichKing extends RenderBiped {

    public RenderLichKing() {
        super(new ModelBiped(), 0.6F);
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partial) {
        if (entity instanceof IBossDisplayData) DreBossBar.show(entity); // полоска в стиле Divine
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
