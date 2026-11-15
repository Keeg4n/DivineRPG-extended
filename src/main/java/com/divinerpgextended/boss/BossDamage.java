package com.divinerpgextended.boss;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.item.ArmorData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Tiered boss damage depends on the player's armor: in armor of the required tier (or better) the fight is fair, and for each
 * tier below the damage is multiplied by underTierMultiplier. Applied before the armor calculation.
 */
public class BossDamage {

    /** Armor tier required against the boss, or -1 if the boss is not tiered. */
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
            case KAROT: // Karot is a mirror: his strength is set by the player's gear, not by the armor tier
                return -1;
            default:
                return -1;
        }
    }

    /** Raw damage of the Great Cadillion: so that in armor of the required tier a hit takes off cadillionHit health. */
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
        com.divinerpgextended.debug.Debug.note(event.entityLiving, "Armor of tier " + have + " is below the required " + req + ": boss damage x" + Math.pow(Config.underTierMultiplier, req - have));
    }
}
