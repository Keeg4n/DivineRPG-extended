package com.divinerpgextended.magic;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Small scorcher of the Scorcher Brand ({@link AnchorSummons}): runs on the ground after the owner like the crab, melee only, every hit
 * sets the target on fire for {@link #BURN} seconds. Does not burn itself.
 */
public class EntitySummonScorcher extends EntitySummonCrab {

    public static final int BURN = 4;

    public EntitySummonScorcher(World w) {
        super(w);
        setSize(0.6F, 0.9F);
        isImmuneToFire = true;
    }

    public EntitySummonScorcher(World w, EntityPlayer owner) {
        super(w, owner);
        setSize(0.6F, 0.9F);
        isImmuneToFire = true;
    }

    @Override
    public AnchorSummons.Kind kind() {
        return AnchorSummons.Kind.SCORCHER;
    }

    @Override
    protected void onHit(EntityLivingBase t, EntityPlayer o) {
        t.setFire(BURN);
        worldObj.playSoundAtEntity(this, "mob.blaze.hit", 0.4F, 1.5F);
    }
}
