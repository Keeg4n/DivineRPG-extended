package com.divinerpgextended.magic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Frost of the Sound of Carols ({@link AnchorSummons}): a flying snowflake, the only ranged summon. It does not rush at the target - it keeps
 * circling the owner and every {@link #SHOT_EVERY} ticks shoots an ice shard ({@link EntityFrostShard}) at the target it can see. The shard
 * deals arcane damage and slows; no explosions, blocks stay whole.
 */
public class EntitySummonFrost extends EntitySummonShark {

    public static final int SHOT_EVERY = 20;
    public static final float SHOT_SPEED = 1.0F;
    private int shot;

    public EntitySummonFrost(World w) {
        super(w);
        setSize(0.6F, 0.6F);
    }

    public EntitySummonFrost(World w, EntityPlayer owner) {
        super(w, owner);
        setSize(0.6F, 0.6F);
    }

    @Override
    public AnchorSummons.Kind kind() {
        return AnchorSummons.Kind.FROST;
    }

    @Override
    protected boolean ranged() {
        return true;
    }

    @Override
    protected double orbitRadius() {
        return 1.4;
    }

    @Override
    protected double orbitHeight() {
        return 2.4;
    }

    @Override
    protected void chase(Entity t, EntityPlayer o) {
        if (--shot > 0 || !canEntityBeSeen(t)) return;
        shot = SHOT_EVERY;
        double sx = posX, sy = posY + height / 2, sz = posZ;
        EntityFrostShard s = new EntityFrostShard(worldObj, o, sx, sy, sz, AnchorSummons.damage(o, kind()));
        s.setThrowableHeading(t.posX - sx, t.posY + t.height * 0.5 - sy, t.posZ - sz, SHOT_SPEED, 0F);
        worldObj.spawnEntityInWorld(s);
        worldObj.playSoundAtEntity(this, "dig.glass", 0.35F, 1.8F);
    }
}
