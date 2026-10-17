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
 * стая Вереков. Чтобы не быть грушей: пока есть руки, вырывает и швыряет глыбы ({@link EntityBoulder}) в тех, кто
 * держится на расстоянии; пока есть ноги, прыжком сокращает дистанцию (с топотом при приземлении); далёкую цель
 * подтягивает лианой. Лесной следопыт (сет Дикого леса) видит суставы - по частям урон x{@link #TRACKER_BONUS}.
 */
public class EntityAncientGolem extends EntityGroveGuardian {

    public static final int ARM_R = 0, ARM_L = 1, LEGS = 2, CORE = 3;
    /** Доли здоровья частей: руки, ноги, ядро. */
    public static final float[] SHARE = { 0.2F, 0.2F, 0.25F, 0.35F };
    public static final float TRACKER_BONUS = 1.5F;
    public static final double EXTRA_HP = 200;
    public static final float PHASER_BONUS = 2.0F;
    /** Замах лианы (тиков): в это время слайсер её перерезает. */
    public static final int LASH_WINDUP = 15;
    private EntityPlayer lashTarget;
    private int lashWindup;
    public static final float SCALE = 2.2F;
    private static final int DW_BROKEN = 21, DW_FIRST = 22;
    /** Модель: ширина корпуса и плеч, высота ног (доля роста). */
    private static final double SIDE = 1.0, LEG_SHARE = 0.33;

    private final float[] partHp = new float[4];
    private int stompTimer = 120, lashTimer = 80, packTimer = 160, lastPunch = -100, stagger;
    private int boulderTimer = 60, leapTimer = 100, pullTimer = 200, answerTimer;
    /** Ответ стрелку: дальше этого - лиана подтягивает, перезарядка ответа (тики). */
    public static final double ARCHER_DIST = 6;
    public static final int ANSWER_CD = 60;
    private boolean leaping;
    /** Родники капища: когда каждый снова проснётся (время мира) и кто сколько в нём стоит. */
    private final long[] springWake = new long[4];
    private final java.util.Map<Integer, Integer> inSpring = new java.util.HashMap<Integer, Integer>();
    /** Сколько можно стоять в роднике до гейзера, сколько он спит после, лечение (Лесной следопыт) раз в 5 тиков. */
    public static final int SPRING_STAY = 30, SPRING_SLEEP = 400;
    public static final float SPRING_HEAL = 1.0F;
    private long clangHint = -1000;

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
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
        // крепче стража мира: частей много, а убить его должно быть не быстрее кадиллиона
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(getEntityAttribute(SharedMonsterAttributes.maxHealth).getBaseValue() + EXTRA_HP);
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
            if (part == CORE) clang(hit, src.getEntity());
            return false;
        }
        if (src.getEntity() instanceof EntityPlayer && com.divinerpgextended.perk.HomeArmor.wears((EntityPlayer) src.getEntity(), TwilightWorld.WILDWOOD, false))
            amount *= TRACKER_BONUS;
        answerArcher(src);
        int rk = com.divinerpgextended.perk.RangedRoles.kind(src.getSourceOfDamage());
        if (rk == com.divinerpgextended.perk.RangedRoles.PHASER) amount *= PHASER_BONUS; // пробой по частям
        if (rk == com.divinerpgextended.perk.RangedRoles.SLICER && lashTarget != null) cutVine();
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

    private void clang(Vec3 at, Entity who) {
        worldObj.playSoundAtEntity(this, "random.anvil_land", 0.6F, 1.6F);
        if (who instanceof EntityPlayer && worldObj.getTotalWorldTime() - clangHint > 200) { // не чаще раза в 10 с
            clangHint = worldObj.getTotalWorldTime();
            ((EntityPlayer) who).addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.chat.golem_clang"));
        }
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
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.2D);
        }
        if (coreOpen()) worldObj.playSoundAtEntity(this, "mob.zombie.woodbreak", 2.0F, 0.5F);
        // шатается 2 с: видно, что что-то отвалилось
        stagger = 40;
        String key = coreOpen() ? "dre.chat.golem_core" : part == LEGS ? "dre.chat.golem_legs"
            : broken(ARM_R) && broken(ARM_L) ? "dre.chat.golem_arms" : part == ARM_R ? "dre.chat.golem_arm_r" : "dre.chat.golem_arm_l";
        for (EntityPlayer p : players(30)) {
            net.minecraft.util.ChatComponentTranslation m = new net.minecraft.util.ChatComponentTranslation(key);
            m.getChatStyle().setColor(net.minecraft.util.EnumChatFormatting.GREEN);
            p.addChatMessage(m);
        }
        String[] names = { "правая рука", "левая рука", "ноги" };
        com.divinerpgextended.debug.Debug.noteNear(this, 40, "Голем: отбита " + names[part] + ", здоровье " + (int) getHealth() + (coreOpen() ? ", ядро открыто" : ""));
    }

    // ---------------- атаки ----------------

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            if (broken(LEGS) && height > 5) setSize(3.4F, 4.4F);
            return;
        }
        springs();
        lashTick();
        if (answerTimer > 0) answerTimer--;
        if (stagger > 0) {
            stagger--;
            motionX = motionZ = 0;
            getNavigator().clearPathEntity();
            rotationYaw += (rand.nextFloat() - 0.5F) * 10; // качается
            if (stagger % 4 == 0) ((WorldServer) worldObj).func_147487_a("blockcrack_"
                + net.minecraft.block.Block.getIdFromBlock(net.divinerpg.utils.blocks.TwilightBlocks.wildwoodLogs) + "_0", posX, posY + height * 0.5, posZ, 10, 1, 1, 1, 0.1);
            return;
        }
        EntityLivingBase t = getAttackTarget();
        if (coreOpen() && ticksExisted % 10 == 0)
            ((WorldServer) worldObj).func_147487_a("happyVillager", posX, posY + height * 0.6, posZ, 8, 0.6, 0.6, 0.6, 0);
        if (t == null) return;
        if (leaping && onGround && motionY <= 0) { // приземлился после прыжка
            leaping = false;
            stomp();
        }
        double dist = getDistanceToEntity(t);
        boolean anyArm = !broken(ARM_R) || !broken(ARM_L);
        if (anyArm && --boulderTimer <= 0 && dist > 6 && dist < 30 && canEntityBeSeen(t)) {
            boulderTimer = broken(LEGS) ? 70 : 110;
            throwBoulder(t);
        }
        if (!broken(LEGS) && --leapTimer <= 0 && dist > 10 && onGround) {
            leapTimer = 180 + rand.nextInt(60);
            leap(t);
        }
        if (anyArm && --pullTimer <= 0 && dist > 14) {
            pullTimer = 240;
            lash((EntityPlayer) (t instanceof EntityPlayer ? t : null));
        }
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

    /**
     * Родники капища: стоящего в воде родника Лесной следопыт лечит; через {@link #SPRING_STAY} тиков бьёт гейзер и
     * выкидывает любого, родник засыпает на {@link #SPRING_SLEEP}.
     */
    private void springs() {
        int[] home = com.divinerpgextended.boss.LairSummons.home(this);
        if (home == null) return;
        long now = worldObj.getTotalWorldTime();
        WorldServer ws = (WorldServer) worldObj;
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
        for (int i = 0; i < 4; i++) {
            int[] s = com.divinerpgextended.world.twilight.WorldGenNests.spring(i);
            double sx = home[0] + s[0] + 0.5, sz = home[2] + s[1] + 0.5, sy = home[1] - 0.5;
            boolean awake = now >= springWake[i];
            if (awake && ticksExisted % 10 == 0) ws.func_147487_a("splash", sx, sy + 0.6, sz, 6, 0.6, 0.1, 0.6, 0);
            for (EntityPlayer p : players(30)) {
                double dx = p.posX - sx, dz = p.posZ - sz;
                if (dx * dx + dz * dz > 2.6 || Math.abs(p.posY - sy) > 1.5 || !p.isInWater() || !awake) continue;
                seen.add(p.getEntityId());
                Integer t0 = inSpring.get(p.getEntityId());
                int t = t0 == null ? 0 : t0 + 1;
                inSpring.put(p.getEntityId(), t);
                if (t % 5 == 0 && com.divinerpgextended.perk.HomeArmor.wears(p, TwilightWorld.WILDWOOD, false)) {
                    p.heal(SPRING_HEAL);
                    ws.func_147487_a("heart", p.posX, p.posY + 1.8, p.posZ, 1, 0.3, 0.2, 0.3, 0);
                }
                if (t >= SPRING_STAY) {
                    p.motionY = 1.3;
                    p.motionX = dx * 0.4;
                    p.motionZ = dz * 0.4;
                    p.velocityChanged = true;
                    p.fallDistance = 0;
                    springWake[i] = now + SPRING_SLEEP;
                    inSpring.remove(p.getEntityId());
                    worldObj.playSoundEffect(sx, sy + 1, sz, "random.splash", 2.0F, 0.6F);
                    ws.func_147487_a("splash", sx, sy + 1, sz, 60, 0.5, 2.0, 0.5, 0.3);
                    ws.func_147487_a("cloud", sx, sy + 1, sz, 20, 0.4, 1.5, 0.4, 0.05);
                }
            }
        }
        inSpring.keySet().retainAll(seen);
    }

    /** Родник спит (для тестов и подсказок). */
    public boolean springAwake(int i) {
        return worldObj.getTotalWorldTime() >= springWake[i];
    }

    /** Стрелок издалека получает ответ: лиана подтягивает его к голему (или глыба, если лиане не дотянуться). */
    private void answerArcher(DamageSource src) {
        if (!src.isProjectile() || !(src.getEntity() instanceof EntityPlayer) || answerTimer > 0) return;
        EntityPlayer p = (EntityPlayer) src.getEntity();
        double d = p.getDistanceToEntity(this);
        if (d < ARCHER_DIST) return;
        answerTimer = ANSWER_CD;
        setAttackTarget(p);
        if (d < 24 || (broken(ARM_R) && broken(ARM_L))) lash(p);
        else throwBoulder(p);
    }

    /** Вырвать глыбу из земли и швырнуть дугой в цель. */
    private void throwBoulder(EntityLivingBase t) {
        EntityBoulder b = new EntityBoulder(worldObj, this, (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * 0.7F);
        b.setPosition(posX, posY + height * 0.75, posZ);
        double dx = t.posX - b.posX, dz = t.posZ - b.posZ, dy = t.posY + 0.5 - b.posY, d = Math.sqrt(dx * dx + dz * dz);
        b.setThrowableHeading(dx, dy + d * 0.35, dz, (float) (0.9 + d * 0.035), 2.0F);
        worldObj.spawnEntityInWorld(b);
        int dirt = net.minecraft.block.Block.getIdFromBlock(net.divinerpg.utils.blocks.TwilightBlocks.wildwoodDirt);
        ((WorldServer) worldObj).func_147487_a("blockcrack_" + dirt + "_0", posX, posY + 0.3, posZ, 30, 1.5, 0.2, 1.5, 0.2);
        worldObj.playSoundAtEntity(this, "dig.gravel", 2.0F, 0.5F);
        worldObj.playSoundAtEntity(this, "mob.irongolem.throw", 2.0F, 0.5F);
    }

    /** Прыжок к цели (приземление - топот). */
    private void leap(EntityLivingBase t) {
        double dx = t.posX - posX, dz = t.posZ - posZ, d = Math.sqrt(dx * dx + dz * dz);
        double speed = Math.min(1.6, d / 14);
        motionX = dx / d * speed;
        motionZ = dz / d * speed;
        motionY = 0.85;
        velocityChanged = true;
        leaping = true;
        worldObj.playSoundAtEntity(this, "mob.irongolem.throw", 2.0F, 0.3F);
    }

    /** Лиана: замах {@link #LASH_WINDUP} тиков (тянется зелёная дорожка), потом удар. */
    private void lash(EntityPlayer p) {
        if (p == null || p.getDistanceToEntity(this) < 4 || lashTarget != null) return;
        lashTarget = p;
        lashWindup = LASH_WINDUP;
        worldObj.playSoundAtEntity(this, "dig.grass", 2.0F, 0.4F);
    }

    /** Слайсер перерезал лиану на замахе. */
    private void cutVine() {
        lashTarget = null;
        lashWindup = 0;
        worldObj.playSoundAtEntity(this, "mob.sheep.shear", 2.0F, 0.8F);
        ((WorldServer) worldObj).func_147487_a("slime", posX, posY + height * 0.6, posZ, 20, 1, 0.5, 1, 0.1);
        com.divinerpgextended.debug.Debug.noteNear(this, 40, "Голем: лиана перерезана");
    }

    public boolean lashing() {
        return lashTarget != null;
    }

    private void lashTick() {
        if (lashTarget == null) return;
        EntityPlayer p = lashTarget;
        if (!p.isEntityAlive() || p.worldObj != worldObj) {
            lashTarget = null;
            return;
        }
        WorldServer ws = (WorldServer) worldObj;
        double k = 1 - lashWindup / (double) LASH_WINDUP; // лиана дотягивается
        ws.func_147487_a("slime", posX + (p.posX - posX) * k, posY + height * 0.6 + (p.posY + 1 - posY - height * 0.6) * k,
            posZ + (p.posZ - posZ) * k, 3, 0.1, 0.1, 0.1, 0);
        if (--lashWindup > 0) return;
        lashTarget = null;
        lashHit(p);
    }

    /** Удар лианы: бьёт, подтягивает к голему и опутывает (замедление). */
    private void lashHit(EntityPlayer p) {
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
        if (stagger > 0 || broken(ARM_R) && broken(ARM_L)) return false;
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
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.2D);
        }
    }
}
