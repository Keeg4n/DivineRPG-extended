package com.divinerpgextended.entity;

import java.util.List;

import net.divinerpg.utils.items.IceikaItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModItems;

/**
 * Король-лич: спрыгивает с плеча павшего Ледяного Колосса.
 * <p>
 * Морозный клинок в руке (урон - только из конфига), Фроссивенс в запасе: каждые 6 секунд лечится на 4% здоровья,
 * если его не бить правильно. Icine Sword (x2.2), Icicle Bane (x2) и Enderice (x1.6, ещё и запрещает телепорт)
 * и любой огонь срывают лечение на 10 секунд. Телепорт за спину, ледяная пушка (очередь сосулек), с замахом -
 * Воющий буран и ледяная тюрьма, мертвецы. Bluefire оглушает, Snowstorm срывает замах и смущает мертвецов.
 */
public class EntityLichKing extends EntityMob implements IBossDisplayData {

    private static final int CAST_BLAST = 1, CAST_PRISON = 2, CAST_TICKS = 30;

    private int blinkTimer = 120, cannonTimer = 60, blastTimer = 180, prisonTimer = 260, deadTimer = 200, healTimer = 120;
    private int healBlocked, blinkBlocked, stunLeft, stunImmune, confusedLeft, castType, castLeft, burst;
    private int perchTimer = 100, perchLeft;

