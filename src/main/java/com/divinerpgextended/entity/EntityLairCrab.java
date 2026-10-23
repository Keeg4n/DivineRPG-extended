package com.divinerpgextended.entity;

import net.divinerpg.libs.Sounds;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Норный краб: живёт в норе Карцинуса и нападает сам, в отличие от нейтральных крабов Divine. Ходит по дну
 * затопленных ходов, не тонет, чует игрока сквозь стены лабиринта.
 */
public class EntityLairCrab extends EntityMob {

    public EntityLairCrab(World world) {
        super(world);
        setSize(1.0F, 1.0F);
        experienceValue = 20;
        getNavigator().setCanSwim(true); // путь через воду, но без всплытия: краб идёт по дну
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 12.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, false));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(45.0D);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(6.0D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(24.0D);
    }

    @Override
    public boolean isAIEnabled() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        EntityLivingBase target = getAttackTarget();
        if (target == null) return;
        // в воде путь часто не строится: тогда просто ползём к цели и подпрыгиваем на уступах
        if (getNavigator().noPath()) getMoveHelper().setMoveTo(target.posX, target.posY, target.posZ, 1.0D);
        if (isCollidedHorizontally || (isInWater() && target.posY > posY + 1.0D)) getJumpHelper().setJumping();
    }

    @Override
    protected String getLivingSound() {
        return Sounds.getSoundName(Sounds.crab);
    }

    @Override
    protected String getHurtSound() {
        return Sounds.getSoundName(Sounds.crabHurt);
    }

    @Override
    protected String getDeathSound() {
        return Sounds.getSoundName(Sounds.crabHurt);
    }

    /** Метка краба, которого позвал Карцинус в бою: с него ничего не падает (иначе бой - ферма клешней). */
    public static final String CARCINUS_ADD = "dreCarcinusAdd";

    /** Позван Карцинусом - пустой. */
    public static boolean summoned(net.minecraft.entity.Entity e) {
        return e.getEntityData().getBoolean(CARCINUS_ADD);
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        if (summoned(this)) return;
        dropItem(VanillaItemsOther.crabClaw, rand.nextInt(2 + looting));
    }

    @Override
    protected int getExperiencePoints(net.minecraft.entity.player.EntityPlayer p) {
        return summoned(this) ? 0 : super.getExperiencePoints(p);
    }

    /** Норный король крабов: крупнее и злее, клешня падает всегда. */
    public static class King extends EntityLairCrab {

        public King(World world) {
            super(world);
            setSize(1.55F, 1.25F);
            experienceValue = 40;
        }

        @Override
        protected void applyEntityAttributes() {
            super.applyEntityAttributes();
            getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(100.0D);
            getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(9.0D);
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.27D);
        }

        @Override
        protected void dropFewItems(boolean byPlayer, int looting) {
            if (summoned(this)) return;
            dropItem(VanillaItemsOther.crabClaw, 1 + rand.nextInt(2 + looting));
        }
    }
}
