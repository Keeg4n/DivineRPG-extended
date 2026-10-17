package com.divinerpgextended.world.twilight;

import net.minecraft.block.Block;

/**
 * Дерево миров Divine: божественное (верхний мир), морозное (Iceika), эвкалипт (Аркана) и пять сумеречных. Номер =
 * мета палки мира. Палки мира идут в рукояти оружия и инструментов своего мира, в луки, стрелы и факелы.
 */
public enum WorldWood {

    DIVINE, FROZEN, EUCALYPTUS, EDEN, WILDWOOD, APALACHIA, SKYTHERN, MORTUM;

    public String key() {
        return name().toLowerCase();
    }

    public Block planks() {
        switch (this) {
            case DIVINE: return net.divinerpg.utils.blocks.VanillaBlocks.divinePlanks;
            case FROZEN: return net.divinerpg.utils.blocks.IceikaBlocks.frozenPlanks;
            case EUCALYPTUS: return net.divinerpg.utils.blocks.ArcanaBlocks.eucalyptusPlank;
            default: return twilight().planks();
        }
    }

    /** Сумеречный мир этого дерева или null. */
    public TwilightWorld twilight() {
        return ordinal() >= EDEN.ordinal() ? TwilightWorld.values()[ordinal() - EDEN.ordinal()] : null;
    }

    public static WorldWood of(TwilightWorld w) {
        return values()[EDEN.ordinal() + w.ordinal()];
    }

    public static WorldWood byMeta(int meta) {
        return values()[Math.max(0, Math.min(meta, values().length - 1))];
    }
}
