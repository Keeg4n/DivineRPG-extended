package com.divinerpgextended.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
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
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Древний драмкрикс - босс логова под храмом джунглей (модель драмкрикса Divine, втрое крупнее, замшелый).
 * <ul>
 * <li>Хвост: цель рядом - замах {@link #SWEEP_WIND} тиков, удар по кругу ({@link #SWEEP_R}) и сильный отброс.</li>
 * <li>Таран: цель далеко - замах, бег по прямой; врезался в стену - оглушён {@link #STUN} тиков, урон по нему x1.5.</li>
 * <li>Споры: под игроками светится место, через {@link #SPORE_FUSE} тиков там ядовитое облако на {@link #SPORE_LIFE}.</li>
 * <li>На половине здоровья зовёт {@link #ADDS} порабощённых драмкриксов: пока они живы, шкура держит урон (x{@link #SHELL}).</li>
 * <li>На четверти здоровья - ярость: всё чаще.</li>
 * </ul>
 */
public class EntityAncientDramcryx extends EntityMob implements IBossDisplayData {

    public static final float SCALE = 3.0F;
    public static final int SWEEP_EVERY = 100, SWEEP_WIND = 20, CHARGE_EVERY = 180, CHARGE_WIND = 20, CHARGE_TICKS = 30, STUN = 60;
    public static final int SPORE_EVERY = 240, SPORE_FUSE = 30, SPORE_LIFE = 120, ADDS = 3;
    public static final double SWEEP_R = 6.5, SPORE_R = 2.5;
    public static final float SWEEP_DMG = 14, CHARGE_DMG = 18, SHELL = 0.25F;
    public static final String ADD = "dreJungleAdd";

    private int sweepWind, chargeWind, charge, stun, sweepCd = 60, chargeCd = 120, sporeCd = 160;
    private double cdx, cdz;
    private boolean called;
    /** Споры: {x, y, z, тик облака, тик конца}. */
    private final List<double[]> spores = new ArrayList<double[]>();

    public EntityAncientDramcryx(World w) {
        super(w);
        setSize(2.6F, 3.4F);
        experienceValue = 500;
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        tasks.addTask(6, new EntityAIWander(this, 0.8D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(1500);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(20);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(48);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected String getLivingSound() {
        return "divinerpg:dramcryx";
    }

    @Override
    protected String getHurtSound() {
        return "divinerpg:dramcryxHurt";
    }

    @Override
    protected String getDeathSound() {
        return "divinerpg:dramcryxHurt";
    }

    @Override
    protected float getSoundPitch() {
        return 0.5F;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5F;
    }

    private static final int DW_STUN = 20;

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_STUN, Byte.valueOf((byte) 0));
    }

    /** Оглушён (видно и на клиенте - темнеет). */
    public boolean stunned() {
        return worldObj.isRemote ? dataWatcher.getWatchableObjectByte(DW_STUN) != 0 : stun > 0;
    }

    /** Живые порабощённые драмкриксы, которых он позвал. */
    @SuppressWarnings("unchecked")
    public int adds() {
        int n = 0;
        for (Object o : worldObj.getEntitiesWithinAABB(EntityLiving.class, boundingBox.expand(40, 16, 40)))
            if (((Entity) o).isEntityAlive() && ((Entity) o).getEntityData().getInteger(ADD) == getEntityId()) n++;
        return n;
    }

    @Override
    public boolean attackEntityFrom(DamageSource s, float amount) {
        if (!worldObj.isRemote && s != DamageSource.outOfWorld) {
            if (adds() > 0) {
                amount *= SHELL;
                ((WorldServer) worldObj).func_147487_a("magicCrit", posX, posY + height / 2, posZ, 10, 1.0, 1.0, 1.0, 0.1);
            }
            if (stun > 0) amount *= 1.5F;
        }
        return super.attackEntityFrom(s, amount);
    }

    private float rate() {
        return getHealth() < getMaxHealth() * 0.25F ? 0.6F : 1.0F; // ярость
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        WorldServer ws = (WorldServer) worldObj;
        sporeTick(ws);
        if ((dataWatcher.getWatchableObjectByte(DW_STUN) != 0) != (stun > 0)) dataWatcher.updateObject(DW_STUN, Byte.valueOf((byte) (stun > 0 ? 1 : 0)));
        if (!called && getHealth() <= getMaxHealth() / 2) callAdds();
        if (stun > 0) {
            stun--;
            motionX = motionZ = 0;
            getNavigator().clearPathEntity();
            if (stun % 8 == 0) ws.func_147487_a("magicCrit", posX, posY + height, posZ, 6, 0.6, 0.3, 0.6, 0.05);
            return;
        }
        if (sweepWind > 0) {
            sweepWind--;
            motionX = motionZ = 0;
            getNavigator().clearPathEntity();
            if (sweepWind % 3 == 0) ws.func_147487_a("crit", posX, posY + 0.3, posZ, 20, SWEEP_R / 2, 0.1, SWEEP_R / 2, 0.1);
            if (sweepWind == 0) sweep();
            return;
        }
        if (chargeWind > 0) {
            chargeWind--;
            motionX = motionZ = 0;
            getNavigator().clearPathEntity();
            if (chargeWind % 4 == 0) ws.func_147487_a("largesmoke", posX, posY + 0.2, posZ, 6, 1.0, 0.1, 1.0, 0.02);
            if (chargeWind == 0) {
                charge = CHARGE_TICKS;
                playSound("divinerpg:dramcryx", 3.0F, 0.4F);
            }
            return;
        }
        if (charge > 0) {
            chargeTick();
            return;
        }
        EntityPlayer t = getAttackTarget() instanceof EntityPlayer ? (EntityPlayer) getAttackTarget() : worldObj.getClosestVulnerablePlayerToEntity(this, 32);
        if (t == null) return;
        if (sweepCd > 0) sweepCd--;
        if (chargeCd > 0) chargeCd--;
        if (sporeCd > 0) sporeCd--;
        double d = getDistanceToEntity(t);
        if (sweepCd <= 0 && d < SWEEP_R) startSweep();
        else if (chargeCd <= 0 && d > 8 && onGround) startCharge(t);
        if (sporeCd <= 0) spores();
    }

    public void startSweep() {
        sweepWind = SWEEP_WIND;
        sweepCd = (int) (SWEEP_EVERY * rate());
        playSound("divinerpg:dramcryx", 2.5F, 0.6F);
    }

    /** Хвост по кругу: урон и отброс всем рядом. @return сколько задело */
    @SuppressWarnings("unchecked")
    public int sweep() {
        int n = 0;
        ((WorldServer) worldObj).func_147487_a("largeexplode", posX, posY + 0.5, posZ, 6, SWEEP_R / 2, 0.2, SWEEP_R / 2, 0);
        worldObj.playSoundAtEntity(this, "random.explode", 1.2F, 0.6F);
        for (EntityPlayer p : (List<EntityPlayer>) worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(SWEEP_R, 2, SWEEP_R))) {
            if (getDistanceToEntity(p) > SWEEP_R + width / 2) continue;
            p.attackEntityFrom(DamageSource.causeMobDamage(this), SWEEP_DMG);
            double dx = p.posX - posX, dz = p.posZ - posZ, l = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.addVelocity(dx / l * 1.4, 0.45, dz / l * 1.4);
            p.velocityChanged = true;
            n++;
        }
        return n;
    }

    public void startCharge(EntityPlayer t) {
        double dx = t.posX - posX, dz = t.posZ - posZ, l = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        cdx = dx / l;
        cdz = dz / l;
        chargeWind = CHARGE_WIND;
        chargeCd = (int) (CHARGE_EVERY * rate());
        rotationYaw = renderYawOffset = rotationYawHead = (float) Math.toDegrees(Math.atan2(-dx, dz));
        playSound("divinerpg:dramcryx", 2.5F, 0.3F);
    }

    @SuppressWarnings("unchecked")
    private void chargeTick() {
        getNavigator().clearPathEntity();
        if (charge < CHARGE_TICKS - 2 && isCollidedHorizontally) {
            charge = 0;
            stun = STUN;
            worldObj.playSoundAtEntity(this, "random.anvil_land", 1.5F, 0.5F);
            ((WorldServer) worldObj).func_147487_a("explode", posX, posY + 1, posZ, 12, 1.0, 1.0, 1.0, 0.05);
            return;
        }
        charge--;
        motionX = cdx * 1.0;
        motionZ = cdz * 1.0;
        velocityChanged = true;
        for (EntityPlayer p : (List<EntityPlayer>) worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(0.6, 0.3, 0.6))) {
            if (p.hurtResistantTime > 10) continue;
            p.attackEntityFrom(DamageSource.causeMobDamage(this), CHARGE_DMG);
            p.addVelocity(cdx * 1.6, 0.6, cdz * 1.6);
            p.velocityChanged = true;
        }
    }

    /** Отметить места спор под игроками рядом (до трёх). @return сколько */
    @SuppressWarnings("unchecked")
    public int spores() {
        sporeCd = (int) (SPORE_EVERY * rate());
        long now = worldObj.getTotalWorldTime();
        int n = 0;
        for (EntityPlayer p : (List<EntityPlayer>) worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(24, 8, 24))) {
            if (n >= 3) break;
            spores.add(new double[] { p.posX, p.posY, p.posZ, now + SPORE_FUSE, now + SPORE_FUSE + SPORE_LIFE });
            n++;
        }
        if (n > 0) playSound("mob.slime.big", 1.5F, 0.5F);
        return n;
    }

    @SuppressWarnings("unchecked")
    private void sporeTick(WorldServer ws) {
        long now = worldObj.getTotalWorldTime();
        for (Iterator<double[]> it = spores.iterator(); it.hasNext();) {
            double[] s = it.next();
            if (now >= s[4]) {
                it.remove();
                continue;
            }
            if (now < s[3]) {
                if (now % 3 == 0) ws.func_147487_a("happyVillager", s[0], s[1] + 0.1, s[2], 6, SPORE_R / 2, 0.05, SPORE_R / 2, 0);
                continue;
            }
            if (now % 4 == 0) ws.func_147487_a("mobSpell", s[0], s[1] + 0.6, s[2], 12, SPORE_R / 2, 0.5, SPORE_R / 2, 0.1);
            if (now % 10 != 0) continue;
            for (EntityPlayer p : (List<EntityPlayer>) worldObj.getEntitiesWithinAABB(EntityPlayer.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(s[0] - SPORE_R, s[1] - 1, s[2] - SPORE_R, s[0] + SPORE_R, s[1] + 2.5, s[2] + SPORE_R))) {
                p.addPotionEffect(new PotionEffect(Potion.poison.id, 60, 1));
                p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 0));
            }
        }
    }

    /** Отравить игрока спорами прямо сейчас (для проверок): облако там, где он стоит. */
    public void sporeAt(double x, double y, double z) {
        long now = worldObj.getTotalWorldTime();
        spores.add(new double[] { x, y, z, now, now + SPORE_LIFE });
    }

    /** Зов порабощённых драмкриксов. @return сколько пришло */
    public int callAdds() {
        called = true;
        int n = 0;
        for (int i = 0; i < ADDS; i++) {
            net.divinerpg.entities.vanilla.EntityEnthralledDramcryx a = new net.divinerpg.entities.vanilla.EntityEnthralledDramcryx(worldObj);
            double ang = i * Math.PI * 2 / ADDS;
            a.setLocationAndAngles(posX + Math.cos(ang) * 4, posY + 0.5, posZ + Math.sin(ang) * 4, 0, 0);
            if (!worldObj.getCollidingBoundingBoxes(a, a.boundingBox).isEmpty()) a.setLocationAndAngles(posX, posY + 0.5, posZ, 0, 0);
            a.getEntityData().setInteger(ADD, getEntityId());
            a.getEntityData().setBoolean("dreDemonAdd", true); // успокоение арены и бестиарий их не трогают
            if (getAttackTarget() != null) a.setAttackTarget(getAttackTarget());
            if (worldObj.spawnEntityInWorld(a)) n++;
        }
        playSound("divinerpg:dramcryx", 3.0F, 0.35F);
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("largesmoke", posX, posY + 2, posZ, 40, 3, 1, 3, 0.05);
        return n;
    }

    @Override
    protected void dropFewItems(boolean hit, int looting) {
        entityDropItem(new ItemStack(net.divinerpg.utils.items.VanillaItemsOther.jungleShards, 8 + rand.nextInt(5 + looting)), 1);
        entityDropItem(new ItemStack(net.divinerpg.utils.items.VanillaItemsOther.jungleStone, 2 + rand.nextInt(2)), 1);
        entityDropItem(new ItemStack(net.minecraft.init.Items.emerald, 3 + rand.nextInt(4)), 1);
    }
}
