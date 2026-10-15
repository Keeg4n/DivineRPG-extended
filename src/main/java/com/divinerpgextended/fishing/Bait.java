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
 * Приманки. Ранние - гнилая плоть, паучий глаз и черви (червекопалкой из земли), сильные - урожай с семян Леорны
 * (Аркана). Берутся из инвентаря сами: самая сильная (в лаве без лавовой удочки - огнецвет). Без приманки ловится
 * только обычная рыба и мусор.
 */
public enum Bait {

    ROTTEN_FLESH(5, false), SPIDER_EYE(10, false), WORM(15, false),
    VEILO(10, true), MARSINE(15, true), EUCALYPTUS(20, true), PINFLY(20, true), LAMONA(25, true), FIRESTOCK(25, true),
    AQUAMARINE(30, true), HITCHAK(40, true);

    public final int power;
    /** Урожай Арканы (их Леорна даёт в награду). */
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
            default:
                return ArcanaItems.hitchak;
        }
    }

    /** Случайная арканная приманка (награды Леорны и ящики). */
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

    /** Огненная приманка позволяет рыбачить в лаве. */
    public boolean lava() {
        return this == FIRESTOCK;
    }

    /** Pinfly - клюёт быстрее. */
    public float waitMultiplier() {
        return this == PINFLY ? 0.8F : 1.0F;
    }

    /** Эвкалипт - чаще ящики. */
    public float crateBonus() {
        return this == EUCALYPTUS ? 0.05F : 0;
    }

    /** Строка подсказки удочки: какая приманка пойдёт следующей. */
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

    /** Слот самой сильной приманки (при равенстве - первой по порядку) или -1. */
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

    /** Для лавы без лавовой удочки годится только огнецвет; иначе - самая сильная. */
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
