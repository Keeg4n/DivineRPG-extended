package com.divinerpgextended.item;

import net.minecraft.init.Items;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.player.FillBucketEvent;

import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Lethe Bucket. Lethe has the "water" material, and a regular bucket scooped plain water from the Mortum river: a bucket from a Lethe source
 * now becomes a Lethe Bucket ({@link Fill}), which places flowing Lethe (it flows like water). In the Forge fluid dictionary - a Lethe container.
 */
public class ItemLetheBucket extends ItemBucket {

    public ItemLetheBucket() {
        super(ModBlocks.letheFlowing);
        setContainerItem(Items.bucket);
        setMaxStackSize(1);
        setUnlocalizedName("dre.letheBucket");
        setTextureName("divinerpgextended:lethe_bucket");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    /** An empty bucket on a Lethe source becomes a Lethe Bucket (before, the vanilla logic made water out of it). */
    public static class Fill {

        @SubscribeEvent
        public void onFill(FillBucketEvent e) {
            if (e.world.isRemote || e.current == null || e.current.getItem() != Items.bucket || e.target == null
                || e.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
            int x = e.target.blockX, y = e.target.blockY, z = e.target.blockZ;
            // Lethe and dream water: both have the water material, a vanilla bucket would scoop plain water
            Item full = com.divinerpgextended.block.BlockLethe.isSource(e.world, x, y, z) ? com.divinerpgextended.registry.ModItems.letheBucket
                : com.divinerpgextended.block.BlockDreamWater.isSource(e.world, x, y, z) ? com.divinerpgextended.registry.ModItems.dreamWaterBucket : null;
            if (full == null || !e.entityPlayer.canPlayerEdit(x, y, z, e.target.sideHit, e.current)) return;
            e.world.setBlockToAir(x, y, z);
            e.result = new ItemStack(full);
            e.setResult(Event.Result.ALLOW);
        }
    }
}
