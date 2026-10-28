package com.divinerpgextended.magic;

import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

/**
 * Эффекты арканных зелий (варятся на арканной стойке, {@link TileArcaneBrewer}). Номер в списке - метаданные зелья:
 * новые добавлять только в конец. Сильное (светопыль) - второй уровень, длинное (редстоун) - вдвое дольше, общее (порох) -
 * действует и на игроков в 8 блоках.
 */
public enum ArcaneEffect {

    /** Без задержки восстановления арканы, восстановление x2 (сильное x3). */
    CLARITY(0xB57BFF, 45),
    /** Арканный урон +20% (+35%). */
    SORCERY(0x7A3CFF, 240),
    /** Выстрелы арканного оружия дешевле на 25% (40%). */
    COMPOSURE(0x3FD2C8, 240),
    /** Входящий арканный урон -30% (-45%). */
    CHARM_WARD(0xC8C8FF, 240),
    /** Удары вблизи по тебе -15% (-25%). */
    STONE_SKIN(0x8A8A8A, 240),
    /** Снаряды по тебе -35% (-50%). */
    DEFLECTION(0xE8E0B0, 240),
    /** Весь урон по тебе -10% (-15%). */
    FORTITUDE(0x8B3A3A, 240),
    /** 30% (45%) урона ближнего боя возвращается атакующему. */
    BRAMBLE_HIDE(0x4F7A28, 240),
    /** Холод Айсики не копится. */
    INNER_HEAT(0xFF7A1A, 240),
    /** Удары вблизи +15% (+25%). */
    BATTLE_FERVOR(0xD01818, 240),
    /** Снаряды +15% (+25%), 20% (30%) шанс вернуть стрелу. */
    STEADY_HAND(0x6FA8DC, 240),
    /** Арканное оружие возвращает 5% (8%) нанесённого урона арканой. */
    ARCANE_HUNGER(0x9B1FA8, 240),
    /** Сразу домой: к кровати (или точке появления мира). */
    WAY_HOME(0x5DE8B0, 0),
    /** Мобов вокруг больше. */
    CALL_TO_ARMS(0x6B1010, 300),
    /** Мобов вокруг меньше. */
    STILLNESS(0xCFF5FF, 300),
    /** Клюёт быстрее, редкая рыба чаще. */
    FISHERS_FORTUNE(0x2E7DD8, 300);

    public final int color;
    /** Длительность в секундах (0 - действует сразу). */
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

    /** Есть ли у зелья сильная версия (у мгновенного возврата и у тепла - нет). */
    public boolean leveled() {
        return !instant() && this != INNER_HEAT;
    }

    /** Ингредиент, из которого на стойке варится это зелье (склянка воды + он). */
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

    public static ArcaneEffect byIngredient(Item i) {
        if (i == null) return null;
        for (ArcaneEffect e : values()) if (e.ingredient() == i) return e;
        return null;
    }

    public static ArcaneEffect of(int meta) {
        int i = meta & 31;
        return i < values().length ? values()[i] : CLARITY;
    }
}
