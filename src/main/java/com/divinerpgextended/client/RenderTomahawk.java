package com.divinerpgextended.client;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Брошенный томагавк: значок предмета с толщиной, вертится вокруг оси поперёк полёта. */
@SideOnly(Side.CLIENT)
public class RenderTomahawk extends Render {

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {
        ItemStack thrown = e instanceof com.divinerpgextended.entity.EntityTomahawk ? ((com.divinerpgextended.entity.EntityTomahawk) e).item() : null;
        IIcon icon = thrown != null ? thrown.getIconIndex() : ModItems.tomahawk.getIconFromDamage(0);
        if (icon == null) return;
        bindEntityTexture(e);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        float dir = (float) (Math.atan2(e.motionX, e.motionZ) * 180.0 / Math.PI);
        GL11.glRotatef(dir - 90F, 0F, 1F, 0F);
        GL11.glRotatef(-((e.ticksExisted + pt) * 40F) % 360F, 0F, 0F, 1F);
        GL11.glScalef(0.9F, 0.9F, 0.9F);
        GL11.glTranslatef(-0.5F, -0.5F, 0.03125F);
        ItemRenderer.renderItemIn2D(Tessellator.instance, icon.getMaxU(), icon.getMinV(), icon.getMinU(), icon.getMaxV(), icon.getIconWidth(), icon.getIconHeight(), 0.0625F);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TextureMap.locationItemsTexture;
    }
}
