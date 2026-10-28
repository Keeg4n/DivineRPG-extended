package com.divinerpgextended.asm;

import java.util.UUID;

import net.minecraft.entity.Entity;

/** Вызовы из классов Айрако ({@link AyeracoTransformer}). */
public final class AyeracoHooks {

    /** Пустой ID: при загрузке Divine такого брата не найдёт и оставит ссылку пустой, как и без него. */
    public static final UUID NONE = new UUID(0L, 0L);

    private AyeracoHooks() {}

    public static UUID id(Entity brother) {
        return brother == null ? NONE : brother.getPersistentID();
    }

    /** Полёт Айрако: true - это орда гнездовья, полёт уже задан аддоном (коршуны), Divine свой не считает. */
    public static boolean steer(net.minecraft.entity.EntityLiving e) {
        return com.divinerpgextended.boss.AyeracoHorde.steer(e);
    }

    /** Телепорт Айрако вверх: у орды гнездовья - в пределах острова, а не в бездну. */
    public static boolean blink(net.minecraft.entity.EntityLiving e) {
        return com.divinerpgextended.boss.AyeracoHorde.blink(e);
    }
}
