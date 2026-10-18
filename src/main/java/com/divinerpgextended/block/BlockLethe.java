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
 * Лета - багровая река забвения в низинах Мортума. Жидкость, в которой плавают, но она не растекается (река лежит в своём русле, как её
 * положила генерация). Вошедший забывает: с него слетают все эффекты, мобы теряют его из виду; пока стоишь в реке -
 * утекает опыт ({@link com.divinerpgextended.world.twilight.MortumWorld}).
 */
public class BlockLethe extends BlockFluidClassic {

    @SideOnly(Side.CLIENT)
    private IIcon still, flow;

    public BlockLethe(Fluid f) {
        super(f, Material.water);
        setBlockName("dre.lethe");
        setLightLevel(0.25F);
        setHardness(100F);
        setLightOpacity(3);
    }

    /** Не течёт: русло задаёт генерация. */
    @Override
    public void updateTick(World w, int x, int y, int z, Random r) {}

    @Override
    public void onEntityCollidedWithBlock(World w, int x, int y, int z, Entity e) {
        if (!w.isRemote && e instanceof EntityPlayer) com.divinerpgextended.world.twilight.MortumWorld.inLethe((EntityPlayer) e);
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
