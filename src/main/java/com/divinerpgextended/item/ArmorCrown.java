package com.divinerpgextended.item;

import net.minecraft.item.Item;

import com.divinerpgextended.perk.Perk;

/**
 * Венец брони Странника R8 (за Леди Луну): одно свойство на выбор. Выбирается донорским сетом, который раньше давал это
 * свойство перком: Ангельский - полёт, Вемос - восстановление, Корма - поток арканы. С 0.99.114 эти три перка больше не ставятся
 * в малые слоты, только Венцом. У каждого Венца свой облик R8 ({@link ItemWandererArmor#look}).
 */
public enum ArmorCrown {

    /** Полёт как в творческом, без ограничений (кроме самой Ветеи). Облик - Призрак. */
    FLIGHT(Perk.ANGEL_FLIGHT),
    /** Восстановление здоровья. Облик - Лунная. */
    REGEN(Perk.REGENERATION),
    /** Поток арканы: аркана восстанавливается быстрее, арканный урон x2. Облик - Сновидец. */
    ARCANA(Perk.ARCANA_FLOW);

    /** Перк, чьё действие даёт Венец. */
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

    /** Венец по донорскому сету [шлем, нагрудник, поножи, ботинки] или null. */
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
