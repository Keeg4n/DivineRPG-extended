package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/** Модели пасхалок: костюм и монтировка - детальные меши ({@link EasterMesh}), диван и граната - коробки (текстуры - ~/mc/tools/easter_tex.py). */
public final class EasterModels {

    private EasterModels() {}

    /**
     * Костюм H.E.V. из детальной модели ({@link EasterMesh}, ~/mc/tools/hev_model.py): поза - как у брони игрока (кости
     * ModelBiped), на каждой кости - свои детали надетой части: шлем или очки, торс с руками, поножи с поясом, ботинки.
     */
    public static class HevArmor extends ModelBiped {

        public static final EasterMesh MESH = new EasterMesh("hev");
        /** Какая часть рисуется в этот проход брони: helmet, goggles, chest, legs, boots. */
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
     * Диван с РПГ-7 (текстура 128x64, образцы по строкам: бархат 0, тёмное дерево 28, олива 36, светлое дерево 46, чёрный 54,
     * металл 59). Два блока в ширину по X, спинка сзади (+Z), сидящий смотрит в -Z. Низ - y=24.
     */
    public static class Sofa extends ModelBase {

        final ModelRenderer frame, rpg;

        public Sofa() {
            textureWidth = 128;
            textureHeight = 64;
            frame = new ModelRenderer(this, 0, 0);
            frame.addBox(-16F, 15F, -7F, 32, 6, 14);            // основание
            add(frame, 0, 0, -13F, 12F, -7F, 13, 3, 11);      // подушки сиденья
            add(frame, 0, 0, 0F, 12F, -7F, 13, 3, 11);
            add(frame, 0, 0, -16F, 2F, 4F, 32, 13, 4);         // спинка
            add(frame, 0, 0, -13F, 4F, 2F, 13, 8, 2);          // подушки спинки
            add(frame, 0, 0, 0F, 4F, 2F, 13, 8, 2);
            add(frame, 0, 0, -16F, 8F, -7F, 3, 7, 15);         // подлокотники
            add(frame, 0, 0, 13F, 8F, -7F, 3, 7, 15);
            for (float[] l : new float[][] { { -15, -6 }, { 13, -6 }, { -15, 4 }, { 13, 4 } }) add(frame, 0, 28, l[0], 21F, l[1], 2, 3, 2);
            // РПГ-7 на подушках: чуть по диагонали, граната лежит на подлокотнике
            rpg = new ModelRenderer(this, 0, 36);
            rpg.setRotationPoint(-2F, 10.5F, -2F);
            rpg.rotateAngleY = 0.3F;
            rpg.rotateAngleZ = -0.2F;
            rpg.addBox(-13F, -1F, -1F, 26, 2, 2);               // труба
            add(rpg, 0, 36, -15F, -1.5F, -1.5F, 2, 3, 3);      // раструб
            add(rpg, 0, 46, -4F, -1.5F, -1.5F, 7, 3, 3);       // деревянная накладка
            add(rpg, 0, 54, -1F, 1F, -0.5F, 1, 3, 1);          // рукоять
            add(rpg, 0, 54, -7F, 1F, -0.5F, 1, 3, 1);          // задняя рукоять
            add(rpg, 0, 59, 1F, -2.5F, -0.5F, 1, 2, 1);        // прицел
            add(rpg, 0, 36, 13F, -2.5F, -2.5F, 5, 5, 5);       // граната ПГ-7: луковица
            add(rpg, 0, 36, 18F, -1.5F, -1.5F, 3, 3, 3);       //   конус
            add(rpg, 0, 59, 21F, -0.5F, -0.5F, 1, 1, 1);       //   носик
        }

        private void add(ModelRenderer parent, int u, int v, float x, float y, float z, int w, int h, int d) {
            ModelRenderer r = new ModelRenderer(this, u, v);
            r.addBox(x, y, z, w, h, d);
            parent.addChild(r);
        }

        @Override
        public void render(Entity e, float a, float b, float c, float d, float f, float scale) {
            frame.render(scale);
            rpg.render(scale);
        }
    }

    /** Граната ПГ-7 в полёте (текстура 64x32): нос в +Z. */
    public static class Pg7 extends ModelBase {

        final ModelRenderer body;

        public Pg7() {
            textureWidth = 64;
            textureHeight = 32;
            body = new ModelRenderer(this, 0, 0);
            body.addBox(-1F, -1F, -6F, 2, 2, 8);                // корпус
            sub(0, 10, -2F, -2F, 2F, 4, 4, 4);                 // луковица
            sub(0, 10, -1F, -1F, 6F, 2, 2, 3);                 // конус
            sub(0, 25, -0.5F, -0.5F, 9F, 1, 1, 1);             // носик
            sub(0, 18, -3F, -0.5F, -7F, 6, 1, 2);              // стабилизаторы
            sub(0, 18, -0.5F, -3F, -7F, 1, 6, 2);
        }

        private void sub(int u, int v, float x, float y, float z, int w, int h, int d) {
            ModelRenderer r = new ModelRenderer(this, u, v);
            r.addBox(x, y, z, w, h, d);
            body.addChild(r);
        }

        public void render(float scale) {
            body.render(scale);
        }
    }
}
