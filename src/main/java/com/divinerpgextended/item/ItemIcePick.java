package com.divinerpgextended.item;

import java.util.List;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Iceika pickaxes. The Snow Pickaxe (from the Frosts' ice stones) digs frozen rock (stone, dirt, snow, ice)
 * about twice as fast as a diamond one, one block at a time. The Rime Pickaxe (from Rime Ingots) - instantly and in a 3x3 square, sneaking -
 * one block at a time. Dungeon walls, chests and spawners are not touched. Outside Iceika - regular sturdy pickaxes.
 */
public class ItemIcePick extends ItemPickaxe {

    private static final ToolMaterial SNOW = EnumHelper.addToolMaterial("DRE_ICE_PICK", 2, 1000, 6.0F, 2.0F, 14);
    private static final ToolMaterial RIME = EnumHelper.addToolMaterial("DRE_RIME_PICK", 3, 2500, 8.0F, 3.0F, 16);

    /** true - Rime Pickaxe (instant, 3x3), false - Snow Pickaxe (faster, one block). */
    private final boolean rime;

    public ItemIcePick(boolean rime) {
        super(rime ? RIME : SNOW);
        this.rime = rime;
        setUnlocalizedName(rime ? "dre.rimePick" : "dre.icePick");
        setTextureName(rime ? "divinerpgextended:rimePick" : "divinerpgextended:icePick");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private float iceikaSpeed() {
        return rime ? 60.0F : 14.0F;
    }

    private static boolean inIceika(World world) {
        return world != null && world.provider.dimensionId == ConfigurationHelper.iceika;
    }

    /** Iceika frozen rock that the ice pick digs quickly and in a square. */
    public static boolean isIcyRock(Block b) {
        return b == IceikaBlocks.frozenStone || b == IceikaBlocks.frozenDirt || b == IceikaBlocks.frozenGrass
            || b == Blocks.snow || b == Blocks.snow_layer || b == Blocks.ice || b == Blocks.packed_ice
            || b == Blocks.stone || b == Blocks.dirt || b == Blocks.gravel || b == ModBlocks.frostIce;
    }

    /** What the Rime Pickaxe breaks in a 3x3 square: frozen rock and rime ore. */
    public static boolean square(Block b) {
        return isIcyRock(b) || b == ModBlocks.rimeOre;
    }

    /** Speed in Iceika: getDigSpeed is not passed the world, so via an event. */
    public static class Handler {

        @SubscribeEvent
        public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
            ItemStack held = event.entityPlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemIcePick) || !isIcyRock(event.block)
                || !inIceika(event.entityPlayer.worldObj)) return;
            event.newSpeed = Math.max(event.newSpeed, ((ItemIcePick) held.getItem()).iceikaSpeed());
        }
    }

    @Override
    public boolean canHarvestBlock(Block block, ItemStack stack) {
        return isIcyRock(block) || super.canHarvestBlock(block, stack);
    }

    /** Breaking of neighbors is in progress (it calls onBlockDestroyed itself - we do not recurse). */
    private static boolean breaking;

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World world, Block block, int x, int y, int z,
        EntityLivingBase entity) {
        boolean r = super.onBlockDestroyed(stack, world, block, x, y, z, entity);
        if (!rime || world.isRemote || !inIceika(world) || !square(block) || !(entity instanceof EntityPlayer)) return r;
        EntityPlayer player = (EntityPlayer) entity;
        if (player.isSneaking() || breaking || !(player instanceof net.minecraft.entity.player.EntityPlayerMP)) return r;
        breaking = true;
        try {
        // the 3x3 plane across the view: looking up/down - horizontal, otherwise a vertical wall
        boolean vertical = Math.abs(player.rotationPitch) > 50;
        double yaw = Math.toRadians(player.rotationYaw);
        boolean alongX = Math.abs(Math.sin(yaw)) > Math.abs(Math.cos(yaw));
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                int dx, dy, dz;
                if (vertical) {
                    dx = a;
                    dy = 0;
                    dz = b;
                } else if (alongX) {
                    dx = 0;
                    dy = a;
                    dz = b;
                } else {
                    dx = a;
                    dy = b;
                    dz = 0;
                }
                int bx = x + dx, by = y + dy, bz = z + dz;
                Block other = world.getBlock(bx, by, bz);
                if (!square(other) || other.getBlockHardness(world, bx, by, bz) < 0) continue;
                if (stack.stackSize <= 0) return r; // the pickaxe broke
                // like a regular player hit: claims and plugins see the breaking and can forbid it
                ((net.minecraft.entity.player.EntityPlayerMP) player).theItemInWorldManager.tryHarvestBlock(bx, by, bz);
            }
        }
        } finally {
            breaking = false;
        }
        return r;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        com.divinerpgextended.item.ToolInfo.add(list, stack, efficiencyOnProperMaterial);
        if (rime) {
            list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.ice_pick"));
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.ice_pick_sneak"));
        } else {
            list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.snow_pick"));
        }
    }
}
