package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.Config;
import com.divinerpgextended.world.CadillionWorld;

/**
 * Король-воин Апалачии, страж рощи - босс-эскалация: три встречи подряд. Упав, он не умирает, а встаёт на колено
 * (неуязвим, {@link #KNEEL} тиков) и поднимается сильнее:
 * <ol>
 * <li>дуэль: клинок, выпад к цели и прыжок за спину с ударом;</li>
 * <li>+ ударная волна: замах, потом кольцо отбрасывает всех рядом далеко в сторону - с острова можно слететь
 * (Каменная стена, сет Апалачии, держит на месте); прыжок ввысь - зависает над целью, на земле метка, потом обрушивается
 * туда (успей отойти);</li>
 * <li>+ свита: големы и кадиллионы Апалачии.</li>
 * </ol>
 * Трофеи - только после третьей победы. На священной горе каждая следующая встреча начинается ярусом выше на кургане.
 */
public class EntityWarriorKing extends EntityGroveGuardian {

    public static final int ROUNDS = 3, KNEEL = 100;
    /** Здоровье и урон по встречам (доля от силы стража мира). */
    public static final float[] HEALTH = { 0.5F, 0.75F, 1.0F }, DAMAGE = { 1.0F, 1.2F, 1.4F };
    public static final float SCALE = 2.2F;
    private static final int DW_ROUND = 21, DW_KNEEL = 22;

    private int dashTimer = 80, waveTimer = 140, windup = -1, retinueTimer = 60, lastSlash = -100;
    private int behindTimer = 120, slamTimer = 200, slamState, slamHold;
    private boolean behindLeap;
    private double markX, markY, markZ;
    /** Прыжок ввысь: подъём, зависание, удар. */
    public static final int SLAM_UP = 1, SLAM_HOLD = 2, SLAM_DOWN = 3, HOLD_TICKS = 30;
    public static final double SLAM_R = 3.5;

