package com.divinerpgextended.trinket;

import java.util.EnumSet;
import java.util.Set;

import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Accessories. The number in the list = the item's metadata: add new ones only at the end.
 * <ul>
 * <li>the first five are made by Jack O Man to order from mob heads (heads: 0 skeleton, 1 wither skeleton, 2 zombie, 4 creeper);</li>
 * <li>then - from bosses, from merchants and from chests;</li>
 * <li>the last ones are merged at the Iceika workshop master: they work as both components and take one slot.</li>
 * </ul>
 */
public enum Trinket {

    /** +20% arrow damage, 25% chance to return the arrow. */
    BONE_RING(new int[] { 8, 0, 0, 0 }, Items.bone, 32, 16),
    /** When health is below 30% - Regeneration II (once per 30 s); rot does not cause hunger. */
    DEAD_AMULET(new int[] { 0, 0, 8, 0 }, Items.rotten_flesh, 32, 16),
    /** -75% explosion damage. */
    BLAST_BELT(new int[] { 0, 0, 0, 6 }, Items.gunpowder, 32, 24),
    /** Immunity to wither, hits wither (20%). */
    WITHER_RING(new int[] { 0, 4, 0, 0 }, null, 8, 24),
    /** At night: night vision, +15% damage, mobs do not notice beyond 8 blocks. */
    JACK_AMULET(new int[] { 2, 2, 2, 2 }, Item.getItemFromBlock(Blocks.pumpkin), 32, 32),

    // from bosses
    CARCINUS_SHELL, ICE_BROOCH, COLOSSUS_PAULDRON, LICH_CROWN, ANCIENT_CHARM, WATCHER_EYE, BLUE_FLAME, DRAMIX_SEAL,
    PARASECTA_STING,
    // from merchants
    TACKLE_BOX, MASTER_SCABBARD, VALOR_MEDAL, POCKET_MAGNET, WARM_SCARF,
    // from chests and mobs
    DEEP_SHELL, PHARAOH_ANKH, ETERNAL_ICE_RING, DUNGEON_LAMP,
    // merged at the workshop master
    STEADFAST_BELT, HEART_OF_WINTER, TIDE_ARMOR,
    /** From the last of the five Ayeraco: a jump in the air, in the Twilight dimensions - two. */
    AYERACO_WING,
    /** From the Great Eden Cadillion: hover at one height in the Twilight dimensions (the reserve grows per cadillion). */
    MAGIC_CARPET,
    /** Dream Wings (Vethea only): takeoff and passage to floors 2, 3, 4. Crafted from pearl and floor ingots. */
    DREAM_WINGS_1, DREAM_WINGS_2, DREAM_WINGS_3,
    /** Quivers, crafted from Eden materials: infinite ammo only for their own weapon kind. */
    ENDLESS_QUIVER, DUST_POUCH, SLICER_SLING,
    /** Hunter's marks - for closed bestiary chapters (from the Livestock Merchant): stronger in their own world ({@link com.divinerpgextended.bestiary.Bestiary}). */
    HUNTER_OVERWORLD, HUNTER_NETHER, HUNTER_END, HUNTER_ICEIKA, HUNTER_EDEN, HUNTER_WILDWOOD, HUNTER_APALACHIA, HUNTER_SKYTHERN, HUNTER_MORTUM,
    HUNTER_ARCANA, HUNTER_VETHEA,
    /** Arcane Ward (from Vatticus): arcana takes 75% of arcane damage at 1 arcana per point (without it 60% at 2). */
    ARCANE_WARD,
    /** Crystal of Clarity (from Vatticus): arcana recovery delay 0.75 s instead of 2, recovery +50%. */
    CLARITY_CRYSTAL,
    /** Sun Halo (Eden reliquary): heals by day under open sky, Eden overheating does not apply. */
    SUN_HALO,
    /** Fins (from the Liopleurodon): swim faster in water, a dash forward on sprint. */
    LIO_FINS;

    /** Jack's order: how many heads [skeleton, wither skeleton, zombie, creeper]; null - not a Jack order. */
    public final int[] heads;
    private final Item material;
    public final int materialCount, coins;

    Trinket(int[] heads, Item material, int materialCount, int coins) {
        this.heads = heads;
        this.material = material;
        this.materialCount = materialCount;
        this.coins = coins;
    }

    Trinket() {
        this(null, null, 0, 0);
    }

    public String key() {
        return name().toLowerCase();
    }

    public boolean jack() {
        return heads != null;
    }

    /** Head metadata by its index in the heads array. */
    public static final int[] HEAD_META = { 0, 1, 2, 4 };

