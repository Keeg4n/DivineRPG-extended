package com.divinerpgextended.asm;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.world.IBlockAccess;

/** Вызовы из BlockLiquid ({@link WaterTransformer}) на обеих сторонах. */
public final class FluidHooks {

    private FluidHooks() {}

    /**
     * Жидкость другого мода (Forge) с тем же материалом - для ванильной воды не своя: иначе источник воды мечтаний держал
     * ванильные потоки, и их нельзя было убрать.
     */
    public static boolean foreign(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return !(b instanceof BlockLiquid) && b instanceof net.minecraftforge.fluids.IFluidBlock;
    }
}
