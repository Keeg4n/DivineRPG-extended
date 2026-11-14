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
                // от третьего лица игра отодвинула камеру от точки в пятках до первой стены; опущенная к голове, она
                // уходила под пол (вид сквозь землю на пещеры) - подвигаем ближе, как если бы стену искали от головы
                if (Minecraft.getMinecraft().gameSettings.thirdPersonView > 0) {
                    double x = v.prevPosX + (v.posX - v.prevPosX) * pt, z = v.prevPosZ + (v.posZ - v.prevPosZ) * pt;
                    double y = v.prevPosY + (v.posY - v.prevPosY) * pt - (v.yOffset - 1.62F);
                    double closer = clip(v, x, y, z) - clip(v, x, y - s, z);
                    if (closer > 0) GL11.glTranslated(0, 0, closer);
                }
            }
            return;
        }
        double yaw = v.prevRotationYaw + (v.rotationYaw - v.prevRotationYaw) * pt;
        double pitch = v.prevRotationPitch + (v.rotationPitch - v.prevRotationPitch) * pt;
        double r = SkyCartView.roll(yaw, pitch, RenderSkyCart.up(c, pt));
        if (!Double.isNaN(r)) last = r;
        if (Math.abs(last) > 0.01) GL11.glRotated(last, 0, 0, 1);
    }

    /** Как далеко игра отодвинет камеру третьего лица от точки (x, y, z), пока не упрётся в блок (как EntityRenderer.orientCamera). */
    static double clip(Entity v, double x, double y, double z) {
        double d = 4.0D;
        float yaw = v.rotationYaw, pitch = v.rotationPitch;
        if (Minecraft.getMinecraft().gameSettings.thirdPersonView == 2) pitch += 180F;
        double dx = -Math.sin(yaw / 180F * Math.PI) * Math.cos(pitch / 180F * Math.PI) * d;
        double dz = Math.cos(yaw / 180F * Math.PI) * Math.cos(pitch / 180F * Math.PI) * d;
        double dy = -Math.sin(pitch / 180F * Math.PI) * d;
        for (int k = 0; k < 8; k++) {
            float a = ((k & 1) * 2 - 1) * 0.1F, b = ((k >> 1 & 1) * 2 - 1) * 0.1F, c = ((k >> 2 & 1) * 2 - 1) * 0.1F;
            net.minecraft.util.MovingObjectPosition hit = v.worldObj.rayTraceBlocks(net.minecraft.util.Vec3.createVectorHelper(x + a, y + b, z + c),
                net.minecraft.util.Vec3.createVectorHelper(x - dx + a + c, y - dy + b, z - dz + c));
            if (hit != null) {
                double h = hit.hitVec.distanceTo(net.minecraft.util.Vec3.createVectorHelper(x, y, z));
                if (h < d) d = h;
            }
        }
        return d;
    }

    /** Вагонетка сменила плоскость (пол - стена - потолок): взгляд седока поворачивается вместе с ней. */
    public static class Follow {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onTick(cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent e) {
            if (e.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null || mc.isGamePaused()) return;
            EntitySkyCart c = cart(mc.thePlayer);
            if (c == null) return;
            float[] a = c.prevNormal, b = c.normal();
            double[] look = SkyCartView.follow(SkyCartView.look(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), new double[] { a[0], a[1], a[2] },
                new double[] { b[0], b[1], b[2] });
            if (look == null) return;
            float[] yp = SkyCartView.angles(look, mc.thePlayer.rotationYaw);
            mc.thePlayer.rotationYaw = yp[0];
            mc.thePlayer.rotationPitch = yp[1];
        }
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
