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
 * Посмертие Мортума: убитый игроком моб с шансом {@link Config#mortumSoulChance} оставляет душу ({@link EntityLostSoul}). Добыча
 * первой смерти падает как обычно, вставший моб добычи и опыта уже не даёт и второй раз не встаёт. Боссы, их приспешники и души
 * Пожирателя не считаются: у них свои правила.
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
            // опыт падает, пока недавно бил игрок, - убираем отметку удара
            try {
                ReflectionHelper.setPrivateValue(EntityLivingBase.class, v, 0, "recentlyHit", "field_70718_bc");
            } catch (Throwable ignored) {}
            return;
        }
        if (!(v instanceof EntityLiving) || !(v instanceof IMob) || v instanceof IBossDisplayData || com.divinerpgextended.boss.Boss.of(v) != null || BossMinions.isMinion(v) || v.getEntityData().hasKey(MortumArenaBosses.SOUL)) return;
        if (!(e.source.getEntity() instanceof EntityPlayer) || v.getRNG().nextDouble() >= Config.mortumSoulChance) return;
        v.worldObj.spawnEntityInWorld(new EntityLostSoul(v.worldObj, (EntityLiving) v));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent e) {
        if (risen(e.entityLiving)) e.drops.clear();
    }
}
