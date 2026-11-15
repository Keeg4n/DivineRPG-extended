package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * Charon: a tall figure in a black robe down to the ground (widening toward the bottom, a ragged hem), a deep hood with a pointed top,
 * darkness instead of a face, wide sleeves, a long train behind; in the right hand a pole-oar down to the ground. The legs are not visible. The 128x64
 * texture: ModelBiped layout + details on the right and at the bottom.
 */
public class ModelCharon extends ModelBiped {

    private final ModelRenderer robeUpper, robeLower, tail, peak, sleeveR, sleeveL, pole, blade;

    public ModelCharon() {
        super(0.0F, 0.0F, 128, 64);
        bipedLeftLeg.showModel = false;
        bipedRightLeg.showModel = false;
        robeUpper = new ModelRenderer(this, 40, 32);
        robeUpper.addBox(-4.5F, 9.0F, -3.0F, 9, 8, 6);
        bipedBody.addChild(robeUpper);
        robeLower = new ModelRenderer(this, 0, 32);
        robeLower.addBox(-5.5F, 15.0F, -4.0F, 11, 10, 8); // down to the ground and slightly lower: the hem lies on it
        bipedBody.addChild(robeLower);
        tail = new ModelRenderer(this, 80, 0);
        tail.addBox(-5.0F, 0.0F, 0.0F, 10, 18, 1);
        tail.setRotationPoint(0.0F, 1.0F, 3.0F);
        bipedBody.addChild(tail);
        peak = new ModelRenderer(this, 104, 0);
        peak.addBox(-2.0F, -10.5F, -1.0F, 4, 3, 4); // pointed top of the hood, slightly back
        bipedHead.addChild(peak);
        sleeveR = new ModelRenderer(this, 72, 32);
        sleeveR.addBox(-3.5F, 5.0F, -2.5F, 5, 6, 5);
        bipedRightArm.addChild(sleeveR);
        sleeveL = new ModelRenderer(this, 72, 32);
        sleeveL.mirror = true;
        sleeveL.addBox(-1.5F, 5.0F, -2.5F, 5, 6, 5);
        bipedLeftArm.addChild(sleeveL);
        pole = new ModelRenderer(this, 64, 0);
        pole.addBox(-1.5F, -14.0F, -4.5F, 1, 44, 1);
        bipedRightArm.addChild(pole);
        blade = new ModelRenderer(this, 70, 0);
        blade.addBox(-2.5F, 22.0F, -5.0F, 4, 9, 1); // oar blade at the bottom
        bipedRightArm.addChild(blade);
    }

    @Override
    public void setRotationAngles(float swing, float amount, float age, float yaw, float pitch, float scale, Entity e) {
        super.setRotationAngles(swing, amount, age, yaw, pitch, scale, e);
        // stands still, holds the pole in front; the arms barely swing, the train sways slightly
        bipedRightArm.rotateAngleX = -0.3F + MathHelper.cos(age * 0.05F) * 0.03F;
        bipedRightArm.rotateAngleZ = 0.05F;
        bipedLeftArm.rotateAngleX = 0.05F + MathHelper.cos(swing * 0.6662F) * 0.15F * amount;
        bipedLeftArm.rotateAngleZ = -0.08F;
        tail.rotateAngleX = 0.18F + MathHelper.cos(age * 0.07F) * 0.04F + amount * 0.5F;
    }
}
