package com.divinerpgextended.magic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Ice shard of Frost ({@link EntitySummonFrost}): flies straight, arcane damage and slowness ({@link #SLOW} ticks) on hit; no explosion, does
 * not break blocks. Flies through the owner, the owner's summons and players spared from PvP. Lives {@link #LIFE} ticks, is not saved.
 */
public class EntityFrostShard extends EntityThrowable {

    public static final int SLOW = 40, LIFE = 40;
    private EntityPlayer owner;
    private float damage;

    public EntityFrostShard(World w) {
        super(w);
        setSize(0.25F, 0.25F);
    }

    public EntityFrostShard(World w, EntityPlayer owner, double x, double y, double z, float damage) {
        super(w, x, y, z);
        setSize(0.25F, 0.25F);
        this.owner = owner;
        this.damage = damage;
    }

    @Override
    protected float getGravityVelocity() {
        return 0F;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!worldObj.isRemote && ticksExisted > LIFE) setDead();
    }

    /** Whether the shard flies through: the owner, the owner's summons, other summons and players where PvP is not allowed. */
    boolean passes(Entity e) {
        if (owner == null || e == owner || e instanceof AnchorSummons.Summon || e instanceof EntityArcaneWraith) return true;
        return e instanceof EntityPlayer && com.divinerpgextended.perk.Protection.spare(owner, e);
    }

    @Override
    protected void onImpact(MovingObjectPosition hit) {
        if (worldObj.isRemote || isDead) return; // one hit per shard
        if (hit.entityHit != null) {
            if (passes(hit.entityHit)) return;
            if (hit.entityHit instanceof EntityLivingBase) {
                EntityLivingBase t = (EntityLivingBase) hit.entityHit;
                t.hurtResistantTime = 0;
                if (t.attackEntityFrom(ArcanaCombat.arcaneSource(owner, t), damage)) t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, SLOW, 1));
                AnchorSummons.mark(owner, t);
            }
        }
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("snowshovel", posX, posY, posZ, 8, 0.15, 0.15, 0.15, 0.05);
        worldObj.playSoundAtEntity(this, "dig.glass", 0.3F, 2.0F);
        setDead();
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound t) {
        return false;
    }
}
