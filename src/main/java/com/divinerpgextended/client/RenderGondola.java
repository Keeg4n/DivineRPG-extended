package com.divinerpgextended.client;

import net.minecraft.client.renderer.entity.RenderBoat;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/** Ладья Харона: модель ванильной лодки из чёрного дерева. */
public class RenderGondola extends RenderBoat {

    private static final ResourceLocation TEX = new ResourceLocation("divinerpgextended", "textures/entity/gondola.png");

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }
}
