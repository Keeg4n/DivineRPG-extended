package com.divinerpgextended.magic;

import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

/**
 * Effects of arcane potions (brewed on the Arcane Brewing Stand, {@link TileArcaneBrewer}). The number in the list is the potion metadata:
 * add new ones only at the end. Strong (glowstone dust) - second level, long (redstone) - twice as long, shared (gunpowder) -
 * also affects players within 8 blocks.
 */
public enum ArcaneEffect {

    /** No arcana recovery delay, recovery x2 (strong x3). */
    CLARITY(0xB57BFF, 45),
    /** Arcane damage +20% (+35%). */
    SORCERY(0x7A3CFF, 240),
    /** Arcane weapon shots are 25% cheaper (40%). */
    COMPOSURE(0x3FD2C8, 240),
    /** Incoming arcane damage -30% (-45%). */
    CHARM_WARD(0xC8C8FF, 240),
    /** Melee hits on you -15% (-25%). */
    STONE_SKIN(0x8A8A8A, 240),
    /** Projectiles on you -35% (-50%). */
    DEFLECTION(0xE8E0B0, 240),
    /** All damage to you -10% (-15%). */
    FORTITUDE(0x8B3A3A, 240),
    /** 30% (45%) of melee damage is returned to the attacker. */
    BRAMBLE_HIDE(0x4F7A28, 240),
    /** Iceika cold does not build up. */
    INNER_HEAT(0xFF7A1A, 240),
    /** Melee hits +15% (+25%). */
    BATTLE_FERVOR(0xD01818, 240),
    /** Projectiles +15% (+25%), 20% (30%) chance to return the arrow. */
    STEADY_HAND(0x6FA8DC, 240),
    /** Arcane weapons return 5% (8%) of the damage dealt as arcana. */
    ARCANE_HUNGER(0x9B1FA8, 240),
    /** Removed (way home). */
    WAY_HOME(0x5DE8B0, 0),
    /** Removed (call to arms). */
    CALL_TO_ARMS(0x6B1010, 300),
    /** Removed (stillness). */
    STILLNESS(0xCFF5FF, 300),
    /** Bites faster, rare fish more often. */
    FISHERS_FORTUNE(0x2E7DD8, 300);

    public final int color;
    /** Duration in seconds (0 - acts at once). */
    public final int seconds;

    ArcaneEffect(int color, int seconds) {
        this.color = color;
        this.seconds = seconds;
    }

    public String key() {
        return name().toLowerCase();
    }

    public boolean instant() {
        return seconds == 0;
    }

    /** Whether the potion has a strong version (the instant return and warmth do not). */
    public boolean leveled() {
        return !instant() && this != INNER_HEAT;
    }

    /** The ingredient from which this potion is brewed on the stand (a water bottle + it). */
    public Item ingredient() {
        switch (this) {
            case CLARITY: return ArcanaItems.aquamarine;
            case SORCERY: return ArcanaItems.lamona;
            case COMPOSURE: return ArcanaItems.hitchak;
            case CHARM_WARD: return ArcanaItems.veilo;
            case STONE_SKIN: return VanillaItemsOther.arlemiteIngot;
            case DEFLECTION: return Items.feather;
            case FORTITUDE: return TwilightItemsOther.mortumSoul;
            case BRAMBLE_HIDE: return VanillaItemsOther.crabClaw;
            case INNER_HEAT: return ArcanaItems.firestock;
            case BATTLE_FERVOR: return VanillaItemsOther.furyFire;
            case STEADY_HAND: return TwilightItemsOther.skythernSoul;
            case ARCANE_HUNGER: return ArcanaItems.collector;
            case WAY_HOME: return Items.ender_pearl;
            case CALL_TO_ARMS: return TwilightItemsOther.apalachiaSoul;
            case STILLNESS: return TwilightItemsOther.edenSoul;
            default: return com.divinerpgextended.registry.ModItems.fish;
        }
    }

    /** Removed potions (0.99.30): not brewed, not visible, drunk ones are empty. Their numbers stay reserved - metadata does not shift. */
    public boolean removed() {
        return this == WAY_HOME || this == CALL_TO_ARMS || this == STILLNESS;
    }

    public static ArcaneEffect byIngredient(Item i) {
        if (i == null) return null;
        for (ArcaneEffect e : values()) if (!e.removed() && e.ingredient() == i) return e;
        return null;
    }

    public static ArcaneEffect of(int meta) {
        int i = meta & 31;
        return i < values().length ? values()[i] : CLARITY;
    }
}
