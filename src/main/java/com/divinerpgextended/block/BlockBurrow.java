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
 * <p>
 * Metas from {@link #DECOR} (1.1.3) are the decor of lairs that must not be a free resource: the wreck in the Carcinus lair (planks, logs,
 * sail), the inlaid dial of the tomb hall (colored clay), the sand of the hourglass. They look like the vanilla blocks, drop nothing,
 * do not fall and do not collapse the burrow.
 */
public class BlockBurrow extends Block {

    /** Decor metas: dark planks, spruce log, white and gray sail, clay white / blue / yellow / orange / brown, sand, light planks. */
    public static final int DECOR = 2, PLANKS = 2, LOG = 3, SAIL = 4, SAIL_GRAY = 5, CLAY_WHITE = 6, CLAY_BLUE = 7, CLAY_YELLOW = 8,
        CLAY_ORANGE = 9, CLAY_BROWN = 10, SAND = 11, PLANKS_LIGHT = 12;

    private static final String[] DECOR_ICONS = { "planks_big_oak", "log_spruce", "wool_colored_white", "wool_colored_gray",
        "hardened_clay_stained_white", "hardened_clay_stained_blue", "hardened_clay_stained_yellow", "hardened_clay_stained_orange",
        "hardened_clay_stained_brown", "sand", "planks_oak" };

    private IIcon sandstoneSide, sandstoneTop, sand, logTop;
    private final IIcon[] decor = new IIcon[DECOR_ICONS.length];

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
        logTop = reg.registerIcon("log_spruce_top");
        for (int i = 0; i < DECOR_ICONS.length; i++) decor[i] = reg.registerIcon(DECOR_ICONS[i]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (meta == 1) return sand;
        if (meta == LOG && (side == 0 || side == 1)) return logTop;
        if (meta >= DECOR && meta - DECOR < decor.length) return decor[meta - DECOR];
        return side == 0 || side == 1 ? sandstoneTop : sandstoneSide;
    }

    /** Decor drops nothing (the walls drop themselves and turn into ordinary sandstone and sand in the inventory). */
    @Override
    public net.minecraft.item.Item getItemDropped(int meta, java.util.Random rand, int fortune) {
        return meta >= DECOR ? null : super.getItemDropped(meta, rand, fortune);
    }

    /** Silk touch does not take the decor whole either. */
    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int meta) {
        return meta < DECOR && super.canSilkHarvest(world, player, x, y, z, meta);
    }

    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
        if (!world.isRemote && !player.capabilities.isCreativeMode && meta < DECOR) {
            collapse(world, x, y, z, player);
            player.addChatMessage(new ChatComponentTranslation("dre.chat.burrow_collapse"));
        }
        super.onBlockHarvested(world, x, y, z, meta, player);
    }

    /** In survival, falling sand appears at once in place of the broken block: the breach is filled at once. Decor is simply removed. */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!world.isRemote && !player.capabilities.isCreativeMode && world.getBlockMetadata(x, y, z) < DECOR) {
            return world.setBlock(x, y, z, Blocks.sand);
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    /** The meta is known only before removal: the decor does not collapse the burrow when blown up either. */
    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
        int meta = world.getBlockMetadata(x, y, z);
        world.setBlockToAir(x, y, z);
        if (!world.isRemote && meta < DECOR) collapse(world, x, y, z);
    }

    @Override
    public void onBlockDestroyedByExplosion(World world, int x, int y, int z, Explosion explosion) {
        if (!world.isRemote) collapse(world, x, y, z);
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, net.minecraft.entity.EntityLivingBase e, net.minecraft.item.ItemStack s) {
        Convert.placed(w, x, y, z, e, s);
    }

    public static void collapse(World world, int x, int y, int z) {
        collapse(world, x, y, z, null);
    }

    /**
     * Fill with sand the empty and flooded passage around the breach: the sand falls and clogs the passage. Only cells touching the burrow
     * walls (the passage itself, not a base dug next to it), and if a player broke it - only where he may build: before, the whole 7x6x7 box
     * was filled, reaching into a neighboring region, and every breach gave hundreds of free sand.
     */
    public static void collapse(World world, int x, int y, int z, EntityPlayer by) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    int cx = x + dx, cy = y + dy, cz = z + dz;
                    Block b = world.getBlock(cx, cy, cz);
                    if (b.getMaterial() != Material.water && !b.isAir(world, cx, cy, cz) || !wall(world, cx, cy, cz)) continue;
                    if (by != null && !com.divinerpgextended.perk.Protection.canBuildCached(by, cx, cy, cz)) continue;
                    world.setBlock(cx, cy, cz, Blocks.sand);
                }
            }
        }
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "dig.sand", 2.0F, 0.6F);
    }

    /** The cell touches a burrow wall, the nest or the heart (or the breach itself, already sand). */
    private static boolean wall(World w, int x, int y, int z) {
        int[][] d = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] o : d) {
            Block b = w.getBlock(x + o[0], y + o[1], z + o[2]);
            if (b == com.divinerpgextended.registry.ModBlocks.burrow || b == com.divinerpgextended.registry.ModBlocks.sharkNest
                || b == com.divinerpgextended.registry.ModBlocks.carcinusHeart) return true;
        }
        return false;
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
