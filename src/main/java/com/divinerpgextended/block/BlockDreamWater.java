package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Вода мечтаний - перевёрнутая вода висячих озёр Ветеи: течёт вверх (плотность меньше нуля), гладь смотрит вниз. Для
 * игры это вода (материал воды): в ней плавают, тонут и рыбачат. Кто в ней - для того верх и низ меняются местами: тянет
 * вверх, к дну озера, прыжок - вниз, к глади; картинка перевёрнута ({@link com.divinerpgextended.client.CameraTilt}).
 * Вынырнул вниз - падаешь как обычно. Между двумя источниками под опорой рождается новый (бесконечная, как вода).
 */
public class BlockDreamWater extends BlockFluidClassic {

    @SideOnly(Side.CLIENT)
    private IIcon still, flow;

    public BlockDreamWater(Fluid f) {
        super(f, Material.water);
        setBlockName("dre.dreamWater");
        setLightLevel(0.4F);
        setHardness(100F);
        setLightOpacity(2);
    }

    @Override
    public void updateTick(World w, int x, int y, int z, Random r) {
        super.updateTick(w, x, y, z, r);
        if (w.isRemote || w.getBlock(x, y, z) != this || w.getBlockMetadata(x, y, z) == 0) return;
        int sources = 0;
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            if (isSource(w, x + d[0], y, z + d[1])) sources++;
        // опора - сверху: вода течёт вверх
        if (sources >= 2 && (w.getBlock(x, y + 1, z).getMaterial().isSolid() || isSource(w, x, y + 1, z))) w.setBlock(x, y, z, this, 0, 3);
    }

    public static boolean isSource(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) instanceof BlockDreamWater && w.getBlockMetadata(x, y, z) == 0;
    }

    /** Тело существа в воде мечтаний. */
    public static boolean inside(Entity e) {
        AxisAlignedBB bb = e.boundingBox;
        int x0 = MathHelper.floor_double(bb.minX), x1 = MathHelper.floor_double(bb.maxX), y0 = MathHelper.floor_double(bb.minY),
            y1 = MathHelper.floor_double(bb.maxY), z0 = MathHelper.floor_double(bb.minZ), z1 = MathHelper.floor_double(bb.maxZ);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
            if (e.worldObj.getBlock(x, y, z) instanceof BlockDreamWater) return true;
        return false;
    }

    /** Глаза в воде мечтаний (картинка перевёрнута). */
    public static boolean eyesIn(EntityLivingBase e) {
        return e.worldObj.getBlock(MathHelper.floor_double(e.posX), MathHelper.floor_double(e.posY + e.getEyeHeight() - e.yOffset),
            MathHelper.floor_double(e.posZ)) instanceof BlockDreamWater;
    }

    /**
     * Перевёрнутое плавание (на обеих сторонах - движение игрока считает клиент): обычная вода тянет вниз на 0.02 за тик
     * и прыжок поднимает на 0.04 - здесь наоборот.
     */
    public static class Swim {

        private static java.lang.reflect.Field jumping;

        static boolean jumping(EntityLivingBase e) {
            try {
                if (jumping == null) jumping = ReflectionHelper.findField(EntityLivingBase.class, "isJumping", "field_70703_bu");
                return jumping.getBoolean(e);
            } catch (Throwable t) {
                return false;
            }
        }

        @SubscribeEvent
        public void onUpdate(LivingEvent.LivingUpdateEvent ev) {
            EntityLivingBase e = ev.entityLiving;
            if (!inside(e)) return;
            if (e instanceof net.minecraft.entity.player.EntityPlayer && ((net.minecraft.entity.player.EntityPlayer) e).capabilities.isFlying) return;
            e.motionY += 0.04; // вместо -0.02 воды - +0.02: тянет вверх, к дну озера
            if (jumping(e)) e.motionY -= 0.08; // прыжок (+0.04 у воды) - вниз, к глади
            if (e.isSneaking()) e.motionY += 0.02;
            e.motionY = Math.max(-0.3, Math.min(0.3, e.motionY));
            e.fallDistance = 0;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        still = reg.registerIcon("divinerpgextended:dreamwater_still");
        flow = reg.registerIcon("divinerpgextended:dreamwater_flow");
        getFluid().setIcons(still, flow);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 0 || side == 1 ? still : flow;
    }
}
