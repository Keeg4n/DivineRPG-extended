package com.divinerpgextended.asm;

import java.util.UUID;

import net.minecraft.entity.Entity;

/** Calls from Ayeraco classes ({@link AyeracoTransformer}). */
public final class AyeracoHooks {

    /** Empty ID: on load Divine will not find such a sibling and will leave the reference empty, as it would without it. */
    public static final UUID NONE = new UUID(0L, 0L);

    private AyeracoHooks() {}

    public static UUID id(Entity brother) {
        return brother == null ? NONE : brother.getPersistentID();
    }

    /** Ayeraco flight: true - this is an aerie horde, flight is already set by the addon (kites), Divine does not compute its own. */
    public static boolean steer(net.minecraft.entity.EntityLiving e) {
        return com.divinerpgextended.boss.AyeracoHorde.steer(e);
    }

    /** Ayeraco teleport upward: for the aerie horde - within the island, not into the abyss. */
    public static boolean blink(net.minecraft.entity.EntityLiving e) {
        return com.divinerpgextended.boss.AyeracoHorde.blink(e);
    }
}
