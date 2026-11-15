package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * Wanderer: a hooded person (a layer over the head), a cloak to the knees, a backpack with a bedroll and a staff of world wood with a
 * world stone head in the right hand. The 64x64 texture: ModelBiped layout + details in the lower half.
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
        // holds the staff in front: the arm barely swings; the cloak flies back while walking
        bipedRightArm.rotateAngleX = -0.35F + MathHelper.cos(swing * 0.6662F) * 0.25F * amount;
        // the cloak is attached to the body, and the pack and bedroll are on top of it - the cloak deflects behind the backpack
        cloak.rotateAngleX = 0.12F + amount * 0.6F;
    }
}