    public EntityLichKing(World world) {
        super(world);
        setSize(0.9F, 2.6F);
        experienceValue = 300;
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.1D, false));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, false));
        setCurrentItemOrArmor(0, new ItemStack(IceikaItems.frostkingSword));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.lichHealth);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(Config.lichDamage);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.32D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(160.0D); // видит всю арену
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.8D);
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
        return StatCollector.translateToLocal("dre.entity.lich_king");
    }

    @Override
    protected String getLivingSound() {
        return "mob.wither.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.skeleton.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.wither.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.6F;
    }

    /** Удар клинком: урон только из конфига (клинок в руке - для вида, его урон не прибавляется). */
    @Override
    public boolean attackEntityAsMob(Entity e) {
        if (stunLeft > 0) return false;
        boolean hit = e.attackEntityFrom(DamageSource.causeMobDamage(this), (float) Config.lichDamage);
        if (hit) swingItem();
        return hit;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        Item melee = ThroneCombat.melee(source);
        float mult = ThroneCombat.vsLich(melee);
        boolean blocksHeal = mult > 1.0F || source.isFireDamage();
        if (source.isFireDamage()) mult = 1.5F;
        if (!worldObj.isRemote) {
            if (melee == IceikaItems.enderice) blinkBlocked = 200;
            if (ThroneCombat.isArrow(source, "infernoArrow")) {
                mult = 1.5F;
                blocksHeal = true;
                setFire(5);
            } else if (ThroneCombat.isArrow(source, "bluefireArrow") && stunImmune <= 0) {
                stunLeft = 100;
                stunImmune = 300;
                FrostPowers.tell(this, "dre.chat.lich_stunned");
            } else if (ThroneCombat.isArrow(source, "snowstormArrow")) {
                if (castLeft > 0) {
                    castLeft = 0;
                    castType = 0;
                    stunLeft = Math.max(stunLeft, 20);
                    FrostPowers.tell(this, "dre.chat.cast_interrupted");
                }
                if (confusedLeft <= 0) {
                    confusedLeft = 160;
                    EntityFrostColossus.turnMinions(this, 30);
                    FrostPowers.tell(this, "dre.chat.minions_confused");
                }
            }
            if (blocksHeal && healBlocked <= 0) FrostPowers.tell(this, "dre.chat.lich_heal_blocked");
            if (blocksHeal) healBlocked = 200;
        }
        return super.attackEntityFrom(source, amount * mult);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            worldObj.spawnParticle("snowshovel", posX + (rand.nextDouble() - 0.5), posY + rand.nextDouble() * height,
                posZ + (rand.nextDouble() - 0.5), 0, 0.02, 0);
            return;
        }
        if (healBlocked > 0) healBlocked--;
        if (blinkBlocked > 0) blinkBlocked--;
        if (stunImmune > 0) stunImmune--;
        if (confusedLeft > 0) confusedLeft--;
        if (stunLeft > 0) {
            stunLeft--;
            getNavigator().clearPathEntity();
            motionX = motionZ = 0;
            if (stunLeft % 5 == 0) FrostPowers.particles(worldObj, "magicCrit", posX, posY + height + 0.3, posZ, 8, 0.4, 0.05);
            return;
        }
        EntityLivingBase target = getAttackTarget();
        if (target == null && worldObj.getClosestPlayerToEntity(this, 160) == null) return;
        boolean enraged = getHealth() / getMaxHealth() <= 0.5F;

        // ярость: телепортируется на пилоны и колонны арены и закидывает снегом сверху
        if (perchLeft > 0) {
            perchLeft--;
            getNavigator().clearPathEntity();
            motionX = motionZ = 0;
            if (target != null && perchLeft % 12 == 0) {
                EntityFrostCharge c = new EntityFrostCharge(worldObj, this);
                double dx = target.posX - posX, dy = target.posY + target.getEyeHeight() - c.posY, dz = target.posZ - posZ;
                c.setThrowableHeading(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.05, dz, 1.3F, 3.0F);
                worldObj.spawnEntityInWorld(c);
                worldObj.playSoundAtEntity(this, "random.bow", 1.0F, 0.6F);
            }
            // опору разбили - падает
            if (worldObj.isAirBlock(MathHelper.floor_double(posX), MathHelper.floor_double(posY) - 1,
                MathHelper.floor_double(posZ))) perchLeft = 0;
            return;
        }
        if (enraged && --perchTimer <= 0 && target != null) {
            perch(target);
            perchTimer = 300;
        }

        // Фроссивенс: лечится, если его не бьют правильным оружием или огнём
        if (--healTimer <= 0) {
            if (healBlocked <= 0 && getHealth() < getMaxHealth()) {
                heal(getMaxHealth() * 0.04F);
                FrostPowers.particles(worldObj, "happyVillager", posX, posY + 1.5, posZ, 15, 0.5, 0.05);
                worldObj.playSoundAtEntity(this, "random.orb", 1.0F, 0.6F);
            }
            healTimer = 120;
        }

        if (--blinkTimer <= 0 && target != null) {
            if (blinkBlocked <= 0) blink(target);
            blinkTimer = enraged ? 140 : 200;
        }
        // ледяная пушка: очередь из четырёх сосулек
        if (burst > 0 && ticksExisted % 4 == 0 && target != null) {
            EntityIcicle shard = new EntityIcicle(worldObj, this, EntityIcicle.ICICLE);
            double dx = target.posX - posX, dy = target.posY + target.getEyeHeight() - shard.posY, dz = target.posZ - posZ;
            shard.setThrowableHeading(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.05, dz, 1.8F, 3.0F);
            worldObj.spawnEntityInWorld(shard);
            worldObj.playSoundAtEntity(this, "random.bow", 1.0F, 0.5F);
            burst--;
        }
        if (--cannonTimer <= 0 && target != null && getDistanceSqToEntity(target) < 30 * 30) {
            burst = 4;
            cannonTimer = enraged ? 100 : 140;
        }

        if (castLeft > 0) {
            FrostPowers.particles(worldObj, castType == CAST_BLAST ? "snowshovel" : "spell", posX, posY + 2.8, posZ, 10, 0.5,
                0.05);
            getNavigator().clearPathEntity();
            if (--castLeft == 0) {
                if (castType == CAST_BLAST && target != null) FrostPowers.howlingBlast(this, target, posY + 1.6);
                if (castType == CAST_PRISON) {
                    List<EntityPlayer> near = FrostPowers.players(this, 24);
                    if (!near.isEmpty()) {
                        EntityPlayer p = near.get(rand.nextInt(near.size()));
                        if (!p.capabilities.isCreativeMode) FrostPowers.icePrison(p);
                    }
                }
                castType = 0;
            }
        } else if (--blastTimer <= 0 && target != null) {
            castType = CAST_BLAST;
            castLeft = CAST_TICKS;
            FrostPowers.tell(this, "dre.chat.cast_blast");
            blastTimer = 280;
        } else if (--prisonTimer <= 0) {
            castType = CAST_PRISON;
            castLeft = CAST_TICKS;
            FrostPowers.tell(this, "dre.chat.cast_prison");
            prisonTimer = 380;
        }
        if (--deadTimer <= 0) {
            if (confusedLeft <= 0) FrostPowers.raiseDead(this, 2, 4);
            deadTimer = 400;
        }
        if (enraged && ticksExisted % 300 == 0) FrostPowers.extinguishBrazier(this, 48);
    }

    /**
     * Перелёт на возвышение арены: верх пилона, уступ колонны, ступень винтовой лестницы, спинка трона - любая
     * твёрдая опора на 5-30 блоков выше цели в 6-40 блоках от неё, над которой есть место встать.
     */
    private void perch(EntityLivingBase target) {
        int ty = MathHelper.floor_double(target.posY);
        for (int attempt = 0; attempt < 300; attempt++) {
            double a = rand.nextDouble() * Math.PI * 2, d = 6 + rand.nextDouble() * 34;
            int x = MathHelper.floor_double(target.posX + Math.cos(a) * d), z = MathHelper.floor_double(target.posZ + Math.sin(a) * d);
            for (int y = ty + 30; y >= ty + 5; y--) {
                if (!worldObj.isAirBlock(x, y, z) || !worldObj.isAirBlock(x, y + 1, z) || !worldObj.isAirBlock(x, y + 2, z)) continue;
                net.minecraft.block.Block below = worldObj.getBlock(x, y - 1, z);
                if (!below.isOpaqueCube() || below == com.divinerpgextended.registry.ModBlocks.frostIce) continue;
                FrostPowers.particles(worldObj, "snowshovel", posX, posY + 1, posZ, 40, 0.5, 0.1);
                setPositionAndUpdate(x + 0.5, y, z + 0.5);
                FrostPowers.particles(worldObj, "snowshovel", x + 0.5, y + 1, z + 0.5, 40, 0.5, 0.1);
                perchLeft = 160;
                worldObj.playSoundAtEntity(this, "mob.endermen.portal", 1.5F, 0.4F);
                FrostPowers.tell(this, "dre.chat.lich_perch");
                return;
            }
        }
    }

    /** Телепорт за спину цели. */
    private void blink(EntityLivingBase target) {
        double yaw = Math.toRadians(target.rotationYaw);
        double x = target.posX + Math.sin(yaw) * 2.5, z = target.posZ - Math.cos(yaw) * 2.5, y = target.posY;
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y), bz = MathHelper.floor_double(z);
        if (!worldObj.isAirBlock(bx, by, bz) || !worldObj.isAirBlock(bx, by + 1, bz) || !worldObj.isAirBlock(bx, by + 2, bz)) {
            return;
        }
        FrostPowers.particles(worldObj, "snowshovel", posX, posY + 1, posZ, 40, 0.5, 0.1);
        setPositionAndUpdate(x, y, z);
        FrostPowers.particles(worldObj, "snowshovel", x, y + 1, z, 40, 0.5, 0.1);
        worldObj.playSoundAtEntity(this, "mob.endermen.portal", 1.0F, 0.5F);
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        entityDropItem(new ItemStack(ModItems.frozenHeart), 1.0F);
        entityDropItem(new ItemStack(ModItems.throneShard, 2 + rand.nextInt(2)), 1.0F);
        entityDropItem(new ItemStack(IceikaItems.snowflake, 16 + rand.nextInt(16 + looting)), 1.0F);
    }

    @Override
    protected void dropEquipment(boolean byPlayer, int looting) {} // клинок не роняет
}
