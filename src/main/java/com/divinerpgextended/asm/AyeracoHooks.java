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
}
