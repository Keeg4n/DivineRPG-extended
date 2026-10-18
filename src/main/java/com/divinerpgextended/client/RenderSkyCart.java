package com.divinerpgextended.client;

import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelMinecart;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntitySkyCart;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Вагонетка Апалачии: модель ванильной вагонетки, фиолетовая, повёрнута по пути - колёсами к опоре (на стене боком, на
 * потолке вверх дном, на горке наклонно), носом по ходу. Седок ({@link Rider}) поворачивается вместе с ней.
 */
@SideOnly(Side.CLIENT)
public class RenderSkyCart extends Render {

    private static final ResourceLocation TEX = new ResourceLocation("textures/entity/minecart.png");
    private final ModelBase model = new ModelMinecart();

    public RenderSkyCart() {
        shadowSize = 0.4F;
    }

    /** Нормаль пути между тиками. */
    static double[] up(EntitySkyCart c, float pt) {
        float[] a = c.prevNormal, b = c.normal();
        double[] n = { a[0] + (b[0] - a[0]) * pt, a[1] + (b[1] - a[1]) * pt, a[2] + (b[2] - a[2]) * pt };
        double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        return l < 1e-4 ? new double[] { 0, 1, 0 } : new double[] { n[0] / l, n[1] / l, n[2] / l };
    }

    /**
     * Наклон по кратчайшему повороту "вверх" -> нормаль (взгляд не крутится вокруг себя): {угол в градусах, ось x, ось z};
     * на потолке ось - вбок от хода. null - на полу, наклона нет.
     */
    static double[] tilt(EntitySkyCart c, double[] up) {
        if (up[1] > 0.999) return null;
        double ax = up[2], az = -up[0];
        if (ax * ax + az * az < 1e-6) {
            float[] f = c.course();
            ax = f[2];
            az = -f[0];
            if (ax * ax + az * az < 1e-6) ax = 1;
        }
        return new double[] { Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, up[1])))), ax, az };
    }

    /** Поворот модели: X - по ходу, Y - по нормали, Z - вбок (матрица по столбцам для glMultMatrix). */
    static FloatBuffer basis(EntitySkyCart c, double[] up) {
        float[] f0 = c.course();
        double d = f0[0] * up[0] + f0[1] * up[1] + f0[2] * up[2];
        double[] f = { f0[0] - up[0] * d, f0[1] - up[1] * d, f0[2] - up[2] * d };
        double l = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        if (l < 1e-4) { // курс вдоль нормали - любой перпендикуляр
            f = Math.abs(up[0]) < 0.9 ? new double[] { 1, 0, 0 } : new double[] { 0, 0, 1 };
            d = f[0] * up[0] + f[1] * up[1] + f[2] * up[2];
            f = new double[] { f[0] - up[0] * d, f[1] - up[1] * d, f[2] - up[2] * d };
            l = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        }
        f = new double[] { f[0] / l, f[1] / l, f[2] / l };
        double[] s = { f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0] };
        FloatBuffer m = BufferUtils.createFloatBuffer(16);
        m.put(new float[] { (float) f[0], (float) f[1], (float) f[2], 0, (float) up[0], (float) up[1], (float) up[2], 0,
            (float) s[0], (float) s[1], (float) s[2], 0, 0, 0, 0, 1 });
        m.flip();
        return m;
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
        EntitySkyCart c = (EntitySkyCart) e;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + c.height / 2, z);
        GL11.glMultMatrix(basis(c, up(c, partial)));
        GL11.glScalef(-1, -1, 1);
        GL11.glColor4f(0.85F, 0.7F, 1.0F, 1.0F);
        bindEntityTexture(e);
        model.render(e, 0, 0, -0.1F, 0, 0, 0.0625F);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }

    /**
     * Седок вагонетки Апалачии: на стене и потолке рисуется повёрнутым вместе с ней - сидит внутри, а не торчит рядом
     * стоймя. Его настоящая позиция - у глаз (камера и рамка не в блоках), рисунок переносится к сиденью.
     */
    public static class Rider {

        private final Set<EntityPlayer> pushed = new HashSet<EntityPlayer>();

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPre(RenderPlayerEvent.Pre e) {
            EntityPlayer p = e.entityPlayer;
            if (!(p.ridingEntity instanceof EntitySkyCart)) return;
            EntitySkyCart c = (EntitySkyCart) p.ridingEntity;
            float pt = e.partialRenderTick;
            double[] up = up(c, pt);
            if (up[1] > 0.999) return; // на полу - как в обычной вагонетке
            double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - RenderManager.renderPosX;
            double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - RenderManager.renderPosY - p.yOffset;
            double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - RenderManager.renderPosZ;
            double mx = c.lastTickPosX + (c.posX - c.lastTickPosX) * pt - RenderManager.renderPosX;
            double my = c.lastTickPosY + (c.posY - c.lastTickPosY) * pt - RenderManager.renderPosY + c.height / 2;
            double mz = c.lastTickPosZ + (c.posZ - c.lastTickPosZ) * pt - RenderManager.renderPosZ;
            GL11.glPushMatrix();
            // точка ног сиденья: ниже середины по нормали, как у ванильной вагонетки
            GL11.glTranslated(mx - up[0] * EntitySkyCart.SEAT, my - up[1] * EntitySkyCart.SEAT, mz - up[2] * EntitySkyCart.SEAT);
            double[] t = tilt(c, up);
            GL11.glRotated(t[0], t[1], 0, t[2]);
            // сам игрок рисуется от своей позиции - её убрать
            GL11.glTranslated(-px, -py, -pz);
            pushed.add(p);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onPost(RenderPlayerEvent.Post e) {
            if (pushed.remove(e.entityPlayer)) GL11.glPopMatrix();
        }
    }
}
