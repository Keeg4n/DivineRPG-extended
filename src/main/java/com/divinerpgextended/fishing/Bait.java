package com.divinerpgextended.fishing;

import java.util.List;

import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.registry.ModItems;

/**
 * Baits. Early ones - rotten flesh, spider eye and worms (dug from dirt with a Worm Shovel), strong ones - a harvest from Leorna's seeds
 * (Arcana). Taken from the inventory automatically: the strongest (in lava without a lava rod - firebloom). Without bait only
 * regular fish and junk are caught.
 */
public enum Bait {

    ROTTEN_FLESH(5, false), SPIDER_EYE(10, false), WORM(15, false),
    VEILO(10, true), MARSINE(15, true), EUCALYPTUS(20, true), PINFLY(20, true), LAMONA(25, true), FIRESTOCK(25, true),
    AQUAMARINE(30, true), HITCHAK(40, true),
    /** Sun Lure (from Eden sparks): stronger than aquamarine, in Eden the golden catch is twice as frequent. */
    SUN_LURE(35, false),
    /** Cyclops Eye Bait (from cyclops eye shards): in caves bites faster and the Cave Angler can be caught. */
    CYCLOPS_EYE(20, false);

    /** Cyclops Eye Bait in a cave: bite wait x0.6. */
    public static final float CAVE_WAIT = 0.6F;

    public final int power;
    /** Arcana harvest (Leorna gives them as a reward). */
    public final boolean arcana;

    Bait(int power, boolean arcana) {
        this.power = power;
        this.arcana = arcana;
    }

    public String key() {
        return name().toLowerCase();
    }

    public Item item() {
        switch (this) {
            case ROTTEN_FLESH:
                return Items.rotten_flesh;
            case SPIDER_EYE:
                return Items.spider_eye;
            case WORM:
                return ModItems.worm;
            case VEILO:
                return ArcanaItems.veilo;
            case MARSINE:
                return ArcanaItems.marsine;
            case EUCALYPTUS:
                return ArcanaItems.eucalyptusSeeds;
            case PINFLY:
                return ArcanaItems.pinfly;
            case LAMONA:
                return ArcanaItems.lamona;
            case FIRESTOCK:
                return ArcanaItems.firestock;
            case AQUAMARINE:
                return ArcanaItems.aquamarine;
            case SUN_LURE:
                return ModItems.sunLure;
            case CYCLOPS_EYE:
                return ModItems.cyclopsBait;
            default:
                return ArcanaItems.hitchak;
        }
    }

    /** A random Arcana bait (Leorna's rewards and crates). */
    public static Bait randomArcana(java.util.Random r) {
        Bait b;
        do b = values()[r.nextInt(values().length)]; while (!b.arcana);
        return b;
    }

    public static Bait of(Item item) {
        if (item == null) return null;
        for (Bait b : values()) if (b.item() == item) return b;
        return null;
    }

    /** Fire bait allows fishing in lava. */
    public boolean lava() {
        return this == FIRESTOCK;
    }

    /** Pinfly - bites faster; Cyclops Eye Bait - in a cave even faster. */
    public float waitMultiplier(Zone zone) {
        if (this == CYCLOPS_EYE && zone == Zone.CAVE) return CAVE_WAIT;
        return this == PINFLY ? 0.8F : 1.0F;
    }

    /** Eucalyptus - crates more often. */
    public float crateBonus() {
        return this == EUCALYPTUS ? 0.05F : 0;
    }

    /** The fishing rod tooltip line: which bait will be used next. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void tooltip(EntityPlayer p, List list) {
        int slot = p == null ? -1 : find(p);
        if (slot < 0) {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.no_bait"));
            return;
        }
        ItemStack s = p.inventory.mainInventory[slot];
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.bait_current", s.getDisplayName(),
            of(s.getItem()).power));
    }

    /** Slot of the strongest bait (on a tie - the first in order) or -1. */
    public static int find(EntityPlayer p) {
        int best = -1, bestPower = -1;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            Bait b = s == null ? null : of(s.getItem());
            if (b != null && b.power > bestPower) {
                best = i;
                bestPower = b.power;
            }
        }
        return best;
    }

    /** For lava without a lava rod only firebloom will do; otherwise - the strongest. */
    public static int find(EntityPlayer p, boolean needFire) {
        if (!needFire) return find(p);
        return slotOf(p, FIRESTOCK);
    }

    public static int slotOf(EntityPlayer p, Bait b) {
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.getItem() == b.item()) return i;
        }
        return -1;
    }
}
