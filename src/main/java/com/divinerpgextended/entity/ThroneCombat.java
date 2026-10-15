package com.divinerpgextended.entity;

import net.divinerpg.entities.base.EntityDivineArrow;
import net.divinerpg.entities.vanilla.projectile.EntityShooterBullet;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/**
 * Чем ударили босса Ледяного Трона: оружие ближнего боя, стрела какого лука Divine, выстрел ледяной пушки. По этому
 * боссы решают, что сработало: каждый ледяной меч и лук Iceika полезен против своей цели.
 */
public final class ThroneCombat {

    /** Метка помощников боссов Трона (поднятые мертвецы). */
    public static final String MINION_TAG = "dreThroneMinion";

    private ThroneCombat() {}

    /** Меч или молот в руке, если удар был в ближнем бою, иначе null. */
    public static Item melee(DamageSource s) {
        Entity e = s.getEntity();
        if (!(e instanceof EntityPlayer) || s.getSourceOfDamage() != e || s.isProjectile()) return null;
        ItemStack held = ((EntityPlayer) e).getHeldItem();
        return held == null ? null : held.getItem();
    }

    /** Название стрелы Divine ("infernoArrow", "bluefireArrow", "snowstormArrow"...) или null. */
    public static String arrow(DamageSource s) {
        Entity src = s.getSourceOfDamage();
        return src instanceof EntityDivineArrow ? ((EntityDivineArrow) src).getTextureName() : null;
    }

    public static boolean isArrow(DamageSource s, String name) {
        return name.equals(arrow(s));
    }

    /** Выстрел ледяной пушки (Frost, Fractite или Frostclaw Cannon). */
    public static boolean iceCannon(DamageSource s) {
        if (!(s.getSourceOfDamage() instanceof EntityShooterBullet) || !(s.getEntity() instanceof EntityPlayer)) return false;
        ItemStack held = ((EntityPlayer) s.getEntity()).getHeldItem();
        if (held == null) return false;
        Item i = held.getItem();
        return i == VanillaItemsWeapons.frostCannon || i == IceikaItems.fractiteCannon || i == IceikaItems.frostclawCannon;
    }

    /** Множитель урона по Ледяному Колоссу: тяжёлое ледяное оружие раскалывает его тело. */
    public static float vsColossus(Item w) {
        if (w == IceikaItems.frozenMaul) return 2.5F;
        if (w == IceikaItems.glacierSword) return 2.0F;
        if (w == IceikaItems.frostkingSword) return 1.6F;
        return 1.0F;
    }

    /** Множитель урона по Королю-личу: эти клинки ещё и не дают ему лечиться. */
    public static float vsLich(Item w) {
        if (w == IceikaItems.icineSword) return 2.2F;
        if (w == IceikaItems.icicleBane) return 2.0F;
        if (w == IceikaItems.enderice) return 1.6F;
        return 1.0F;
    }
}
