package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * Frost of the Sound of Carols: a flying snowflake - three crossing ice bars with branches spin around a core with a face, the whole
 * flake bobs. Texture 32x32, made by tools/summon_textures.py.
 */
public class ModelSummonFrost extends ModelBase {

    private final ModelRenderer core;
    private final ModelRenderer[] bars = new ModelRenderer[3];

    public ModelSummonFrost() {
        textureWidth = 32;
        textureHeight = 32;
        core = new ModelRenderer(this, 0, 0);
        core.addBox(-1.5F, -1.5F, -1F, 3, 3, 2);
        core.setRotationPoint(0F, 18F, 0F);
        for (int i = 0; i < bars.length; i++) {
            ModelRenderer b = new ModelRenderer(this, 0, 6);
            b.addBox(-0.5F, -6F, -0.5F, 1, 12, 1);
            b.setTextureOffset(12, 0).addBox(-1.5F, -5F, -0.5F, 3, 1, 1);
            b.setTextureOffset(12, 0).addBox(-1.5F, 4F, -0.5F, 3, 1, 1);
            b.setRotationPoint(0F, 18F, 0F);
            bars[i] = b;
        }
    }

    @Override
    public void render(Entity e, float swing, float swingAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, e);
        core.render(scale);
        for (ModelRenderer b : bars) b.render(scale);
    }

    @Override
    public void setRotationAngles(float swing, float swingAmount, float age, float yaw, float pitch, float scale, Entity e) {
        float bob = 18F + MathHelper.sin(age * 0.1F) * 0.6F;
        core.rotationPointY = bob;
        for (int i = 0; i < bars.length; i++) {
            bars[i].rotationPointY = bob;
            bars[i].rotateAngleZ = i * (float) Math.PI / 3F + age * 0.08F;
        }
    }
}
