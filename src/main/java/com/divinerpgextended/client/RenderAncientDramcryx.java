package com.divinerpgextended.client;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntityAncientDramcryx;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Древний драмкрикс: модель и текстура драмкрикса джунглей из Divine, втрое крупнее и замшелый; оглушённый темнеет. */
@SideOnly(Side.CLIENT)
public class RenderAncientDramcryx extends RenderLiving {

    private static final ResourceLocation TEX = new ResourceLocation("divinerpg", "textures/mobs/jungleDramcryx.png");
    /** Корона между ушами: отличить от обычных драмкриксов джунглей. */
    private static final BossCrown CROWN = new BossCrown("WolfHead", 2F, -3F, 0F, 5F, 0x5AD05A);

    public RenderAncientDramcryx() {
        super(new net.divinerpg.client.render.entity.vanilla.model.ModelDramcryx(), 2.4F);
    }

    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float partial) {
        DreBossBar.show(e); // полоска как у боссов Divine, не ванильная
        super.doRender(e, x, y, z, yaw, partial);
    }

    @Override
    protected void renderModel(EntityLivingBase e, float f, float f1, float f2, float f3, float f4, float scale) {
        super.renderModel(e, f, f1, f2, f3, f4, scale);
        if (!e.isInvisible()) CROWN.render(mainModel, scale);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float partial) {
        float s = EntityAncientDramcryx.SCALE;
        GL11.glScalef(s, s, s);
        float k = ((EntityAncientDramcryx) e).stunned() ? 0.6F : 1.0F;
        GL11.glColor4f(0.75F * k, 0.95F * k, 0.55F * k, 1.0F); // мох и старость
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }
}
