package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Вьюжная ель: четыре яруса хвои на стволе, ходит на двух корнях, ветви-руки и светлые глаза на стволе. Текстура - tools/spruce_texture.py. */
@SideOnly(Side.CLIENT)
public class ModelBlizzardSpruce extends ModelBase {

    private final ModelRenderer trunk, tier1, tier2, tier3, top, rootL, rootR, armL, armR, eyes;

    public ModelBlizzardSpruce() {
        textureWidth = 128;
        textureHeight = 128;
        trunk = part(96, 36, -4, -36, -4, 8, 36, 8, 0, 16, 0);
        tier1 = part(0, 0, -14, -8, -14, 28, 8, 28, 0, -2, 0);
        tier2 = part(0, 36, -11, -8, -11, 22, 8, 22, 0, -12, 0);
        tier3 = part(0, 66, -8, -8, -8, 16, 8, 16, 0, -22, 0);
        top = part(64, 66, -4, -8, -4, 8, 8, 8, 0, -32, 0);
        rootL = part(64, 90, -3, 0, -3, 6, 8, 6, -5, 16, 0);
        rootR = part(64, 90, -3, 0, -3, 6, 8, 6, 5, 16, 0);
        armL = part(0, 100, 0, -2, -2, 16, 4, 4, 4, 2, 0);
        armR = part(0, 100, -16, -2, -2, 16, 4, 4, -4, 2, 0);
        eyes = part(44, 100, -3, 0, -4.5F, 6, 2, 1, 0, 6, 0);
    }

    private ModelRenderer part(int u, int v, float x, float y, float z, int w, int h, int d, float px, float py, float pz) {
        ModelRenderer r = new ModelRenderer(this, u, v);
        r.setTextureSize(textureWidth, textureHeight);
        r.addBox(x, y, z, w, h, d);
        r.setRotationPoint(px, py, pz);
        return r;
    }

    @Override
    public void render(Entity e, float swing, float amount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, amount, age, yaw, pitch, scale, e);
        for (ModelRenderer r : new ModelRenderer[] { trunk, tier1, tier2, tier3, top, rootL, rootR, armL, armR, eyes }) r.render(scale);
    }

    @Override
    public void setRotationAngles(float swing, float amount, float age, float yaw, float pitch, float scale, Entity e) {
        rootL.rotateAngleX = MathHelper.cos(swing * 0.5F) * 0.8F * amount;
        rootR.rotateAngleX = -MathHelper.cos(swing * 0.5F) * 0.8F * amount;
        float sway = MathHelper.sin(age * 0.08F) * 0.05F;
        tier1.rotateAngleZ = tier3.rotateAngleZ = sway;
        tier2.rotateAngleZ = top.rotateAngleZ = -sway;
        armL.rotateAngleZ = -0.35F + MathHelper.sin(age * 0.1F) * 0.12F;
        armR.rotateAngleZ = 0.35F - MathHelper.sin(age * 0.1F) * 0.12F;
    }
}
