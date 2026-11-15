package com.divinerpgextended.perk;

import net.divinerpg.utils.Util;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.TwilightItemsArmor;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.vethea.Nightmare;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Properties of Divine full sets - the same numbers as in EventArmorFullSet, but armor is read from the right player right in
 * the handler and only on the server (Divine has shared fields that get overwritten; see ArmorSetTransformer). Works
 * only if the patch of the Divine class succeeded, otherwise Divine would also apply the properties - twice over.
 * <p>
 * In Vethea, sets not from Vethea give nothing (nightmare in reverse). Explosion protection (Bedrock, Ender) - only for those who defeated
 * the Watcher: his fight is about explosions, and the reward for it must not cancel them in advance.
 */
public class ArmorSets {

    private static Boolean active;

    private static void note(Entity e, String s) {
        com.divinerpgextended.debug.Debug.note(e, s);
    }

    private static boolean active() {
        if (active == null) {
            try {
                active = Class.forName("net.divinerpg.utils.events.EventArmorFullSet").getField("dre_patched").getBoolean(null);
            } catch (Throwable t) {
                active = false;
            }
        }
        return active;
    }

    /** Player's armor: [boots, leggings, chestplate, helmet]. */
    private static Item[] armor(EntityPlayer p) {
        Item[] a = new Item[4];
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.inventory.armorItemInSlot(i);
            a[i] = s != null ? s.getItem() : null;
        }
        return a;
    }

    private static boolean set(Item[] a, Item boots, Item legs, Item body, Item helmet) {
        return a[0] == boots && a[1] == legs && a[2] == body && a[3] == helmet;
    }

    private static boolean any(Item i, Item... options) {
        for (Item o : options) if (i == o) return true;
        return false;
    }

    /** A Vethea set without the helmet: boots, leggings, chestplate match. */
    private static boolean vetheaBody(Item[] a, Item boots, Item legs, Item body) {
        return a[0] == boots && a[1] == legs && a[2] == body;
    }

    /** Protection multiplier of a Vethea set: helmet - against melee, mask - against arrows, hood - against magic. */
    private static boolean vetheaGuard(Item helmetWorn, Item helmet, Item mask, Item hood, DamageSource s) {
        boolean proj = s.isProjectile(), magic = s.isMagicDamage();
        return helmetWorn == helmet && !proj && !magic || helmetWorn == mask && proj && !magic || helmetWorn == hood && magic;
    }

    // ---------------- Protection: player is attacked ----------------

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !active()) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if (Nightmare.active(p)) return; // there are no Vethea sets among these
        Item[] a = armor(p);
        DamageSource s = e.source;
        if ((set(a, VanillaItemsArmor.aquastriveBoots, VanillaItemsArmor.aquastriveLegs, VanillaItemsArmor.aquastriveBody,
            VanillaItemsArmor.aquastriveHelmet)
            || set(a, VanillaItemsArmor.krakenBoots, VanillaItemsArmor.krakenLegs, VanillaItemsArmor.krakenBody, VanillaItemsArmor.krakenHelmet))
            && s == DamageSource.drown) {
            e.setCanceled(true);
            note(p, "Kraken or Aquastrive armor: no drowning");
        }
        else if (set(a, TwilightItemsArmor.apalachiaBoots, TwilightItemsArmor.apalachiaLeggings, TwilightItemsArmor.apalachiaChestplate,
            TwilightItemsArmor.apalachiaHelmet)
            && (s == DamageSource.cactus || s == DamageSource.fallingBlock || s == DamageSource.anvil || s == DamageSource.inWall
                || s == Util.trapSource)) {
            e.setCanceled(true);
            note(p, "Apalachia set: cactus, traps and blocks do not hurt");
        }
        else if (set(a, VanillaItemsArmor.witherReaperBoots, VanillaItemsArmor.witherReaperLegs, VanillaItemsArmor.witherReaperBody,
            VanillaItemsArmor.witherReaperHelmet) && s == DamageSource.wither) {
            e.setCanceled(true);
            note(p, "Wither Reaper set: wither does not hurt");
        }
        else if (set(a, VanillaItemsArmor.jungleBoots, VanillaItemsArmor.jungleLegs, VanillaItemsArmor.jungleBody,
            VanillaItemsArmor.jungleHelmet) && s == DamageSource.magic && p.isPotionActive(net.minecraft.potion.Potion.poison) && e.ammount <= 1.0F) {
            // only the poison tick (1 damage with poison): the harming potion and foreign magic work as usual
            e.setCanceled(true);
            note(p, "Jungle set: poison does not hurt");
        }
    }

    // ---------------- Damage: victim's protection and attacker's bonus ----------------

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote || !active()) return;
        if (e.entityLiving instanceof EntityPlayer) defend(e, (EntityPlayer) e.entityLiving);
        if (e.isCanceled()) return;
        Entity src = e.source.getEntity();
        if (src instanceof EntityPlayer) attack(e, (EntityPlayer) src);
    }

    private static void defend(LivingHurtEvent e, EntityPlayer p) {
        Item[] a = armor(p);
        DamageSource s = e.source;
        boolean dream = Nightmare.active(p);
        if (!dream) {
            boolean blastProof = com.divinerpgextended.boss.BossProgress.has(p, com.divinerpgextended.boss.Boss.WATCHER); // explosion protection - after the Watcher
            if (set(a, VanillaItemsArmor.bedrockBoots, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBody,
                VanillaItemsArmor.bedrockHelmet) && s.isExplosion() && blastProof) {
                e.setCanceled(true);
                note(p, "Bedrock set: explosion does not hurt");
                return;
            }
            if (any(a[0], VanillaItemsArmor.enderBoots, VanillaItemsArmor.redEnderBoots, VanillaItemsArmor.yellowEnderBoots,
                VanillaItemsArmor.greenEnderBoots, VanillaItemsArmor.blueEnderBoots, VanillaItemsArmor.grayEnderBoots)
                && any(a[1], VanillaItemsArmor.enderLegs, VanillaItemsArmor.redEnderLegs, VanillaItemsArmor.yellowEnderLegs,
                    VanillaItemsArmor.greenEnderLegs, VanillaItemsArmor.blueEnderLegs, VanillaItemsArmor.grayEnderLegs)
                && any(a[2], VanillaItemsArmor.enderBody, VanillaItemsArmor.redEnderBody, VanillaItemsArmor.yellowEnderBody,
                    VanillaItemsArmor.greenEnderBody, VanillaItemsArmor.blueEnderBody, VanillaItemsArmor.grayEnderBody)
                && any(a[3], VanillaItemsArmor.enderHelmet, VanillaItemsArmor.redEnderHelmet, VanillaItemsArmor.yellowEnderHelmet,
                    VanillaItemsArmor.greenEnderHelmet, VanillaItemsArmor.blueEnderHelmet, VanillaItemsArmor.grayEnderHelmet)
                && s.isExplosion() && blastProof) {
                e.setCanceled(true);
                note(p, "Ender set: explosion does not hurt");
                return;
            }
            if (set(a, VanillaItemsArmor.arlemiteBoots, VanillaItemsArmor.arlemiteLegs, VanillaItemsArmor.arlemiteBody,
                VanillaItemsArmor.arlemiteHelmet) && (s.isProjectile() || "thrown".equals(s.damageType))) {
            e.ammount *= 0.3F;
            note(p, "Arlemite set: arrows x0.3");
        }
            if (any(a[0], VanillaItemsArmor.rupeeBoots, VanillaItemsArmor.redRupeeBoots, VanillaItemsArmor.yellowRupeeBoots,
                VanillaItemsArmor.greenRupeeBoots, VanillaItemsArmor.blueRupeeBoots, VanillaItemsArmor.grayRupeeBoots)
                && any(a[1], VanillaItemsArmor.rupeeLegs, VanillaItemsArmor.redRupeeLegs, VanillaItemsArmor.yellowRupeeLegs,
                    VanillaItemsArmor.greenRupeeLegs, VanillaItemsArmor.blueRupeeLegs, VanillaItemsArmor.grayRupeeLegs)
                && any(a[2], VanillaItemsArmor.rupeeBody, VanillaItemsArmor.redRupeeBody, VanillaItemsArmor.yellowRupeeBody,
                    VanillaItemsArmor.greenRupeeBody, VanillaItemsArmor.blueRupeeBody, VanillaItemsArmor.grayRupeeBody)
                && any(a[3], VanillaItemsArmor.rupeeHelmet, VanillaItemsArmor.redRupeeHelmet, VanillaItemsArmor.yellowRupeeHelmet,
                    VanillaItemsArmor.greenRupeeHelmet, VanillaItemsArmor.blueRupeeHelmet, VanillaItemsArmor.grayRupeeHelmet)
                && "mob".equals(s.damageType) && !s.isProjectile()) {
            e.ammount *= 0.3F;
            note(p, "Rupee set: mob hits x0.3");
        }
            if (set(a, IceikaItems.santaBoots, IceikaItems.santaPants, IceikaItems.santaTunic, IceikaItems.santaCap)
                && p.worldObj.provider.dimensionId == ConfigurationHelper.iceika) {
            e.ammount *= 0.2F;
            note(p, "Santa set in Iceika: x0.2");
        }
        }
        // Vethea sets: the helmet, mask or hood decides what it protects against
        if (vetheaBody(a, VetheaItems.degradedBoots, VetheaItems.degradedLegs, VetheaItems.degradedBody)
            && vetheaGuard(a[3], VetheaItems.degradedHelmet, VetheaItems.degradedMask, VetheaItems.degradedHood, s)) {
            e.ammount *= 0.82F;
            note(p, "Degraded set: x0.82");
        }
        if (vetheaBody(a, VetheaItems.finishedBoots, VetheaItems.finishedLegs, VetheaItems.finishedBody)
            && vetheaGuard(a[3], VetheaItems.finishedHelmet, VetheaItems.finishedMask, VetheaItems.finishedHood, s)) {
            e.ammount *= 0.773F;
            note(p, "Finished set: x0.773");
        }
        if (vetheaBody(a, VetheaItems.glisteningBoots, VetheaItems.glisteningLegs, VetheaItems.glisteningBody)
            && vetheaGuard(a[3], VetheaItems.glisteningHelmet, VetheaItems.glisteningMask, VetheaItems.glisteningHood, s)) {
            e.ammount *= 0.7F;
            note(p, "Glistening set: x0.7");
        }
        if (vetheaBody(a, VetheaItems.demonizedBoots, VetheaItems.demonizedLegs, VetheaItems.demonizedBody)
            && vetheaGuard(a[3], VetheaItems.demonizedHelmet, VetheaItems.demonizedMask, VetheaItems.demonizedHood, s)) {
            e.ammount *= 0.625F;
            note(p, "Demonized set: x0.625");
        }
        if (vetheaBody(a, VetheaItems.tormentedBoots, VetheaItems.tormentedLegs, VetheaItems.tormentedBody)
            && vetheaGuard(a[3], VetheaItems.tormentedHelmet, VetheaItems.tormentedMask, VetheaItems.tormentedHood, s)) {
            e.ammount *= 0.348F;
            note(p, "Tormented set: x0.348");
        }
    }

    private static void attack(LivingHurtEvent e, EntityPlayer p) {
        Item[] a = armor(p);
        DamageSource s = e.source;
        boolean melee = !s.isProjectile() && !s.isMagicDamage(), dream = Nightmare.active(p);
        if (!dream) {
            if (melee && set(a, IceikaItems.santaBoots, IceikaItems.santaPants, IceikaItems.santaTunic, IceikaItems.santaCap)
                && e.entityLiving.worldObj.provider.dimensionId == ConfigurationHelper.iceika) {
            e.ammount += 6.0F;
            note(p, "Santa set in Iceika: +6");
        }
            if (melee && set(a, TwilightItemsArmor.haliteBoots, TwilightItemsArmor.haliteLeggings, TwilightItemsArmor.haliteChestplate,
                TwilightItemsArmor.haliteHelmet)) {
            e.ammount += 16.0F;
            note(p, "Halite set: +16");
        }
            if (melee && set(a, VanillaItemsArmor.divineBoots, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBody,
                VanillaItemsArmor.divineHelmet)) {
            float bonus = PerkEffects.divineMelee(e.entityLiving);
            e.ammount += bonus;
            note(p, "Divine set: +" + bonus);
        }
            if (s.isProjectile() && set(a, VanillaItemsArmor.corruptedBoots, VanillaItemsArmor.corruptedLegs,
                VanillaItemsArmor.corruptedBody, VanillaItemsArmor.corruptedHelmet)) {
            e.ammount *= 1.2F;
            note(p, "Corrupted set: shooting x1.2");
        }
        }
        if (melee && set(a, VetheaItems.glisteningBoots, VetheaItems.glisteningLegs, VetheaItems.glisteningBody, VetheaItems.glisteningHelmet))
        {
            e.ammount += 3.0F;
            note(p, "Glistening set: +3");
        }
        if (melee && set(a, VetheaItems.demonizedBoots, VetheaItems.demonizedLegs, VetheaItems.demonizedBody, VetheaItems.demonizedHelmet)) {
            e.ammount += 6.0F;
            note(p, "Demonized set: +6");
        }
        if (melee && set(a, VetheaItems.tormentedBoots, VetheaItems.tormentedLegs, VetheaItems.tormentedBody, VetheaItems.tormentedHelmet))
        {
            e.ammount += 9.0F;
            note(p, "Tormented set: +9");
        }
    }
}
