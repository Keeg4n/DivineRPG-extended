package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModItems;

/**
 * Лиоплевродон, рыбий босс (по образцу Рыброна из Террарии): необязательный, между Апалачией и Небесным терном. Призывается на
 * Кровавую наживку в океане обычного мира ({@link com.divinerpgextended.fishing.EntityDreHook}). Плавает без тяжести, на суше
 * беспомощен.
 * <ul>
 * <li>Только в океане: ранить его можно, лишь стоя в воде, в лодке на воде или плывя над океаном; с берега и со столба удары не
 * проходят. Вне океана (сам вышел или выманили) или на суше он злится: урон x{@link #RAGE}.</li>
 * <li>Охота: кружит и раз в {@link #RAM_EVERY} тиков таранит с отбросом (ссаживает с лодки).</li>
 * <li>Водоворот (меньше 60%): тянет к себе и вниз всех рядом, зовёт мальков-акул (без добычи).</li>
 * <li>Бешенство (меньше 30%): выпрыгивает дугой и обрушивается рядом с игроком (удар по площади), тех, кто не в воде, поливает
 * струями.</li>
 * </ul>
 * Добыча: череп, зубы, океанский ящик; лук Цунами, ласты и якорь лиоплевродона - каждому бившему
 * ({@link com.divinerpgextended.boss.BossDrops}).
 */
public class EntityLiopleurodonBoss extends EntityMob implements IBossDisplayData {

    private static final int DW_RAGE = 20;
    public static final float RAGE = 1.5F, RAM_DAMAGE = 18F, SLAM_DAMAGE = 20F, JET_DAMAGE = 12F;
    public static final int RAM_EVERY = 70, WHIRL_EVERY = 240, WHIRL_TICKS = 60, LEAP_EVERY = 160, JET_EVERY = 40, MAX_FRY = 6;
    public static final double WHIRL_R = 10, SLAM_R = 6;
    private int ramTimer = 60, ramLeft, whirlTimer = 120, whirlLeft, leapTimer = 80, jetTimer = 40, biteCd, dryTicks;
    private boolean leaping;
    /** Для самопроверки: сколько раз ранили не из океана (удар не прошёл). */
    public int shoreBlocked;

