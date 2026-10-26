package com.divinerpgextended.vethea;

import net.minecraft.item.ItemStack;

import com.divinerpgextended.registry.ModItems;

/** Вещи Ветеи от аддона (метаданные предмета dreamItem): шаблоны крыльев и лунный осколок. Новые - только в конец. */
public enum DreamItem {

    /** Шаблон Крыльев сна у Голода. */
    WINGS_TEMPLATE,
    /** Шаблон Крыльев II у Голода (глыбы цермила к нему - с Королевы улья). */
    QUEEN_TEMPLATE,
    /** Лунный осколок - с Леди Луны: из оружия арксиана делает её оружие. */
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
