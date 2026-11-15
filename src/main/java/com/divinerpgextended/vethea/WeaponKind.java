package com.divinerpgextended.vethea;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/**
 * The kind of Vethean weapon that dealt the hit. Projectiles are recognized by class (Divine's names are the same across versions),
 * melee - by the weapon in hand. A foreign (non-Vethean) weapon - null.
 */
public enum WeaponKind {

    SWORD, CLAW, HAMMER, BOW, DISK, STAFF, CANNON;

    /** Group for helmet roles: 0 melee (helmet), 1 ranged (mask), 2 arcana (hood). */
    public int group() {
        return this == SWORD || this == CLAW || this == HAMMER ? 0 : this == STAFF ? 2 : 1;
    }

    public String key() {
        return name().toLowerCase();
    }

    public static WeaponKind of(DamageSource s) {
        Entity src = s.getEntity(), direct = s.getSourceOfDamage();
        if (!(src instanceof EntityPlayer)) return null;
        EntityPlayer p = (EntityPlayer) src;
        if (direct == p) {
            WeaponKind k = ofItem(p.getHeldItem());
            return k != null && k.group() == 0 ? k : null;
        }
        if (direct == null) return null;
        if (direct instanceof com.divinerpgextended.entity.EntityTomahawk) return ((com.divinerpgextended.entity.EntityTomahawk) direct).disk()
            && ofItem(((com.divinerpgextended.entity.EntityTomahawk) direct).item()) == DISK ? DISK : null;
        for (Class<?> c = direct.getClass(); c != null && c != Entity.class; c = c.getSuperclass()) {
            String n = c.getSimpleName();
            if (n.equals("EntityDisk")) return DISK;
            if (n.equals("EntityBouncingProjectile") || n.equals("EntityEvernightProjectile")) return STAFF;
            if (n.equals("EntityVetheanCannonEversight") || n.equals("EntityEversightProjectile")) return CANNON;
            if (n.equals("EntityShooterBullet")) return ofItem(p.getHeldItem()) == CANNON ? CANNON : null;
        }
        if (direct instanceof EntityArrow) return ofItem(p.getHeldItem()) == BOW ? BOW : null;
        return null;
    }

    /** A Vethean weapon in hand. */
    public static WeaponKind ofItem(ItemStack s) {
        if (s == null || !Nightmare.vethean(s)) return null;
        Item i = s.getItem();
        for (Class<?> c = i.getClass(); c != null && c != Item.class; c = c.getSuperclass()) {
            String n = c.getSimpleName();
            if (n.equals("ItemVetheanClaw")) return CLAW;
            if (n.equals("ItemVetheanSword")) {
                String u = String.valueOf(i.getUnlocalizedName()).toLowerCase();
                return u.contains("hammer") || u.contains("maul") ? HAMMER : SWORD;
            }
            if (n.equals("ItemStaff")) return STAFF;
            if (n.equals("ItemVetheanDisk") || n.equals("ItemDisk")) return DISK;
            if (n.equals("ItemVetheanCannon") || n.equals("ItemVetheanEversight")) return CANNON;
        }
        return i instanceof ItemBow ? BOW : null;
    }
}
