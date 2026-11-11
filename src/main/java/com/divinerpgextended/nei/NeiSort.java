package com.divinerpgextended.nei;

import java.util.Comparator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemList;
import codechicken.nei.ItemSorter;

import com.divinerpgextended.DreTab;

/**
 * Порядок предметов аддона в списке NEI. Сам NEI сортирует внутри мода по номеру предмета, а номера раздаются по порядку
 * добавления в аддон, поэтому новые вещи оказывались вперемешку. Здесь своя ступень сортировки сразу за "по модам":
 * предметы аддона идут по вкладкам творческого режима (основное, снаряжение, аксессуары, рыбалка, аркана, боссы), внутри
 * вкладки - по имени, так что родственные вещи стоят рядом. Номера предметов не меняются, поэтому это работает и в уже
 * созданных мирах. Ступень видна в настройках сортировки NEI, её можно передвинуть.
 */
final class NeiSort {

    static final String NAME = "nei.itemsort.dre";
    private static final String PREFIX = "divinerpgextended:";
    private static final DreTab[] ORDER = { DreTab.TAB, DreTab.GEAR, DreTab.TRINKETS, DreTab.FISHING, DreTab.MAGIC, DreTab.BOSSES };

    private NeiSort() {}

    /** Ключ сортировки: номер вкладки и имя; null - не предмет аддона. */
    static String key(ItemStack s) {
        if (s == null || s.getItem() == null) return null;
        String n = Item.itemRegistry.getNameForObject(s.getItem());
        if (n == null || !n.startsWith(PREFIX)) return null;
        String name = n.substring(PREFIX.length());
        DreTab tab = DreTab.of(name);
        int i = 0;
        while (i < ORDER.length && ORDER[i] != tab) i++;
        if (s.getItem() instanceof net.minecraft.item.ItemArmor) name = armor(s, name);
        return i + ":" + name;
    }

    /**
     * Броня - полными сетами: сначала весь первый сет (шлем, нагрудник, поножи, ботинки), потом следующий. У брони Странника сет -
     * ступень и ядро (R1 с каждым ядром, потом R2-R7), у прочей - имя без названия части.
     */
    static String armor(ItemStack s, String name) {
        int slot = ((net.minecraft.item.ItemArmor) s.getItem()).armorType;
        if (s.getItem() instanceof com.divinerpgextended.item.ItemWandererArmor)
            return String.format("wanderer#%02d#%02d#%d", com.divinerpgextended.item.ArmorData.tier(s), com.divinerpgextended.item.ArmorData.core(s).ordinal(), slot);
        return name.replaceAll("(Helmet|Hat|Chestplate|Chest|Vest|Body|Leggings|Legs|Pants|Boots)$", "") + "#" + slot;
    }

    /** Предметы аддона между собой; с чужими - поровну (их уже развела ступень "по модам"). */
    static final Comparator<ItemStack> CMP = (a, b) -> {
        String ka = key(a), kb = key(b);
        return ka == null || kb == null ? 0 : ka.compareTo(kb);
    };

    static void install() {
        if (ItemSorter.find(NAME) == null) ItemSorter.add(NAME, CMP);
        ItemList.loadCallbacks.add(NeiSort::place);
        place();
    }

    /** Поставить ступень сразу за сортировкой по модам (NEI перечитывает список из своих настроек). */
    static void place() {
        ItemSorter.SortEntry e = ItemSorter.find(NAME);
        if (e == null || ItemSorter.list.contains(e)) return;
        int at = ItemSorter.list.indexOf(ItemSorter.find("nei.itemsort.mod"));
        ItemSorter.list.add(at < 0 ? 0 : at + 1, e);
    }
}
