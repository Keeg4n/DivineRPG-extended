package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.world.CadillionWorld;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Древний голем Дикого леса, страж рощи - босс-конструктор. Тело из частей со своим здоровьем: правая и левая руки,
 * ноги, моховое ядро. Куда попал удар (по высоте и стороне), та часть и получает урон; ядро закрыто, пока целы руки или
 * ноги - удар по корпусу только звенит. Сломанная часть отваливается и забирает свою атаку:
 * <ul>
 * <li>руки - удары кулаком (каждая своя сторона); без обеих рук голем хлещет лианами издалека и подтягивает к себе;</li>
 * <li>ноги - топот (волна подбрасывает рядом); без ног голем ползает и зовёт Вереков.</li>
 * </ul>
 * Что сломано первым, то решает последнюю фазу (ядро): первыми руки - лиановая буря по всем на поляне, первыми ноги -
 * стая Вереков. Лесной следопыт (сет Дикого леса) видит суставы - по частям урон x{@link #TRACKER_BONUS}.
 */
public class EntityAncientGolem extends EntityGroveGuardian {

    public static final int ARM_R = 0, ARM_L = 1, LEGS = 2, CORE = 3;
    /** Доли здоровья частей: руки, ноги, ядро. */
    public static final float[] SHARE = { 0.2F, 0.2F, 0.25F, 0.35F };
    public static final float TRACKER_BONUS = 1.5F;
    public static final float SCALE = 2.2F;
    private static final int DW_BROKEN = 21, DW_FIRST = 22;
    /** Модель: ширина корпуса и плеч, высота ног (доля роста). */
    private static final double SIDE = 1.0, LEG_SHARE = 0.33;

    private final float[] partHp = new float[4];
    private int stompTimer = 120, lashTimer = 80, packTimer = 160, lastPunch = -100;

    public EntityAncientGolem(World world) {
        super(world);
        setSize(3.4F, 6.6F);
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        for (int i = 0; i < 4; i++) partHp[i] = getMaxHealth() * SHARE[i];
    }

    @Override
    public CadillionWorld world() {
        return CadillionWorld.WILDWOOD;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_BROKEN, Byte.valueOf((byte) 0));
        dataWatcher.addObject(DW_FIRST, Byte.valueOf((byte) -1));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.24D);
    }

    public boolean broken(int part) {
        return (dataWatcher.getWatchableObjectByte(DW_BROKEN) & (1 << part)) != 0;
    }

    public float partHealth(int part) {
        return partHp[part];
    }

    /** Ядро открыто: руки и ноги разбиты. */
    public boolean coreOpen() {
        return broken(ARM_R) && broken(ARM_L) && broken(LEGS);
    }

    /** Что разбито первым: ARM_R/ARM_L (руки) или LEGS, -1 - пока ничего; решает последнюю фазу. */
    public int firstBroken() {
        return dataWatcher.getWatchableObjectByte(DW_FIRST);
    }

    private boolean armsFirst() {
        int f = firstBroken();
        return f == ARM_R || f == ARM_L;
    }

    // ---------------- куда попал удар ----------------

    /** Точка попадания: снаряд - где он сейчас, удар рукой - луч взгляда до тела голема. */
    private Vec3 hitPoint(DamageSource src) {
        Entity direct = src.getSourceOfDamage(), owner = src.getEntity();
        if (direct != null && direct != owner) return Vec3.createVectorHelper(direct.posX, direct.posY, direct.posZ);
        if (owner instanceof EntityLivingBase) {
            EntityLivingBase a = (EntityLivingBase) owner;
            Vec3 eye = Vec3.createVectorHelper(a.posX, a.posY + a.getEyeHeight(), a.posZ);
            Vec3 look = a.getLookVec();
            Vec3 end = eye.addVector(look.xCoord * 8, look.yCoord * 8, look.zCoord * 8);
            MovingObjectPosition mop = boundingBox.expand(0.3, 0.3, 0.3).calculateIntercept(eye, end);
            if (mop != null) return mop.hitVec;
            return Vec3.createVectorHelper(posX, MathHelper.clamp_double(eye.yCoord, posY, posY + height), posZ);
        }
        return null;
    }

    /** Часть тела по точке попадания (в системе модели: x вправо от голема - его левая рука). */
    public int partAt(double x, double y, double z) {
        double f = (y - posY) / height;
        if (f < LEG_SHARE) return LEGS;
        double yaw = Math.toRadians(renderYawOffset);
        double side = (x - posX) * Math.cos(yaw) + (z - posZ) * Math.sin(yaw);
        if (side < -SIDE) return ARM_R;
        if (side > SIDE) return ARM_L;
        return CORE;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (worldObj.isRemote || outOfLair(src.getEntity())) return super.attackEntityFrom(src, amount);
        Vec3 hit = hitPoint(src);
        int part = hit == null ? CORE : partAt(hit.xCoord, hit.yCoord, hit.zCoord);
        if (coreOpen()) part = CORE;
        else if (part == CORE || broken(part)) {
            // корпус закрыт - удар звенит; по уже отбитой части - ничего
            if (part == CORE) clang(hit);
            return false;
        }
        if (src.getEntity() instanceof EntityPlayer && com.divinerpgextended.perk.HomeArmor.wears((EntityPlayer) src.getEntity(), TwilightWorld.WILDWOOD, false))
            amount *= TRACKER_BONUS;
        amount = Math.min(amount, partHp[part]);
        float before = getHealth();
        boolean ok = super.attackEntityFrom(src, amount);
        float dealt = before - getHealth();
        if (dealt > 0) {
            partHp[part] -= dealt;
            if (partHp[part] <= 0.01F && part != CORE) breakPart(part);
        }
        return ok;
    }

    private void clang(Vec3 at) {
        worldObj.playSoundAtEntity(this, "random.anvil_land", 0.6F, 1.6F);
        if (at != null) ((WorldServer) worldObj).func_147487_a("crit", at.xCoord, at.yCoord, at.zCoord, 6, 0.2, 0.2, 0.2, 0.2);
    }

    private void breakPart(int part) {
        partHp[part] = 0;
        int mask = dataWatcher.getWatchableObjectByte(DW_BROKEN) | 1 << part;
        dataWatcher.updateObject(DW_BROKEN, Byte.valueOf((byte) mask));
        if (firstBroken() < 0) dataWatcher.updateObject(DW_FIRST, Byte.valueOf((byte) part));
        worldObj.playSoundAtEntity(this, "random.break", 2.0F, 0.5F);
        worldObj.playSoundAtEntity(this, "dig.wood", 2.0F, 0.6F);
        int logId = net.minecraft.block.Block.getIdFromBlock(net.divinerpg.utils.blocks.TwilightBlocks.wildwoodLogs);
        double y = part == LEGS ? posY + 1 : posY + height * 0.7;
        ((WorldServer) worldObj).func_147487_a("blockcrack_" + logId + "_0", posX, y, posZ, 60, 1.2, 0.8, 1.2, 0.2);
        if (part == LEGS) {
            setSize(3.4F, 4.4F); // осел на корпус
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.12D);
        }
        if (coreOpen()) worldObj.playSoundAtEntity(this, "mob.zombie.woodbreak", 2.0F, 0.5F);
        com.divinerpgextended.debug.Debug.note(this, "Голем: отбита часть " + part + (coreOpen() ? ", ядро открыто" : ""));
    }

    // ---------------- атаки ----------------

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            if (broken(LEGS) && height > 5) setSize(3.4F, 4.4F);
            return;
        }
        EntityLivingBase t = getAttackTarget();
        if (coreOpen() && ticksExisted % 10 == 0)
            ((WorldServer) worldObj).func_147487_a("happyVillager", posX, posY + height * 0.6, posZ, 8, 0.6, 0.6, 0.6, 0);
        if (t == null) return;
        if (!broken(LEGS) && --stompTimer <= 0) stomp();
        boolean noArms = broken(ARM_R) && broken(ARM_L);
        if (noArms && --lashTimer <= 0) {
            boolean storm = coreOpen() && armsFirst();
            lashTimer = storm ? 50 : 80;
            for (EntityPlayer p : players(20)) {
                if (storm || p == t) lash(p);
            }
        }
        if (broken(LEGS) && --packTimer <= 0) {
            boolean pack = coreOpen() && !armsFirst();
            packTimer = pack ? 140 : 240;
            int want = pack ? 4 : 2;
            for (int i = 0; i < 2 && addsAlive() < want; i++) summon(net.divinerpg.entities.twilight.EntityVerek.class, 5);
            worldObj.playSoundAtEntity(this, "mob.wolf.howl", 2.0F, 0.6F);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EntityPlayer> players(double r) {
        return worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(r, 10, r));
    }

    /** Топот: волна по земле - подбрасывает и бьёт тех, кто рядом (перепрыгнуть или отойти). */
    private void stomp() {
        stompTimer = 160 + rand.nextInt(60);
        worldObj.playSoundAtEntity(this, "random.explode", 1.5F, 0.5F);
        int dirt = net.minecraft.block.Block.getIdFromBlock(net.divinerpg.utils.blocks.TwilightBlocks.wildwoodDirt);
        ((WorldServer) worldObj).func_147487_a("blockcrack_" + dirt + "_0", posX, posY + 0.2, posZ, 80, 4, 0.1, 4, 0.3);
        for (EntityPlayer p : players(7)) {
            if (!p.onGround || p.getDistanceSqToEntity(this) > 64) continue;
            p.attackEntityFrom(DamageSource.causeMobDamage(this), (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * 0.8F);
            p.motionY = 0.9;
            p.velocityChanged = true;
        }
    }

    /** Лиана: хлещет цель издалека, подтягивает к голему и опутывает (замедление). */
    private void lash(EntityPlayer p) {
        if (p.getDistanceToEntity(this) < 4) return;
        p.attackEntityFrom(DamageSource.causeMobDamage(this), (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * 0.5F);
        double dx = posX - p.posX, dz = posZ - p.posZ, d = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        p.motionX += dx / d * 1.1;
        p.motionZ += dz / d * 1.1;
        p.motionY += 0.3;
        p.velocityChanged = true;
        p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1));
        WorldServer ws = (WorldServer) worldObj;
        for (int i = 0; i <= 10; i++) {
            double k = i / 10.0;
            ws.func_147487_a("slime", posX + (p.posX - posX) * k, posY + height * 0.6 + (p.posY + 1 - posY - height * 0.6) * k,
                posZ + (p.posZ - posZ) * k, 2, 0.05, 0.05, 0.05, 0);
        }
        worldObj.playSoundAtEntity(p, "dig.grass", 1.5F, 0.6F);
    }

    /** Кулак: только живой рукой со стороны цели (или любой живой), с перезарядкой. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        if (broken(ARM_R) && broken(ARM_L)) return false;
        if (ticksExisted - lastPunch < 30) return false;
        double dx = target.posX - posX, dz = target.posZ - posZ;
        if (dx * dx + dz * dz > 5.0 * 5.0) return false;
        lastPunch = ticksExisted;
        boolean hit = super.attackEntityAsMob(target);
        if (hit) {
            target.motionY += 0.4;
            target.velocityChanged = true;
        }
        return hit;
    }

    @Override
    protected String getLivingSound() {
        return net.divinerpg.libs.Sounds.getSoundName(net.divinerpg.libs.Sounds.growlHurt);
    }

    @Override
    protected String getHurtSound() {
        return "dig.wood";
    }

    @Override
    protected String getDeathSound() {
        return "mob.zombie.woodbreak";
    }

    @Override
    protected float getSoundPitch() {
        return 0.5F;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setByte("Broken", dataWatcher.getWatchableObjectByte(DW_BROKEN));
        tag.setByte("First", (byte) firstBroken());
        for (int i = 0; i < 4; i++) tag.setFloat("Part" + i, partHp[i]);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        dataWatcher.updateObject(DW_BROKEN, Byte.valueOf(tag.getByte("Broken")));
        dataWatcher.updateObject(DW_FIRST, Byte.valueOf(tag.hasKey("First") ? tag.getByte("First") : (byte) -1));
        if (tag.hasKey("Part0")) for (int i = 0; i < 4; i++) partHp[i] = tag.getFloat("Part" + i);
        if (broken(LEGS)) {
            setSize(3.4F, 4.4F);
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.12D);
        }
    }
}
