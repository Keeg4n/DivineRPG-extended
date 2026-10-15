package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Жаровня Ледяного Трона: зажжённая (мета 1) светит и греет, как огонь. Боссы во второй фазе гасят жаровни; снова
 * зажечь - огнивом или огненным зарядом.
 */
public class BlockBrazier extends Block {

    private IIcon side, lit, unlit;

    public BlockBrazier() {
        super(Material.rock);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setStepSound(soundTypeStone);
        setBlockName("dre.brazier");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static boolean isLit(IBlockAccess w, int x, int y, int z) {
        return w.getBlockMetadata(x, y, z) == 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        side = reg.registerIcon("divinerpg:icyBricks");
        lit = reg.registerIcon("netherrack");
        unlit = reg.registerIcon("obsidian");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        if (s == 1) return meta == 1 ? lit : unlit;
        return side;
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return isLit(world, x, y, z) ? 15 : 0;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int s, float hx, float hy,
        float hz) {
        ItemStack held = player.getHeldItem();
        if (held == null || isLit(world, x, y, z)
            || held.getItem() != Items.flint_and_steel && held.getItem() != Items.fire_charge) return false;
        if (world.isRemote) return true;
        world.setBlockMetadataWithNotify(x, y, z, 1, 3);
        world.playSoundEffect(x + 0.5, y + 1, z + 0.5, "fire.ignite", 1.0F, 0.9F);
        if (!player.capabilities.isCreativeMode) {
            if (held.getItem() == Items.flint_and_steel) held.damageItem(1, player);
            else held.stackSize--;
        }
        return true;
    }

    /** Погасить (боссом): шипение и клуб пара. */
    public static void extinguish(World world, int x, int y, int z) {
        world.setBlockMetadataWithNotify(x, y, z, 0, 3);
        world.playSoundEffect(x + 0.5, y + 1, z + 0.5, "random.fizz", 1.5F, 0.6F);
        if (world instanceof WorldServer) {
            ((WorldServer) world).func_147487_a("largesmoke", x + 0.5, y + 1.2, z + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        if (!isLit(world, x, y, z)) return;
        for (int i = 0; i < 3; i++) {
            double px = x + 0.2 + rand.nextDouble() * 0.6, pz = z + 0.2 + rand.nextDouble() * 0.6;
            world.spawnParticle("flame", px, y + 1.05, pz, 0, 0.03, 0);
            world.spawnParticle("smoke", px, y + 1.3, pz, 0, 0.05, 0);
        }
        if (rand.nextInt(16) == 0) world.playSound(x + 0.5, y + 1, z + 0.5, "fire.fire", 0.8F, 0.9F, false);
    }
}
