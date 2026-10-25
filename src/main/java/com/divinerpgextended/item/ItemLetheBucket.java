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
 * Ведро Леты. У Леты материал "вода", и обычное ведро набирало из реки Мортума простую воду: ведро из источника Леты
 * теперь становится ведром Леты ({@link Fill}), а оно ставит текучую Лету (течёт, как вода). В словаре жидкостей Forge - контейнер Леты.
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

    /** Пустое ведро по источнику Леты - ведро Леты (раньше ванильная логика делала из неё воду). */
    public static class Fill {

        @SubscribeEvent
        public void onFill(FillBucketEvent e) {
            if (e.world.isRemote || e.current == null || e.current.getItem() != Items.bucket || e.target == null
                || e.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
            int x = e.target.blockX, y = e.target.blockY, z = e.target.blockZ;
            // Лета и вода мечтаний: обе - материал воды, ванильное ведро набрало бы простую воду
            Item full = com.divinerpgextended.block.BlockLethe.isSource(e.world, x, y, z) ? com.divinerpgextended.registry.ModItems.letheBucket
                : com.divinerpgextended.block.BlockDreamWater.isSource(e.world, x, y, z) ? com.divinerpgextended.registry.ModItems.dreamWaterBucket : null;
            if (full == null || !e.entityPlayer.canPlayerEdit(x, y, z, e.target.sideHit, e.current)) return;
            e.world.setBlockToAir(x, y, z);
            e.result = new ItemStack(full);
            e.setResult(Event.Result.ALLOW);
        }
    }
}
