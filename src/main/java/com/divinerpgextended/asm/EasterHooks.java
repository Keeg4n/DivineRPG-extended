package com.divinerpgextended.asm;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/** Вызовы из {@link EasterArmorTransformer} (только клиент): костюм H.E.V. на стойках для брони и мобах. */
public final class EasterHooks {

    private EasterHooks() {}

    /** Модель брони для вещи: костюм - своя модель с нужной частью, остальное - null (как было). */
    public static ModelBiped armorModel(EntityLivingBase e, ItemStack s, int slot) {
        return com.divinerpgextended.client.EasterClient.armorModel(s);
    }

    /** Текстура брони: костюм - атлас его модели, остальное - null (как было). */
    public static String armorTexture(Entity e, ItemStack s, String def, int slot, String type) {
        return com.divinerpgextended.client.EasterClient.armorTexture(s);
    }
}