    public Item material() {
        return material != null ? material : VanillaItemsOther.hellstoneIngot;
    }

    /** Armor parts by armorType: 0 helmet, 1 chestplate, 2 leggings, 3 boots (bits for {@link #armorParts}). */
    public static final int HEAD = 1, BODY = 2, LEGS = 4, FEET = 8;

    /**
     * Which armor parts it embeds into (bits HEAD..FEET): head and neck - helmet; torso, shoulders, arms and back - chestplate;
     * belt - leggings; feet and movement - boots. A merged one - into a part of either component.
     */
    public int armorParts() {
        Trinket[] p = parts();
        if (p != null) return p[0].armorParts() | p[1].armorParts();
        if (name().startsWith("HUNTER_")) return HEAD;
        switch (this) {
            case LICH_CROWN: case WATCHER_EYE: case DEEP_SHELL: case DUNGEON_LAMP: case JACK_AMULET: case DEAD_AMULET:
            case WARM_SCARF: case CLARITY_CRYSTAL: case SUN_HALO:
                return HEAD;
            case BLAST_BELT: case MASTER_SCABBARD: case ENDLESS_QUIVER: case DUST_POUCH: case SLICER_SLING: case TACKLE_BOX:
            case POCKET_MAGNET:
                return LEGS;
            case MAGIC_CARPET: case AYERACO_WING: case BLUE_FLAME: case LIO_FINS:
                return FEET;
            default:
                return BODY;
        }
    }

    /** Embedding price at the Wanderer Anvil, experience levels. */
    public int levels() {
        switch (this) {
            case PHARAOH_ANKH: case MAGIC_CARPET: case AYERACO_WING: case DREAM_WINGS_1: case DREAM_WINGS_2: case DREAM_WINGS_3:
                return 20;
            case BONE_RING: case DEAD_AMULET: case BLAST_BELT: case WITHER_RING: case JACK_AMULET: case DEEP_SHELL:
            case ETERNAL_ICE_RING: case DUNGEON_LAMP: case ENDLESS_QUIVER: case DUST_POUCH: case SLICER_SLING: case TACKLE_BOX:
            case WARM_SCARF: case VALOR_MEDAL:
                return 10;
            default:
                return parts() != null ? 20 : 15;
        }
    }

    /** Which two it is made of (for merged ones) or null. */
    public Trinket[] parts() {
        switch (this) {
            case STEADFAST_BELT: return new Trinket[] { BLAST_BELT, COLOSSUS_PAULDRON };
            case HEART_OF_WINTER: return new Trinket[] { WARM_SCARF, ICE_BROOCH };
            case TIDE_ARMOR: return new Trinket[] { DEEP_SHELL, CARCINUS_SHELL };
            default: return null;
        }
    }

    /** An item native to Vethea: works in the nightmare (other accessories are silent there). */
    public boolean vethean() {
        return this == DREAM_WINGS_1 || this == DREAM_WINGS_2 || this == DREAM_WINGS_3 || this == HUNTER_VETHEA;
    }

    private static Set<Trinket>[] provided;

    /** Which effects it gives: itself and, for merged ones, both components (read-only - queried on every hit). */
    @SuppressWarnings("unchecked")
    public Set<Trinket> provides() {
        if (provided == null) {
            Set<Trinket>[] all = new Set[values().length];
            for (Trinket t : values()) {
                Set<Trinket> s = EnumSet.of(t);
                Trinket[] p = t.parts();
                if (p != null) for (Trinket c : p) s.add(c);
                all[t.ordinal()] = java.util.Collections.unmodifiableSet(s);
            }
            provided = all;
        }
        return provided[ordinal()];
    }

    /** Merging two at the workshop master: the result or null. */
    public static Trinket combine(Trinket a, Trinket b) {
        for (Trinket t : values()) {
            Trinket[] p = t.parts();
            if (p != null && (p[0] == a && p[1] == b || p[0] == b && p[1] == a)) return t;
        }
        return null;
    }

    /** Which Jack order fits the head in hand (meta) - the first where this head is needed the most; for a pumpkin - Jack's amulet. */
    public static Trinket forHeld(ItemStack held) {
        if (held == null) return null;
        if (held.getItem() == Item.getItemFromBlock(Blocks.pumpkin)) return JACK_AMULET;
        if (held.getItem() != Items.skull) return null;
        int idx = -1;
        for (int i = 0; i < HEAD_META.length; i++) if (HEAD_META[i] == held.getItemDamage()) idx = i;
        if (idx < 0) return null;
        for (Trinket t : values()) if (t.jack() && t != JACK_AMULET && t.heads[idx] > 0) return t;
        return null;
    }
}
