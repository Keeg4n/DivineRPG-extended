package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.block.BlockCloud;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Cloud Staff (Skythern reliquary): right click - a 3 x 3 cloud platform for {@link BlockCloud#TEMP_TICKS} ticks (30 s) under
 * the feet (in the air - catches a fall), and if the ground underfoot is solid - {@link #AHEAD} blocks forward at floor level (a bridge across a
 * chasm). Clouds are placed only into emptiness and only where the player may build (someone else's claim - no,
 * {@link com.divinerpgextended.perk.Protection#canBuild}); falling onto a cloud does not hurt. Cooldown {@link #COOLDOWN} ticks
 * (a bar on the item).
 */
public class ItemCloudStaff extends Item {

    public static final String READY = "dreCloudReady";
    public static final int COOLDOWN = 800, AHEAD = 3;

    public ItemCloudStaff() {
        setMaxStackSize(1);
        setFull3D();
        setUnlocalizedName("dre.cloudStaff");
        setTextureName("divinerpgextended:cloud_staff");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static long readyAt(ItemStack s) {
        return s.hasTagCompound() ? s.getTagCompound().getLong(READY) : 0;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        long now = w.getTotalWorldTime();
        if (readyAt(s) > now) return s;
        if (!w.isRemote) {
            int n = platform(w, p);
            if (n > 0) {
                if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
                s.getTagCompound().setLong(READY, now + COOLDOWN);
                w.playSoundAtEntity(p, "dig.cloth", 1.0F, 0.6F);
            }
        }
        p.swingItem();
        return s;
    }

    /** Place a platform; how many clouds were placed. */
    public static int platform(World w, EntityPlayer p) {
        int x = MathHelper.floor_double(p.posX), y = MathHelper.floor_double(p.boundingBox.minY) - 1, z = MathHelper.floor_double(p.posZ);
        int n = fill(w, p, x, y, z);
        if (n > 0) return n;
        int dir = MathHelper.floor_double(p.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int[][] step = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };
        return fill(w, p, x + step[dir][0] * AHEAD, y, z + step[dir][1] * AHEAD);
    }

    private static int fill(World w, EntityPlayer p, int cx, int y, int cz) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = cx + dx, z = cz + dz;
            if (y < 1 || y > 254 || !w.isAirBlock(x, y, z) || !com.divinerpgextended.perk.Protection.canBuild(p, x, y, z)) continue;
            // not into a creature: a cloud in another player's or mob's head suffocates them like a block
            if (!w.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1)).isEmpty()) continue;
            w.setBlock(x, y, z, ModBlocks.cloud, BlockCloud.TEMP, 3);
            w.scheduleBlockUpdate(x, y, z, ModBlocks.cloud, BlockCloud.TEMP_TICKS);
            n++;
        }
        return n;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean showDurabilityBar(ItemStack s) {
        return readyAt(s) > clientTime();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getDurabilityForDisplay(ItemStack s) {
        return Math.max(0, Math.min(1, (readyAt(s) - clientTime()) / (double) COOLDOWN));
    }

    @SideOnly(Side.CLIENT)
    private static long clientTime() {
        World w = net.minecraft.client.Minecraft.getMinecraft().theWorld;
        return w == null ? 0 : w.getTotalWorldTime();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List l, boolean adv) {
        l.add(EnumChatFormatting.WHITE + StatCollector.translateToLocalFormatted("dre.tooltip.cloud_staff", BlockCloud.TEMP_TICKS / 20, COOLDOWN / 20));
    }
}
