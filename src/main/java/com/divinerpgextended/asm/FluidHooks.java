package com.divinerpgextended.asm;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.world.IBlockAccess;

/** Calls from BlockLiquid ({@link WaterTransformer}) on both sides. */
public final class FluidHooks {

    private FluidHooks() {}

    /**
     * A liquid of another mod (Forge) with the same material - not its own for vanilla water: otherwise a Dream Water source held
     * vanilla flows, and they could not be removed.
     */
    public static boolean foreign(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return !(b instanceof BlockLiquid) && b instanceof net.minecraftforge.fluids.IFluidBlock;
    }
}
