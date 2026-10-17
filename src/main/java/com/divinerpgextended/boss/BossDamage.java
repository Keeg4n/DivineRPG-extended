package com.divinerpgextended.boss;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.item.ArmorData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Урон ступенчатых боссов зависит от брони игрока: в броне требуемой ступени (или лучше) бой честный, а за каждую
 * ступень ниже урон умножается на underTierMultiplier. Работает до расчёта брони.
 */
public class BossDamage {

    /** Требуемая ступень брони против босса или -1, если босс не ступенчатый. */
    public static int requiredTier(Boss boss) {
        if (boss == null) return -1;
        switch (boss) {
            case CADILLION_EDEN:
            case CADILLION_WILDWOOD:
            case CADILLION_APALACHIA:
            case CADILLION_SKYTHERN: {
                int v = boss.ordinal() - Boss.CADILLION_EDEN.ordinal();
                return Config.cadillionRequiredTier[Math.min(v, Config.cadillionRequiredTier.length - 1)];
            }
            case TWILIGHT_DEMON:
                return Config.twilightDemonRequiredTier;
            case KAROT: // Карот - зеркало: силу задаёт снаряжение игрока, а не ступень брони
                return -1;
            default:
                return -1;
        }
    }

    /** Сырой урон великого кадиллиона: чтобы в броне требуемой ступени удар снимал cadillionHit здоровья. */
    public static float cadillionRawDamage(int variant) {
        int v = Math.min(variant, Config.cadillionHit.length - 1);
        int req = Config.cadillionRequiredTier[Math.min(variant, Config.cadillionRequiredTier.length - 1)];
        double reduction = Config.fullReduction(req) / 100.0;
        return (float) (Config.cadillionHit[v] / Math.max(0.05, 1.0 - reduction));
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer) || Config.underTierMultiplier <= 1.0) return;
        Entity attacker = event.source.getEntity();
        if (attacker == null) return;
        int req = requiredTier(Boss.of(attacker));
        if (req < 0) return;
        int have = ArmorData.effectiveTier((EntityPlayer) event.entityLiving);
        if (have >= req) return;
        event.ammount *= (float) Math.pow(Config.underTierMultiplier, req - have);
        com.divinerpgextended.debug.Debug.note(event.entityLiving, "Броня ступени " + have + " ниже нужной " + req + ": урон босса x" + Math.pow(Config.underTierMultiplier, req - have));
    }
}
