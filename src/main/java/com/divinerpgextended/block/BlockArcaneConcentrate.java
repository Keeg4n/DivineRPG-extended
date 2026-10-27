package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Арканный концентрат - жидкость фонтанных залов Арканы ({@link com.divinerpgextended.world.ArcanaFountain}). Для игры -
 * вода (в нём плавают и рыбачат), но рыба Арканы ловится только здесь и только на арканную удочку. Кто стоит в нём,
 * быстро восстанавливает аркану.
 */
public class BlockArcaneConcentrate extends BlockFluidClassic {

    /** Сколько арканы в тик восстанавливает стоящему в концентрате. */
    public static final float REGEN = 2.0F;
    private static java.lang.reflect.Method props, regen;

    @SideOnly(Side.CLIENT)
    private IIcon still, flow;

    public BlockArcaneConcentrate(Fluid f) {
        super(f, Material.water);
        setBlockName("dre.arcaneConcentrate");
        setLightLevel(0.6F);
        setHardness(100F);
        setLightOpacity(2);
    }

    /** Точка в концентрате. */
    public static boolean at(World w, double x, double y, double z) {
        return w.getBlock(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z)) instanceof BlockArcaneConcentrate;
    }

    @Override
    public void onEntityCollidedWithBlock(World w, int x, int y, int z, Entity e) {
        if (w.isRemote || !(e instanceof EntityPlayer)) return;
        try {
            if (props == null) {
                Class<?> c = Class.forName("net.divinerpg.utils.events.ArcanaHelper");
                props = c.getMethod("getProperties", EntityPlayer.class);
                regen = c.getMethod("regen", float.class);
            }
            Object h = props.invoke(null, e);
            if (h != null) regen.invoke(h, REGEN);
        } catch (Throwable ignored) {}
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World w, int x, int y, int z, Random r) {
        if (r.nextInt(4) == 0) w.spawnParticle("portal", x + r.nextDouble(), y + 0.9, z + r.nextDouble(), 0, 0.3, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        still = reg.registerIcon("divinerpgextended:arcane_still");
        flow = reg.registerIcon("divinerpgextended:arcane_flow");
        getFluid().setIcons(still, flow);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 0 || side == 1 ? still : flow;
    }
}
