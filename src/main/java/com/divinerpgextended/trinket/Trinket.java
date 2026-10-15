package com.divinerpgextended.trinket;

import java.util.EnumSet;
import java.util.Set;

import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Аксессуары (слоты как в Террарии). Номер в списке = метаданные предмета: новые добавлять только в конец.
 * <ul>
 * <li>первые пять делает Джек о'Мэн на заказ из голов мобов (головы: 0 скелет, 1 визер-скелет, 2 зомби, 4 крипер);</li>
 * <li>дальше - с боссов, от торговцев и из сундуков;</li>
 * <li>последние - объединённые у мастера мастерской Айсики: работают как оба компонента и занимают один слот.</li>
 * </ul>
 */
public enum Trinket {

    /** +20% урона стрелами, 25% шанс вернуть стрелу. */
    BONE_RING(new int[] { 8, 0, 0, 0 }, Items.bone, 32, 16),
    /** Когда здоровья меньше 30% - регенерация II (раз в 30 с); гниль не вызывает голод. */
    DEAD_AMULET(new int[] { 0, 0, 8, 0 }, Items.rotten_flesh, 32, 16),
    /** -75% урона от взрывов. */
    BLAST_BELT(new int[] { 0, 0, 0, 6 }, Items.gunpowder, 32, 24),
    /** Иммунитет к иссушению, удары иссушают (20%). */
    WITHER_RING(new int[] { 0, 4, 0, 0 }, null, 8, 24),
    /** Ночью: ночное зрение, +15% урона, мобы не замечают дальше 8 блоков. */
    JACK_AMULET(new int[] { 2, 2, 2, 2 }, Item.getItemFromBlock(Blocks.pumpkin), 32, 32),

    // с боссов
    CARCINUS_SHELL, ICE_BROOCH, COLOSSUS_PAULDRON, LICH_CROWN, ANCIENT_CHARM, WATCHER_EYE, BLUE_FLAME, DRAMIX_SEAL,
    PARASECTA_STING,
    // от торговцев
    TACKLE_BOX, MASTER_SCABBARD, VALOR_MEDAL, POCKET_MAGNET, WARM_SCARF,
    // из сундуков и с мобов
    DEEP_SHELL, PHARAOH_ANKH, ETERNAL_ICE_RING, DUNGEON_LAMP,
    // объединённые у мастера мастерской
    STEADFAST_BELT, HEART_OF_WINTER, TIDE_ARMOR,
    /** С последнего из пяти Айрако: прыжок в воздухе, в Сумеречных мирах - два. */
    AYERACO_WING;

    /** Заказ у Джека: сколько голов [скелет, визер-скелет, зомби, крипер]; null - не заказ Джека. */
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

    /** Метаданные головы по номеру в массиве heads. */
    public static final int[] HEAD_META = { 0, 1, 2, 4 };

    public Item material() {
        return material != null ? material : VanillaItemsOther.hellstoneIngot;
    }

    /** Из каких двух собран (для объединённых) или null. */
    public Trinket[] parts() {
        switch (this) {
            case STEADFAST_BELT: return new Trinket[] { BLAST_BELT, COLOSSUS_PAULDRON };
            case HEART_OF_WINTER: return new Trinket[] { WARM_SCARF, ICE_BROOCH };
            case TIDE_ARMOR: return new Trinket[] { DEEP_SHELL, CARCINUS_SHELL };
            default: return null;
        }
    }

    /** Какие эффекты даёт: сам и, у объединённых, оба компонента. */
    public Set<Trinket> provides() {
        Set<Trinket> s = EnumSet.of(this);
        Trinket[] p = parts();
        if (p != null) for (Trinket t : p) s.add(t);
        return s;
    }

    /** Объединение двух у мастера мастерской: результат или null. */
    public static Trinket combine(Trinket a, Trinket b) {
        for (Trinket t : values()) {
            Trinket[] p = t.parts();
            if (p != null && (p[0] == a && p[1] == b || p[0] == b && p[1] == a)) return t;
        }
        return null;
    }

    /** Какой заказ Джека подходит к голове в руке (meta) - первый, где этой головы нужно больше всего; для тыквы - амулет Джека. */
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
