package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.perk.PerkEffects;

/**
 * Взрывная снежинка Снежного Глакона: небольшой взрыв без разрушения блоков и замедление. Рядом с игроком в
 * огненной броне (Inferno, Netherite или Странник с огнезащитой) снежинка тает на подлёте и ничего не делает.
 */
public class EntityFrostCharge extends EntityThrowable {

    public EntityFrostCharge(World world) {
        super(world);
    }

    public EntityFrostCharge(World world, EntityLivingBase thrower) {
        super(world, thrower);
    }

    @Override
    protected float getGravityVelocity() {
        return 0.02F;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote || isDead) return;
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(2.0, 2.0, 2.0));
        for (EntityPlayer p : near) {
            if (PerkEffects.wearsFireArmor(p)) {
                melt();
                return;
            }
        }
    }

    private void melt() {
        worldObj.playSoundAtEntity(this, "random.fizz", 0.7F, 1.6F);
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("cloud", posX, posY, posZ, 6, 0.2, 0.2, 0.2, 0.02);
        }
        setDead();
    }

    @Override
    protected void onImpact(MovingObjectPosition hit) {
        if (worldObj.isRemote) return;
        if (hit.entityHit instanceof EntityPlayer && PerkEffects.wearsFireArmor((EntityPlayer) hit.entityHit)) {
            melt();
            return;
        }
        if (hit.entityHit instanceof EntityLivingBase && hit.entityHit != getThrower()) {
            hit.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, getThrower()), 4.0F);
            ((EntityLivingBase) hit.entityHit).addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
        }
        worldObj.createExplosion(getThrower(), posX, posY, posZ, 1.3F, false);
        setDead();
    }
}