    public EntityWarriorKing(World world) {
        super(world);
        setSize(1.4F, 4.2F);
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        // клинок только для вида: пустой список модификаторов - урон клинка к урону Короля не прибавляется
        ItemStack blade = new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.apalachiaBlade);
        blade.setTagCompound(new NBTTagCompound());
        blade.getTagCompound().setTag("AttributeModifiers", new net.minecraft.nbt.NBTTagList());
        setCurrentItemOrArmor(0, blade);
        applyRound(0);
    }

    @Override
    public CadillionWorld world() {
        return CadillionWorld.APALACHIA;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_ROUND, Byte.valueOf((byte) 0));
        dataWatcher.addObject(DW_KNEEL, Integer.valueOf(0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.32D);
    }

    /** Номер встречи: 0, 1, 2. */
    public int round() {
        return dataWatcher.getWatchableObjectByte(DW_ROUND);
    }

    public int slamState() {
        return slamState;
    }

    /** Сколько ещё стоит на колене (0 - сражается). */
    public int kneeling() {
        return dataWatcher.getWatchableObjectInt(DW_KNEEL);
    }

    private float baseHealth() {
        return Config.cadillionHealth[Math.min(world().ordinal(), Config.cadillionHealth.length - 1)];
    }

    private void applyRound(int r) {
        dataWatcher.updateObject(DW_ROUND, Byte.valueOf((byte) r));
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(baseHealth() * HEALTH[r]);
        getEntityAttribute(SharedMonsterAttributes.attackDamage)
            .setBaseValue(com.divinerpgextended.boss.BossDamage.cadillionRawDamage(world().ordinal()) * DAMAGE[r]);
        setHealth(getMaxHealth());
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (kneeling() > 0 && src != DamageSource.outOfWorld) return false;
        return super.attackEntityFrom(src, amount);
    }

    /** Смертельный удар до последней встречи - встаёт на колено вместо смерти. */
    @Override
    public void setHealth(float hp) {
        if (hp <= 0 && !worldObj.isRemote && round() < ROUNDS - 1 && dataWatcher != null) {
            super.setHealth(1);
            dataWatcher.updateObject(DW_KNEEL, Integer.valueOf(KNEEL));
            getNavigator().clearPathEntity();
            windup = -1;
            slamState = 0;
            behindLeap = false;
            worldObj.playSoundAtEntity(this, "random.anvil_land", 1.5F, 0.4F);
            com.divinerpgextended.debug.Debug.note(this, "Король-воин: колено после встречи " + (round() + 1));
            return;
        }
        super.setHealth(hp);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        WorldServer ws = (WorldServer) worldObj;
        int kneel = kneeling();
        if (kneel > 0) {
            motionX = motionZ = 0;
            getNavigator().clearPathEntity();
            if (ticksExisted % 5 == 0) ws.func_147487_a("witchMagic", posX, posY + height * 0.5, posZ, 6, 0.6, 1.0, 0.6, 0);
            dataWatcher.updateObject(DW_KNEEL, Integer.valueOf(kneel - 1));
            if (kneel == 1) {
                applyRound(round() + 1);
                climbTier();
                dashTimer = 60;
                waveTimer = 100;
                retinueTimer = 40;
                worldObj.playSoundAtEntity(this, "mob.wither.spawn", 1.0F, 1.4F);
                ws.func_147487_a("largeexplode", posX, posY + 1, posZ, 3, 1, 1, 1, 0);
            }
            return;
        }
        EntityLivingBase t = getAttackTarget();
        if (slamState > 0) {
            slamTick(ws, t);
            return;
        }
        if (behindLeap && onGround && motionY <= 0) { // приземлился за спиной - сразу удар
            behindLeap = false;
            sweep(3.5, 1.0F, 0.4);
        }
        if (t == null) return;
        if (windup >= 0) { // замах перед волной: стоит, клинок вверх
            motionX = motionZ = 0;
            getNavigator().clearPathEntity();
            ws.func_147487_a("crit", posX, posY + height, posZ, 4, 0.3, 0.3, 0.3, 0.1);
            if (windup-- == 0) wave();
            return;
        }
        if (--dashTimer <= 0 && getDistanceToEntity(t) > 4 && getDistanceToEntity(t) < 16) dash(t);
        if (--behindTimer <= 0 && getDistanceToEntity(t) < 10 && onGround) {
            behindTimer = 160 + rand.nextInt(60);
            leapBehind(t);
        }
        if (round() >= 1 && --slamTimer <= 0 && onGround) {
            slamTimer = 260 + rand.nextInt(80);
            slamState = SLAM_UP;
            motionX = motionZ = 0;
            motionY = 1.5;
            velocityChanged = true;
            worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 2.0F, 0.6F);
            return;
        }
        if (round() >= 1 && --waveTimer <= 0) {
            windup = 20;
            waveTimer = 180 + rand.nextInt(60);
            worldObj.playSoundAtEntity(this, "mob.irongolem.throw", 2.0F, 0.5F);
        }
        if (round() >= 2 && --retinueTimer <= 0) {
            retinueTimer = 260;
            Class<?>[] kinds = { net.divinerpg.entities.twilight.EntityApalachiaGolem.class, net.divinerpg.entities.twilight.EntityApalachiaCadillion.class };
            for (int i = 0; i < 2 && addsAlive() < 3; i++) {
                @SuppressWarnings("unchecked")
                Class<? extends net.minecraft.entity.EntityLiving> k = (Class<? extends net.minecraft.entity.EntityLiving>) kinds[rand.nextInt(kinds.length)];
                summon(k, 5);
            }
            worldObj.playSoundAtEntity(this, "mob.wither.idle", 1.0F, 1.6F);
        }
    }

    /** На священной горе новая встреча начинается ярусом выше: прыжок на верх яруса кургана. */
    private void climbTier() {
        int[] home = com.divinerpgextended.boss.LairSummons.home(this);
        int r = round();
        if (home == null || r >= com.divinerpgextended.world.twilight.WorldGenNests.TIER_H.length) return;
        int y = home[1] + com.divinerpgextended.world.twilight.WorldGenNests.TIER_H[r];
        if (worldObj.isAirBlock(home[0] + 3, y - 1, home[2])) return; // не курган - ярусов нет
        ((WorldServer) worldObj).func_147487_a("largeexplode", posX, posY + 1, posZ, 2, 0.5, 0.5, 0.5, 0);
        setPositionAndUpdate(home[0] + 3.5, y, home[2] + 0.5); // центр вершины - костровище
        worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 2.0F, 0.8F);
    }

    /** Прыжок за спину цели: дугой через неё, при приземлении - круговой удар клинком. */
    private void leapBehind(EntityLivingBase t) {
        net.minecraft.util.Vec3 look = t.getLookVec();
        double lx = look.xCoord, lz = look.zCoord, ll = Math.max(0.01, Math.sqrt(lx * lx + lz * lz));
        double bx = t.posX - lx / ll * 3, bz = t.posZ - lz / ll * 3;
        double dx = bx - posX, dz = bz - posZ;
        motionX = dx / 18;
        motionZ = dz / 18;
        motionY = 0.85;
        velocityChanged = true;
        behindLeap = true;
        worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 1.5F, 1.5F);
    }

    /** Удар по всем вокруг (после прыжков). */
    private void sweep(double r, float mult, double up) {
        swingItem();
        float dmg = (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * mult;
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(r, 2, r));
        for (EntityPlayer p : near) {
            double dx = p.posX - posX, dz = p.posZ - posZ;
            if (dx * dx + dz * dz > r * r) continue;
            p.attackEntityFrom(DamageSource.causeMobDamage(this), dmg);
            p.motionY += up;
            p.velocityChanged = true;
        }
        worldObj.playSoundAtEntity(this, "mob.irongolem.hit", 2.0F, 0.6F);
    }

    /** Прыжок ввысь: на вершине зависает и отмечает место цели, потом падает туда. */
    private void slamTick(WorldServer ws, EntityLivingBase t) {
        getNavigator().clearPathEntity();
        switch (slamState) {
            case SLAM_UP:
                if (motionY > 0.05) return;
                slamState = SLAM_HOLD;
                slamHold = HOLD_TICKS;
                if (t != null) {
                    markX = t.posX;
                    markY = t.posY;
                    markZ = t.posZ;
                } else {
                    markX = posX;
                    markY = posY - 8;
                    markZ = posZ;
                }
                worldObj.playSoundAtEntity(this, "mob.wither.idle", 1.5F, 1.4F);
                return;
            case SLAM_HOLD:
                motionX = motionZ = 0;
                motionY = 0.08; // висит
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12 + slamHold * 0.1;
                    ws.func_147487_a("reddust", markX + Math.cos(a) * SLAM_R, markY + 0.2, markZ + Math.sin(a) * SLAM_R, 1, 0, 0, 0, 0);
                }
                ws.func_147487_a("witchMagic", markX, markY + 0.3, markZ, 3, 0.3, 0, 0.3, 0);
                if (--slamHold > 0) return;
                setPosition(markX, posY, markZ);
                motionY = -2.2;
                velocityChanged = true;
                slamState = SLAM_DOWN;
                return;
            default:
                if (!onGround) return;
                slamState = 0;
                ws.func_147487_a("largeexplode", posX, posY + 0.5, posZ, 4, 1.5, 0.2, 1.5, 0);
                worldObj.playSoundAtEntity(this, "random.explode", 2.0F, 0.6F);
                sweep(SLAM_R, 1.5F, 0.8);
        }
    }

    /** Выпад: прыжок к цели. */
    private void dash(EntityLivingBase t) {
        dashTimer = 120 + rand.nextInt(40);
        double dx = t.posX - posX, dz = t.posZ - posZ, d = Math.sqrt(dx * dx + dz * dz);
        motionX = dx / d * 1.4;
        motionZ = dz / d * 1.4;
        motionY = 0.45;
        velocityChanged = true;
        worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 1.5F, 1.3F);
    }

    /** Ударная волна: всех в 9 блоках бьёт и отбрасывает прочь (Каменная стена держит). */
    private void wave() {
        WorldServer ws = (WorldServer) worldObj;
        worldObj.playSoundAtEntity(this, "random.explode", 2.0F, 0.7F);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            ws.func_147487_a("cloud", posX + Math.cos(a) * 3, posY + 0.3, posZ + Math.sin(a) * 3, 2, 0.1, 0.05, 0.1, 0.25);
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(9, 4, 9));
        for (EntityPlayer p : near) {
            if (p.getDistanceToEntity(this) > 9) continue;
            p.attackEntityFrom(DamageSource.causeMobDamage(this), (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * 0.6F);
            if (p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue() >= 1.0) continue;
            double dx = p.posX - posX, dz = p.posZ - posZ, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.motionX = dx / d * 2.2;
            p.motionZ = dz / d * 2.2;
            p.motionY = 0.5;
            p.velocityChanged = true;
        }
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        if (kneeling() > 0 || windup >= 0 || slamState > 0 || ticksExisted - lastSlash < 20) return false;
        double dx = target.posX - posX, dz = target.posZ - posZ;
        if (dx * dx + dz * dz > 4.0 * 4.0) return false;
        lastSlash = ticksExisted;
        swingItem();
        return super.attackEntityAsMob(target);
    }

    @Override
    protected boolean isMovementBlocked() {
        return kneeling() > 0 || super.isMovementBlocked();
    }

    @Override
    protected String getLivingSound() {
        return "mob.irongolem.walk";
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.irongolem.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.7F;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setByte("Round", (byte) round());
        tag.setInteger("Kneel", kneeling());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        int r = tag.getByte("Round");
        dataWatcher.updateObject(DW_ROUND, Byte.valueOf((byte) r));
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(baseHealth() * HEALTH[Math.min(r, ROUNDS - 1)]);
        super.readEntityFromNBT(tag);
        dataWatcher.updateObject(DW_KNEEL, Integer.valueOf(tag.getInteger("Kneel")));
    }
}
