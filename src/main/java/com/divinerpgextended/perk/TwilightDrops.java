package com.divinerpgextended.perk;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.twilight.TwilightMaterial;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** С мобов сумеречных миров, убитых игроком, кроме душ падает их материал: 35% и +10% за уровень Добычи. */
public class TwilightDrops {

    public static final float CHANCE = 0.35F, PER_LOOTING = 0.10F;

    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer)) return;
        TwilightMaterial m = TwilightMaterial.of(e.entityLiving);
        if (m == null || e.entityLiving.worldObj.rand.nextFloat() >= CHANCE + PER_LOOTING * e.lootingLevel) return;
        e.drops.add(new EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY + 0.5, e.entityLiving.posZ,
            new ItemStack(ModItems.twilightMaterial, 1, m.ordinal())));
    }
}
