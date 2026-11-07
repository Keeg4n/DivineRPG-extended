package com.divinerpgextended.entity.mini;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.cold.ColdHandler;

/** Снежный ком Вьюжной ели и её снеговиков: немного урона и холод. Мобов не трогает, блоков не ломает. */
public class EntitySnowClump extends EntityThrowable {

    private float damage = 2F, cold = 6F;

    public EntitySnowClump(World w) {
        super(w);
    }

    public EntitySnowClump(World w, EntityLivingBase thrower, float damage, float cold) {
        super(w, thrower);
        this.damage = damage;
        this.cold = cold;
    }

    @Override
    protected float getGravityVelocity() {
        return 0.03F;
    }

    /** Попал в игрока. */
    public void strike(EntityPlayer p) {
        p.attackEntityFrom(DamageSource.causeThrownDamage(this, getThrower()), damage);
        ColdHandler.addCold(p, cold);
    }

    @Override
    protected void onImpact(MovingObjectPosition mop) {
        if (worldObj.isRemote) return;
        if (mop.entityHit instanceof EntityPlayer) strike((EntityPlayer) mop.entityHit);
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("snowballpoof", posX, posY, posZ, 10, 0.2, 0.2, 0.2, 0.05);
        setDead();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setFloat("Damage", damage);
        tag.setFloat("Cold", cold);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        damage = tag.getFloat("Damage");
        cold = tag.getFloat("Cold");
    }
}
