package com.divinerpgextended.world.twilight;

import net.minecraft.block.Block;

/**
 * Wood of the Divine worlds: divine (overworld), frozen (Iceika), eucalyptus (Arcana) and five twilight ones. Number =
 * meta of the world's stick. World sticks go into the handles of weapons and tools of their world, into bows, arrows and torches.
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

    /** The twilight dimension of this wood or null. */
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
