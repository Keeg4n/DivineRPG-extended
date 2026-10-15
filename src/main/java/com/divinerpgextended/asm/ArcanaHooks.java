package com.divinerpgextended.asm;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Максимум арканы Divine (в Divine зашито 200). Вызывается из кода Divine после {@link ArcanaTransformer}: на сервере -
 * по игроку (200 + 25 за каждое задание Лорда Ваттикуса), на клиенте - значение, присланное сервером.
 */
public final class ArcanaHooks {

    public static final float BASE = 200.0F, PER_TASK = 25.0F;
    public static final String TASKS = "dre_vatticus_tasks";

    /** Клиентская копия максимума (приходит с сервера). */
    public static float clientMax = BASE;

    private ArcanaHooks() {}

    public static int tasks(EntityPlayer p) {
        if (p == null) return 0;
        NBTTagCompound d = p.getEntityData();
        return d.hasKey(EntityPlayer.PERSISTED_NBT_TAG) ? d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getInteger(TASKS) : 0;
    }

    public static float max(EntityPlayer p) {
        return BASE + PER_TASK * tasks(p);
    }

    public static float clientMax() {
        return clientMax;
    }

    /** Полоска Divine рисуется шириной 12.5 * (аркана / 25): подменяем 25 на max / 8, чтобы полная = 100 пикселей. */
    public static float clientScale() {
        return clientMax / 8.0F;
    }
}
