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
    CADILLION_WILDWOOD(com.divinerpgextended.entity.EntityAncientGolem.class, -1), // страж рощи Дикого леса - Древний голем
    CADILLION_APALACHIA(com.divinerpgextended.entity.EntityWarriorKing.class, -1), // страж рощи Апалачии - Король-воин
    CADILLION_SKYTHERN(com.divinerpgextended.entity.EntityWindLord.class, -1), // страж рощи Скайтерна - Повелитель ветров
    TWILIGHT_DEMON(EntityTwilightDemon.class, -1),
    KAROT(EntityKarot.class, -1),
    CARCINUS(EntityCarcinus.class, -1),
    SNOW_GLACON(EntitySnowGlacon.class, -1),
    LICH_KING(EntityLichKing.class, -1),
    DRAMIX(net.divinerpg.entities.arcana.EntityDramix.class, -1),
    PARASECTA(net.divinerpg.entities.arcana.EntityParasecta.class, -1),
    // боссы арен Мортума (ворота Ветеи); близнецы - одной победой: засчитываются, когда пал последний из двоих
    ETERNAL_ARCHER(net.divinerpg.entities.twilight.EntityEternalArcher.class, -1),
    TWINS(net.divinerpg.entities.twilight.EntityDensos.class, -1),
    SOUL_FIEND(net.divinerpg.entities.twilight.EntitySoulFiend.class, -1),
    VAMACHERON(net.divinerpg.entities.twilight.EntityVamacheron.class, -1),
    /** Босс логова под храмом джунглей. */
    JUNGLE_KING(com.divinerpgextended.entity.EntityAncientDramcryx.class, -1),
    // три последних босса Ветеи (4 этаж); очередь Раглок - Крушитель - Луна держит снаряжение
    // ({@link com.divinerpgextended.vethea.VetheaCombat#gate}): Крушителя ранит только гелиозис с Раглока, Луну - арксиан с Крушителя
    WRECK(net.divinerpg.entities.vethea.EntityWreck.class, -1),
    RAGLOK(net.divinerpg.entities.vethea.EntityRaglok.class, -1),
    LADY_LUNA(net.divinerpg.entities.vethea.EntityLadyLuna.class, -1);

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
            boolean match = b == TWINS ? entity instanceof net.divinerpg.entities.twilight.EntityDensos || entity instanceof net.divinerpg.entities.twilight.EntityReyvor
                : b.entityClass.isInstance(entity);
            if (!match) continue;
            if (b.variant >= 0 && ((EntityGreatCadillion) entity).getVariant() != b.variant) continue;
            return b;
        }
        return null;
    }

    public static Boss cadillion(int variant) {
        return values()[CADILLION_EDEN.ordinal() + variant];
    }
}
