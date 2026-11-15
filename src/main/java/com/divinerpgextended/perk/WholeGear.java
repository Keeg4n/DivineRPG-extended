package com.divinerpgextended.perk;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Gear from Divine mobs (the inferno bow from Wildfire, etc.) drops whole: the game drops a mob's gear with random wear,
 * but in Divine this is rare weaponry that cannot be obtained otherwise.
 */
public class WholeGear {

    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote || !e.entityLiving.getClass().getName().startsWith("net.divinerpg.")) return;
        for (EntityItem ei : e.drops) {
            ItemStack s = ei.getEntityItem();
            // without subtypes the item damage is only wear (Divine's "unbreakable" bows have durability 0, and the game set wear anyway)
            if (s != null && !s.getHasSubtypes() && s.getItemDamage() > 0) s.setItemDamage(0);
        }
    }
}
