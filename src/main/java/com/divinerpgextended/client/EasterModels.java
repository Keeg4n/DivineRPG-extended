package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/** Easter egg models: the suit and the crowbar are detailed meshes ({@link EasterMesh}), the sofa and the grenade are boxes (textures - ~/mc/tools/easter_tex.py). */
public final class EasterModels {

    private EasterModels() {}

    /**
     * The H.E.V. suit from a detailed model ({@link EasterMesh}, ~/mc/tools/hev_model.py): the pose is like the player's armor (ModelBiped
     * bones), on each bone are the details of the worn part: helmet or goggles, torso with arms, leggings with a belt, boots.
     */
    public static class HevArmor extends ModelBiped {

        public static final EasterMesh MESH = new EasterMesh("hev");
        /** Which part is drawn in this armor pass: helmet, goggles, chest, legs, boots. */
        public String piece = "chest";

        public HevArmor() {
            super(0F);
        }

        @Override
        public void render(Entity e, float f, float f1, float f2, float f3, float f4, float scale) {
            setRotationAngles(f, f1, f2, f3, f4, scale, e);
            part(bipedHead, "head", scale);
            part(bipedBody, "body", scale);
            part(bipedRightArm, "rightArm", scale);
            part(bipedLeftArm, "leftArm", scale);
            part(bipedRightLeg, "rightLeg", scale);
            part(bipedLeftLeg, "leftLeg", scale);
        }

        private void part(ModelRenderer bone, String name, float scale) {
            if (!MESH.has(piece, name)) return;
            org.lwjgl.opengl.GL11.glPushMatrix();
            bone.postRender(scale);
            MESH.render(piece, name, scale);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }

    /**
     * The sofa (128x64 texture: velvet in row 0, dark wood of the legs in row 28). Two blocks wide along X, the back behind
     * (+Z), the sitter faces -Z. The bottom is y=24. The RPG-7 on the cushions is a separate mesh ({@link EasterClient.SofaRenderer}).
     */
    public static class Sofa extends ModelBase {

        final ModelRenderer frame;

        public Sofa() {
            textureWidth = 128;
            textureHeight = 64;
            frame = new ModelRenderer(this, 0, 0);
            frame.addBox(-16F, 15F, -7F, 32, 6, 14);            // base
            add(frame, 0, 0, -13F, 12F, -7F, 13, 3, 11);      // seat cushions
            add(frame, 0, 0, 0F, 12F, -7F, 13, 3, 11);
            add(frame, 0, 0, -16F, 2F, 4F, 32, 13, 4);         // back
            add(frame, 0, 0, -13F, 4F, 2F, 13, 8, 2);          // back cushions
            add(frame, 0, 0, 0F, 4F, 2F, 13, 8, 2);
            add(frame, 0, 0, -16F, 8F, -7F, 3, 7, 15);         // armrests
            add(frame, 0, 0, 13F, 8F, -7F, 3, 7, 15);
            for (float[] l : new float[][] { { -15, -6 }, { 13, -6 }, { -15, 4 }, { 13, 4 } }) add(frame, 0, 28, l[0], 21F, l[1], 2, 3, 2);
        }

        private void add(ModelRenderer parent, int u, int v, float x, float y, float z, int w, int h, int d) {
            ModelRenderer r = new ModelRenderer(this, u, v);
            r.addBox(x, y, z, w, h, d);
            parent.addChild(r);
        }

        @Override
        public void render(Entity e, float a, float b, float c, float d, float f, float scale) {
            frame.render(scale);
        }
    }

}
