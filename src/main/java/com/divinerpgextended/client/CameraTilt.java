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
 * Перевёрнутый в воде мечтаний (ноги в озере в потолке Ветеи) видит картинку вверх ногами, камера - в голове, а не в пятках.
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
            if (last != 0) {
                GL11.glRotated(last, 0, 0, 1);
                // игра держит камеру (и точку, вокруг которой ходит камера от третьего лица) на высоте глаз от низа хитбокса -
                // у перевёрнутого там пятки, в воде; опускаем её к голове: сдвиг мира вверх, переведённый в оси камеры
                // (дальше идут повороты по наклону взгляда; вид спереди развёрнут на 180 - знак по оси взгляда другой)
                double pitch = Math.toRadians(v.prevRotationPitch + (v.rotationPitch - v.prevRotationPitch) * pt), s = DreamWaterView.HEAD_DROP;
                int front = Minecraft.getMinecraft().gameSettings.thirdPersonView == 2 ? -1 : 1;
                GL11.glTranslated(0, s * Math.cos(pitch), front * s * Math.sin(pitch));
            }
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
