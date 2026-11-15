package com.divinerpgextended.world.twilight;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.BossMinions;
import com.divinerpgextended.boss.MortumArenaBosses;
import com.divinerpgextended.entity.EntityLostSoul;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Mortum afterlife: a mob killed by a player leaves a soul ({@link EntityLostSoul}) with chance {@link Config#mortumSoulChance}. The loot
 * of the first death drops as usual, the risen mob gives no loot or experience and does not rise a second time. Bosses, their minions and Soul Fiend
 * souls do not count: they have their own rules.
 */
public class MortumAfterlife {

    public static final String RISEN = "dreRisen";

    private static boolean risen(EntityLivingBase e) {
        return e.getEntityData().getBoolean(RISEN);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || v.dimension != ConfigurationHelper.mortum) return;
        if (risen(v)) {
            // experience drops while a player hit recently - remove the hit mark
            try {
                ReflectionHelper.setPrivateValue(EntityLivingBase.class, v, 0, "recentlyHit", "field_70718_bc");
            } catch (Throwable ignored) {}
            return;
        }
        if (!(v instanceof EntityLiving) || !(v instanceof IMob) || v instanceof IBossDisplayData || v instanceof com.divinerpgextended.entity.mini.EntityMiniBoss || com.divinerpgextended.boss.Boss.of(v) != null || BossMinions.isMinion(v) || v.getEntityData().hasKey(MortumArenaBosses.SOUL)) return;
        if (!(e.source.getEntity() instanceof EntityPlayer) || v.getRNG().nextDouble() >= Config.mortumSoulChance) return;
        v.worldObj.spawnEntityInWorld(new EntityLostSoul(v.worldObj, (EntityLiving) v));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent e) {
        if (risen(e.entityLiving)) e.drops.clear();
    }
}
