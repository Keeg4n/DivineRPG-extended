package com.divinerpgextended.perk;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Снаряжение с мобов Divine (лук инферно с Wildfire и т.п.) падает целым: игра роняет снаряжение моба со случайным износом,
 * а у Divine это редкое оружие, которое иначе не достать.
 */
public class WholeGear {

    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote || !e.entityLiving.getClass().getName().startsWith("net.divinerpg.")) return;
        for (EntityItem ei : e.drops) {
            ItemStack s = ei.getEntityItem();
            // без подтипов урон предмета - только износ (у "неломаемых" луков Divine прочность 0, а износ игра ставила всё равно)
            if (s != null && !s.getHasSubtypes() && s.getItemDamage() > 0) s.setItemDamage(0);
        }
    }
}
