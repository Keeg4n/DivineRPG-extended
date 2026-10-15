package com.divinerpgextended.boss;

import net.divinerpg.entities.twilight.EntityKarot;
import net.divinerpg.entities.twilight.EntityTwilightDemon;
import net.divinerpg.entities.vanilla.EntityAncientEntity;
import net.divinerpg.entities.vanilla.EntityJungleDramcryx;
import net.divinerpg.entities.vanilla.EntityKingOfScorchers;
import net.divinerpg.entities.vanilla.EntityTheWatcher;
import net.minecraft.entity.Entity;

import com.divinerpgextended.entity.EntityCarcinus;
import com.divinerpgextended.entity.EntityGreatCadillion;
import com.divinerpgextended.entity.EntityLichKing;
import com.divinerpgextended.entity.EntitySnowGlacon;

/**
 * Боссы (и "хозяева" биомов), убийство которых открывает улучшения. Порядок не менять: прогресс игрока хранится
 * битами по номеру. Новые боссы добавлять только в конец.
 */
public enum Boss {

    ANCIENT_ENTITY(EntityAncientEntity.class, -1),
    WATCHER(EntityTheWatcher.class, -1),
    KING_OF_SCORCHERS(EntityKingOfScorchers.class, -1),
    JUNGLE_DRAMCRYX(EntityJungleDramcryx.class, -1),
    CADILLION_EDEN(EntityGreatCadillion.class, 0),
    CADILLION_WILDWOOD(EntityGreatCadillion.class, 1),
    CADILLION_APALACHIA(EntityGreatCadillion.class, 2),
    CADILLION_SKYTHERN(EntityGreatCadillion.class, 3),
    TWILIGHT_DEMON(EntityTwilightDemon.class, -1),
    KAROT(EntityKarot.class, -1),
    CARCINUS(EntityCarcinus.class, -1),
    SNOW_GLACON(EntitySnowGlacon.class, -1),
    LICH_KING(EntityLichKing.class, -1);

    private final Class<? extends Entity> entityClass;
    /** Для великого кадиллиона - номер мира, иначе -1. */
    private final int variant;

    Boss(Class<? extends Entity> entityClass, int variant) {
        this.entityClass = entityClass;
        this.variant = variant;
    }

    public String langKey() {
        return "dre.boss." + name().toLowerCase();
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static Boss of(Entity entity) {
        for (Boss b : values()) {
            if (!b.entityClass.isInstance(entity)) continue;
            if (b.variant >= 0 && ((EntityGreatCadillion) entity).getVariant() != b.variant) continue;
            return b;
        }
        return null;
    }

    public static Boss cadillion(int variant) {
        return values()[CADILLION_EDEN.ordinal() + variant];
    }
}
