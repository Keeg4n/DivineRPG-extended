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
 * Свойства полных сетов Divine - те же числа, что в EventArmorFullSet, но броня читается у нужного игрока прямо в
 * обработчике и только на сервере (у Divine общие поля, которые перетираются; см. ArmorSetTransformer). Работает,
 * только если правка класса Divine удалась, иначе свойства считал бы и Divine - вдвойне.
 * <p>
 * В Ветее сеты не из Ветеи ничего не дают (кошмар наоборот).
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

    /** Броня игрока: [ботинки, поножи, нагрудник, шлем]. */
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

    /** Сет Ветеи без шлема: ботинки, поножи, нагрудник совпадают. */
    private static boolean vetheaBody(Item[] a, Item boots, Item legs, Item body) {
        return a[0] == boots && a[1] == legs && a[2] == body;
    }

    /** Множитель защиты ветейского сета: шлем - от ближнего боя, маска - от стрел, капюшон - от магии. */
    private static boolean vetheaGuard(Item helmetWorn, Item helmet, Item mask, Item hood, DamageSource s) {
        boolean proj = s.isProjectile(), magic = s.isMagicDamage();
        return helmetWorn == helmet && !proj && !magic || helmetWorn == mask && proj && !magic || helmetWorn == hood && magic;
    }

    // ---------------- Защита: игрок атакован ----------------

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !active()) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if (Nightmare.active(p)) return; // ветейских среди этих сетов нет
        Item[] a = armor(p);
        DamageSource s = e.source;
        if ((set(a, VanillaItemsArmor.aquastriveBoots, VanillaItemsArmor.aquastriveLegs, VanillaItemsArmor.aquastriveBody,
            VanillaItemsArmor.aquastriveHelmet)
            || set(a, VanillaItemsArmor.krakenBoots, VanillaItemsArmor.krakenLegs, VanillaItemsArmor.krakenBody, VanillaItemsArmor.krakenHelmet))
            && s == DamageSource.drown) {
            e.setCanceled(true);
            note(p, "Сет Kraken/Aquastrive: не тонешь");
        }
        else if (set(a, TwilightItemsArmor.apalachiaBoots, TwilightItemsArmor.apalachiaLeggings, TwilightItemsArmor.apalachiaChestplate,
            TwilightItemsArmor.apalachiaHelmet)
            && (s == DamageSource.cactus || s == DamageSource.fallingBlock || s == DamageSource.anvil || s == DamageSource.inWall
                || s == Util.trapSource)) {
            e.setCanceled(true);
            note(p, "Сет Apalachia: кактус, ловушки и блоки не ранят");
        }
        else if (set(a, VanillaItemsArmor.witherReaperBoots, VanillaItemsArmor.witherReaperLegs, VanillaItemsArmor.witherReaperBody,
            VanillaItemsArmor.witherReaperHelmet) && s == DamageSource.wither) {
            e.setCanceled(true);
            note(p, "Сет Wither Reaper: иссушение не ранит");
        }
        else if (set(a, VanillaItemsArmor.jungleBoots, VanillaItemsArmor.jungleLegs, VanillaItemsArmor.jungleBody,
            VanillaItemsArmor.jungleHelmet) && s == DamageSource.magic) {
            e.setCanceled(true);
            note(p, "Сет Jungle: яд не ранит");
        }
    }

    // ---------------- Урон: защита жертвы и бонус атакующего ----------------

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
            if (set(a, VanillaItemsArmor.bedrockBoots, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBody,
                VanillaItemsArmor.bedrockHelmet) && s.isExplosion()) {
                e.setCanceled(true);
                note(p, "Сет Bedrock: взрыв не ранит");
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
                && s.isExplosion()) {
                e.setCanceled(true);
                note(p, "Сет Ender: взрыв не ранит");
                return;
            }
            if (set(a, VanillaItemsArmor.arlemiteBoots, VanillaItemsArmor.arlemiteLegs, VanillaItemsArmor.arlemiteBody,
                VanillaItemsArmor.arlemiteHelmet) && (s.isProjectile() || "thrown".equals(s.damageType))) {
            e.ammount *= 0.3F;
            note(p, "Сет Arlemite: стрелы x0.3");
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
            note(p, "Сет Rupee: удары мобов x0.3");
        }
            if (set(a, IceikaItems.santaBoots, IceikaItems.santaPants, IceikaItems.santaTunic, IceikaItems.santaCap)
                && p.worldObj.provider.dimensionId == ConfigurationHelper.iceika) {
            e.ammount *= 0.2F;
            note(p, "Сет Santa в Айсике: x0.2");
        }
        }
        // ветейские сеты: шлем, маска или капюшон решают, от чего защищает
        if (vetheaBody(a, VetheaItems.degradedBoots, VetheaItems.degradedLegs, VetheaItems.degradedBody)
            && vetheaGuard(a[3], VetheaItems.degradedHelmet, VetheaItems.degradedMask, VetheaItems.degradedHood, s)) {
            e.ammount *= 0.82F;
            note(p, "Сет Degraded: x0.82");
        }
        if (vetheaBody(a, VetheaItems.finishedBoots, VetheaItems.finishedLegs, VetheaItems.finishedBody)
            && vetheaGuard(a[3], VetheaItems.finishedHelmet, VetheaItems.finishedMask, VetheaItems.finishedHood, s)) {
            e.ammount *= 0.773F;
            note(p, "Сет Finished: x0.773");
        }
        if (vetheaBody(a, VetheaItems.glisteningBoots, VetheaItems.glisteningLegs, VetheaItems.glisteningBody)
            && vetheaGuard(a[3], VetheaItems.glisteningHelmet, VetheaItems.glisteningMask, VetheaItems.glisteningHood, s)) {
            e.ammount *= 0.7F;
            note(p, "Сет Glistening: x0.7");
        }
        if (vetheaBody(a, VetheaItems.demonizedBoots, VetheaItems.demonizedLegs, VetheaItems.demonizedBody)
            && vetheaGuard(a[3], VetheaItems.demonizedHelmet, VetheaItems.demonizedMask, VetheaItems.demonizedHood, s)) {
            e.ammount *= 0.625F;
            note(p, "Сет Demonized: x0.625");
        }
        if (vetheaBody(a, VetheaItems.tormentedBoots, VetheaItems.tormentedLegs, VetheaItems.tormentedBody)
            && vetheaGuard(a[3], VetheaItems.tormentedHelmet, VetheaItems.tormentedMask, VetheaItems.tormentedHood, s)) {
            e.ammount *= 0.348F;
            note(p, "Сет Tormented: x0.348");
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
            note(p, "Сет Santa в Айсике: +6");
        }
            if (melee && set(a, TwilightItemsArmor.haliteBoots, TwilightItemsArmor.haliteLeggings, TwilightItemsArmor.haliteChestplate,
                TwilightItemsArmor.haliteHelmet)) {
            e.ammount += 16.0F;
            note(p, "Сет Halite: +16");
        }
            if (melee && set(a, VanillaItemsArmor.divineBoots, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBody,
                VanillaItemsArmor.divineHelmet)) {
            e.ammount += 6.0F;
            note(p, "Сет Divine: +6");
        }
            if (s.isProjectile() && set(a, VanillaItemsArmor.corruptedBoots, VanillaItemsArmor.corruptedLegs,
                VanillaItemsArmor.corruptedBody, VanillaItemsArmor.corruptedHelmet)) {
            e.ammount *= 1.2F;
            note(p, "Сет Corrupted: стрельба x1.2");
        }
        }
        if (melee && set(a, VetheaItems.glisteningBoots, VetheaItems.glisteningLegs, VetheaItems.glisteningBody, VetheaItems.glisteningHelmet))
        {
            e.ammount += 3.0F;
            note(p, "Сет Glistening: +3");
        }
        if (melee && set(a, VetheaItems.demonizedBoots, VetheaItems.demonizedLegs, VetheaItems.demonizedBody, VetheaItems.demonizedHelmet)) {
            e.ammount += 6.0F;
            note(p, "Сет Demonized: +6");
        }
        if (melee && set(a, VetheaItems.tormentedBoots, VetheaItems.tormentedLegs, VetheaItems.tormentedBody, VetheaItems.tormentedHelmet))
        {
            e.ammount += 9.0F;
            note(p, "Сет Tormented: +9");
        }
    }
}
