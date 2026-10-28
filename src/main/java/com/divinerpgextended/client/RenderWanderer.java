package com.divinerpgextended.client;

import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import com.divinerpgextended.entity.EntityWanderer;
import com.divinerpgextended.world.twilight.TwilightWorld;

/** Странник со скином своего мира (плащ и навершие посоха цвета мира). */
public class RenderWanderer extends RenderBiped {

    private static final ResourceLocation[] SKINS = new ResourceLocation[TwilightWorld.values().length];
    static {
        for (TwilightWorld w : TwilightWorld.values())
            SKINS[w.ordinal()] = new ResourceLocation("divinerpgextended", "textures/entity/wanderer_" + w.key() + ".png");
    }

    /** Странник без мира - в зелёной робе. */
    private static final ResourceLocation STRAY = new ResourceLocation("divinerpgextended", "textures/entity/wanderer_stray.png");

    public RenderWanderer() {
        super(new ModelWanderer(), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        EntityWanderer v = (EntityWanderer) e;
        return v.stray() ? STRAY : SKINS[v.world().ordinal()];
    }
}
