package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import com.divinerpgextended.entity.EntityFrostColossus;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Ледяной Колосс (голем x6, 17 блоков) и Король-лич, стоящий у него на левом плече. */
@SideOnly(Side.CLIENT)
public class RenderFrostColossus extends RenderScaledBoss {

    public static final ResourceLocation COLOSSUS = new ResourceLocation("divinerpgextended",
        "textures/entity/frost_colossus.png");
    public static final ResourceLocation LICH = new ResourceLocation("divinerpgextended", "textures/entity/lich_king.png");

    private final ModelBiped lich = new ModelBiped();

    public RenderFrostColossus() {
        super(new ModelFrostColossus(), EntityFrostColossus.SCALE, COLOSSUS, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderEquippedItems(EntityLivingBase entity, float partial) {
        super.renderEquippedItems(entity, partial);
        bindTexture(LICH);
        GL11.glPushMatrix();
        GL11.glColor4f(1, 1, 1, 1);
        // верх левого плеча в координатах модели голема (9 пикселей вбок, 9 вверх), лич ростом ~2.4 блока
        GL11.glTranslatef(0.5F, -0.5625F, 0.05F);
        float s = 2.4F / 1.8F / EntityFrostColossus.SCALE;
        GL11.glScalef(s, s, s);
        GL11.glTranslatef(0.0F, -1.5F, 0.0F);
        lich.isRiding = false;
        float t = entity.ticksExisted + partial;
        lich.render(entity, 0, 0, t, 0, 0, 0.0625F);
        GL11.glPopMatrix();
    }
}
