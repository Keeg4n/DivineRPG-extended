package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.divinerpgextended.entity.EntityWindLord;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Ветряной тотем арены Повелителя ветров (мета 0 - погашен, 1 - заряжен). Правый клик начинает зарядку: Повелитель
 * рядом запоминает, кто заряжает. Не ломается, ничего не роняет; ставит и убирает его сам Повелитель.
 */
public class BlockWindTotem extends Block {

    private IIcon off, on;

    public BlockWindTotem() {
        super(Material.rock);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setStepSound(soundTypeStone);
        setBlockName("dre.windTotem");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        off = reg.registerIcon("divinerpgextended:wind_totem_off");
        on = reg.registerIcon("divinerpgextended:wind_totem_on");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return meta == 1 ? on : off;
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        return w.getBlockMetadata(x, y, z) == 1 ? 15 : 6;
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return null;
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        if (w.getBlockMetadata(x, y, z) == 1) return true;
        for (Object o : w.getEntitiesWithinAABB(EntityWindLord.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 48, y - 32, z - 48, x + 48, y + 32, z + 48))) {
            ((EntityWindLord) o).startCharge(x, y, z, p);
            return true;
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World w, int x, int y, int z, Random r) {
        if (w.getBlockMetadata(x, y, z) != 1) return;
        for (int i = 0; i < 3; i++) w.spawnParticle("magicCrit", x + r.nextDouble(), y + 1.1, z + r.nextDouble(), 0, 0.2, 0);
    }
}
