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
 * Walls of the crab burrow: look like sandstone (meta 0) and sand (meta 1). Broken in survival or blown up - the burrow collapses:
 * the passage around is filled with sand. The burrow blocks themselves, the nest and the heart cannot be carried away: in the player's inventory they immediately become ordinary
 * sandstone, sand and chiseled sandstone ({@link Convert}), and those placed in survival do too.
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

    /** In survival, falling sand appears at once in place of the broken block: the breach is filled at once. */
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

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, net.minecraft.entity.EntityLivingBase e, net.minecraft.item.ItemStack s) {
        Convert.placed(w, x, y, z, e, s);
    }

    /** Fill with sand everything empty and flooded around the breach: the sand falls and clogs the passage. */
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

    /** Vanilla twin of a burrow block (null - not a burrow block). */
    public static net.minecraft.item.ItemStack plain(net.minecraft.item.ItemStack s) {
        if (s == null) return null;
        Block b = Block.getBlockFromItem(s.getItem());
        if (b == com.divinerpgextended.registry.ModBlocks.burrow)
            return s.getItemDamage() == 1 ? new net.minecraft.item.ItemStack(Blocks.sand, s.stackSize) : new net.minecraft.item.ItemStack(Blocks.sandstone, s.stackSize);
        if (b == com.divinerpgextended.registry.ModBlocks.carcinusHeart) return new net.minecraft.item.ItemStack(Blocks.sandstone, s.stackSize, 1);
        if (b == com.divinerpgextended.registry.ModBlocks.sharkNest) return new net.minecraft.item.ItemStack(Blocks.sand, s.stackSize);
        return null;
    }

    /** Burrow blocks in a survival player's hands are ordinary; those placed by him too, without burrow properties. */
    public static class Convert {

        static void placed(World w, int x, int y, int z, net.minecraft.entity.EntityLivingBase e, net.minecraft.item.ItemStack s) {
            if (w.isRemote || !(e instanceof EntityPlayer) || ((EntityPlayer) e).capabilities.isCreativeMode) return;
            net.minecraft.item.ItemStack p = plain(new net.minecraft.item.ItemStack(s.getItem(), 1, s.getItemDamage()));
            if (p != null) w.setBlock(x, y, z, Block.getBlockFromItem(p.getItem()), p.getItemDamage(), 3);
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onTick(cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent e) {
            EntityPlayer p = e.player;
            if (e.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END || p.worldObj.isRemote || p.capabilities.isCreativeMode
                || p.ticksExisted % 10 != 0) return;
            net.minecraft.item.ItemStack[] inv = p.inventory.mainInventory;
            boolean changed = false;
            for (int i = 0; i < inv.length; i++) {
                net.minecraft.item.ItemStack r = plain(inv[i]);
                if (r == null) continue;
                inv[i] = r;
                changed = true;
            }
            if (changed) p.inventoryContainer.detectAndSendChanges();
        }
    }
}
