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
 * Стены крабьей норы: на вид песчаник (мета 0) и песок (мета 1). Сломал в выживании или взорвал - нора обрушивается:
 * проход вокруг засыпает песком. Сами блоки норы, гнездо и сердце не унести: в инвентаре игрока сразу становятся обычными
 * песчаником, песком и резным песчаником ({@link Convert}), поставленные в выживании - тоже.
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

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, net.minecraft.entity.EntityLivingBase e, net.minecraft.item.ItemStack s) {
        Convert.placed(w, x, y, z, e, s);
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

    /** Ванильный двойник блока норы (null - не блок норы). */
    public static net.minecraft.item.ItemStack plain(net.minecraft.item.ItemStack s) {
        if (s == null) return null;
        Block b = Block.getBlockFromItem(s.getItem());
        if (b == com.divinerpgextended.registry.ModBlocks.burrow)
            return s.getItemDamage() == 1 ? new net.minecraft.item.ItemStack(Blocks.sand, s.stackSize) : new net.minecraft.item.ItemStack(Blocks.sandstone, s.stackSize);
        if (b == com.divinerpgextended.registry.ModBlocks.carcinusHeart) return new net.minecraft.item.ItemStack(Blocks.sandstone, s.stackSize, 1);
        if (b == com.divinerpgextended.registry.ModBlocks.sharkNest) return new net.minecraft.item.ItemStack(Blocks.sand, s.stackSize);
        return null;
    }

    /** Блоки норы у игрока в выживании - обычные; поставленные им - тоже обычные, без свойств норы. */
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
