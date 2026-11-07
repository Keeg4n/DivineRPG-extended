package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.world.twilight.WorldWeather;

/**
 * Глыба камнепада Апалачии: падает с высоты {@link #DROP}, пока летит, на земле видна метка. Блоков не ставит и не ломает. Ранит только
 * тех, у кого над головой открытое небо; урон - как от падающего блока, поэтому сет Апалачии его не чувствует.
 */
public class EntityRockfall extends EntityThrowable {

    public static final int DROP = 18;
    public static final double RADIUS = 2.2;
    public static final float DAMAGE = 24F;
    private int ground = -1;

    public EntityRockfall(World w) {
        super(w);
        setSize(1.0F, 1.0F);
    }

    public EntityRockfall(World w, double x, double y, double z, int ground) {
        super(w, x, y, z);
        setSize(1.0F, 1.0F);
        this.ground = ground;
        motionX = motionZ = 0;
        motionY = -0.1;
    }

    @Override
    protected float getGravityVelocity() {
        return 0.05F;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!worldObj.isRemote && ground >= 0 && ticksExisted % 3 == 0 && worldObj instanceof WorldServer)
            ((WorldServer) worldObj).func_147487_a("crit", posX, ground + 0.15, posZ, 8, RADIUS / 2, 0.02, RADIUS / 2, 0);
    }

    @Override
    protected void onImpact(MovingObjectPosition mop) {
        if (worldObj.isRemote) return;
        strike();
        setDead();
    }

    /** Удар о землю. */
    public void strike() {
        if (worldObj instanceof WorldServer) {
            int stone = net.minecraft.block.Block.getIdFromBlock(net.divinerpg.utils.blocks.TwilightBlocks.twilightStone);
            ((WorldServer) worldObj).func_147487_a("blockcrack_" + stone + "_0", posX, posY + 0.5, posZ, 30, 0.8, 0.4, 0.8, 0.2);
        }
        worldObj.playSoundAtEntity(this, "dig.stone", 2.0F, 0.5F);
        worldObj.playSoundAtEntity(this, "random.explode", 0.5F, 1.5F);
        for (Object o : worldObj.getEntitiesWithinAABB(com.divinerpgextended.entity.mini.EntityStonebreaker.class, boundingBox.expand(RADIUS, RADIUS, RADIUS)))
            ((com.divinerpgextended.entity.mini.EntityStonebreaker) o).breakSkin();
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(RADIUS, RADIUS, RADIUS));
        for (EntityPlayer p : near) {
            if (p.getDistanceToEntity(this) > RADIUS + 0.5 || !WorldWeather.open(p)) continue;
            p.attackEntityFrom(DamageSource.fallingBlock, DAMAGE);
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("Ground", ground);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        ground = tag.getInteger("Ground");
    }
}
