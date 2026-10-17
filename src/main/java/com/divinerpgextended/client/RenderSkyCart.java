package com.divinerpgextended.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelMinecart;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntitySkyCart;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Вагонетка Апалачии: модель ванильной вагонетки, колёсами к опоре - на стене боком, на потолке вверх дном, фиолетовая. */
@SideOnly(Side.CLIENT)
public class RenderSkyCart extends Render {

    private static final ResourceLocation TEX = new ResourceLocation("textures/entity/minecart.png");
    private final ModelBase model = new ModelMinecart();

    public RenderSkyCart() {
        shadowSize = 0.4F;
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
        ForgeDirection f = ((EntitySkyCart) e).face();
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + 0.35, z);
        switch (f) {
            case UP: GL11.glRotatef(180, 1, 0, 0); break;
            case NORTH: GL11.glRotatef(-90, 1, 0, 0); break;
            case SOUTH: GL11.glRotatef(90, 1, 0, 0); break;
            case WEST: GL11.glRotatef(90, 0, 0, 1); break;
            case EAST: GL11.glRotatef(-90, 0, 0, 1); break;
            default: break;
        }
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
}
