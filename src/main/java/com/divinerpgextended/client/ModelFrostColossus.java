package com.divinerpgextended.client;

import net.minecraft.client.model.ModelIronGolem;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLivingBase;

import com.divinerpgextended.entity.EntityFrostColossus;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Модель Ледяного Колосса: железный голем с ледяной короной на голове и ледяными шипами на плечах. Анимация удара
 * - от своего таймера (у ванильной модели она жёстко завязана на железного голема).
 */
@SideOnly(Side.CLIENT)
public class ModelFrostColossus extends ModelIronGolem {

    public ModelFrostColossus() {
        super();
        // корона: обруч и зубцы, текстура в свободном углу 128x128
        ModelRenderer crown = new ModelRenderer(this, 96, 96).setTextureSize(128, 128);
        crown.addBox(-4.5F, -13.0F, -6.0F, 9, 1, 9);
        crown.setTextureOffset(96, 108)
            .addBox(-4.5F, -16.0F, -6.0F, 2, 3, 2);
        crown.addBox(2.5F, -16.0F, -6.0F, 2, 3, 2);
        crown.addBox(-1.0F, -18.0F, -6.0F, 2, 5, 2);
        crown.addBox(-4.5F, -15.0F, 1.0F, 2, 2, 2);
        crown.addBox(2.5F, -15.0F, 1.0F, 2, 2, 2);
        ironGolemHead.addChild(crown);
        // шипы на плечах
        ModelRenderer spikes = new ModelRenderer(this, 112, 108).setTextureSize(128, 128);
        spikes.addBox(-9.0F, -6.0F, -3.0F, 3, 4, 3);
        spikes.addBox(-8.0F, -9.0F, -2.0F, 2, 3, 2);
        spikes.addBox(6.0F, -6.0F, -3.0F, 3, 4, 3);
        spikes.addBox(6.0F, -9.0F, -2.0F, 2, 3, 2);
        spikes.addBox(-2.0F, -5.0F, 3.0F, 4, 3, 3);
        ironGolemBody.addChild(spikes);
    }

    private static float wave(float x, float period) {
        return (Math.abs(x % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
    }

    @Override
    public void setLivingAnimations(EntityLivingBase entity, float limb, float amount, float partial) {
        int t = entity instanceof EntityFrostColossus ? ((EntityFrostColossus) entity).getAttackTimer() : 0;
        if (t > 0) {
            ironGolemRightArm.rotateAngleX = -2.0F + 1.5F * wave(t - partial, 12.0F);
            ironGolemLeftArm.rotateAngleX = -2.0F + 1.5F * wave(t - partial, 12.0F);
        } else {
            ironGolemRightArm.rotateAngleX = (-0.2F + 1.5F * wave(limb, 13.0F)) * amount;
            ironGolemLeftArm.rotateAngleX = (-0.2F - 1.5F * wave(limb, 13.0F)) * amount;
        }
    }
}
