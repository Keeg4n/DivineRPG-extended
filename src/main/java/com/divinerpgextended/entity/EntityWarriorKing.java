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
 * Warrior King of Apalachia, the grove guardian - an escalation boss: three encounters in a row. When he falls he does not die but kneels
 * (invulnerable, {@link #KNEEL} ticks) and rises stronger:
 * <ol>
 * <li>duel: blade, lunge at the target and a jump behind the back with a strike;</li>
 * <li>+ shockwave: wind-up, then a ring throws everyone nearby far aside - you can be blown off the island
 * (Stone Wall, the Apalachia set, holds you in place); high jump - hovers over the target, a marker on the ground, then crashes down
 * there (get away in time);</li>
 * <li>+ retinue: Apalachia golems and cadillions.</li>
 * </ol>
 * Trophies - only after the third victory. On the sacred mountain each following encounter starts a tier higher on the mound.
 */
public class EntityWarriorKing extends EntityGroveGuardian {

    public static final int ROUNDS = 3, KNEEL = 100;
    /** Health and damage per encounter (a share of the world guardian's strength). */
    public static final float[] HEALTH = { 0.5F, 0.75F, 1.0F }, DAMAGE = { 1.0F, 1.2F, 1.4F };
    public static final float SCALE = 2.2F;
    private static final int DW_ROUND = 21, DW_KNEEL = 22;

    private int dashTimer = 80, waveTimer = 140, windup = -1, retinueTimer = 60, lastSlash = -100;
    private int behindTimer = 120, slamTimer = 200, slamState, slamHold;
    private boolean behindLeap;
    private double markX, markY, markZ;
    /** High jump: rise, hover, strike. */
    public static final int SLAM_UP = 1, SLAM_HOLD = 2, SLAM_DOWN = 3, HOLD_TICKS = 30;
    public static final double SLAM_R = 3.5;

    public EntityWarriorKing(World world) {
        super(world);
        setSize(1.4F, 4.2F);
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        // the blade is for looks only: an empty modifier list - the blade's damage is not added to the King's damage
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

    /** Encounter number: 0, 1, 2. */
    public int round() {
        return dataWatcher.getWatchableObjectByte(DW_ROUND);
    }

    public int slamState() {
        return slamState;
    }

    /** How much longer he stays kneeling (0 - fighting). */
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

    /** Wave wind-up right now (the slicer interrupts it). */
    public boolean windingUp() {
        return windup >= 0;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (kneeling() > 0 && src != DamageSource.outOfWorld) return false;
        if (windup >= 0 && com.divinerpgextended.perk.RangedRoles.kind(src.getSourceOfDamage()) == com.divinerpgextended.perk.RangedRoles.SLICER) {
            windup = -1; // wind-up interrupted - no wave
            worldObj.playSoundAtEntity(this, "random.anvil_land", 1.0F, 1.8F);
            com.divinerpgextended.debug.Debug.noteNear(this, 40, "King: swing interrupted by the slicer");
        }
        return super.attackEntityFrom(src, amount);
    }

    /** A lethal blow before the last encounter - he kneels instead of dying. */
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
            com.divinerpgextended.debug.Debug.note(this, "Warrior King: step after encounter " + (round() + 1));
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
        if (behindLeap && onGround && motionY <= 0) { // landed behind the back - strike right away
            behindLeap = false;
            sweep(3.5, 1.0F, 0.4);
        }
        if (t == null) return;
        if (windup >= 0) { // wind-up before the wave: stands, blade up
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

    /**
     * On the sacred mountain a new encounter starts on the top of the mound: a jump to the fire pit (next to it, not into the fire).
     * Height - by the real surface, and only where the king fits entirely (before, the second encounter placed him
     * at the height of the lower tier 3 blocks from the center - inside the summit).
     */
    private void climbTier() {
        int[] home = com.divinerpgextended.boss.LairSummons.home(this);
        int[] tierH = com.divinerpgextended.world.twilight.WorldGenNests.TIER_H;
        if (home == null || round() >= tierH.length) return;
        int tx = home[0] + 4, tz = home[2];
        if (worldObj.isAirBlock(tx, home[1] + tierH[1] - 1, tz)) return; // not a mound - no tiers
        int y = home[1] + tierH[tierH.length - 1] + 12;
        while (y > home[1] && worldObj.isAirBlock(tx, y - 1, tz)) y--;
        for (int k = 0; k < 8; k++, y++) {
            setPosition(tx + 0.5, y, tz + 0.5);
            if (worldObj.getCollidingBoundingBoxes(this, boundingBox).isEmpty()) break;
        }
        ((WorldServer) worldObj).func_147487_a("largeexplode", posX, posY + 1, posZ, 2, 0.5, 0.5, 0.5, 0);
        setPositionAndUpdate(tx + 0.5, y, tz + 0.5);
        worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 2.0F, 0.8F);
    }

    /** Jump behind the target's back: in an arc over it, on landing - a circular blade strike. */
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

    /** Strike at everyone around (after the jumps). */
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

    /** High jump: at the peak it hovers and marks the target's spot, then falls there. */
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
                motionY = 0.08; // hanging
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

    /** Lunge: a jump to the target. */
    private void dash(EntityLivingBase t) {
        dashTimer = 120 + rand.nextInt(40);
        double dx = t.posX - posX, dz = t.posZ - posZ, d = Math.sqrt(dx * dx + dz * dz);
        motionX = dx / d * 1.4;
        motionZ = dz / d * 1.4;
        motionY = 0.45;
        velocityChanged = true;
        worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 1.5F, 1.3F);
    }

    /** Shockwave: hits everyone within 9 blocks and throws them away (Stone Wall holds you). */
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
