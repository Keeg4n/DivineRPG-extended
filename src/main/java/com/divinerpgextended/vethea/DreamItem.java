package com.divinerpgextended.vethea;

import net.minecraft.item.ItemStack;

import com.divinerpgextended.registry.ModItems;

/** Vethea items from the addon (dreamItem item metadata): wing templates and the Lunar Shard. New ones - only at the end. */
public enum DreamItem {

    /** Dream Wings Template from the Hunger. */
    WINGS_TEMPLATE,
    /** Wings II template from the Hunger (cermile boulders for it - from the Hive Queen). */
    QUEEN_TEMPLATE,
    /** Lunar Shard - from Lady Luna: makes her weapon from an Arksiane weapon. */
    LUNAR_SHARD;

    public static String[] keys() {
        String[] k = new String[values().length];
        for (int i = 0; i < k.length; i++) k[i] = values()[i].name().toLowerCase();
        return k;
    }

    public static ItemStack stack(DreamItem d) {
        return stack(d, 1);
    }

    public static ItemStack stack(DreamItem d, int n) {
        return new ItemStack(ModItems.dreamItem, n, d.ordinal());
    }

    public boolean is(ItemStack s) {
        return s != null && s.getItem() == ModItems.dreamItem && s.getItemDamage() == ordinal();
    }
}
