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
 * What hit the Frozen Throne boss: a melee weapon, an arrow of which Divine bow, a shot from the ice cannon. The bosses
 * use this to decide what worked: each Iceika ice sword and bow is useful against its own target.
 */
public final class ThroneCombat {

    /** Marker of the Throne bosses' helpers (raised dead). */
    public static final String MINION_TAG = "dreThroneMinion";

    private ThroneCombat() {}

    /** The sword or maul in hand if the blow was in melee, otherwise null. */
    public static Item melee(DamageSource s) {
        Entity e = s.getEntity();
        if (!(e instanceof EntityPlayer) || s.getSourceOfDamage() != e || s.isProjectile()) return null;
        ItemStack held = ((EntityPlayer) e).getHeldItem();
        return held == null ? null : held.getItem();
    }

    /** Name of the Divine arrow ("infernoArrow", "bluefireArrow", "snowstormArrow"...) or null. */
    public static String arrow(DamageSource s) {
        Entity src = s.getSourceOfDamage();
        return src instanceof EntityDivineArrow ? ((EntityDivineArrow) src).getTextureName() : null;
    }

    public static boolean isArrow(DamageSource s, String name) {
        return name.equals(arrow(s));
    }

    /** A shot from the ice cannon (Frost, Fractite or Frostclaw Cannon). */
    public static boolean iceCannon(DamageSource s) {
        if (!(s.getSourceOfDamage() instanceof EntityShooterBullet) || !(s.getEntity() instanceof EntityPlayer)) return false;
        ItemStack held = ((EntityPlayer) s.getEntity()).getHeldItem();
        if (held == null) return false;
        Item i = held.getItem();
        return i == VanillaItemsWeapons.frostCannon || i == IceikaItems.fractiteCannon || i == IceikaItems.frostclawCannon;
    }

    /** Damage multiplier against the Frost Colossus: heavy ice weapons split its body. */
    public static float vsColossus(Item w) {
        if (w == IceikaItems.frozenMaul) return 2.5F;
        if (w == IceikaItems.glacierSword) return 2.0F;
        if (w == IceikaItems.frostkingSword) return 1.6F;
        if (vsLich(w) > 1.0F) return 1.3F; // the Lich's blades are weaker than the maul, but they split ice
        return 1.0F;
    }

    /** Multiplier against the Throne bosses' dead: any Throne ice weapon (against the Colossus and against the Lich) - x2. */
    public static float vsMinion(Item w) {
        return vsColossus(w) > 1.0F || vsLich(w) > 1.0F ? 2.0F : 1.0F;
    }

    /** Damage multiplier against the Lich King: these blades also keep him from healing. */
    public static float vsLich(Item w) {
        if (w == IceikaItems.icineSword) return 2.2F;
        if (w == IceikaItems.icicleBane) return 2.0F;
        if (w == IceikaItems.enderice) return 1.6F;
        return 1.0F;
    }
}
