package com.divinerpgextended.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.divinerpgextended.block.BlockSkyRail;
import com.divinerpgextended.block.SkyTrack;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Рельсы Апалачии: плоская текстура у грани опоры (с обеих сторон), сама соединяется с соседями
 * ({@link BlockSkyRail#links}): прямой рельс - вдоль соседа, два соседа под углом - поворот.
 */
@SideOnly(Side.CLIENT)
public class RenderSkyRail implements ISimpleBlockRenderingHandler {

    private static final double LIFT = 1 / 16.0;

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks rb) {}

    @Override
    public boolean renderWorldBlock(IBlockAccess w, int x, int y, int z, Block block, int modelId, RenderBlocks rb) {
        int meta = w.getBlockMetadata(x, y, z);
        ForgeDirection n = BlockSkyRail.support(meta);
        ForgeDirection cr = SkyTrack.corner(w, x, y, z);
        if (cr != ForgeDirection.UNKNOWN) return renderRamp(w, x, y, z, block, rb, n, cr);
        ForgeDirection[] links = BlockSkyRail.links(w, x, y, z);
        ForgeDirection a, b; // текстура: +u к a, +v к b
        boolean turn = false;
        // рельсы арканы, как золотые, не гнутся: на повороте рисуются прямыми вдоль первого соседа
        if (links.length >= 2 && links[0] != links[1].getOpposite() && !(block instanceof com.divinerpgextended.block.BlockSkyBoostRail)) {
            a = links[0];
            b = links[1];
            turn = true;
        } else {
            ForgeDirection along = links.length > 0 ? links[0] : BlockSkyRail.defaultAxis(meta);
            b = along;
            a = along.getRotation(n); // поперёк, в той же плоскости
        }
        IIcon icon = rb.hasOverrideBlockTexture() ? rb.overrideBlockTexture : turn ? BlockSkyRail.turnedIcon : block.getIcon(0, 0);
        Tessellator t = Tessellator.instance;
        t.setBrightness(block.getMixedBrightnessForBlock(w, x, y, z));
        t.setColorOpaque_F(1, 1, 1);
        double cx = x + 0.5 + n.offsetX * (0.5 - LIFT), cy = y + 0.5 + n.offsetY * (0.5 - LIFT), cz = z + 0.5 + n.offsetZ * (0.5 - LIFT);
        double[][] ab = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        double[][] p = new double[4][];
        double[][] uv = new double[4][];
        for (int i = 0; i < 4; i++) {
            double ka = ab[i][0] - 0.5, kb = ab[i][1] - 0.5;
            p[i] = new double[] { cx + a.offsetX * ka + b.offsetX * kb, cy + a.offsetY * ka + b.offsetY * kb, cz + a.offsetZ * ka + b.offsetZ * kb };
            uv[i] = new double[] { icon.getInterpolatedU(ab[i][0] * 16), icon.getInterpolatedV(ab[i][1] * 16) };
        }
        for (int i = 0; i < 4; i++) t.addVertexWithUV(p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1]);
        for (int i = 3; i >= 0; i--) t.addVertexWithUV(p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1]);
        return true;
    }

    /** Горка во внутреннем углу: по диагонали блока от края у пола до края у рельса на стене, как ванильный рельс на блок выше. */
    private boolean renderRamp(IBlockAccess w, int x, int y, int z, Block block, RenderBlocks rb, ForgeDirection n, ForgeDirection d) {
        IIcon icon = rb.hasOverrideBlockTexture() ? rb.overrideBlockTexture : block.getIcon(0, 0);
        ForgeDirection u = d.getRotation(n); // поперёк горки
        Tessellator t = Tessellator.instance;
        t.setBrightness(block.getMixedBrightnessForBlock(w, x, y, z));
        t.setColorOpaque_F(1, 1, 1);
        double lift = -LIFT * Math.sqrt(0.5);
        double[][] p = new double[4][];
        double[][] uv = new double[4][];
        double[][] ab = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        for (int i = 0; i < 4; i++) {
            double ku = ab[i][0] - 0.5, kv = ab[i][1]; // kv 0 - низ горки, 1 - верх
            double sn = 0.5 - kv, sd = kv - 0.5; // по опоре: от края грани к противоположному; к стене: от -0.5 до 0.5
            p[i] = new double[] { x + 0.5 + n.offsetX * (sn + lift) + d.offsetX * (sd + lift) + u.offsetX * ku,
                y + 0.5 + n.offsetY * (sn + lift) + d.offsetY * (sd + lift) + u.offsetY * ku,
                z + 0.5 + n.offsetZ * (sn + lift) + d.offsetZ * (sd + lift) + u.offsetZ * ku };
            uv[i] = new double[] { icon.getInterpolatedU(ab[i][0] * 16), icon.getInterpolatedV(ab[i][1] * 16) };
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
