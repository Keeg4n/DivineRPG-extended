package com.divinerpgextended.asm;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;

/** Вызовы из BlockLiquid ({@link WaterTransformer}); только клиент (иконки бывают только там). */
public final class WaterHooks {

    /** Золотая вода Эдема: стоячая и текущая (регистрирует {@link com.divinerpgextended.client.EdenWater}). */
    public static IIcon still, flow;

    private WaterHooks() {}

    private static boolean eden() {
        Minecraft mc = Minecraft.getMinecraft();
        return still != null && mc != null && mc.theWorld != null && mc.theWorld.provider.dimensionId == net.divinerpg.utils.config.ConfigurationHelper.eden;
    }

    public static IIcon icon(Block b, int side) {
        if (b.getMaterial() != Material.water || !eden()) return null;
        return side == 0 || side == 1 ? still : flow;
    }

    public static IIcon liquidIcon(String name) {
        if (name == null || !name.startsWith("water") || !eden()) return null;
        return name.endsWith("flow") ? flow : still;
    }
}
