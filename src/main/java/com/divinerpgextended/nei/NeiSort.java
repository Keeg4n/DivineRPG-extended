package com.divinerpgextended.nei;

import java.util.Comparator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemList;
import codechicken.nei.ItemSorter;

import com.divinerpgextended.DreTab;

/**
 * Order of addon items in the NEI list. NEI itself sorts inside a mod by item id, and ids are handed out in the order
 * items were added to the addon, so new items ended up mixed together. Here is our own sort tier right after "by mod":
 * addon items go by creative tabs (main, gear, accessories, fishing, arcana, bosses), within a
 * tab - by name, so related items stand next to each other. Item ids do not change, so this works in already
 * created worlds too. The tier is visible in the NEI sort settings.
 * <p>
 * NEI (both classic and GTNH) puts a new tier at the very end - after sorting by id and damage, where it no longer
 * decides anything. So on every list load it is moved to right after "by mod".
 */
final class NeiSort {

    static final String NAME = "nei.itemsort.dre";
    private static final String PREFIX = "divinerpgextended:";
    private static final DreTab[] ORDER = { DreTab.TAB, DreTab.GEAR, DreTab.TRINKETS, DreTab.FISHING, DreTab.MAGIC, DreTab.BOSSES };

    private NeiSort() {}

    /** Sort key: tab number and name; null - not an addon item. */
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
     * Armor goes in full sets: first the whole first set (helmet, chestplate, leggings, boots), then the next. For Wanderer armor a set is
     * a tier, core and Crown (R1 with each core, then R2-R7, then R8 with each Crown), for the rest - the name without the part name.
     */
    static String armor(ItemStack s, String name) {
        int slot = ((net.minecraft.item.ItemArmor) s.getItem()).armorType;
        if (s.getItem() instanceof com.divinerpgextended.item.ItemWandererArmor) {
            com.divinerpgextended.item.ArmorCrown crown = com.divinerpgextended.item.ArmorData.crown(s);
            return String.format("wanderer#%02d#%02d#%d#%d", com.divinerpgextended.item.ArmorData.tier(s), com.divinerpgextended.item.ArmorData.core(s).ordinal(),
                crown == null ? 0 : crown.ordinal() + 1, slot);
        }
        return name.replaceAll("(Helmet|Hat|Chestplate|Chest|Vest|Body|Leggings|Legs|Pants|Boots)$", "") + "#" + slot;
    }

    /** Addon items among themselves; with foreign ones - equal (the "by mod" tier has already separated them). */
    static final Comparator<ItemStack> CMP = (a, b) -> {
        String ka = key(a), kb = key(b);
        return ka == null || kb == null ? 0 : ka.compareTo(kb);
    };

    static void install() {
        if (ItemSorter.find(NAME) == null) ItemSorter.add(NAME, CMP);
        ItemList.loadCallbacks.add(NeiSort::place);
        place();
    }

    /** Put the tier right after sorting by mod (NEI rereads the list from its settings). */
    static void place() {
        ItemSorter.SortEntry e = ItemSorter.find(NAME);
        if (e == null) return;
        java.util.ArrayList<ItemSorter.SortEntry> l = new java.util.ArrayList<ItemSorter.SortEntry>(ItemSorter.list);
        l.remove(e);
        int at = l.indexOf(ItemSorter.find("nei.itemsort.mod"));
        l.add(at < 0 ? 0 : at + 1, e);
        ItemSorter.list = l;
    }

    /** Position of the tier in the NEI sort list (for the selftest): its index and the index of the "by mod" tier. */
    static int[] position() {
        return new int[] { ItemSorter.list.indexOf(ItemSorter.find(NAME)), ItemSorter.list.indexOf(ItemSorter.find("nei.itemsort.mod")) };
    }
}
