package com.divinerpgextended.entity;

import java.util.List;

import net.divinerpg.libs.Sounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.BossDamage;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.CadillionWorld;

/**
 * Великий кадиллион - босс сумеречного мира (Eden, Wildwood, Apalachia, Skythern). Модель кадиллиона из Divine,
 * увеличенная в 2.5 раза. С него падает шестерня времени своего мира для Часов Странника.
 */
public class EntityGreatCadillion extends EntityMob implements IBossDisplayData {

    private static final int DW_VARIANT = 20;
    private int abilityTimer = 100;

    public EntityGreatCadillion(World world) {
        super(world);
        setSize(2.5F, 3.25F);
        experienceValue = 200;
        isImmuneToFire = true;
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_VARIANT, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.cadillionHealth[0]);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BossDamage.cadillionRawDamage(0));
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.8D);
    }

    public int getVariant() {
        return dataWatcher.getWatchableObjectByte(DW_VARIANT);
    }

    public CadillionWorld world() {
        return CadillionWorld.byMeta(getVariant());
    }

    /** Задать мир при призыве: меняет силу и восстанавливает здоровье. */
    public void setVariant(int variant) {
        dataWatcher.updateObject(DW_VARIANT, Byte.valueOf((byte) variant));
        getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(Config.cadillionHealth[Math.min(variant, Config.cadillionHealth.length - 1)]);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BossDamage.cadillionRawDamage(variant));
        setHealth(getMaxHealth());
    }

    @Override
    public boolean isAIEnabled() {
        return true;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected void fall(float distance) {}

    @Override
    public String getCommandSenderName() {
        return StatCollector.translateToLocal("dre.entity.great_cadillion." + world().key());
    }

    @Override
    protected String getLivingSound() {
        return Sounds.getSoundName(Sounds.cadillion);
    }

    @Override
    protected String getHurtSound() {
        return Sounds.getSoundName(Sounds.growlHurt);
    }

    @Override
    protected String getDeathSound() {
        return Sounds.getSoundName(Sounds.growlHurt);
    }

    @Override
    protected float getSoundPitch() {
        return 0.6F;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote || getAttackTarget() == null) return;
        if (--abilityTimer > 0) return;

        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(10, 6, 10));
        switch (world()) {
            case EDEN: // вспышка: ослепление
                for (EntityPlayer p : near) p.addPotionEffect(new PotionEffect(Potion.blindness.id, 80, 0));
                worldObj.playSoundAtEntity(this, "random.fizz", 2.0F, 0.5F);
                abilityTimer = 200;
                break;
            case WILDWOOD: // лианы: сильное замедление
                for (EntityPlayer p : near) p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 70, 3));
                worldObj.playSoundAtEntity(this, "dig.grass", 2.0F, 0.5F);
                abilityTimer = 160;
                break;
            case APALACHIA: // удар о землю: подбрасывает тех, кто рядом
                for (EntityPlayer p : near) {
                    if (p.getDistanceSqToEntity(this) > 64) continue;
                    p.motionY = 1.1D;
                    p.motionX += (p.posX - posX) * 0.15D;
                    p.motionZ += (p.posZ - posZ) * 0.15D;
                    p.velocityChanged = true;
                }
                worldObj.playSoundAtEntity(this, "random.explode", 1.5F, 0.6F);
                abilityTimer = 160;
                break;
            default: // Skythern: молния по цели
                EntityLivingBase t = getAttackTarget();
                worldObj.addWeatherEffect(new EntityLightningBolt(worldObj, t.posX, t.posY, t.posZ));
                abilityTimer = 240;
                break;
        }
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        entityDropItem(new ItemStack(ModItems.timeGear, 1, getVariant()), 0.5F);
        int souls = 4 + rand.nextInt(5 + looting);
        entityDropItem(new ItemStack(world().soul(), souls), 0.5F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setByte("Variant", (byte) getVariant());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        dataWatcher.updateObject(DW_VARIANT, Byte.valueOf(tag.getByte("Variant")));
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = super.attackEntityAsMob(target);
        if (hit) target.motionY += 0.4D;
        return hit;
    }
}
