package com.divinerpgextended.perk;

import net.divinerpg.entities.vanilla.EntityFrost;
import net.divinerpg.entities.vanilla.EntityGlacon;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Морозные мобы ({@link #isCold}: Глаконы, Фросты, мобы Iceika, Колосс, Король-лич и их мертвецы) боятся адского: адское
 * оружие (мечи Inferno, Molten, Scorching, Bluefire и адский лук) бьёт их вдвое сильнее и поджигает, огонь жжёт вдвое
 * сильнее, а игроку в огненной броне они наносят вдвое меньше урона. Палящий меч поджигает любую цель.
 */
public class ColdMobs {

    /** Морозные: Глаконы, Фросты, враждебные мобы Iceika, боссы и помощники Ледяного Трона. */
    public static boolean isCold(Entity e) {
        if (e instanceof EntityGlacon || e instanceof EntityFrost || e instanceof com.divinerpgextended.entity.EntitySnowGlacon
            || e instanceof com.divinerpgextended.entity.EntityFrostColossus || e instanceof com.divinerpgextended.entity.EntityLichKing) return true;
        if (e == null) return false;
        if (e.getEntityData().getBoolean(com.divinerpgextended.entity.ThroneCombat.MINION_TAG)) return true;
        return e instanceof net.minecraft.entity.monster.IMob && e.getClass().getName().startsWith("net.divinerpg.entities.iceika.");
    }

    private static boolean isHellWeapon(EntityPlayer p) {
        ItemStack held = p.getHeldItem();
        return held != null && isHellWeapon(held.getItem());
    }

    public static boolean isHellWeapon(Item i) {
        return i == VanillaItemsWeapons.infernoSword || i == VanillaItemsWeapons.moltenSword
            || i == VanillaItemsWeapons.scorchingSword || i == VanillaItemsWeapons.bluefireSword || i == VanillaItemsWeapons.infernoBow;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        // Палящий меч Divine - обычный меч без свойств: поджигаем сами
        if (attacker instanceof EntityPlayer && event.source.getSourceOfDamage() == attacker && !event.entityLiving.worldObj.isRemote) {
            ItemStack held = ((EntityPlayer) attacker).getHeldItem();
            if (held != null && held.getItem() == VanillaItemsWeapons.scorchingSword && !Protection.spare(attacker, event.entityLiving))
                event.entityLiving.setFire(4);
        }
        if (isCold(event.entityLiving)) {
            if (event.source.isFireDamage()) {
                event.ammount *= 2.0F;
                com.divinerpgextended.debug.Debug.note(event.entityLiving, "Холодный моб: огонь x2");
            }
            if (attacker instanceof EntityPlayer && isHellWeapon((EntityPlayer) attacker)) {
                event.ammount *= 2.0F;
                event.entityLiving.setFire(5);
                com.divinerpgextended.debug.Debug.note(attacker, "Адское оружие по холодному мобу: x2 и поджог");
            }
        } else if (event.entityLiving instanceof EntityPlayer && isCold(attacker)
            && PerkEffects.wearsFireArmor((EntityPlayer) event.entityLiving)) {
                event.ammount *= 0.5F;
                com.divinerpgextended.debug.Debug.note(event.entityLiving, "Огненная броня: от холодных мобов x0.5");
            }
    }
}
