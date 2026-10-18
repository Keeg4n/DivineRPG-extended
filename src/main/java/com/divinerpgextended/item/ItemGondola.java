package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.divinerpgextended.entity.EntityGondola;

/** Ладья Харона: ставится на воду или Лету, как лодка (покупается у Харона за души Мортума). */
public class ItemGondola extends Item {

    public ItemGondola() {
        setMaxStackSize(1);
        setUnlocalizedName("dre.gondola");
        setTextureName("divinerpgextended:gondola");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        Vec3 from = Vec3.createVectorHelper(p.posX, p.posY + 1.62 - p.yOffset, p.posZ);
        float yaw = p.rotationYaw, pitch = p.rotationPitch;
        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI), sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = -MathHelper.cos(-pitch * 0.017453292F), sp = MathHelper.sin(-pitch * 0.017453292F);
        Vec3 to = from.addVector(sy * cp * 5.0, sp * 5.0, cy * cp * 5.0);
        MovingObjectPosition hit = w.rayTraceBlocks(from, to, true);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return s;
        int x = hit.blockX, y = hit.blockY, z = hit.blockZ;
        if (w.getBlock(x, y, z) == Blocks.snow_layer) y--;
        EntityGondola g = new EntityGondola(w, x + 0.5, y + 1.0, z + 0.5);
        g.rotationYaw = ((MathHelper.floor_double(p.rotationYaw * 4.0F / 360.0F + 0.5D) & 3) - 1) * 90;
        if (!w.getCollidingBoundingBoxes(g, g.boundingBox.expand(-0.1, -0.1, -0.1)).isEmpty()) return s;
        if (!w.isRemote) w.spawnEntityInWorld(g);
        if (!p.capabilities.isCreativeMode) s.stackSize--;
        return s;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.gondola"));
    }
}