    public EntityLiopleurodonBoss(World w) {
        super(w);
        setSize(3.5F, 1.8F);
        experienceValue = 200;
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, false));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_RAGE, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.liopleurodonHealth);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(22.0D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(48.0D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    public boolean raging() {
        return dataWatcher.getWatchableObjectByte(DW_RAGE) != 0;
    }

    private float health() {
        return getHealth() / getMaxHealth();
    }

    /** Над водой океана: в воде (или в лодке на воде) или в биоме океана не на земле. */
    public static boolean atSea(Entity e) {
        if (e.isInWater() || e.ridingEntity != null && e.ridingEntity.isInWater()) return true;
        return ocean(e) && !e.onGround;
    }

    public static boolean ocean(Entity e) {
        return com.divinerpgextended.world.Biomes.ocean(e.worldObj.getBiomeGenForCoords(MathHelper.floor_double(e.posX), MathHelper.floor_double(e.posZ)));
    }

    /** В воде без тяжести, лёгкое сопротивление; на суше - как обычный моб (падает, ползёт). */
    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        if (!isInWater()) {
            super.moveEntityWithHeading(strafe, forward);
            return;
        }
        moveEntity(motionX, motionY, motionZ);
        motionX *= 0.9;
        motionY *= 0.9;
        motionZ *= 0.9;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        boolean wet = isInWater();
        dryTicks = wet ? 0 : dryTicks + 1;
        boolean rage = !ocean(this) || dryTicks > 60;
        if (rage != raging()) dataWatcher.updateObject(DW_RAGE, Byte.valueOf((byte) (rage ? 1 : 0)));
        EntityLivingBase t = getAttackTarget();
        if (t != null && (t.isDead || t.getDistanceSqToEntity(this) > 64 * 64 || t instanceof EntityPlayer && ((EntityPlayer) t).capabilities.isCreativeMode)) {
            setAttackTarget(null);
            t = null;
        }
        if (biteCd > 0) biteCd--;
        if (leaping) {
            if (wet && motionY <= 0 || onGround) {
                leaping = false;
                slam();
            }
            return;
        }
        if (t == null) {
            if (wet) motionY += 0.004 * Math.sin(ticksExisted * 0.05);
            return;
        }
        float hp = health();
        if (wet) swim(t);
        // укус вплотную
        if (biteCd <= 0 && getDistanceSqToEntity(t) < 3.4 * 3.4) {
            attackEntityAsMob(t);
            biteCd = 20;
        }
        // таран
        if (ramLeft > 0) {
            ramLeft--;
            if (getDistanceSqToEntity(t) < 3.0 * 3.0) {
                ramLeft = 0;
                ram(t);
            }
        } else if (--ramTimer <= 0 && wet && getDistanceSqToEntity(t) < 20 * 20) {
            ramTimer = RAM_EVERY;
            ramLeft = 15;
            playSound("divinerpg:liopleurodon", 2.0F, 0.7F);
        }
        // водоворот
        if (hp < 0.6F) {
            if (whirlLeft > 0) {
                whirlLeft--;
                whirl();
            } else if (--whirlTimer <= 0) {
                whirlTimer = WHIRL_EVERY;
                whirlLeft = WHIRL_TICKS;
                fry();
            }
        }
        // бешенство: прыжок и струи по тем, кто не в воде
        if (hp < 0.3F) {
            if (--leapTimer <= 0 && wet && getDistanceSqToEntity(t) < 24 * 24) {
                leapTimer = LEAP_EVERY;
                leap(t);
            }
            if (--jetTimer <= 0 && !t.isInWater()) {
                jetTimer = JET_EVERY;
                jet(t);
            }
        }
    }

    /** Плыть к цели (не выше поверхности воды); во время тарана - быстро. */
    private void swim(EntityLivingBase t) {
        double tx = t.posX, ty = t.posY + t.height * 0.5, tz = t.posZ;
        int x = MathHelper.floor_double(posX), z = MathHelper.floor_double(posZ), y = MathHelper.floor_double(posY + height);
        if (!worldObj.getBlock(x, y + 1, z).getMaterial().isLiquid()) ty = Math.min(ty, posY); // у поверхности - не выпрыгивать
        double dx = tx - posX, dy = ty - (posY + height * 0.5), dz = tz - posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01) return;
        double speed = (ramLeft > 0 ? 0.9 : 0.28) * (raging() ? 1.4 : 1.0);
        motionX += (dx / len * speed - motionX) * 0.25;
        motionY += (dy / len * speed - motionY) * 0.25;
        motionZ += (dz / len * speed - motionZ) * 0.25;
        rotationYaw = renderYawOffset = rotationYawHead = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
    }

    private float rage(float d) {
        return raging() ? d * RAGE : d;
    }

    /** Таран: урон, отброс, ссаживает с лодки. */
    private void ram(EntityLivingBase t) {
        if (t.ridingEntity != null) t.mountEntity(null);
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causeMobDamage(this), rage(RAM_DAMAGE));
        double dx = t.posX - posX, dz = t.posZ - posZ, len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        t.addVelocity(dx / len * 1.4, 0.4, dz / len * 1.4);
        t.velocityChanged = true;
        playSound("random.explode", 0.6F, 1.4F);
    }

    /** Водоворот: всех рядом тянет к пасти и вниз. */
    private void whirl() {
        @SuppressWarnings("unchecked")
        List<EntityPlayer> l = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(WHIRL_R, WHIRL_R, WHIRL_R));
        for (EntityPlayer p : l) {
            if (p.capabilities.isCreativeMode || p.getDistanceToEntity(this) > WHIRL_R) continue;
            double dx = posX - p.posX, dy = posY - p.posY, dz = posZ - p.posZ, len = Math.max(0.5, Math.sqrt(dx * dx + dy * dy + dz * dz));
            p.addVelocity(dx / len * 0.12, (p.isInWater() ? -0.07 : 0) + dy / len * 0.04, dz / len * 0.12);
            p.velocityChanged = true;
        }
        if (worldObj instanceof WorldServer && ticksExisted % 4 == 0)
            ((WorldServer) worldObj).func_147487_a("bubble", posX, posY + 1, posZ, 30, WHIRL_R * 0.5, 1.0, WHIRL_R * 0.5, 0.1);
    }

    /** Мальки: акулы Divine-приспешники (без добычи и опыта). */
    private void fry() {
        if (com.divinerpgextended.boss.BossMinions.count(this) >= MAX_FRY) return;
        for (int i = 0; i < 3; i++) {
            net.divinerpg.entities.vanilla.EntityShark s = new net.divinerpg.entities.vanilla.EntityShark(worldObj);
            s.setLocationAndAngles(posX + rand.nextGaussian() * 3, posY, posZ + rand.nextGaussian() * 3, rand.nextFloat() * 360F, 0F);
            com.divinerpgextended.boss.BossMinions.mark(s, this);
            com.divinerpgextended.boss.BossMinions.noXp(s);
            worldObj.spawnEntityInWorld(s);
            if (getAttackTarget() != null) s.setAttackTarget(getAttackTarget());
        }
        playSound("divinerpg:liopleurodon", 2.5F, 0.5F);
    }

    /** Прыжок дугой к цели. */
    private void leap(EntityLivingBase t) {
        double dx = t.posX - posX, dz = t.posZ - posZ, len = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
        double h = Math.min(1.0, len / 16);
        motionX = dx / len * (0.6 + h * 0.6);
        motionZ = dz / len * (0.6 + h * 0.6);
        motionY = 1.1;
        leaping = true;
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("splash", posX, posY + 1, posZ, 60, 2.0, 0.5, 2.0, 0.3);
        playSound("divinerpg:liopleurodon", 3.0F, 0.6F);
    }

    /** Удар по площади при падении. */
    private void slam() {
        @SuppressWarnings("unchecked")
        List<EntityPlayer> l = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(SLAM_R, SLAM_R, SLAM_R));
        for (EntityPlayer p : l) {
            if (p.capabilities.isCreativeMode || p.getDistanceToEntity(this) > SLAM_R) continue;
            p.hurtResistantTime = 0;
            p.attackEntityFrom(DamageSource.causeMobDamage(this), rage(SLAM_DAMAGE));
            if (p.ridingEntity != null) p.mountEntity(null);
            double dx = p.posX - posX, dz = p.posZ - posZ, len = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.addVelocity(dx / len, 0.6, dz / len);
            p.velocityChanged = true;
        }
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("splash", posX, posY + 1, posZ, 120, SLAM_R * 0.5, 0.5, SLAM_R * 0.5, 0.4);
        playSound("random.explode", 1.2F, 0.6F);
    }

    /** Струя воды в того, кто не в воде. */
    private void jet(EntityLivingBase t) {
        EntityWaterJet j = new EntityWaterJet(worldObj, this, rage(JET_DAMAGE));
        double dx = t.posX - posX, dy = t.posY + t.getEyeHeight() * 0.5 - (posY + height * 0.8), dz = t.posZ - posZ;
        j.setPosition(posX, posY + height * 0.8, posZ);
        j.setThrowableHeading(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.08, dz, 1.4F, 2.0F);
        worldObj.spawnEntityInWorld(j);
        playSound("random.splash", 1.0F, 0.6F);
    }

    @Override
    public boolean attackEntityAsMob(Entity e) {
        float d = rage((float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue());
        boolean hit = e.attackEntityFrom(DamageSource.causeMobDamage(this), d);
        if (hit) playSound("divinerpg:liopleurodonHurt", 1.0F, 0.6F);
        return hit;
    }

    /** Ранить можно только из океана: с берега и со столбов в море удары и выстрелы не проходят. */
    @Override
    public boolean attackEntityFrom(DamageSource s, float a) {
        Entity by = s.getEntity();
        if (by instanceof EntityPlayer && !((EntityPlayer) by).capabilities.isCreativeMode && !atSea(by)) {
            shoreBlocked++;
            EntityPlayer p = (EntityPlayer) by;
            if (!worldObj.isRemote && (!p.getEntityData().hasKey("dreLioShore") || p.getEntityData().getLong("dreLioShore") + 100 < worldObj.getTotalWorldTime())) {
                p.getEntityData().setLong("dreLioShore", worldObj.getTotalWorldTime());
                ChatComponentTranslation m = new ChatComponentTranslation("dre.chat.lio_shore");
                m.getChatStyle().setColor(EnumChatFormatting.AQUA);
                p.addChatMessage(m);
            }
            return false;
        }
        if (s == DamageSource.drown || s == DamageSource.inWall) return false;
        return super.attackEntityFrom(s, a);
    }

    @Override
    protected void fall(float d) {}

    @Override
    protected String getLivingSound() {
        return "divinerpg:liopleurodon";
    }

    @Override
    protected String getHurtSound() {
        return "divinerpg:liopleurodonHurt";
    }

    @Override
    protected String getDeathSound() {
        return "divinerpg:liopleurodonHurt";
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        entityDropItem(new ItemStack(net.divinerpg.utils.items.VanillaItemsOther.liopleurodonSkull), 0.5F);
        entityDropItem(new ItemStack(net.divinerpg.utils.items.VanillaItemsOther.liopleurodonTeeth, 4 + rand.nextInt(5 + looting)), 0.5F);
        entityDropItem(new ItemStack(ModItems.crate, 1, com.divinerpgextended.fishing.Zone.OCEAN.ordinal()), 0.5F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound t) {
        super.writeEntityToNBT(t);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound t) {
        super.readEntityFromNBT(t);
    }
}
