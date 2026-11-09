package com.divinerpgextended.asm;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;

import com.divinerpgextended.perk.Ammo;

/**
 * Вызовы из луков и стрелков, исправленных {@link AmmoTransformer}: патрон - сначала из заряда оружия в руке. Блиц берёт
 * за выстрел {@link Ammo#perShot} пыли (по пыли на дробину): меньше - не стреляет вовсе.
 */
public final class AmmoHooks {

    private AmmoHooks() {}

    /** Вместо inventory.hasItem(патрон). */
    public static boolean has(InventoryPlayer inv, Item ammo) {
        int need = Ammo.perShot(inv.getCurrentItem(), ammo);
        if (need > 1) return Ammo.stock(inv, inv.getCurrentItem(), ammo) >= need;
        return Ammo.has(inv.getCurrentItem(), ammo) || inv.hasItem(ammo);
    }

    /** Вместо inventory.consumeInventoryItem(патрон). */
    public static boolean consume(InventoryPlayer inv, Item ammo) {
        int need = Ammo.perShot(inv.getCurrentItem(), ammo);
        if (need <= 1) return Ammo.take(inv.getCurrentItem(), ammo) || inv.consumeInventoryItem(ammo);
        if (Ammo.stock(inv, inv.getCurrentItem(), ammo) < need) return false; // не хватает на выстрел - ничего не трогаем
        for (int n = 0; n < need; n++) if (!Ammo.take(inv.getCurrentItem(), ammo)) inv.consumeInventoryItem(ammo);
        return true;
    }
}
