package com.divinerpgextended.boss;

import net.divinerpg.entities.vanilla.EntityAyeraco;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Every Ayeraco killed by a player drops an Ayeraco Scale - the center of the Ayeraco Prism recipe. */
public class AyeracoScales {

    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (!(e.entityLiving instanceof EntityAyeraco) || e.entityLiving.worldObj.isRemote || !e.recentlyHit || ModItems.ayeracoScale == null) return;
        EntityItem it = new EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY + 1, e.entityLiving.posZ, new ItemStack(ModItems.ayeracoScale));
        it.delayBeforeCanPickup = 10;
        e.drops.add(it);
    }
}
