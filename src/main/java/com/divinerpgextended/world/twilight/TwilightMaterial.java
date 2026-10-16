package com.divinerpgextended.world.twilight;

import net.minecraft.entity.Entity;

/**
 * Материалы мобов сумеречных миров: у каждого из трёх мобов мира свой. Номер = мета предмета twilightMaterial.
 * Идут на Зов кадиллиона ({@link #call}) и закалку брони и лука мира ({@link #ofWorld}).
 */
public enum TwilightMaterial {

    SUN_FEATHER(TwilightWorld.EDEN, net.divinerpg.entities.twilight.EntityEdenCori.class),
    HORN_SHARD(TwilightWorld.EDEN, net.divinerpg.entities.twilight.EntityEdenCadillion.class),
    GREENFEET_ROOT(TwilightWorld.EDEN, net.divinerpg.entities.twilight.EntityGreenfeet.class),
    VEREK_FANG(TwilightWorld.WILDWOOD, net.divinerpg.entities.twilight.EntityVerek.class),
    MOON_HORN(TwilightWorld.WILDWOOD, net.divinerpg.entities.twilight.EntityWildwoodCadillion.class),
    MOSS_CORE(TwilightWorld.WILDWOOD, net.divinerpg.entities.twilight.EntityWildwoodGolem.class),
    GOLEM_HEART(TwilightWorld.APALACHIA, net.divinerpg.entities.twilight.EntityApalachiaGolem.class),
    DUSK_HORN(TwilightWorld.APALACHIA, net.divinerpg.entities.twilight.EntityApalachiaCadillion.class),
    WARRIOR_SIGIL(TwilightWorld.APALACHIA, net.divinerpg.entities.twilight.EntityApalachiaWarrior.class),
    SKY_ESSENCE(TwilightWorld.SKYTHERN, net.divinerpg.entities.twilight.EntitySkythernFiend.class),
    SKY_STRING(TwilightWorld.SKYTHERN, net.divinerpg.entities.twilight.EntitySkythernArcher.class),
    CLOUD_CORE(TwilightWorld.SKYTHERN, net.divinerpg.entities.twilight.EntitySkythernGolem.class),
    DARK_HORN(TwilightWorld.MORTUM, net.divinerpg.entities.twilight.EntityMortumCadillion.class),
    DEMON_HEART(TwilightWorld.MORTUM, net.divinerpg.entities.twilight.EntityMortumDemon.class),
    SOUL_SILK(TwilightWorld.MORTUM, net.divinerpg.entities.twilight.EntitySoulSpider.class);

    public final TwilightWorld world;
    public final Class<? extends Entity> mob;

    TwilightMaterial(TwilightWorld world, Class<? extends Entity> mob) {
        this.world = world;
        this.mob = mob;
    }

    public String key() {
        return name().toLowerCase();
    }

    /** Материал, который падает с этого моба, или null. */
    public static TwilightMaterial of(Entity e) {
        for (TwilightMaterial m : values()) if (m.mob == e.getClass()) return m;
        return null;
    }

    public static TwilightMaterial byMeta(int meta) {
        return values()[Math.max(0, Math.min(meta, values().length - 1))];
    }

    /** Три материала мира по порядку. */
    public static TwilightMaterial[] ofWorld(TwilightWorld w) {
        TwilightMaterial[] r = new TwilightMaterial[3];
        int i = 0;
        for (TwilightMaterial m : values()) if (m.world == w) r[i++] = m;
        return r;
    }

    /** Материал в Зове кадиллиона этого мира (рог или эссенция). */
    public static TwilightMaterial call(TwilightWorld w) {
        switch (w) {
            case EDEN: return HORN_SHARD;
            case WILDWOOD: return MOON_HORN;
            case APALACHIA: return DUSK_HORN;
            default: return SKY_ESSENCE;
        }
    }
}
