package com.divinerpgextended.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.divinerpgextended.boss.WatcherHunt;

/**
 * The Watcher's Servant Eye: a small flying eye. Flies to a fighter in the lair through blocks - reaches even behind a column, where
 * they hide from the boss's gaze - and bites up close. Lives while its Watcher lives (without its master it disperses), no loot or experience, is not saved in the world.
 * Looks like a shrunken Divine Watcher with a crimson tint.
 */
public class EntityWatcherEye extends EntityFlying implements IMob {

    public static final float BITE = 4F;
    public static final double SPEED = 0.38;
    public static final int BITE_EVERY = 20;
    /** The master is in memory only: after a world reload the servant has no master and disperses. */
    private Entity owner;
    private int cooldown, orphan;
    /** Bites per lifetime (for checks). */
    public int bites;

    public EntityWatcherEye(World w) {
        super(w);
        setSize(0.8F, 0.8F);
        isImmuneToFire = true;
        experienceValue = 0;
    }

    public EntityWatcherEye(World w, Entity owner) {
        this(w);
        this.owner = owner;
    }

    public Entity owner() {
        return owner;
    }

    @Override
    public void onUpdate() {
        noClip = true; // through blocks
        super.onUpdate();
    }

    @Override
    public boolean isEntityInsideOpaqueBlock() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(12);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(48);
    }

    @Override
    protected void updateEntityActionState() {
        if (worldObj.isRemote) return;
        if (owner == null || owner.isDead || owner.worldObj != worldObj || getDistanceSqToEntity(owner) > 64 * 64) {
            if (++orphan > 40) {
                ((net.minecraft.world.WorldServer) worldObj).func_147487_a("smoke", posX, posY + 0.4, posZ, 8, 0.2, 0.2, 0.2, 0.02);
                setDead();
            }
            return;
        }
        orphan = 0;
        EntityPlayer t = WatcherHunt.nearestFighter(worldObj, this);
        double tx, ty, tz;
        if (t != null) {
            tx = t.posX;
            ty = t.posY + t.getEyeHeight() * 0.6;
            tz = t.posZ;
        } else { // no one to attack - circles around the master
            double a = ticksExisted * 0.08 + getEntityId();
            tx = owner.posX + Math.cos(a) * 4;
            ty = owner.posY + owner.height * 0.5;
            tz = owner.posZ + Math.sin(a) * 4;
        }
        double dx = tx - posX, dy = ty - (posY + height / 2), dz = tz - posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > 0.01) {
            double gx = tx, gy = ty, gz = tz;
            double ex = gx - posX, ey = gy - (posY + height / 2), ez = gz - posZ, e = Math.max(0.01, Math.sqrt(ex * ex + ey * ey + ez * ez));
            double vx = ex / e * SPEED, vy = ey / e * SPEED, vz = ez / e * SPEED;
            motionX = motionX * 0.6 + vx * 0.4;
            motionY = motionY * 0.6 + vy * 0.4;
            motionZ = motionZ * 0.6 + vz * 0.4;
            rotationYaw = renderYawOffset = rotationYawHead = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
            rotationPitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        }
        if (--cooldown <= 0 && t != null && d < 1.6) {
            cooldown = BITE_EVERY;
            bites++;
            t.attackEntityFrom(DamageSource.causeMobDamage(this), BITE);
            motionX -= dx / d * 0.5; // bounce back after a bite
            motionZ -= dz / d * 0.5;
            worldObj.playSoundAtEntity(this, "mob.slime.attack", 0.6F, 1.6F);
        }
    }

    @Override
    protected String getLivingSound() {
        return "mob.ghast.moan";
    }

    @Override
    protected String getHurtSound() {
        return "mob.slime.small";
    }

    @Override
    protected String getDeathSound() {
        return "mob.ghast.death";
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected float getSoundPitch() {
        return 1.8F + rand.nextFloat() * 0.3F;
    }

    @Override
    public int getTalkInterval() {
        return 200;
    }

    @Override
    protected Item getDropItem() {
        return null;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    /** Not saved: the servant lives only with its master. */
    @Override
    public boolean writeToNBTOptional(NBTTagCompound t) {
        return false;
    }
}
