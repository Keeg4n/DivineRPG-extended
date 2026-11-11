package com.divinerpgextended.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** Струя воды Лиоплевродона: летит почти прямо, бьёт и отбрасывает; видна брызгами. */
public class EntityWaterJet extends EntityThrowable {

    private float damage = 12F;

    public EntityWaterJet(World w) {
        super(w);
    }

    public EntityWaterJet(World w, EntityLivingBase thrower, float damage) {
        super(w, thrower);
        this.damage = damage;
    }

    @Override
    protected float getGravityVelocity() {
        return 0.01F;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) for (int i = 0; i < 3; i++) worldObj.spawnParticle("splash", posX, posY, posZ, 0, 0, 0);
        if (ticksExisted > 60) setDead();
    }

    @Override
    protected void onImpact(MovingObjectPosition m) {
        if (worldObj.isRemote) return;
        if (m.entityHit != null && m.entityHit != getThrower()) {
            m.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, getThrower()), damage);
            m.entityHit.addVelocity(motionX * 0.4, 0.3, motionZ * 0.4);
            m.entityHit.velocityChanged = true;
        }
        playSound("random.splash", 0.6F, 1.2F);
        setDead();
    }
}
