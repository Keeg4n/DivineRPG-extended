package com.divinerpgextended.client;

import java.lang.reflect.Field;

import net.divinerpg.client.render.EntityResourceLocation;
import net.divinerpg.client.render.entity.twilight.model.ModelTwilightGolem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntityAncientGolem;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Древний голем: модель голема Дикого леса из Divine, крупнее и темнее, мшистая. Отбитые части не рисуются (поля модели
 * Divine не публичные - берём рефлексией), без ног корпус оседает на землю. Лесной следопыт видит суставы - искры на
 * живых частях.
 */
@SideOnly(Side.CLIENT)
public class RenderAncientGolem extends RenderLiving {

    private static final String[][] PARTS = { { "rightShoulder", "rightArm", "rightHand" }, { "leftShoulder", "leftArm", "leftHand" },
        { "rightLeg", "leftLeg" } };
    private static ModelRenderer[][] parts;

    public RenderAncientGolem() {
        super(new ModelTwilightGolem(), 2.0F);
        parts = new ModelRenderer[PARTS.length][];
        for (int i = 0; i < PARTS.length; i++) {
            parts[i] = new ModelRenderer[PARTS[i].length];
            for (int j = 0; j < PARTS[i].length; j++) {
                try {
                    Field f = ModelTwilightGolem.class.getDeclaredField(PARTS[i][j]);
                    f.setAccessible(true);
                    parts[i][j] = (ModelRenderer) f.get(mainModel);
                } catch (Exception e) {
                    parts[i][j] = null;
                }
            }
        }
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partial) {
        EntityAncientGolem g = (EntityAncientGolem) entity;
        DreBossBar.show(g);
        for (int i = 0; i < parts.length; i++) for (ModelRenderer r : parts[i]) if (r != null) r.showModel = !g.broken(i);
        super.doRender(entity, x, y, z, yaw, partial);
        for (ModelRenderer[] ps : parts) for (ModelRenderer r : ps) if (r != null) r.showModel = true;
        if (Minecraft.getMinecraft().thePlayer != null && g.ticksExisted % 6 == 0
            && com.divinerpgextended.perk.HomeArmor.wears(Minecraft.getMinecraft().thePlayer, TwilightWorld.WILDWOOD, false)) joints(g);
    }

    /** Суставы для Лесного следопыта: плечи и колени живых частей, ядро - когда открыто. */
    private static void joints(EntityAncientGolem g) {
        double yaw = Math.toRadians(g.renderYawOffset), cx = Math.cos(yaw), cz = Math.sin(yaw);
        double s = EntityAncientGolem.SCALE / 16.0;
        double[][] pts = { { -12, 20 }, { 12, 20 }, { 0, 2 } }; // x модели, высота от земли в единицах модели (ноги - колени)
        for (int i = 0; i < 3; i++) {
            if (g.broken(i)) continue;
            double side = pts[i][0] * s, up = (i == 2 ? 8 : 44) * s;
            if (i == 2) {
                for (int k = -1; k <= 1; k += 2)
                    g.worldObj.spawnParticle("happyVillager", g.posX + cx * 6 * s * k, g.posY + up, g.posZ + cz * 6 * s * k, 0, 0, 0);
            } else g.worldObj.spawnParticle("happyVillager", g.posX + cx * side, g.posY + up, g.posZ + cz * side, 0, 0, 0);
        }
    }

    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partial) {
        EntityAncientGolem g = (EntityAncientGolem) entity;
        GL11.glScalef(EntityAncientGolem.SCALE, EntityAncientGolem.SCALE, EntityAncientGolem.SCALE);
        if (g.broken(EntityAncientGolem.LEGS)) GL11.glTranslatef(0, 1.0F, 0); // без ног - корпус на земле
        float glow = g.coreOpen() ? 0.85F + 0.15F * (float) Math.sin(g.ticksExisted * 0.3) : 1.0F;
        GL11.glColor4f(0.7F * glow, 0.95F * glow, 0.7F * glow, 1.0F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return EntityResourceLocation.wildwoodGolem;
    }
}
