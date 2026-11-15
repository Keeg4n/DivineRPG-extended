package com.divinerpgextended.client;

import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import com.divinerpgextended.entity.EntityWanderer;
import com.divinerpgextended.world.twilight.TwilightWorld;

/** The Wanderer with the skin of its world (the cloak and staff head in the world color). */
public class RenderWanderer extends RenderBiped {

    private static final ResourceLocation[] SKINS = new ResourceLocation[TwilightWorld.values().length];
    static {
        for (TwilightWorld w : TwilightWorld.values())
            SKINS[w.ordinal()] = new ResourceLocation("divinerpgextended", "textures/entity/wanderer_" + w.key() + ".png");
    }

    /** A Wanderer without a world - in a green robe. */
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
