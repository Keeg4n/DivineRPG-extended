package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntitySkyCart;
import com.divinerpgextended.entity.SkyCartView;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * View from the Apalachia Cart: on a wall and ceiling the picture tilts around the look axis so that the cart's "top"
 * is at the top of the screen, while the look itself stays real (previously the whole world was rotated, and on the ceiling the game took the look
 * "up" for the look "down" - hits and clicks went the wrong way). The mouse is rotated by the same angle ({@link #angles}).
 * Someone inverted in Dream Water (feet in a lake in the Vethea ceiling) sees the picture upside down, the camera is in the head, not in the heels.
 */
@SideOnly(Side.CLIENT)
public class CameraTilt {

    /** The last roll (degrees): when looking along the cart's "top", the roll is undefined - keep the previous one. */
    private static double last;

    private static EntitySkyCart cart(Entity v) {
        return v != null && v.ridingEntity instanceof EntitySkyCart ? (EntitySkyCart) v.ridingEntity : null;
    }

    /** At the start of EntityRenderer.orientCamera: roll around the look axis. */
    public static void apply(float pt) {
        Entity v = Minecraft.getMinecraft().renderViewEntity;
        EntitySkyCart c = cart(v);
        if (c == null) {
            // in Dream Water (Vethea's hanging lakes) up and down are swapped: the picture is upside down
            last = v != null && com.divinerpgextended.block.BlockDreamWater.inverted(v) ? 180 : 0;
            if (last != 0) {
                GL11.glRotated(last, 0, 0, 1);
                // the game keeps the camera (and the point the third-person camera orbits) at eye height from the bottom of the hitbox -
                // for an inverted one the heels are there, in the water; we lower it to the head: a world shift upward, converted into camera axes
                // (the rotations by the look tilt follow; the front view is turned 180 - the sign along the look axis differs)
                double pitch = Math.toRadians(v.prevRotationPitch + (v.rotationPitch - v.prevRotationPitch) * pt), s = DreamWaterView.HEAD_DROP;
                int front = Minecraft.getMinecraft().gameSettings.thirdPersonView == 2 ? -1 : 1;
                GL11.glTranslated(0, s * Math.cos(pitch), front * s * Math.sin(pitch));
                // in third person the game pushed the camera away from the point in the heels to the first wall; lowered to the head, it
                // went under the floor (a view through the ground into caves) - move it closer, as if the wall were searched from the head
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

    /** How far the game pushes the third-person camera from the point (x, y, z) until it hits a block (as in EntityRenderer.orientCamera). */
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

    /** The cart changed plane (floor - wall - ceiling): the rider's look turns together with it. */
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

    /** Instead of Entity.setAngles in EntityRenderer.updateCameraAndRender: the mouse movement is in the axes of the tilted screen. */
    public static void angles(Entity e, float dx, float dy) {
        if (Math.abs(last) > 0.01 && (cart(e) != null || last == 180)) {
            float[] m = SkyCartView.mouse(dx, dy, last);
            dx = m[0];
            dy = m[1];
        }
        e.setAngles(dx, dy);
    }
}
