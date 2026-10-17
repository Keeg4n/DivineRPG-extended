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

    public RenderWanderer() {
        super(new ModelWanderer(), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKINS[((EntityWanderer) e).world().ordinal()];
    }
}
