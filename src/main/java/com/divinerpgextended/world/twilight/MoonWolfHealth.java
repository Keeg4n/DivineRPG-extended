package com.divinerpgextended.world.twilight;

import net.divinerpg.entities.twilight.EntityMoonWolf;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraftforge.event.entity.living.LivingEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Лунный волк: дикий - 200 здоровья (EntityStats.moonWolfHealth), а Divine при приручении ставит ему жёстко 20.
 * Приручённому возвращаем здоровье дикого (и лечим до него один раз - тем, кто уже приручён с 20).
 */
public class MoonWolfHealth {

    private static double wild = -1;

    static double wild() {
        if (wild < 0) {
            try {
                wild = net.divinerpg.entities.base.EntityStats.class.getField("moonWolfHealth").getDouble(null);
            } catch (Throwable t) {
                wild = 0;
            }
            if (wild <= 0) wild = 200;
        }
        return wild;
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.entityLiving instanceof EntityMoonWolf) || e.entityLiving.ticksExisted % 20 != 0) return;
        EntityMoonWolf w = (EntityMoonWolf) e.entityLiving;
        if (!w.isTamed()) return;
        IAttributeInstance hp = w.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        if (hp.getBaseValue() >= wild()) return;
        boolean wasFull = w.getHealth() >= w.getMaxHealth() - 0.01F;
        hp.setBaseValue(wild());
        if (wasFull || !w.getEntityData().getBoolean("dreMoonWolfHp")) w.setHealth(w.getMaxHealth());
        w.getEntityData().setBoolean("dreMoonWolfHp", true);
    }
}
