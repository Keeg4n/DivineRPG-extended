package com.divinerpgextended.world;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * The white stone of the addon's structures (1.1.5): Divine milk stone instead of quartz. Milk stone is generated nowhere else, neither by
 * Divine nor by the addon - it is only crafted (4 stone and a milk bucket) and only goes into milk stone bricks. Plain, carved and pillar
 * quartz all became milk stone (meta 0); quartz slabs became smooth stone slabs, as Divine has no white slab.
 */
public final class WhiteStone {

    private WhiteStone() {}

    /** Smooth stone slab metas: bottom and top (instead of quartz 7 and 15). */
    public static final int SLAB = 0, SLAB_TOP = 8;

    public static Block block() {
        return net.divinerpg.utils.blocks.VanillaBlocks.milkStone;
    }

    /** The structure's own white or the quartz of a structure built before 1.1.5 (for checks and rebuilds of old lairs). */
    public static boolean is(Block b) {
        return b == block() || b == Blocks.quartz_block;
    }
}
