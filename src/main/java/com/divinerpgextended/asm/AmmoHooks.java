package com.divinerpgextended.asm;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;

import com.divinerpgextended.perk.Ammo;

/** Вызовы из луков и стрелков, исправленных {@link AmmoTransformer}: патрон - сначала из заряда оружия в руке. */
public final class AmmoHooks {

    private AmmoHooks() {}

    /** Вместо inventory.hasItem(патрон). */
    public static boolean has(InventoryPlayer inv, Item ammo) {
        return Ammo.has(inv.getCurrentItem(), ammo) || inv.hasItem(ammo);
    }

    /** Вместо inventory.consumeInventoryItem(патрон). */
    public static boolean consume(InventoryPlayer inv, Item ammo) {
        return Ammo.take(inv.getCurrentItem(), ammo) || inv.consumeInventoryItem(ammo);
    }
}
