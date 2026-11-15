package com.divinerpgextended.asm;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;

import com.divinerpgextended.perk.Ammo;

/**
 * Calls from bows and shooters patched by {@link AmmoTransformer}: ammo comes first from the loaded ammo of the weapon in hand. A blitz takes
 * {@link Ammo#perShot} dust per shot (one dust per pellet): with less it does not shoot at all.
 */
public final class AmmoHooks {

    private AmmoHooks() {}

    /** Instead of inventory.hasItem(ammo). Infinity on the weapon in hand - the ammo is always there. */
    public static boolean has(InventoryPlayer inv, Item ammo) {
        if (Ammo.infinity(inv.getCurrentItem())) return true;
        int need = Ammo.perShot(inv.getCurrentItem(), ammo);
        if (need > 1) return Ammo.stock(inv, inv.getCurrentItem(), ammo) >= need;
        return Ammo.has(inv.getCurrentItem(), ammo) || inv.hasItem(ammo);
    }

    /** Instead of inventory.consumeInventoryItem(ammo). Infinity - nothing is spent. */
    public static boolean consume(InventoryPlayer inv, Item ammo) {
        if (Ammo.infinity(inv.getCurrentItem())) return true;
        int need = Ammo.perShot(inv.getCurrentItem(), ammo);
        if (need <= 1) return Ammo.take(inv.getCurrentItem(), ammo) || inv.consumeInventoryItem(ammo);
        if (Ammo.stock(inv, inv.getCurrentItem(), ammo) < need) return false; // not enough for a shot - touch nothing
        for (int n = 0; n < need; n++) if (!Ammo.take(inv.getCurrentItem(), ammo)) inv.consumeInventoryItem(ammo);
        return true;
    }
}
