package com.divinerpgextended.asm;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/** Calls from {@link EasterArmorTransformer} (client only): the H.E.V. suit on armor stands and mobs. */
public final class EasterHooks {

    private EasterHooks() {}

    /** Armor model for an item: the suit - its own model with the needed part, anything else - null (as before). */
    public static ModelBiped armorModel(EntityLivingBase e, ItemStack s, int slot) {
        return com.divinerpgextended.client.EasterClient.armorModel(s);
    }

    /** Armor texture: the suit - the atlas of its model, anything else - null (as before). */
    public static String armorTexture(Entity e, ItemStack s, String def, int slot, String type) {
        return com.divinerpgextended.client.EasterClient.armorTexture(s);
    }
}
