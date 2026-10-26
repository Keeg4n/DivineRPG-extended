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
 * Лета - багровая река забвения в низинах Мортума. Река и водопады мира не растекаются (лежат в русле, как их положила
 * генерация); Лета из ведра ({@code flows}) течёт, как вода, и между двумя источниками рождает новый. Вошедший забывает: с него слетают все эффекты, мобы теряют его из виду; пока стоишь в реке -
 * утекает опыт ({@link com.divinerpgextended.world.twilight.MortumWorld}).
 */
public class BlockLethe extends BlockFluidClassic {

    @SideOnly(Side.CLIENT)
    private IIcon still, flow;

    /** Течёт ли (Лета из ведра); река мира - нет. */
    public final boolean flows;

    public BlockLethe(Fluid f, boolean flows) {
        super(f, Material.water);
        this.flows = flows;
        setBlockName("dre.lethe");
        setLightLevel(0.25F);
        setHardness(100F);
        setLightOpacity(3);
    }

    /** Река мира не течёт (русло задаёт генерация); Лета из ведра течёт, а между двумя источниками на дне - новый источник. */
    @Override
    public void updateTick(World w, int x, int y, int z, Random r) {
        if (!flows) return;
        super.updateTick(w, x, y, z, r);
        if (w.isRemote || w.getBlock(x, y, z) != this || w.getBlockMetadata(x, y, z) == 0) return;
        int sources = 0;
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            if (isSource(w, x + d[0], y, z + d[1])) sources++;
        if (sources >= 2 && (w.getBlock(x, y - 1, z).getMaterial().isSolid() || isSource(w, x, y - 1, z))) w.setBlock(x, y, z, this, 0, 3);
    }

    /** Источник Леты (река мира или из ведра). */
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
