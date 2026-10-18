package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntitySkyCart;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Вид из вагонетки Апалачии: мир поворачивается вместе с ней - на стене наклонён, на потолке вверх ногами (раньше камера
 * держала горизонт, как стабилизатор). Взгляд мышью - относительно вагонетки. Тот же поворот, что у седока в виде от
 * 3-го лица ({@link RenderSkyCart#tilt}), только обратный: поворачиваем мир, а не игрока.
 */
@SideOnly(Side.CLIENT)
public class CameraTilt {

    public static void apply(float pt) {
        Entity v = Minecraft.getMinecraft().renderViewEntity;
        if (v == null || !(v.ridingEntity instanceof EntitySkyCart)) return;
        EntitySkyCart c = (EntitySkyCart) v.ridingEntity;
        double[] t = RenderSkyCart.tilt(c, RenderSkyCart.up(c, pt));
        if (t == null) return;
        GL11.glRotated(-t[0], t[1], 0, t[2]);
    }
}
