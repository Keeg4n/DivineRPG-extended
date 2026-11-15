package com.divinerpgextended.boss;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Which bosses the player has killed. Stored in the player's "persisted" data (PlayerPersisted), which Forge
 * carries over after death.
 */
public final class BossProgress {

    private static final String KEY = "divinerpgextended_bosses";

    private BossProgress() {}

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static int mask(EntityPlayer player) {
        return persisted(player).getInteger(KEY);
    }

    public static boolean has(EntityPlayer player, Boss boss) {
        return (mask(player) & boss.bit()) != 0;
    }

    /** @return true if the boss was counted for the first time */
    public static boolean grant(EntityPlayer player, Boss boss) {
        int m = mask(player);
        if ((m & boss.bit()) != 0) return false;
        persisted(player).setInteger(KEY, m | boss.bit());
        com.divinerpgextended.debug.Debug.note(player, "Boss counted: " + boss.name().toLowerCase());
        return true;
    }

    /** Remove the mark (for the /dreboss command). */
    public static void revoke(EntityPlayer player, Boss boss) {
        persisted(player).setInteger(KEY, mask(player) & ~boss.bit());
        com.divinerpgextended.debug.Debug.note(player, "Boss removed: " + boss.name().toLowerCase());
    }
}
