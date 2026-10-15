package com.divinerpgextended.arcana;

import net.divinerpg.entities.arcana.EntityCaptianMerik;
import net.divinerpg.entities.arcana.EntityDatticon;
import net.divinerpg.entities.arcana.EntityLeorna;
import net.divinerpg.entities.arcana.EntityVatticus;
import net.divinerpg.entities.arcana.EntityWarGeneral;
import net.divinerpg.entities.arcana.EntityZelus;
import net.divinerpg.entities.base.EntityDivineRPGVillager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;

/**
 * Торговцы Арканы. Появляются по прогрессу (gate - босс, после которого торговец приходит в Аркану; null - сразу
 * со входом в Аркану). Кого игрок встретил и с кем поговорил, того он может звать Кристаллом памяти.
 */
public enum ArcanaMerchant {

    LEORNA(EntityLeorna.class, null),
    VATTICUS(EntityVatticus.class, null),
    DATTICON(EntityDatticon.class, null),
    MERIK(EntityCaptianMerik.class, Boss.CADILLION_EDEN),
    ZELUS(EntityZelus.class, Boss.CADILLION_WILDWOOD),
    GENERAL(EntityWarGeneral.class, Boss.CADILLION_APALACHIA);

    private static final String MET = "dre_met_merchants";

    public final Class<? extends EntityDivineRPGVillager> type;
    public final Boss gate;

    ArcanaMerchant(Class<? extends EntityDivineRPGVillager> type, Boss gate) {
        this.type = type;
        this.gate = gate;
    }

    public String key() {
        return name().toLowerCase();
    }

    public static ArcanaMerchant of(Entity e) {
        if (e == null) return null;
        for (ArcanaMerchant m : values()) if (m.type == e.getClass()) return m;
        return null;
    }

    public boolean unlocked(EntityPlayer p) {
        return gate == null || BossProgress.has(p, gate);
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static int metMask(EntityPlayer p) {
        return persisted(p).getInteger(MET);
    }

    public boolean met(EntityPlayer p) {
        return (metMask(p) & 1 << ordinal()) != 0;
    }

    /** @return true, если встречен впервые */
    public boolean meet(EntityPlayer p) {
        int m = metMask(p);
        if ((m & 1 << ordinal()) != 0) return false;
        persisted(p).setInteger(MET, m | 1 << ordinal());
        return true;
    }

    public static int unlockedMask(EntityPlayer p) {
        int m = 0;
        for (ArcanaMerchant a : values()) if (a.unlocked(p)) m |= 1 << a.ordinal();
        return m;
    }

    public EntityDivineRPGVillager create(World w) {
        try {
            return type.getConstructor(World.class).newInstance(w);
        } catch (Exception e) {
            return null;
        }
    }
}
