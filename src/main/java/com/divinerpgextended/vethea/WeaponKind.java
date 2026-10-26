package com.divinerpgextended.vethea;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/**
 * Вид ветейского оружия, которым нанесён удар. Снаряды узнаются по классу (имена у Divine разных версий совпадают),
 * ближний бой - по оружию в руке. Чужое (не ветейское) оружие - null.
 */
public enum WeaponKind {

    SWORD, CLAW, HAMMER, BOW, DISK, STAFF, CANNON;

    /** Группа для ролей шлемов: 0 ближний бой (шлем), 1 дальний (маска), 2 аркана (капюшон). */
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

    /** Ветейское оружие в руке. */
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
