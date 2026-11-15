package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * Eye of the Cyclops Wand: a veined eye with a red iris, an optic nerve tail behind it and two short leathery stubs on the sides.
 * The stubs flap, the tail sways. Texture 64x32, made by tools/summon_textures.py.
 */
public class ModelSummonEye extends ModelBase {

    private final ModelRenderer eye, tail, left, right;

    public ModelSummonEye() {
        textureWidth = 64;
        textureHeight = 32;
        eye = new ModelRenderer(this, 0, 0);
        eye.addBox(-4F, -4F, -4F, 8, 8, 8);
        eye.setRotationPoint(0F, 20F, 0F);
        tail = new ModelRenderer(this, 32, 0);
        tail.addBox(-1.5F, -1.5F, 0F, 3, 3, 2);
        tail.setTextureOffset(32, 6).addBox(-1F, -1.5F, 2F, 2, 2, 2);
        tail.setTextureOffset(32, 11).addBox(-0.5F, -1.5F, 4F, 1, 1, 2);
        tail.setRotationPoint(0F, 20F, 4F);
        left = new ModelRenderer(this, 44, 0);
        left.addBox(-3F, -0.5F, -1.5F, 3, 1, 3);
        left.setRotationPoint(-4F, 19.5F, -0.5F);
        right = new ModelRenderer(this, 44, 0);
        right.mirror = true;
        right.addBox(0F, -0.5F, -1.5F, 3, 1, 3);
        right.setRotationPoint(4F, 19.5F, -0.5F);
    }

    @Override
    public void render(Entity e, float swing, float swingAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, e);
        eye.render(scale);
        tail.render(scale);
        left.render(scale);
        right.render(scale);
    }

    @Override
    public void setRotationAngles(float swing, float swingAmount, float age, float yaw, float pitch, float scale, Entity e) {
        float flap = MathHelper.cos(age * 0.6F) * 0.5F;
        left.rotateAngleZ = flap;
        right.rotateAngleZ = -flap;
        tail.rotateAngleY = MathHelper.sin(age * 0.15F) * 0.35F;
        tail.rotateAngleX = MathHelper.cos(age * 0.11F) * 0.12F;
    }
}
