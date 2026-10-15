package com.divinerpgextended.arcana;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.TwilightItemsWeapons;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/** Откуда меч Divine: мир определяет цену перековки у Мерика и свойство мира. Ванильные мечи сюда не попадают. */
public enum WeaponOrigin {

    OVERWORLD(3), NETHER(4), ICEIKA(5), ARCANA(6), TWILIGHT(8), VETHEA(10);

    /** Цена перековки в арканиуме. */
    public final int cost;

    WeaponOrigin(int cost) {
        this.cost = cost;
    }

    public String key() {
        return name().toLowerCase();
    }

    private static Map<Item, WeaponOrigin> origins;
    /** Для сумеречного оружия - номер его мира (-1 у Halite: годится в любом сумеречном). */
    private static Map<Item, Integer> dims;

    private static synchronized void init() {
        if (origins != null) return;
        origins = new HashMap<Item, WeaponOrigin>();
        dims = new HashMap<Item, Integer>();
        scan(VanillaItemsWeapons.class, null);
        scan(IceikaItems.class, ICEIKA);
        scan(ArcanaItems.class, ARCANA);
        scan(TwilightItemsWeapons.class, TWILIGHT);
        scan(VetheaItems.class, VETHEA);
    }

    private static void scan(Class<?> holder, WeaponOrigin fixed) {
        for (Field f : holder.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Item.class.isAssignableFrom(f.getType())) continue;
            Item item;
            try {
                item = (Item) f.get(null);
            } catch (Exception e) {
                continue;
            }
            if (!(item instanceof ItemSword)) continue;
            String n = f.getName().toLowerCase();
            WeaponOrigin o = fixed;
            if (o == null) {
                o = n.matches(".*(inferno|molten|scorching|bloodgem|netherite|flaming|bluefire|hell|fury).*") ? NETHER : OVERWORLD;
            }
            origins.put(item, o);
            if (o == TWILIGHT) {
                int d = n.startsWith("eden") ? ConfigurationHelper.eden : n.startsWith("wildwood") ? ConfigurationHelper.wildwood
                    : n.startsWith("apalachia") ? ConfigurationHelper.apalachia : n.startsWith("skythern") ? ConfigurationHelper.skythern
                        : n.startsWith("mortum") ? ConfigurationHelper.mortum : -1;
                dims.put(item, d);
            }
        }
    }

    public static WeaponOrigin of(ItemStack s) {
        if (s == null) return null;
        init();
        return origins.get(s.getItem());
    }

    /** Сумеречный мир оружия или -1 (любой сумеречный). */
    public static int twilightDim(ItemStack s) {
        init();
        Integer d = dims.get(s.getItem());
        return d == null ? -1 : d;
    }

    public static boolean isTwilight(int dim) {
        return dim == ConfigurationHelper.eden || dim == ConfigurationHelper.wildwood || dim == ConfigurationHelper.apalachia
            || dim == ConfigurationHelper.skythern || dim == ConfigurationHelper.mortum;
    }
}
