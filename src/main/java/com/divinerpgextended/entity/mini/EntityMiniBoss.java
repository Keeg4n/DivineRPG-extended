package com.divinerpgextended.entity.mini;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.boss.BossMinions;
import com.divinerpgextended.world.twilight.TwilightCrafts;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Мини-босс мира: сильнее обычных мобов, но слабее стража (здоровье не больше x3 от обычного моба), со своим приёмом. На прогресс
 * не влияет и полоски босса у него нет: имя и здоровье видны над головой. Как обычный моб исчезает, если игрок ушёл далеко.
 */
public abstract class EntityMiniBoss extends EntityMob {

    private final boolean flies;

    protected EntityMiniBoss(World w, float width, float height, boolean flies) {
        super(w);
        this.flies = flies;
        setSize(width, height);
        experienceValue = 60;
        isImmuneToFire = true;
        tasks.addTask(0, new EntityAISwimming(this));
        if (!flies) {
            tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
            tasks.addTask(5, new EntityAIWander(this, 0.8D));
        }
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 16F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, false));
        setAlwaysRenderNameTag(true);
    }

    /** Ключ в названиях и сообщениях. Зовётся и из конструктора родителя: только постоянные значения. */
    public abstract String key();

    protected abstract double baseHealth();

    protected abstract double baseDamage();

    protected double baseSpeed() {
        return 0.28D;
    }

    /** Добыча: всегда на землю. Публично для автотеста. */
    public abstract void loot(int looting);

    /** Цвет модели: можно затемнить, чтобы показать состояние. */
    public float shade() {
        return 1F;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(baseHealth());
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(baseDamage());
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(baseSpeed());
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.6D);
    }

    @Override
    public boolean isAIEnabled() {
        return true;
    }

    @Override
    protected void fall(float distance) {}

    /** Имя над головой - без здоровья: его показывают моды индикаторов здоровья. */
    @Override
    public String getCommandSenderName() {
        return StatCollector.translateToLocal("dre.entity.mini." + key());
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        if (byPlayer) loot(looting);
    }

    @Override
    protected String getLivingSound() {
        return "mob.wither.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.wither.death";
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    protected float getSoundPitch() {
        return 1.2F;
    }

    /** Цель, если это живой игрок рядом с тем, что видит ИИ. */
    protected EntityPlayer targetPlayer() {
        EntityLivingBase t = getAttackTarget();
        return t instanceof EntityPlayer && t.isEntityAlive() ? (EntityPlayer) t : null;
    }

    /** Лететь к точке: тяга гасится, скорость ограничена. */
    protected void steer(double x, double y, double z, double accel, double top) {
        motionX = clamp(motionX * 0.9 + Math.signum(x - posX) * Math.min(accel, Math.abs(x - posX) * 0.05), top);
        motionZ = clamp(motionZ * 0.9 + Math.signum(z - posZ) * Math.min(accel, Math.abs(z - posZ) * 0.05), top);
        motionY = clamp(motionY * 0.7 + 0.08 + (y - posY) * 0.03, top);
        fallDistance = 0;
    }

    private static double clamp(double v, double top) {
        return Math.max(-top, Math.min(top, v));
    }

    /** Позвать мобов мира рядом: без добычи и опыта, гибнут вместе с мини-боссом. Сколько появилось. */
    protected int call(Class<? extends EntityLiving> kind, int count) {
        int made = 0;
        for (int i = 0; i < count; i++) {
            try {
                EntityLiving m = kind.getConstructor(World.class).newInstance(worldObj);
                double a = rand.nextDouble() * Math.PI * 2;
                boolean air = m instanceof net.minecraft.entity.EntityFlying;
                m.setLocationAndAngles(posX + Math.cos(a) * 4, posY + (air ? 4 : 0.5), posZ + Math.sin(a) * 4, rand.nextFloat() * 360, 0);
                if (!air && !worldObj.getCollidingBoundingBoxes(m, m.boundingBox).isEmpty()) continue;
                m.setAttackTarget(getAttackTarget());
                BossMinions.mark(m, this);
                worldObj.spawnEntityInWorld(m);
                if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("portal", m.posX, m.posY + 1, m.posZ, 20, 0.5, 0.8, 0.5, 0.1);
                made++;
            } catch (Exception ignored) {
                // у моба нет конструктора (World)
            }
        }
        return made;
    }

    /** Сообщение всем рядом. */
    protected void tell(String key) {
        for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(32, 16, 32))) ((EntityPlayer) o).addChatMessage(new ChatComponentTranslation(key));
    }

    /** Добыча сумеречного мира: камни и души мира, иногда кусок. */
    protected void twilightLoot(TwilightWorld w, int looting) {
        entityDropItem(new ItemStack(w.gem(), 2 + rand.nextInt(2)), 0.5F);
        entityDropItem(new ItemStack(w.soul(), 3 + rand.nextInt(3 + looting)), 0.5F);
        if (rand.nextInt(4) == 0) entityDropItem(new ItemStack(TwilightCrafts.chunk(w)), 0.5F);
    }
}
