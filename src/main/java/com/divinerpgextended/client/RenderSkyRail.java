package com.divinerpgextended.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.divinerpgextended.block.BlockSkyRail;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Рельсы Апалачии: плоская текстура рельса у грани опоры (с обеих сторон), повёрнутая вдоль или поперёк. */
@SideOnly(Side.CLIENT)
public class RenderSkyRail implements ISimpleBlockRenderingHandler {

    private static final double LIFT = 1 / 16.0;

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks rb) {}

    @Override
    public boolean renderWorldBlock(IBlockAccess w, int x, int y, int z, Block block, int modelId, RenderBlocks rb) {
        int meta = w.getBlockMetadata(x, y, z);
        IIcon icon = rb.hasOverrideBlockTexture() ? rb.overrideBlockTexture : block.getIcon(0, 0);
        Tessellator t = Tessellator.instance;
        t.setBrightness(block.getMixedBrightnessForBlock(w, x, y, z));
        t.setColorOpaque_F(1, 1, 1);
        double u0 = icon.getMinU(), u1 = icon.getMaxU(), v0 = icon.getMinV(), v1 = icon.getMaxV();
        boolean across = BlockSkyRail.across(meta);
        // четыре угла плоскости в локальных координатах (a, b) от 0 до 1; uv поворачиваем для "поперёк"
        double[][] uv = across ? new double[][] { { u0, v1 }, { u0, v0 }, { u1, v0 }, { u1, v1 } } : new double[][] { { u0, v0 }, { u1, v0 }, { u1, v1 }, { u0, v1 } };
        double[][] p = new double[4][];
        ForgeDirection sup = BlockSkyRail.support(meta);
        double[][] ab = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        for (int i = 0; i < 4; i++) {
            double a = ab[i][0], b = ab[i][1];
            switch (sup) {
                case UP: p[i] = new double[] { x + a, y + 1 - LIFT, z + b }; break;
                case NORTH: p[i] = new double[] { x + a, y + b, z + LIFT }; break;
                case SOUTH: p[i] = new double[] { x + a, y + b, z + 1 - LIFT }; break;
                case WEST: p[i] = new double[] { x + LIFT, y + b, z + a }; break;
                case EAST: p[i] = new double[] { x + 1 - LIFT, y + b, z + a }; break;
                default: p[i] = new double[] { x + a, y + LIFT, z + b };
            }
        }
        for (int i = 0; i < 4; i++) t.addVertexWithUV(p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1]);
        for (int i = 3; i >= 0; i--) t.addVertexWithUV(p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1]);
        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return false;
    }

    @Override
    public int getRenderId() {
        return BlockSkyRail.renderId;
    }
}
