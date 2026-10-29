package com.divinerpgextended.easter;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Невидимое сиденье дивана: игрок сидит на нём, встал (или диван сломан) - сиденье исчезает. */
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
        return false; // не сохраняется: после перезахода игрок стоит
    }
}
