package com.divinerpgextended.item;

import net.minecraft.item.Item;

import com.divinerpgextended.perk.Perk;

/**
 * Wanderer armor Crown R8 (for Lady Luna): one property of your choice. It is chosen by the donor set that used to give this
 * property as a perk: Angelic - flight, Vemos - regeneration, Korma - Arcana Flow. Since 0.99.114 these three perks are no longer placed
 * in the small slots, only by the Crown. Each Crown has its own R8 look ({@link ItemWandererArmor#look}).
 */
public enum ArmorCrown {

    /** Flight as in creative, without limits (except Vethea itself). Look - Wraith. */
    FLIGHT(Perk.ANGEL_FLIGHT),
    /** Health regeneration. Look - Lunar. */
    REGEN(Perk.REGENERATION),
    /** Arcana Flow: arcana regenerates faster, arcane damage x2. Look - Dreamer. */
    ARCANA(Perk.ARCANA_FLOW);

    /** The perk whose effect the Crown gives. */
    public final Perk perk;

    ArmorCrown(Perk perk) {
        this.perk = perk;
    }

    public String key() {
        return name().toLowerCase();
    }

    public static ArmorCrown of(Perk p) {
        for (ArmorCrown c : values()) if (c.perk == p) return c;
        return null;
    }

    /** The Crown by donor set [helmet, chestplate, leggings, boots] or null. */
    public static ArmorCrown ofSet(Item[] donor) {
        Perk p = Perk.byDonor(donor);
        return p == null ? null : of(p);
    }

    public static ArmorCrown byName(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
