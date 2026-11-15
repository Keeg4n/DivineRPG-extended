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
 * Cold mobs ({@link #isCold}: Glacons, Frosts, Iceika mobs, the Colossus, the Lich King and their undead) fear the infernal: infernal
 * weapons (Inferno, Molten, Scorching, Bluefire swords and the infernal bow) hit them twice as hard and set them on fire, fire burns them twice as
 * hard, and they deal half damage to a player in fire armor. The Scorching sword sets any target on fire.
 */
public class ColdMobs {

    /** Cold: Glacons, Frosts, hostile Iceika mobs, bosses and helpers of the Frozen Throne. */
    public static boolean isCold(Entity e) {
        if (e instanceof EntityGlacon || e instanceof EntityFrost || e instanceof com.divinerpgextended.entity.EntitySnowGlacon
            || e instanceof com.divinerpgextended.entity.EntityFrostColossus || e instanceof com.divinerpgextended.entity.EntityLichKing) return true;
        if (e == null) return false;
        if (e instanceof com.divinerpgextended.entity.mini.EntityBlizzardSpruce || e instanceof com.divinerpgextended.entity.mini.EntityBlizzardSnowman) return true;
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
        // Divine's Scorching sword is a plain sword with no properties: we set the fire ourselves
        if (attacker instanceof EntityPlayer && event.source.getSourceOfDamage() == attacker && !event.entityLiving.worldObj.isRemote) {
            ItemStack held = ((EntityPlayer) attacker).getHeldItem();
            if (held != null && held.getItem() == VanillaItemsWeapons.scorchingSword && !Protection.spare(attacker, event.entityLiving))
                event.entityLiving.setFire(4);
        }
        if (isCold(event.entityLiving)) {
            if (event.source.isFireDamage()) {
                event.ammount *= 2.0F;
                com.divinerpgextended.debug.Debug.note(event.entityLiving, "Cold mob: fire x2");
            }
            if (attacker instanceof EntityPlayer && isHellWeapon((EntityPlayer) attacker)) {
                event.ammount *= 2.0F;
                event.entityLiving.setFire(5);
                com.divinerpgextended.debug.Debug.note(attacker, "Nether weapon on a cold mob: x2 and ignite");
            }
        } else if (event.entityLiving instanceof EntityPlayer && isCold(attacker)
            && PerkEffects.wearsFireArmor((EntityPlayer) event.entityLiving)) {
                event.ammount *= 0.5F;
                com.divinerpgextended.debug.Debug.note(event.entityLiving, "Fire armor: from cold mobs x0.5");
            }
    }
}
