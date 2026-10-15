package com.divinerpgextended.world;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.minecraft.item.Item;

/** Сумеречные миры, где живёт великий кадиллион. Номер = вариант босса = метаданные шестерни и зова. */
public enum CadillionWorld {

    EDEN,
    WILDWOOD,
    APALACHIA,
    SKYTHERN;

    public String key() {
        return name().toLowerCase();
    }

    public int dimension() {
        switch (this) {
            case EDEN:
                return ConfigurationHelper.eden;
            case WILDWOOD:
                return ConfigurationHelper.wildwood;
            case APALACHIA:
                return ConfigurationHelper.apalachia;
            default:
                return ConfigurationHelper.skythern;
        }
    }

    public Item soul() {
        switch (this) {
            case EDEN:
                return TwilightItemsOther.edenSoul;
            case WILDWOOD:
                return TwilightItemsOther.wildwoodSoul;
            case APALACHIA:
                return TwilightItemsOther.apalachiaSoul;
            default:
                return TwilightItemsOther.skythernSoul;
        }
    }

    public Item gem() {
        switch (this) {
            case EDEN:
                return TwilightItemsOther.edenGem;
            case WILDWOOD:
                return TwilightItemsOther.wildwoodGem;
            case APALACHIA:
                return TwilightItemsOther.apalachiaGem;
            default:
                return TwilightItemsOther.skythernGem;
        }
    }

    public static CadillionWorld byMeta(int meta) {
        return values()[Math.max(0, Math.min(meta, values().length - 1))];
    }
}
