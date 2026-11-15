package com.divinerpgextended.asm;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.perk.Ammo;

/** Called from RenderItem, patched by {@link AmmoCountTransformer}: client only. */
public final class AmmoCountHooks {

    private AmmoCountHooks() {}

    /** Text over the icon: the one passed by the game (usually null) or the weapon's ammo reserve from its own inventory. */
    public static String text(String given, Object stack) {
        if (given != null || !(stack instanceof ItemStack)) return given;
        try {
            return Ammo.label(Minecraft.getMinecraft().thePlayer, (ItemStack) stack);
        } catch (Throwable t) {
            return null; // drawing the icon is more important than the digit
        }
    }
}
