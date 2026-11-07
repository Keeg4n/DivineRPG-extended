package com.divinerpgextended.entity.mini;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Снеговик Вьюжной ели: ванильный снеговик, который бросает комья не в чудовищ, а в игроков; каждый ком добавляет холод. */
public class EntityBlizzardSnowman extends EntitySnowman implements IMob {

    public static final float CLUMP_DAMAGE = 2F, CLUMP_COLD = 8F;

    public EntityBlizzardSnowman(World w) {
        super(w);
        targetTasks.taskEntries.clear();
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, false));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20D);
    }

    @Override
    public void attackEntityWithRangedAttack(EntityLivingBase t, float power) {
        EntitySnowClump c = new EntitySnowClump(worldObj, this, CLUMP_DAMAGE, CLUMP_COLD);
        double dx = t.posX - posX, dy = t.posY + t.getEyeHeight() - 1.1 - c.posY, dz = t.posZ - posZ;
        c.setThrowableHeading(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6F, 8F);
        playSound("random.bow", 1.0F, 0.6F);
        worldObj.spawnEntityInWorld(c);
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {}
}
