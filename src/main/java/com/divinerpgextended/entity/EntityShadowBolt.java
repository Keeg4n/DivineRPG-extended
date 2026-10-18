package com.divinerpgextended.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** Теневой снаряд Демона сумерек: летит прямо (без тяжести), бьёт магией и иссушает; блоки не ломает. */
public class EntityShadowBolt extends EntityThrowable {

    public static final float DAMAGE = 7;

    public EntityShadowBolt(World w) {
        super(w);
    }

    public EntityShadowBolt(World w, EntityLivingBase thrower) {
        super(w, thrower);
    }

    @Override
    protected float getGravityVelocity() {
        return 0;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) worldObj.spawnParticle("witchMagic", posX, posY, posZ, 0, 0, 0);
        if (ticksExisted > 100) setDead();
    }

    @Override
    protected void onImpact(MovingObjectPosition hit) {
        if (worldObj.isRemote) return;
        if (hit.entityHit instanceof EntityLivingBase && hit.entityHit != getThrower()) {
            EntityLivingBase v = (EntityLivingBase) hit.entityHit;
            v.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, getThrower()), DAMAGE);
            v.addPotionEffect(new PotionEffect(Potion.wither.id, 50, 0));
        }
        ((net.minecraft.world.WorldServer) worldObj).func_147487_a("portal", posX, posY, posZ, 20, 0.3, 0.3, 0.3, 0.3);
        setDead();
    }
}
