package com.divinerpgextended.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.SideOnly;
import cpw.mods.fml.relauncher.Side;

/**
 * Маленькая золотая корона на модели босса, чтобы он выделялся среди обычных мобов того же вида (Карцинус, Снежный Глакон,
 * Древний драмкрикс). Крепится к части модели (голове или панцирю) и поворачивается вместе с ней: обод из четырёх планок,
 * зубцы по углам и посередине сторон, спереди и по бокам - камни цвета босса. Размеры - в пикселях модели.
 */
@SideOnly(Side.CLIENT)
public final class BossCrown {

    private static final Map<String, ModelRenderer> PARTS = new HashMap<String, ModelRenderer>();
    private static final int GOLD = 0xF6D25E, GOLD_DARK = 0xC49A2A;

    private final String part;
    private final float cx, top, cz, size;
    private final int gem;

    /** part - поле модели Divine; (cx, top, cz) - середина верха части в её координатах; size - ширина короны. */
    public BossCrown(String part, float cx, float top, float cz, float size, int gem) {
        this.part = part;
        this.cx = cx;
        this.top = top;
        this.cz = cz;
        this.size = size;
        this.gem = gem;
    }

    private ModelRenderer find(ModelBase m) {
        String key = m.getClass().getName() + "#" + part;
        if (PARTS.containsKey(key)) return PARTS.get(key);
        ModelRenderer r = null;
        try {
            r = (ModelRenderer) ReflectionHelper.getPrivateValue((Class) m.getClass(), m, part);
        } catch (Throwable ignored) {}
        PARTS.put(key, r);
        return r;
    }

    /** Нарисовать после модели (части уже повёрнуты), scale - масштаб модели (0.0625). */
    public void render(ModelBase model, float scale) {
        ModelRenderer p = find(model);
        if (p == null || p.isHidden || !p.showModel) return;
        GL11.glPushMatrix();
        p.postRender(scale);
        GL11.glTranslatef(cx * scale, top * scale, cz * scale);
        GL11.glScalef(scale, scale, scale);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        float h = size / 2, th = 0.6F, band = 1.2F, spike = 1.4F;
        // обод (y вниз: верх части - 0, корона выше - отрицательные y)
        box(t, -h, -band, -h, h, 0, -h + th, GOLD);
        box(t, -h, -band, h - th, h, 0, h, GOLD);
        box(t, -h, -band, -h + th, -h + th, 0, h - th, GOLD);
        box(t, h - th, -band, -h + th, h, 0, h - th, GOLD);
        // зубцы: углы и середины сторон
        float[][] at = { { -h, -h }, { h - th, -h }, { -h, h - th }, { h - th, h - th }, { -th / 2, -h }, { -th / 2, h - th }, { -h, -th / 2 }, { h - th, -th / 2 } };
        for (int i = 0; i < at.length; i++) {
            float sp = i < 4 ? spike : spike * 1.4F;
            box(t, at[i][0], -band - sp, at[i][1], at[i][0] + th, -band, at[i][1] + th, i < 4 ? GOLD : GOLD_DARK);
        }
        // камни на ободе: спереди (-z) и по бокам
        float g = 0.45F;
        box(t, -g, -band * 0.8F, -h - 0.25F, g, -band * 0.2F, -h, gem);
        box(t, -h - 0.25F, -band * 0.8F, -g, -h, -band * 0.2F, g, gem);
        box(t, h, -band * 0.8F, -g, h + 0.25F, -band * 0.2F, g, gem);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    private static void face(Tessellator t, int rgb, float k, float nx, float ny, float nz) {
        int r = (int) ((rgb >> 16 & 255) * k), g = (int) ((rgb >> 8 & 255) * k), b = (int) ((rgb & 255) * k);
        t.setColorOpaque(r, g, b);
        t.setNormal(nx, ny, nz);
    }

    /** Коробка от (x0, y0, z0) до (x1, y1, z1); верх (меньший y) светлее, бока темнее. */
    private static void box(Tessellator t, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        face(t, rgb, 1.0F, 0, -1, 0); // верх
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y0, z1);
        t.addVertex(x0, y0, z1);
        face(t, rgb, 0.55F, 0, 1, 0); // низ
        t.addVertex(x0, y1, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y1, z0);
        t.addVertex(x0, y1, z0);
        face(t, rgb, 0.8F, 0, 0, -1); // перед
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x0, y0, z0);
        face(t, rgb, 0.8F, 0, 0, 1); // зад
        t.addVertex(x0, y0, z1);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x0, y1, z1);
        face(t, rgb, 0.7F, -1, 0, 0); // бок
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z1);
        t.addVertex(x0, y1, z0);
        face(t, rgb, 0.7F, 1, 0, 0); // бок
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y0, z0);
    }
}
