package com.divinerpgextended.asm;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Divine arcana maximum (hardcoded 200 in Divine). Called from Divine code after {@link ArcanaTransformer}: on the server -
 * per player (200 + 25 for each Lord Vatticus quest), on the client - the value sent by the server.
 */
public final class ArcanaHooks {

    public static final float BASE = 200.0F, PER_TASK = 25.0F;
    public static final String TASKS = "dre_vatticus_tasks";

    /** Client copy of the maximum (comes from the server). */
    public static float clientMax = BASE;

    private ArcanaHooks() {}

    public static int tasks(EntityPlayer p) {
        if (p == null) return 0;
        NBTTagCompound d = p.getEntityData();
        return d.hasKey(EntityPlayer.PERSISTED_NBT_TAG) ? d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getInteger(TASKS) : 0;
    }

    public static float max(EntityPlayer p) {
        // full Korma set - +50 to the reserve
        return BASE + PER_TASK * tasks(p) + (com.divinerpgextended.magic.ArcanaRules.korma(p) ? com.divinerpgextended.magic.ArcanaRules.KORMA_MAX : 0);
    }

    public static float clientMax() {
        return clientMax;
    }

    /** The Divine bar is drawn with a width of 12.5 * (arcana / 25): we replace 25 with max / 8 so that full = 100 pixels. */
    public static float clientScale() {
        return clientMax / 8.0F;
    }
}
