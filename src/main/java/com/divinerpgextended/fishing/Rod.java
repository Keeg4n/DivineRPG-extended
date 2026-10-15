package com.divinerpgextended.fishing;

/**
 * Удочки и их сила рыбалки (как в Террарии: сила удочки + сила приманки решают, как быстро клюёт и насколько редкий
 * улов). У многих удочек есть особенность.
 */
public enum Rod {

    VANILLA("vanilla", 5, 0x8B6A3E),
    REALMITE("realmite", 10, 0x6FA8C8),
    ARLEMITE("arlemite", 15, 0x7FD06A),
    RUPEE("rupee", 20, 0x3F7FE0),
    /** Тыквенная удочка Джека о'Мэна: ночью +20 к силе. */
    JACK("jack", 25, 0xE07A1A),
    /** Арканная удочка Леорны: половину приманок не тратит. */
    ARCANA("arcana", 30, 0x9A6AE0),
    /** Эдемская: +10% к шансу ящика. */
    EDEN("eden", 35, 0xF0D040),
    /** Лесная (Wildwood): клюёт на четверть быстрее. */
    WILDWOOD("wildwood", 40, 0x3FA0C0),
    /** Апалачская: редкий улов в полтора раза чаще. */
    APALACHIA("apalachia", 45, 0xB050E0),
    /** Скайтернская: 20% шанс поймать сразу два. */
    SKYTHERN("skythern", 50, 0xD8E8F0),
    /** Золотая удочка - награда Леорны за задания: треть приманок не тратит, редкое чаще. */
    GOLDEN("golden", 50, 0xFFD040),
    /** Мортумская: рыбачит в лаве без огненной приманки. */
    MORTUM("mortum", 55, 0x6A3030),
    /** Удочка Голода (Vethea): ловит в лаве, двойной улов, приманки тратит реже. */
    HUNGER("hunger", 65, 0x40C8A0);

    public final String key;
    public final int power;
    public final int color;

    Rod(String key, int power, int color) {
        this.key = key;
        this.power = power;
        this.color = color;
    }

    public boolean lava() {
        return this == MORTUM || this == HUNGER;
    }

    /** Цвет лески в мире (как на текстуре удочки). */
    public int line() {
        switch (this) {
            case REALMITE: return 0xC8D8E0;
            case ARLEMITE: return 0xA8F0A0;
            case RUPEE: return 0x90C8FF;
            case JACK: return 0xF8B050;
            case ARCANA: return 0xD0A0FF;
            case EDEN: return 0xFFD060;
            case WILDWOOD: return 0x60D8FF;
            case APALACHIA: return 0xF0A0FF;
            case SKYTHERN: return 0xFFFFFF;
            case GOLDEN: return 0xFFE060;
            case MORTUM: return 0xFF3030;
            case HUNGER: return 0x50FFD0;
            default: return 0x000000;
        }
    }

    /** Леска светится (не темнеет ночью и в пещерах). */
    public boolean glowingLine() {
        return this == ARCANA || this == SKYTHERN || this == MORTUM || this == HUNGER || this == GOLDEN;
    }

    /** Доля случаев, когда приманка не тратится. */
    public float baitSave() {
        return this == ARCANA ? 0.5F : this == GOLDEN ? 0.33F : this == HUNGER ? 0.4F : 0;
    }

    public float crateBonus() {
        return this == EDEN ? 0.10F : 0;
    }

    public float waitMultiplier() {
        return this == WILDWOOD ? 0.75F : 1.0F;
    }

    public float rareMultiplier() {
        return this == APALACHIA ? 1.5F : this == GOLDEN ? 1.3F : 1.0F;
    }

    public float doubleChance() {
        return this == SKYTHERN ? 0.2F : this == HUNGER ? 0.2F : 0;
    }

    public boolean hasSpecial() {
        return this == JACK || this == ARCANA || this == EDEN || this == WILDWOOD || this == APALACHIA || this == SKYTHERN
            || this == GOLDEN || this == MORTUM || this == HUNGER;
    }
}
