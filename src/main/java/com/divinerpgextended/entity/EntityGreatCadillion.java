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
import net.minecraft.world.WorldServer;

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

    // ---- бой: дальность и перезарядка удара, таран с оглушением
    /** Удар достаёт на столько блоков от центра (игрок мечом - примерно на 4.2): ближний бой честный. */
    public static final double REACH = 3.6;
    /** Перезарядка удара в тиках (ванильный ИИ 1.7.10 бьёт каждые полсекунды). */
    public static final int ATTACK_COOLDOWN = 25;
    public static final int IDLE = 0, WINDUP = 1, CHARGE = 2, STUNNED = 3;
    private int chargeState = IDLE, stateTimer, chargeCooldown = 120, lastAttack = -100;
    private double chargeX, chargeZ;
    private boolean chargeHit;
    /** Подкрепление: мобы его мира (в том числе летучие Кори), раз в ~15 с боя, живых не больше 4. */
    private int addsTimer = 200;
    public static final int MAX_ADDS = 4;
    private static final String ADD_TAG = "dreCadillionAdd";

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

    public int chargeState() {
        return chargeState;
    }

    /** Оглушить на ticks: не бьёт, не ходит, получает двойной урон. */
    public void stun(int ticks) {
        chargeState = STUNNED;
        stateTimer = ticks;
        motionX = motionZ = 0;
        getNavigator().clearPathEntity();
        worldObj.playSoundAtEntity(this, "random.anvil_land", 1.5F, 0.5F);
        for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(24, 12, 24))) {
            ((EntityPlayer) o).addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.chat.cadillion_stunned"));
        }
    }

    /**
     * Таран: раз в ~8 с, если цель в 5-20 блоках, кадиллион опускает рог (секунду стоит, искры и фырканье), потом
     * мчится по прямой туда, где была цель. Врезался в ствол или колонну - оглушён на 4 с.
     */
    private void charge() {
        EntityLivingBase t = getAttackTarget();
        WorldServer ws = (WorldServer) worldObj;
        switch (chargeState) {
            case IDLE:
                if (t == null || --chargeCooldown > 0) return;
                double d = getDistanceToEntity(t);
                if (d < 5 || d > 20 || !canEntityBeSeen(t)) return;
                chargeState = WINDUP;
                stateTimer = 20;
                double dx = t.posX - posX, dz = t.posZ - posZ, len = Math.sqrt(dx * dx + dz * dz);
                chargeX = dx / len;
                chargeZ = dz / len;
                worldObj.playSoundAtEntity(this, "mob.horse.angry", 2.0F, 0.6F);
                return;
            case WINDUP:
                getNavigator().clearPathEntity();
                motionX = motionZ = 0;
                rotationYaw = renderYawOffset = rotationYawHead = (float) (Math.atan2(chargeZ, chargeX) * 180 / Math.PI) - 90;
                ws.func_147487_a("angryVillager", posX, posY + height * 0.8, posZ, 2, 0.6, 0.3, 0.6, 0);
                if (--stateTimer <= 0) {
                    chargeState = CHARGE;
                    stateTimer = 30;
                    chargeHit = false;
                }
                return;
            case CHARGE:
                getNavigator().clearPathEntity();
                int[] home = com.divinerpgextended.boss.LairSummons.home(this);
                if (home != null) {
                    double nx = posX + chargeX * 2 - home[0] - 0.5, nz = posZ + chargeZ * 2 - home[2] - 0.5;
                    if (nx * nx + nz * nz > (home[3] - 1) * (home[3] - 1)) { // край поляны - тормозит, а не телепортируется
                        chargeState = IDLE;
                        chargeCooldown = 140 + rand.nextInt(60);
                        motionX = motionZ = 0;
                        return;
                    }
                }
                motionX = chargeX * 0.85;
                motionZ = chargeZ * 0.85;
                ws.func_147487_a("cloud", posX, posY + 0.3, posZ, 3, 0.8, 0.1, 0.8, 0.02);
                if (!chargeHit) {
                    for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(0.6, 0.2, 0.6))) {
                        EntityPlayer p = (EntityPlayer) o;
                        p.attackEntityFrom(net.minecraft.util.DamageSource.causeMobDamage(this),
                            (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * 1.6F);
                        p.motionX += chargeX * 1.2;
                        p.motionZ += chargeZ * 1.2;
                        p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
                        p.addPotionEffect(new PotionEffect(Potion.weakness.id, 80, 0));
                        p.velocityChanged = true;
                        chargeHit = true;
                    }
                }
                if (isCollidedHorizontally && stateTimer < 28) {
                    stun(80);
                    return;
                }
                if (--stateTimer <= 0) {
                    chargeState = IDLE;
                    chargeCooldown = 140 + rand.nextInt(60);
                }
                return;
            default: // оглушён
                getNavigator().clearPathEntity();
                motionX = motionZ = 0;
                if (ticksExisted % 4 == 0) ws.func_147487_a("crit", posX, posY + height, posZ, 6, 0.8, 0.3, 0.8, 0.1);
                if (--stateTimer <= 0) {
                    chargeState = IDLE;
                    chargeCooldown = 140 + rand.nextInt(60);
                }
        }
    }

    /** Позвать мобов своего мира: двух рядом, если живых подручных меньше {@link #MAX_ADDS}. */
    public int callAdds() {
        com.divinerpgextended.world.twilight.TwilightWorld tw = com.divinerpgextended.world.twilight.TwilightWorld.values()[getVariant()];
        Class<? extends net.minecraft.entity.EntityLiving>[] mobs = tw.surfaceMobs(); // роща открыта небу
        Class<? extends net.minecraft.entity.EntityLiving> flyer = tw.flyer();
        // считаем только своих подручных: обычных мобов мира в роще и так много
        int alive = 0;
        for (Object o : worldObj.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class, boundingBox.expand(40, 24, 40))) {
            if (((net.minecraft.entity.Entity) o).getEntityData().getInteger(ADD_TAG) == getEntityId()) alive++;
        }
        int made = 0;
        for (int i = 0; i < 2 && alive + made < MAX_ADDS; i++) {
            try {
                Class<? extends net.minecraft.entity.EntityLiving> kind = i == 0 && flyer != null ? flyer : mobs[rand.nextInt(mobs.length)];
                net.minecraft.entity.EntityLiving m = kind.getConstructor(World.class).newInstance(worldObj);
                double a = rand.nextDouble() * Math.PI * 2;
                boolean flies = m instanceof net.minecraft.entity.EntityFlying;
                m.setLocationAndAngles(posX + Math.cos(a) * 6, posY + (flies ? 6 : 0.5), posZ + Math.sin(a) * 6, rand.nextFloat() * 360, 0);
                if (!flies && !worldObj.getCollidingBoundingBoxes(m, m.boundingBox).isEmpty()) continue;
                m.setAttackTarget(getAttackTarget());
                m.getEntityData().setInteger(ADD_TAG, getEntityId());
                worldObj.spawnEntityInWorld(m);
                ((WorldServer) worldObj).func_147487_a("portal", m.posX, m.posY + 1, m.posZ, 20, 0.5, 0.8, 0.5, 0.1);
                made++;
            } catch (Exception ignored) {
                // у моба нет конструктора (World)
            }
        }
        if (made > 0) {
            worldObj.playSoundAtEntity(this, "mob.horse.angry", 2.0F, 0.4F);
            for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(30, 16, 30))) {
                ((EntityPlayer) o).addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.chat.cadillion_adds"));
            }
            com.divinerpgextended.debug.Debug.note(this, "Кадиллион позвал подручных: " + made);
        }
        return made;
    }

    /** Атакующий стоит вне поляны логова (стреляет издалека) - урона нет. */
    public boolean outOfLair(Entity attacker) {
        int[] home = com.divinerpgextended.boss.LairSummons.home(this);
        if (home == null || !(attacker instanceof EntityPlayer)) return false;
        double dx = attacker.posX - home[0] - 0.5, dz = attacker.posZ - home[2] - 0.5;
        return dx * dx + dz * dz > (home[3] + 4) * (home[3] + 4) || attacker.posY < home[1] - 4;
    }

    @Override
    public boolean attackEntityFrom(net.minecraft.util.DamageSource src, float amount) {
        if (outOfLair(src.getEntity())) {
            EntityPlayer p = (EntityPlayer) src.getEntity();
            if (p.ticksExisted % 20 == 0 || src.isProjectile()) p.addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.chat.cadillion_out_of_lair"));
            return false;
        }
        return super.attackEntityFrom(src, chargeState == STUNNED ? amount * 2 : amount);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        charge();
        if (getAttackTarget() != null && --addsTimer <= 0) {
            addsTimer = 300;
            callAdds();
        }
        if (getAttackTarget() == null || chargeState != IDLE) return;
        if (--abilityTimer > 0) return;

        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(10, 6, 10));
        switch (world()) {
            case EDEN: // вспышка: ослепление
                for (EntityPlayer p : near) p.addPotionEffect(new PotionEffect(Potion.blindness.id, 40, 0));
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
        entityDropItem(new ItemStack(ModItems.guardianHeart, 1, getVariant()), 0.5F); // камни врат в следующий мир
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

    /** Обычный удар: только вблизи, с перезарядкой, оглушённый и в таране не бьёт. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        if (chargeState != IDLE || ticksExisted - lastAttack < ATTACK_COOLDOWN) return false;
        double dx = target.posX - posX, dz = target.posZ - posZ;
        if (dx * dx + dz * dz > REACH * REACH) return false;
        lastAttack = ticksExisted;
        return super.attackEntityAsMob(target);
    }
}
