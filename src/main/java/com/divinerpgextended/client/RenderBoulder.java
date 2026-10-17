package com.divinerpgextended.client;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Глыба голема: крутящийся блок земли Дикого леса в полтора блока. */
@SideOnly(Side.CLIENT)
public class RenderBoulder extends Render {

    private final RenderBlocks blocks = new RenderBlocks();

    public RenderBoulder() {
        shadowSize = 0.6F;
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + 0.5, z);
        float spin = (e.ticksExisted + partial) * 15;
        GL11.glRotatef(spin, 1, 0, 0.5F);
        GL11.glScalef(1.5F, 1.5F, 1.5F);
        bindEntityTexture(e);
        blocks.renderBlockAsItem(net.divinerpg.utils.blocks.TwilightBlocks.wildwoodDirt, 0, 1.0F);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TextureMap.locationBlocksTexture;
    }
}
