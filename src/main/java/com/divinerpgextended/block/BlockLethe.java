package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Lethe is the crimson river of oblivion in the lowlands of Mortum. Since 0.99.111 the world river is the same liquid as Lethe from a bucket ({@code flows}):
 * it flows like water and between two sources creates a new one (so what is scooped out of the riverbed refills by itself). Without flow
 * ({@code flows = false}) the block stays still, and gaps in the riverbed are filled by {@link #refill}.
 * Whoever enters forgets: all effects fall off, mobs lose sight of him; while you stand in the river experience and arcana drain away, and without arcana
 * the river bites ({@link com.divinerpgextended.world.twilight.MortumWorld}).
 */
public class BlockLethe extends BlockFluidClassic {

    @SideOnly(Side.CLIENT)
    private IIcon still, flow;

    /** Whether it flows (Lethe from a bucket); the world river does not. */
    public final boolean flows;

    public BlockLethe(Fluid f, boolean flows) {
        super(f, Material.water);
        this.flows = flows;
        setBlockName("dre.lethe");
        setLightLevel(0.25F);
        setHardness(100F);
        setLightOpacity(3);
    }

    /**
     * The world river does not flow (the riverbed is set by generation), but does not hold holes either: scooped out with a bucket, a block placed and broken - the empty spot
     * in the riverbed fills again. Lethe from a bucket flows, and between two sources on the bottom - a new source.
     */
    @Override
    public void updateTick(World w, int x, int y, int z, Random r) {
        if (!flows) {
            refill(w, x, y, z);
            return;
        }
        super.updateTick(w, x, y, z, r);
        if (w.isRemote || w.getBlock(x, y, z) != this || w.getBlockMetadata(x, y, z) == 0) return;
        int sources = 0;
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            if (isSource(w, x + d[0], y, z + d[1])) sources++;
        if (sources >= 2 && (w.getBlock(x, y - 1, z).getMaterial().isSolid() || isSource(w, x, y - 1, z))) w.setBlock(x, y, z, this, 0, 3);
    }

    private static final int[][] SIDES = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 } };

    /** Fill gaps in the riverbed near this river block. How many were filled. */
    public int refill(World w, int x, int y, int z) {
        if (w.isRemote || w.getBlockMetadata(x, y, z) != 0) return 0;
        int n = 0;
        for (int[] d : SIDES) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (!w.isAirBlock(nx, ny, nz) || !holeInRiver(w, nx, ny, nz)) continue;
            w.setBlock(nx, ny, nz, this, 0, 3);
            n++;
        }
        return n;
    }

    /**
     * A gap inside the riverbed, not a bank and not the air above the river: two river sources nearby and bottom or river below (as water creates
     * a source), or river above (scooped from the bottom, a waterfall break).
     */
    private boolean holeInRiver(World w, int x, int y, int z) {
        int side = 0;
        for (int i = 0; i < 4; i++) if (river(w, x + SIDES[i][0], y, z + SIDES[i][2])) side++;
        boolean above = river(w, x, y + 1, z), below = river(w, x, y - 1, z);
        boolean bed = below || w.getBlock(x, y - 1, z).getMaterial().isSolid();
        return side >= 2 && bed || above && (side >= 1 || below);
    }

    private boolean river(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) == this && w.getBlockMetadata(x, y, z) == 0;
    }

    /** Lethe source (world river or from a bucket). */
    public static boolean isSource(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) instanceof BlockLethe && w.getBlockMetadata(x, y, z) == 0;
    }

    @Override
    public void onEntityCollidedWithBlock(World w, int x, int y, int z, Entity e) {
        if (w.isRemote) return;
        if (e instanceof EntityPlayer) com.divinerpgextended.world.twilight.MortumWorld.inLethe((EntityPlayer) e);
        else if (e instanceof net.minecraft.entity.EntityLivingBase) com.divinerpgextended.world.twilight.MortumWorld.mobInLethe((net.minecraft.entity.EntityLivingBase) e, x, z);
        else if (e instanceof net.minecraft.entity.item.EntityBoat) com.divinerpgextended.world.twilight.MortumWorld.boatInLethe((net.minecraft.entity.item.EntityBoat) e);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        still = reg.registerIcon("divinerpgextended:lethe_still");
        flow = reg.registerIcon("divinerpgextended:lethe_flow");
        getFluid().setIcons(still, flow);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 0 || side == 1 ? still : flow;
    }
}
