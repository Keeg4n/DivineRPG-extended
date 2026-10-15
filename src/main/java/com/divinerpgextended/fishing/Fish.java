package com.divinerpgextended.fishing;

import net.minecraft.potion.Potion;

/** Рыба мода: где ловится, сытость, эффект при поедании, квестовая ли (задания Леорны), форма и цвет иконки. */
public enum Fish {

    SEA_BASS(Zone.OCEAN, 4, 0.4F, null, 0, 0, false, "cod", 0x5A7FA8),
    CORAL_WRASSE(Zone.OCEAN, 3, 0.3F, Potion.waterBreathing, 1200, 0, false, "clownfish", 0xF07A5A),
    SHARK_PUP(Zone.OCEAN, 2, 0.2F, null, 0, 0, true, "salmon", 0x8A9AA8),
    RUFF(Zone.FRESH, 3, 0.3F, null, 0, 0, false, "cod", 0x8A8A5A),
    GOLD_CRUCIAN(Zone.FRESH, 4, 0.4F, null, 0, 0, true, "clownfish", 0xF0C030),
    ICE_TROUT(Zone.SNOW, 4, 0.4F, null, 0, 0, false, "salmon", 0xA8D8F0),
    SNOW_BURBOT(Zone.ICEIKA, 5, 0.5F, null, 0, 0, false, "cod", 0xE8F4FF),
    CRYSTAL_FISH(Zone.ICEIKA, 2, 0.2F, null, 0, 0, true, "pufferfish", 0x9AF0FF),
    PIRANHA(Zone.JUNGLE, 3, 0.3F, Potion.damageBoost, 400, 0, false, "salmon", 0x6AA040),
    EMERALD_CATFISH(Zone.JUNGLE, 2, 0.2F, null, 0, 0, true, "cod", 0x30D080),
    SAND_CARP(Zone.DESERT, 4, 0.4F, null, 0, 0, false, "cod", 0xD8C080),
    MUMMY_FISH(Zone.DESERT, 2, 0.2F, null, 0, 0, true, "pufferfish", 0xC8B898),
    CAVEFISH(Zone.CAVE, 3, 0.3F, Potion.nightVision, 1800, 0, false, "salmon", 0xC8C8D0),
    OBSIDIFISH(Zone.HELL, 4, 0.4F, Potion.fireResistance, 1800, 0, false, "cod", 0x3A2050),
    FLAREFIN(Zone.HELL, 5, 0.5F, Potion.damageBoost, 600, 0, false, "clownfish", 0xFF6A20),
    EMBER_FRY(Zone.HELL, 2, 0.2F, null, 0, 0, true, "pufferfish", 0xFFA040),
    ARCANE_EEL(Zone.ARCANA, 5, 0.5F, Potion.regeneration, 200, 0, false, "salmon", 0x9A6AE0),
    STARGAZER(Zone.ARCANA, 2, 0.2F, null, 0, 0, true, "pufferfish", 0x6A8AFF),
    EDEN_GOLDFISH(Zone.EDEN, 6, 0.6F, Potion.digSpeed, 1200, 0, false, "clownfish", 0xF8E050),
    WILDWOOD_GLOWFISH(Zone.WILDWOOD, 6, 0.6F, Potion.regeneration, 300, 0, false, "salmon", 0x40B0E0),
    APALACHIA_RAY(Zone.APALACHIA, 6, 0.6F, Potion.jump, 1200, 1, false, "cod", 0xC060F0),
    SKYTHERN_FLYER(Zone.SKYTHERN, 6, 0.6F, Potion.moveSpeed, 1200, 0, false, "salmon", 0xE8F0F8),
    MORTUM_EEL(Zone.MORTUM, 7, 0.7F, Potion.nightVision, 1200, 0, false, "cod", 0x4A2A2A),
    DREAM_CARP(Zone.DREAM, 8, 1.0F, null, 0, 0, false, "clownfish", 0x50E0B0);

    public final Zone zone;
    public final int food;
    public final float saturation;
    public final Potion effect;
    public final int duration, amplifier;
    public final boolean quest;
    public final String shape;
    public final int color;

    Fish(Zone zone, int food, float saturation, Potion effect, int duration, int amplifier, boolean quest, String shape,
        int color) {
        this.zone = zone;
        this.food = food;
        this.saturation = saturation;
        this.effect = effect;
        this.duration = duration;
        this.amplifier = amplifier;
        this.quest = quest;
        this.shape = shape;
        this.color = color;
    }

    public String key() {
        return name().toLowerCase();
    }
}
