package com.divinerpgextended.magic;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Слизень слизневого меча ({@link AnchorSummons}): первый призыв - прыгает по земле за хозяином, бьёт его цель и ближних
 * врагов (радиус агра {@link AnchorSummons.Kind#SLIME} - самый маленький), держится на 1 аркане в секунду.
 */
public class EntitySummonSlime extends EntitySummonCrab {

    public EntitySummonSlime(World w) {
        super(w);
        setSize(0.6F, 0.6F);
    }

    public EntitySummonSlime(World w, EntityPlayer owner) {
        super(w, owner);
        setSize(0.6F, 0.6F);
    }

    @Override
    public AnchorSummons.Kind kind() {
        return AnchorSummons.Kind.SLIME;
    }

    @Override
    protected void onHit(EntityLivingBase t, EntityPlayer o) {
        worldObj.playSoundAtEntity(this, "mob.slime.attack", 0.5F, 1.2F);
    }

    /** Ходит прыжками, как слизень. */
    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        if (!worldObj.isRemote && !isDead && onGround && !getNavigator().noPath() && ticksExisted % 8 == 0) {
            getJumpHelper().setJumping();
            worldObj.playSoundAtEntity(this, "mob.slime.small", 0.25F, 1.3F);
        }
    }
}
