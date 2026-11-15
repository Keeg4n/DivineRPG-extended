package com.divinerpgextended.perk;

import net.divinerpg.entities.twilight.EntityBunny;
import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.TwilightItemsArmor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.divinerpgextended.debug.Debug;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * In Divine healing is hardcoded as "while health is below 20": Vemos (+0.1 per tick), Wildwood in water (+0.25 per tick), feeding a
 * pet bunny. Above 10 hearts (General statues, pets trained by Zelus) it does not work. Here the same
 * healing continues from 20 up to the real maximum; below 20 Divine heals itself, so there is no double healing.
 */
public class OverHealth {

    private static boolean wears(EntityPlayer p, Object boots, Object legs, Object body, Object helmet) {
        ItemStack b = p.inventory.armorItemInSlot(0), l = p.inventory.armorItemInSlot(1), c = p.inventory.armorItemInSlot(2),
            h = p.inventory.armorItemInSlot(3);
        return b != null && b.getItem() == boots && l != null && l.getItem() == legs && c != null && c.getItem() == body && h != null
            && h.getItem() == helmet;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        float hp = p.getHealth(), max = p.getMaxHealth();
        if (hp < 20.0F || hp >= max || hp <= 0) return;
        if (wears(p, ArcanaItems.vemosBoots, ArcanaItems.vemosLegs, ArcanaItems.vemosBody, ArcanaItems.vemosHelmet)) {
            p.setHealth(Math.min(hp + 0.1F, max));
            if (p.ticksExisted % 20 == 0) Debug.note(p, "Vemos set: healing above 20 (up to " + max + ")");
        } else if (p.isInsideOfMaterial(Material.water) && wears(p, TwilightItemsArmor.wildwoodBoots, TwilightItemsArmor.wildwoodLeggings,
            TwilightItemsArmor.wildwoodChestplate, TwilightItemsArmor.wildwoodHelmet)) {
            p.heal(0.25F);
            if (p.ticksExisted % 20 == 0) Debug.note(p, "Wildwood set in water: healing above 20 (up to " + max + ")");
        }
    }

    /** A pet bunny eats while health is below 20 - one trained by Zelus with a large maximum was not healed further. */
    @SubscribeEvent
    public void onFeed(EntityInteractEvent e) {
        if (!(e.target instanceof EntityBunny)) return;
        EntityBunny b = (EntityBunny) e.target;
        ItemStack held = e.entityPlayer.getHeldItem();
        if (!b.isTamed() || held == null || !(held.getItem() instanceof ItemFood) || !((ItemFood) held.getItem()).isWolfsFavoriteMeat()) return;
        if (b.getHealth() < 20.0F || b.getHealth() >= b.getMaxHealth()) return; // below 20 Divine feeds it itself
        e.setCanceled(true);
        if (b.worldObj.isRemote) return;
        if (!e.entityPlayer.capabilities.isCreativeMode && --held.stackSize <= 0) {
            e.entityPlayer.inventory.setInventorySlotContents(e.entityPlayer.inventory.currentItem, null);
        }
        b.heal(((ItemFood) held.getItem()).func_150905_g(held));
        Debug.note(e.entityPlayer, "Bunny fed above 20: " + b.getHealth() + "/" + b.getMaxHealth());
    }
}
