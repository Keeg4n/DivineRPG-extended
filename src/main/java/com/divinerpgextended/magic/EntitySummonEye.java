package com.divinerpgextended.magic;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Око циклопического жезла ({@link AnchorSummons}): спутник над головой хозяина - летает сквозь блоки, как призрак, бьёт
 * слабее всех призывов, зато каждый удар отбрасывает врага от хозяина ({@link AnchorSummons#EYE_PUSH}; боссов - слабее,
 * стойких к отбросу - по их стойкости). Держится на 1 аркане в секунду.
 */
public class EntitySummonEye extends EntitySummonShark {

    public EntitySummonEye(World w) {
        super(w);
        setSize(0.5F, 0.5F);
    }

    public EntitySummonEye(World w, EntityPlayer owner) {
        super(w, owner);
        setSize(0.5F, 0.5F);
    }

    @Override
    public AnchorSummons.Kind kind() {
        return AnchorSummons.Kind.EYE;
    }

    @Override
    protected double orbitRadius() {
        return 1.0;
    }

    @Override
    protected double orbitHeight() {
        return 2.6;
    }

    /** Отброс от хозяина: держит врагов на расстоянии. */
    @Override
    protected void onHit(EntityLivingBase t, EntityPlayer o) {
        push(t, o.posX, o.posZ, posX, posZ);
        worldObj.playSoundAtEntity(this, "mob.ghast.fireball", 0.3F, 1.8F);
    }

    /** Толчок цели прочь от точки (ox, oz); стоит на ней - прочь от ока (ex, ez). @return сила толчка */
    public static double push(EntityLivingBase t, double ox, double oz, double ex, double ez) {
        double dx = t.posX - ox, dz = t.posZ - oz, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            dx = t.posX - ex;
            dz = t.posZ - ez;
            len = Math.max(0.05, Math.sqrt(dx * dx + dz * dz));
        }
        double res = t.getEntityAttribute(SharedMonsterAttributes.knockbackResistance) == null ? 0
            : t.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue();
        boolean boss = t instanceof net.minecraft.entity.boss.IBossDisplayData || t instanceof net.divinerpg.entities.base.EntityDivineRPGBoss;
        double p = AnchorSummons.EYE_PUSH * Math.max(0, 1 - res) * (boss ? AnchorSummons.EYE_PUSH_BOSS : 1);
        if (p <= 0) return 0;
        t.motionX += dx / len * p;
        t.motionZ += dz / len * p;
        t.motionY = Math.max(t.motionY, 0.3 * p / AnchorSummons.EYE_PUSH);
        t.isAirBorne = true;
        t.velocityChanged = true;
        return p;
    }
}
