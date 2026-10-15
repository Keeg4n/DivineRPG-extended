package com.divinerpgextended.entity;

import java.util.List;

import net.divinerpg.entities.vanilla.EntityFrost;
import net.divinerpg.entities.vanilla.EntityGlacon;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
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
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.Config;

/**
 * Снежный Глакон: кидает взрывные снежинки, призывает маленьких Глаконов и летающих Фростов (не больше шести
 * помощников разом - босс несложный, серьёзный ледяной босс будет в самой Iceika). Боится адского: огонь бьёт
 * его втрое сильнее, а подожжённый он в панике бежит и не атакует (после паники 10 секунд огонь на нём гаснет сам).
 * Адский лук (Inferno) бьёт вдвое сильнее, оглушает и поджигает. С него падает Снежный шар - единственный
 * способ зажечь портал в Iceika.
 */
public class EntitySnowGlacon extends EntityMob implements IBossDisplayData {

    private int throwTimer = 60, summonTimer = 200, frostTimer = 300;
    private int panicLeft, fireCooldown, stunLeft;

    public EntitySnowGlacon(World world) {
        super(world);
        setSize(1.8F, 3.2F);
        experienceValue = 150;
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        tasks.addTask(5, new EntityAIWander(this, 0.7D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.snowGlaconHealth);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(Config.snowGlaconDamage);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(32.0D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.7D);
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
        return StatCollector.translateToLocal("dre.entity.snow_glacon");
    }

    @Override
    protected String getLivingSound() {
        return "mob.irongolem.walk";
    }

    @Override
    protected String getHurtSound() {
        return "dig.glass";
    }

    @Override
    protected String getDeathSound() {
        return "random.glass";
    }

    @Override
    protected float getSoundPitch() {
        return 0.7F;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        if (fireCooldown > 0) fireCooldown--;
        if (isBurning() && panicLeft == 0) {
            if (fireCooldown == 0) {
                panicLeft = 80;
                fireCooldown = 80 + 200;
                worldObj.playSoundAtEntity(this, "random.fizz", 2.0F, 0.6F);
                tell("dre.chat.glacon_panic");
            } else {
                extinguish(); // лёд ещё не прогрелся заново: огонь гаснет
                worldObj.playSoundAtEntity(this, "random.fizz", 1.0F, 1.4F);
            }
        }
        if (panicLeft > 0) {
            panicLeft--;
            flee();
            return;
        }
        if (stunLeft > 0) {
            stunLeft--;
            getNavigator().clearPathEntity();
            motionX = motionZ = 0;
            if (worldObj instanceof WorldServer && stunLeft % 5 == 0) ((WorldServer) worldObj)
                .func_147487_a("crit", posX, posY + height + 0.3, posZ, 4, 0.4, 0.1, 0.4, 0.05);
            return;
        }
        EntityLivingBase target = getAttackTarget();
        // призыв идёт, пока рядом игрок (в том числе в творческом, когда цели у босса нет)
        if (target == null && worldObj.getClosestPlayerToEntity(this, 24) == null) return;
        boolean enraged = getHealth() / getMaxHealth() <= 0.5F;

        if (--throwTimer <= 0 && target != null && getDistanceSqToEntity(target) < 30 * 30) {
            for (int i = 0; i < 3; i++) {
                EntityFrostCharge charge = new EntityFrostCharge(worldObj, this);
                double dx = target.posX - posX, dy = target.posY + target.getEyeHeight() - charge.posY,
                    dz = target.posZ - posZ;
                charge.setThrowableHeading(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.1, dz, 1.1F, 6.0F + i * 4);
                worldObj.spawnEntityInWorld(charge);
            }
            worldObj.playSoundAtEntity(this, "random.bow", 1.0F, 0.6F);
            throwTimer = enraged ? 40 : 80;
        }
        if (--summonTimer <= 0) {
            for (int i = 0; i < 2 && helpers() < 6; i++) summon(new EntityGlacon(worldObj), 0);
            summonTimer = enraged ? 150 : 300;
        }
        if (--frostTimer <= 0) {
            if (helpers() < 6) summon(new EntityFrost(worldObj), 2.5);
            frostTimer = enraged ? 260 : 400;
        }
    }

    /** Паника: убегает от ближайшего игрока, не бьёт и не колдует. */
    private void flee() {
        setAttackTarget(null);
        if (panicLeft % 10 != 0) return;
        Entity from = worldObj.getClosestPlayerToEntity(this, 24);
        if (from == null) return;
        Vec3 away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this, 12, 4,
            Vec3.createVectorHelper(from.posX, from.posY, from.posZ));
        if (away != null) getNavigator().tryMoveToXYZ(away.xCoord, away.yCoord, away.zCoord, 1.4D);
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj)
            .func_147487_a("splash", posX, posY + height * 0.6, posZ, 12, 0.5, 0.6, 0.5, 0.1);
    }

    @Override
    public boolean attackEntityAsMob(Entity entity) {
        if (panicLeft > 0 || stunLeft > 0) return false;
        return super.attackEntityAsMob(entity);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source.isFireDamage()) amount *= 3.0F;
        Entity attacker = source.getEntity();
        if (!worldObj.isRemote && source.isProjectile() && attacker instanceof EntityPlayer
            && ((EntityPlayer) attacker).getHeldItem() != null
            && ((EntityPlayer) attacker).getHeldItem()
                .getItem() == VanillaItemsWeapons.infernoBow) {
            amount *= 2.0F;
            stunLeft = 30;
            if (fireCooldown == 0) setFire(4);
            worldObj.playSoundAtEntity(this, "random.glass", 1.5F, 0.5F);
        }
        return super.attackEntityFrom(source, amount);
    }

    private void tell(String key) {
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(24, 12, 24));
        for (EntityPlayer p : near) p.addChatMessage(new ChatComponentTranslation(key));
    }

    private int helpers() {
        return worldObj.getEntitiesWithinAABB(EntityGlacon.class, boundingBox.expand(20, 8, 20))
            .size()
            + worldObj.getEntitiesWithinAABB(EntityFrost.class, boundingBox.expand(20, 8, 20))
                .size();
    }

    private void summon(EntityLiving mob, double up) {
        mob.setLocationAndAngles(posX + (rand.nextDouble() - 0.5) * 4, posY + up, posZ + (rand.nextDouble() - 0.5) * 4,
            rand.nextFloat() * 360, 0);
        worldObj.spawnEntityInWorld(mob);
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        entityDropItem(new ItemStack(IceikaItems.snowGlobe), 0.5F);
        entityDropItem(new ItemStack(Items.snowball, 8 + rand.nextInt(9 + looting)), 0.5F);
        entityDropItem(new ItemStack(Blocks.packed_ice, 2 + rand.nextInt(3)), 0.5F);
    }
}
