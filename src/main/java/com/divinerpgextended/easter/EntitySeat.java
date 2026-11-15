package com.divinerpgextended.easter;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** An invisible sofa seat: the player sits on it, stood up (or the sofa is broken) - the seat disappears. */
public class EntitySeat extends Entity {

    public EntitySeat(World w) {
        super(w);
        setSize(0.0F, 0.0F);
        noClip = true;
    }

    public EntitySeat(World w, double x, double y, double z) {
        this(w);
        setPosition(x, y, z);
    }

    @Override
    protected void entityInit() {}

    @Override
    public double getMountedYOffset() {
        return 0.0;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) return;
        if (riddenByEntity == null || worldObj.getBlock((int) Math.floor(posX), (int) Math.floor(posY), (int) Math.floor(posZ)) != com.divinerpgextended.registry.ModBlocks.sofa) setDead();
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound t) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound t) {}

    @Override
    public boolean writeToNBTOptional(NBTTagCompound t) {
        return false; // not saved: after relogging the player is standing
    }
}
