package com.divinerpgextended.asm;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.perk.Ammo;

/** Вызов из RenderItem, исправленного {@link AmmoCountTransformer}: только клиент. */
public final class AmmoCountHooks {

    private AmmoCountHooks() {}

    /** Текст поверх значка: переданный игрой (обычно null) или запас патронов оружия из своего инвентаря. */
    public static String text(String given, Object stack) {
        if (given != null || !(stack instanceof ItemStack)) return given;
        try {
            return Ammo.label(Minecraft.getMinecraft().thePlayer, (ItemStack) stack);
        } catch (Throwable t) {
            return null; // отрисовка значка важнее цифры
        }
    }
}
