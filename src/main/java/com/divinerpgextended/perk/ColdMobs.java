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
 * Ледяные помощники Снежного Глакона (Глаконы и Фросты) боятся адского, как и сам босс: адское оружие (мечи
 * Inferno, Molten, Scorching и адский лук) бьёт их вдвое сильнее и поджигает, огонь жжёт вдвое сильнее, а игроку
 * в огненной броне они наносят вдвое меньше урона.
 */
public class ColdMobs {

    private static boolean isCold(Entity e) {
        return e instanceof EntityGlacon || e instanceof EntityFrost;
    }

    private static boolean isHellWeapon(EntityPlayer p) {
        ItemStack held = p.getHeldItem();
        if (held == null) return false;
        Item i = held.getItem();
        return i == VanillaItemsWeapons.infernoSword || i == VanillaItemsWeapons.moltenSword
            || i == VanillaItemsWeapons.scorchingSword || i == VanillaItemsWeapons.infernoBow;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
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
