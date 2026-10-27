package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.fishing.EntityDreHook;
import com.divinerpgextended.fishing.Rod;

/** Поплавок как у ванильной удочки, но со своим спрайтом и цветной (у поздних - светящейся) леской; в воде мечтаний - вверх ногами. */
public class RenderDreHook extends Render {

    private static final ResourceLocation VANILLA = new ResourceLocation("textures/particle/particles.png");
    private static final ResourceLocation[] BOBBERS = new ResourceLocation[Rod.values().length];

    static {
        for (Rod r : Rod.values()) {
            BOBBERS[r.ordinal()] = new ResourceLocation("divinerpgextended", "textures/entity/bobber_" + r.key + ".png");
        }
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {
        EntityDreHook hook = (EntityDreHook) e;
        Rod rod = hook.rod();
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, (float) z);
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glScalef(0.5F, 0.5F, 0.5F);
        bindEntityTexture(e);
        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (rod == Rod.VANILLA) {
            u0 = 8 / 128F; u1 = 16 / 128F; v0 = 16 / 128F; v1 = 24 / 128F;
        }
        if (rod.glowingLine()) OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glRotatef(180.0F - renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        boolean flip = hook.dreamWaterAbove(); // у глади озера в потолке поплавок висит вверх ногами
        if (flip) GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setNormal(0.0F, 1.0F, 0.0F);
        t.addVertexWithUV(-0.5, -0.5, 0.0, u0, v1);
        t.addVertexWithUV(0.5, -0.5, 0.0, u1, v1);
        t.addVertexWithUV(0.5, 0.5, 0.0, u1, v0);
        t.addVertexWithUV(-0.5, 0.5, 0.0, u0, v0);
        t.draw();
        GL11.glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();

        EntityPlayer p = hook.field_146042_b;
        if (p == null) return;
        float swing = MathHelper.sin(MathHelper.sqrt_float(p.getSwingProgress(pt)) * (float) Math.PI);
        // перевёрнутый в воде мечтаний: экран повёрнут на 180 - рука на экране там же, значит в мире - напротив
        boolean inv = com.divinerpgextended.block.BlockDreamWater.inverted(p);
        Vec3 v = inv ? Vec3.createVectorHelper(0.5, -0.03, 0.8) : Vec3.createVectorHelper(-0.5, 0.03, 0.8);
        v.rotateAroundX(-(p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt) * (float) Math.PI / 180.0F);
        v.rotateAroundY(-(p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt) * (float) Math.PI / 180.0F);
        v.rotateAroundY(swing * 0.5F);
        v.rotateAroundX(-swing * 0.7F);
        double hx = p.prevPosX + (p.posX - p.prevPosX) * pt + v.xCoord;
        double hy = p.prevPosY + (p.posY - p.prevPosY) * pt + v.yCoord;
        double hz = p.prevPosZ + (p.posZ - p.prevPosZ) * pt + v.zCoord;
        double eye = p == Minecraft.getMinecraft().thePlayer ? 0.0 : p.getEyeHeight();
        if (renderManager.options.thirdPersonView > 0 || p != Minecraft.getMinecraft().thePlayer) {
            float body = (p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * pt) * (float) Math.PI / 180.0F;
            double s = MathHelper.sin(body), c = MathHelper.cos(body);
            double side = inv ? -1 : 1; // модель перевёрнута вокруг оси взгляда: рука на другой стороне и внизу
            hx = p.prevPosX + (p.posX - p.prevPosX) * pt - side * c * 0.35 - s * 0.85;
            hy = p.prevPosY + eye + (p.posY - p.prevPosY) * pt - 0.45;
            if (inv) { // от середины хитбокса - зеркально по высоте
                double feet = p.prevPosY + (p.posY - p.prevPosY) * pt - p.yOffset, mid = feet + p.height / 2;
                hy = 2 * mid - (feet + 1.62 - 0.45);
            }
            hz = p.prevPosZ + (p.posZ - p.prevPosZ) * pt - side * s * 0.35 + c * 0.85;
        }
        double bx = hook.prevPosX + (hook.posX - hook.prevPosX) * pt;
        double tip = flip ? -0.25 : 0.25; // леска - к верху спрайта, у перевёрнутого он внизу
        double by = hook.prevPosY + (hook.posY - hook.prevPosY) * pt + tip;
        double bz = hook.prevPosZ + (hook.posZ - hook.prevPosZ) * pt;
        double dx = (float) (hx - bx), dy = (float) (hy - by), dz = (float) (hz - bz);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        if (rod != Rod.VANILLA) GL11.glLineWidth(2.0F);
        t.startDrawing(GL11.GL_LINE_STRIP);
        t.setColorOpaque_I(rod.line());
        int n = 16;
        for (int i = 0; i <= n; i++) {
            float f = (float) i / n;
            t.addVertex(x + dx * f, y + dy * (f * f + f) * 0.5 + tip, z + dz * f);
        }
        t.draw();
        GL11.glLineWidth(1.0F);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        Rod rod = ((EntityDreHook) e).rod();
        return rod == Rod.VANILLA ? VANILLA : BOBBERS[rod.ordinal()];
    }
}
