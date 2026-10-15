package com.divinerpgextended.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/**
 * Снаряды Ледяного Трона. Сосулька (тип 0) падает с купола и бьёт того, на кого упала; ледяная глыба (тип 1)
 * летит дугой и при ударе взрывается холодом: урон и сильное замедление вокруг, блоки не ломает.
 */
public class EntityIcicle extends EntityThrowable {

    public static final int ICICLE = 0, BOULDER = 1;
    private static final int DW_TYPE = 20;

    public EntityIcicle(World world) {
        super(world);
    }

    public EntityIcicle(World world, EntityLivingBase thrower, int type) {
        super(world, thrower);
        setType(type);
    }

    /** Сосулька, падающая с высоты в точку. */
    public EntityIcicle(World world, double x, double y, double z) {
        super(world, x, y, z);
        setType(ICICLE);
        motionX = motionZ = 0;
        motionY = -0.2;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_TYPE, Byte.valueOf((byte) 0));
    }

    public int getType() {
        return dataWatcher.getWatchableObjectByte(DW_TYPE);
    }

    private void setType(int t) {
        dataWatcher.updateObject(DW_TYPE, Byte.valueOf((byte) t));
    }

    @Override
    protected float getGravityVelocity() {
        return getType() == ICICLE ? 0.05F : 0.04F;
    }

    @Override
    protected void onImpact(MovingObjectPosition hit) {
        if (worldObj.isRemote) return;
        if (getType() == ICICLE) {
            if (hit.entityHit instanceof EntityLivingBase) {
                hit.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, getThrower()), 7.0F);
            }
            worldObj.playSoundAtEntity(this, "random.glass", 1.0F, 1.2F);
            FrostPowers.particles(worldObj, "snowshovel", posX, posY, posZ, 15, 0.3, 0.1);
        } else {
            worldObj.createExplosion(getThrower(), posX, posY, posZ, 2.0F, false);
            for (Object o : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(3.5, 2, 3.5))) {
                EntityLivingBase e = (EntityLivingBase) o;
                if (e == getThrower()) continue;
                e.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 80, 2));
            }
            FrostPowers.particles(worldObj, "snowshovel", posX, posY, posZ, 60, 1.2, 0.2);
        }
        setDead();
    }
}
