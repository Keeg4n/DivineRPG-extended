package com.divinerpgextended.trinket;

import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Бижутерия Джека о'Мэна (кольца, амулеты, пояс для Baubles). Делается на заказ: головы мобов одного вида (или
 * всех видов), материал и теневые монеты. Головы: 0 скелет, 1 визер-скелет, 2 зомби, 4 крипер.
 */
public enum Trinket {

    /** +20% урона стрелами, 25% шанс вернуть стрелу. */
    BONE_RING("ring", new int[] { 8, 0, 0, 0 }, Items.bone, 32, 16),
    /** Когда здоровья меньше 30% - регенерация II (раз в 30 с); гниль не вызывает голод. */
    DEAD_AMULET("amulet", new int[] { 0, 0, 8, 0 }, Items.rotten_flesh, 32, 16),
    /** -75% урона от взрывов. */
    BLAST_BELT("belt", new int[] { 0, 0, 0, 6 }, Items.gunpowder, 32, 24),
    /** Иммунитет к иссушению, удары иссушают (20%). */
    WITHER_RING("ring", new int[] { 0, 4, 0, 0 }, null, 8, 24),
    /** Ночью: ночное зрение, +15% урона, мобы не замечают дальше 8 блоков. */
    JACK_AMULET("amulet", new int[] { 2, 2, 2, 2 }, Item.getItemFromBlock(Blocks.pumpkin), 32, 32);

    /** Тип слота Baubles: ring, amulet, belt. */
    public final String slot;
    /** Сколько голов: [скелет, визер-скелет, зомби, крипер]. */
    public final int[] heads;
    private final Item material;
    public final int materialCount, coins;

    Trinket(String slot, int[] heads, Item material, int materialCount, int coins) {
        this.slot = slot;
        this.heads = heads;
        this.material = material;
        this.materialCount = materialCount;
        this.coins = coins;
    }

    public String key() {
        return name().toLowerCase();
    }

    /** Метаданные головы по номеру в массиве heads. */
    public static final int[] HEAD_META = { 0, 1, 2, 4 };

    public Item material() {
        return material != null ? material : VanillaItemsOther.hellstoneIngot;
    }

    /** Какой заказ подходит к голове в руке (meta) - первый, где этой головы нужно больше всего; для тыквы - амулет Джека. */
    public static Trinket forHeld(ItemStack held) {
        if (held == null) return null;
        if (held.getItem() == Item.getItemFromBlock(Blocks.pumpkin)) return JACK_AMULET;
        if (held.getItem() != Items.skull) return null;
        int idx = -1;
        for (int i = 0; i < HEAD_META.length; i++) if (HEAD_META[i] == held.getItemDamage()) idx = i;
        if (idx < 0) return null;
        for (Trinket t : values()) if (t != JACK_AMULET && t.heads[idx] > 0) return t;
        return null;
    }
}
