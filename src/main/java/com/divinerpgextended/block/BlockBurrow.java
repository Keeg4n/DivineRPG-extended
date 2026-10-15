package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Стены крабьей норы: на вид песчаник (мета 0) и песок (мета 1). Если сломать такой блок в выживании или взорвать,
 * нора обрушивается: проход вокруг засыпает падающим песком. Так лабиринт нельзя пройти насквозь киркой.
 */
public class BlockBurrow extends Block {

    private IIcon sandstoneSide, sandstoneTop, sand;

    public BlockBurrow() {
        super(Material.rock);
        setHardness(2.5F);
        setResistance(10.0F);
        setStepSound(soundTypeStone);
        setBlockName("dre.burrow");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        sandstoneSide = reg.registerIcon("sandstone_normal");
        sandstoneTop = reg.registerIcon("sandstone_top");
        sand = reg.registerIcon("sand");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (meta == 1) return sand;
        return side == 0 || side == 1 ? sandstoneTop : sandstoneSide;
    }

    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
        if (!world.isRemote && !player.capabilities.isCreativeMode) {
            collapse(world, x, y, z);
            player.addChatMessage(new ChatComponentTranslation("dre.chat.burrow_collapse"));
        }
        super.onBlockHarvested(world, x, y, z, meta, player);
    }

    /** В выживании на месте сломанного блока сразу появляется падающий песок: пролом тут же засыпает. */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!world.isRemote && !player.capabilities.isCreativeMode) {
            return world.setBlock(x, y, z, Blocks.sand);
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void onBlockDestroyedByExplosion(World world, int x, int y, int z, Explosion explosion) {
        if (!world.isRemote) collapse(world, x, y, z);
    }

    /** Засыпать песком всё пустое и затопленное вокруг места пролома: песок падает и забивает проход. */
    public static void collapse(World world, int x, int y, int z) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    Block b = world.getBlock(x + dx, y + dy, z + dz);
                    if (b.getMaterial() == Material.water || b.isAir(world, x + dx, y + dy, z + dz)) {
                        world.setBlock(x + dx, y + dy, z + dz, Blocks.sand);
                    }
                }
            }
        }
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "dig.sand", 2.0F, 0.6F);
    }
}
