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
 * В Divine лечение вшито "пока здоровья меньше 20": Vemos (+0.1 за тик), Wildwood в воде (+0.25 за тик), кормёжка
 * кролика-питомца. Сверх 10 сердец (статуи Генерала, обученные Зелусом питомцы) оно не работает. Здесь то же
 * лечение продолжается от 20 до настоящего максимума; ниже 20 лечит сам Divine, двойного лечения нет.
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
            if (p.ticksExisted % 20 == 0) Debug.note(p, "Сет Vemos: лечение выше 20 (до " + max + ")");
        } else if (p.isInsideOfMaterial(Material.water) && wears(p, TwilightItemsArmor.wildwoodBoots, TwilightItemsArmor.wildwoodLeggings,
            TwilightItemsArmor.wildwoodChestplate, TwilightItemsArmor.wildwoodHelmet)) {
            p.heal(0.25F);
            if (p.ticksExisted % 20 == 0) Debug.note(p, "Сет Wildwood в воде: лечение выше 20 (до " + max + ")");
        }
    }

    /** Кролик-питомец ест, пока здоровья меньше 20 - обученный Зелусом с большим максимумом дальше не лечился. */
    @SubscribeEvent
    public void onFeed(EntityInteractEvent e) {
        if (!(e.target instanceof EntityBunny)) return;
        EntityBunny b = (EntityBunny) e.target;
        ItemStack held = e.entityPlayer.getHeldItem();
        if (!b.isTamed() || held == null || !(held.getItem() instanceof ItemFood) || !((ItemFood) held.getItem()).isWolfsFavoriteMeat()) return;
        if (b.getHealth() < 20.0F || b.getHealth() >= b.getMaxHealth()) return; // ниже 20 кормит сам Divine
        e.setCanceled(true);
        if (b.worldObj.isRemote) return;
        if (!e.entityPlayer.capabilities.isCreativeMode && --held.stackSize <= 0) {
            e.entityPlayer.inventory.setInventorySlotContents(e.entityPlayer.inventory.currentItem, null);
        }
        b.heal(((ItemFood) held.getItem()).func_150905_g(held));
        Debug.note(e.entityPlayer, "Кролик накормлен выше 20: " + b.getHealth() + "/" + b.getMaxHealth());
    }
}
