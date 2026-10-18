package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * Харон: высокая фигура в чёрном балахоне до земли (расширяется книзу, подол рваный), глубокий капюшон с острым верхом,
 * вместо лица тьма, широкие рукава, длинный шлейф за спиной; в правой руке шест-весло до земли. Ног не видно. Текстура
 * 128x64: раскладка ModelBiped + детали справа и снизу.
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
        robeLower.addBox(-5.5F, 15.0F, -4.0F, 11, 10, 8); // до земли и чуть ниже: подол лежит
        bipedBody.addChild(robeLower);
        tail = new ModelRenderer(this, 80, 0);
        tail.addBox(-5.0F, 0.0F, 0.0F, 10, 18, 1);
        tail.setRotationPoint(0.0F, 1.0F, 3.0F);
        bipedBody.addChild(tail);
        peak = new ModelRenderer(this, 104, 0);
        peak.addBox(-2.0F, -10.5F, -1.0F, 4, 3, 4); // острый верх капюшона, чуть назад
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
        blade.addBox(-2.5F, 22.0F, -5.0F, 4, 9, 1); // лопасть весла внизу
        bipedRightArm.addChild(blade);
    }

    @Override
    public void setRotationAngles(float swing, float amount, float age, float yaw, float pitch, float scale, Entity e) {
        super.setRotationAngles(swing, amount, age, yaw, pitch, scale, e);
        // стоит неподвижно, шест держит перед собой; руки почти не машут, шлейф чуть колышется
        bipedRightArm.rotateAngleX = -0.3F + MathHelper.cos(age * 0.05F) * 0.03F;
        bipedRightArm.rotateAngleZ = 0.05F;
        bipedLeftArm.rotateAngleX = 0.05F + MathHelper.cos(swing * 0.6662F) * 0.15F * amount;
        bipedLeftArm.rotateAngleZ = -0.08F;
        tail.rotateAngleX = 0.18F + MathHelper.cos(age * 0.07F) * 0.04F + amount * 0.5F;
    }
}
