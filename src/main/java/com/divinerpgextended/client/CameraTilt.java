package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntitySkyCart;
import com.divinerpgextended.entity.SkyCartView;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Вид из вагонетки Апалачии: на стене и потолке картинка наклоняется вокруг оси взгляда так, чтобы "верх" вагонетки
 * был вверху экрана, а сам взгляд остаётся настоящим (раньше поворачивался весь мир, и на потолке игра считала взгляд
 * "вверх" взглядом "вниз" - удары и клики уходили не туда). Мышь поворачивается на тот же угол ({@link #angles}).
 * Перевёрнутый в воде мечтаний (ноги в озере в потолке Ветеи) видит картинку вверх ногами.
 */
@SideOnly(Side.CLIENT)
public class CameraTilt {

    /** Последний крен (градусы): когда смотрим вдоль "верха" вагонетки, крен не определён - держим прежний. */
    private static double last;

    private static EntitySkyCart cart(Entity v) {
        return v != null && v.ridingEntity instanceof EntitySkyCart ? (EntitySkyCart) v.ridingEntity : null;
    }

    /** В начале EntityRenderer.orientCamera: крен вокруг оси взгляда. */
    public static void apply(float pt) {
        Entity v = Minecraft.getMinecraft().renderViewEntity;
        EntitySkyCart c = cart(v);
        if (c == null) {
            // в воде мечтаний (висячие озёра Ветеи) верх и низ поменялись: картинка вверх ногами
            last = v != null && com.divinerpgextended.block.BlockDreamWater.inverted(v) ? 180 : 0;
            if (last != 0) GL11.glRotated(last, 0, 0, 1);
            return;
        }
        double yaw = v.prevRotationYaw + (v.rotationYaw - v.prevRotationYaw) * pt;
        double pitch = v.prevRotationPitch + (v.rotationPitch - v.prevRotationPitch) * pt;
        double r = SkyCartView.roll(yaw, pitch, RenderSkyCart.up(c, pt));
        if (!Double.isNaN(r)) last = r;
        if (Math.abs(last) > 0.01) GL11.glRotated(last, 0, 0, 1);
    }

    /** Вместо Entity.setAngles в EntityRenderer.updateCameraAndRender: сдвиг мыши - в осях наклонённого экрана. */
    public static void angles(Entity e, float dx, float dy) {
        if (Math.abs(last) > 0.01 && (cart(e) != null || last == 180)) {
            float[] m = SkyCartView.mouse(dx, dy, last);
            dx = m[0];
            dy = m[1];
        }
        e.setAngles(dx, dy);
    }
}
