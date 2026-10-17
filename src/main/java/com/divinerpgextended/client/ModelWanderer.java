package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * Странник: человек в капюшоне (слой поверх головы), плащ до колен, рюкзак со скаткой и посох из дерева мира с
 * навершием из камня мира в правой руке. Текстура 64x64: раскладка ModelBiped + детали в нижней половине.
 */
public class ModelWanderer extends ModelBiped {

    private final ModelRenderer cloak, pack, roll, staff, gem;

    public ModelWanderer() {
        super(0.0F, 0.0F, 64, 64);
        cloak = new ModelRenderer(this, 0, 32);
        cloak.addBox(-5.0F, 0.0F, 0.0F, 10, 18, 1);
        cloak.setRotationPoint(0.0F, 0.0F, 2.0F);
        bipedBody.addChild(cloak);
        pack = new ModelRenderer(this, 24, 32);
        pack.addBox(-3.0F, 1.0F, 3.0F, 6, 8, 3);
        bipedBody.addChild(pack);
        roll = new ModelRenderer(this, 24, 44);
        roll.addBox(-4.0F, -2.0F, 3.0F, 8, 3, 3);
        bipedBody.addChild(roll);
        staff = new ModelRenderer(this, 44, 32);
        staff.addBox(-1.5F, -6.0F, -3.5F, 1, 22, 1);
        bipedRightArm.addChild(staff);
        gem = new ModelRenderer(this, 50, 32);
        gem.addBox(-2.0F, -9.0F, -4.0F, 2, 3, 2);
        bipedRightArm.addChild(gem);
    }

    @Override
    public void setRotationAngles(float swing, float amount, float age, float yaw, float pitch, float scale, Entity e) {
        super.setRotationAngles(swing, amount, age, yaw, pitch, scale, e);
        // посох держит перед собой: рука почти не машет; плащ отлетает назад на ходу
        bipedRightArm.rotateAngleX = -0.35F + MathHelper.cos(swing * 0.6662F) * 0.25F * amount;
        // плащ крепится к телу, а пак и скатка поверх него - плащ отклоняется за рюкзак
        cloak.rotateAngleX = 0.12F + amount * 0.6F;
    }
}
