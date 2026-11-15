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
 * Wildwood Ancient Golem, guardian of the grove - a constructor boss. The body is made of parts with their own health: the right and left arms,
 * the legs, the moss core. The part where the hit lands (by height and side) takes the damage; the core is closed while the arms or
 * legs are intact - a hit on the torso only rings. A broken part falls off and takes its attack with it:
 * <ul>
 * <li>arms - fist strikes (each its own side); without both arms the golem lashes with vines from afar and pulls you toward itself;</li>
 * <li>legs - stomping (the wave throws up those nearby); without legs the golem crawls and summons Vereks.</li>
 * </ul>
 * What is broken first decides the last phase (the core): arms first - a vine storm on everyone in the clearing, legs first -
 * a pack of Vereks. So as not to be a punching bag: while it has arms, it tears out and hurls boulders ({@link EntityBoulder}) at those who
 * keep their distance; while it has legs, it closes the distance with a jump (with a stomp on landing); a far target
 * is pulled in with a vine. The Forest Tracker (the Wildwood set) sees the joints - damage to parts x{@link #TRACKER_BONUS}.
 */
public class EntityAncientGolem extends EntityGroveGuardian {

    public static final int ARM_R = 0, ARM_L = 1, LEGS = 2, CORE = 3;
    /** Health fractions of the parts: arms, legs, core. */
    public static final float[] SHARE = { 0.2F, 0.2F, 0.25F, 0.35F };
    public static final float TRACKER_BONUS = 1.5F;
    public static final double EXTRA_HP = 200;
    public static final float PHASER_BONUS = 2.0F;
    /** Vine windup (ticks): during this time the slicer cuts it. */
    public static final int LASH_WINDUP = 15;
    private EntityPlayer lashTarget;
    private int lashWindup;
    public static final float SCALE = 2.2F;
    private static final int DW_BROKEN = 21, DW_FIRST = 22;
    /** Model: width of the torso and shoulders, height of the legs (a fraction of the height). */
    private static final double SIDE = 1.0, LEG_SHARE = 0.33;

    private final float[] partHp = new float[4];
    private int stompTimer = 120, lashTimer = 80, packTimer = 160, lastPunch = -100, stagger;
    private int boulderTimer = 60, leapTimer = 100, pullTimer = 200, answerTimer;
    /** Reply to a shooter: beyond this distance the vine pulls, reply cooldown (ticks). */
    public static final double ARCHER_DIST = 6;
    public static final int ANSWER_CD = 60;
    private boolean leaping;
    /** Shrine springs: when each wakes up again (world time) and who has stood in it for how long. */
    private final long[] springWake = new long[4];
    private final java.util.Map<Integer, Integer> inSpring = new java.util.HashMap<Integer, Integer>();
    /** How long you can stand in a spring before the geyser, how long it sleeps afterwards, healing (Forest Tracker) once per 5 ticks. */
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
        // sturdier than the world guardian: there are many parts, but killing it should not be faster than the cadillion
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(getEntityAttribute(SharedMonsterAttributes.maxHealth).getBaseValue() + EXTRA_HP);
    }

    public boolean broken(int part) {
        return (dataWatcher.getWatchableObjectByte(DW_BROKEN) & (1 << part)) != 0;
    }

    public float partHealth(int part) {
        return partHp[part];
    }

    /** The core is open: the arms and legs are broken. */
    public boolean coreOpen() {
        return broken(ARM_R) && broken(ARM_L) && broken(LEGS);
    }

    /** What was broken first: ARM_R/ARM_L (arms) or LEGS, -1 - nothing yet; decides the last phase. */
    public int firstBroken() {
        return dataWatcher.getWatchableObjectByte(DW_FIRST);
    }

    private boolean armsFirst() {
        int f = firstBroken();
        return f == ARM_R || f == ARM_L;
    }

    // ---------------- where the hit landed ----------------

    /** The hit point: for a projectile - where it is now, for a hand hit - the look ray to the golem's body. */
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

    /** The body part by the hit point (in model space: x to the right of the golem is its left arm). */
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
            // the torso is closed - the hit rings; on an already knocked-off part - nothing
            if (part == CORE) clang(hit, src.getEntity());
            return false;
        }
        if (src.getEntity() instanceof EntityPlayer && com.divinerpgextended.perk.HomeArmor.wears((EntityPlayer) src.getEntity(), TwilightWorld.WILDWOOD, false))
            amount *= TRACKER_BONUS;
        answerArcher(src);
        int rk = com.divinerpgextended.perk.RangedRoles.kind(src.getSourceOfDamage());
        if (rk == com.divinerpgextended.perk.RangedRoles.PHASER) amount *= PHASER_BONUS; // breakthrough by parts
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
        if (who instanceof EntityPlayer && worldObj.getTotalWorldTime() - clangHint > 200) { // no more than once per 10 s
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
            setSize(3.4F, 4.4F); // settled onto the torso
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.2D);
        }
        if (coreOpen()) worldObj.playSoundAtEntity(this, "mob.zombie.woodbreak", 2.0F, 0.5F);
        // staggers for 2 s: it is visible that something fell off
        stagger = 40;
        String key = coreOpen() ? "dre.chat.golem_core" : part == LEGS ? "dre.chat.golem_legs"
            : broken(ARM_R) && broken(ARM_L) ? "dre.chat.golem_arms" : part == ARM_R ? "dre.chat.golem_arm_r" : "dre.chat.golem_arm_l";
        for (EntityPlayer p : players(30)) {
            net.minecraft.util.ChatComponentTranslation m = new net.minecraft.util.ChatComponentTranslation(key);
            m.getChatStyle().setColor(net.minecraft.util.EnumChatFormatting.GREEN);
            p.addChatMessage(m);
        }
        String[] names = { "right arm", "left arm", "legs" };
        com.divinerpgextended.debug.Debug.noteNear(this, 40, "Golem: knocked off " + names[part] + ", health " + (int) getHealth() + (coreOpen() ? ", core open" : ""));
    }

    // ---------------- attacks ----------------

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
            rotationYaw += (rand.nextFloat() - 0.5F) * 10; // sways
            if (stagger % 4 == 0) ((WorldServer) worldObj).func_147487_a("blockcrack_"
                + net.minecraft.block.Block.getIdFromBlock(net.divinerpg.utils.blocks.TwilightBlocks.wildwoodLogs) + "_0", posX, posY + height * 0.5, posZ, 10, 1, 1, 1, 0.1);
            return;
        }
        EntityLivingBase t = getAttackTarget();
        if (coreOpen() && ticksExisted % 10 == 0)
            ((WorldServer) worldObj).func_147487_a("happyVillager", posX, posY + height * 0.6, posZ, 8, 0.6, 0.6, 0.6, 0);
        if (t == null) return;
        if (leaping && onGround && motionY <= 0) { // landed after a jump
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

    /** Stomp: a wave along the ground - throws up and hits those nearby (jump over it or step away). */
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
     * Shrine springs: the Forest Tracker standing in the spring water is healed; after {@link #SPRING_STAY} ticks a geyser hits and
     * throws out anyone, and the spring falls asleep for {@link #SPRING_SLEEP}.
     */
    private void springs() {
        int[] home = com.divinerpgextended.boss.LairSummons.home(this);
        if (home == null) return;
        long now = worldObj.getTotalWorldTime();
        WorldServer ws = (WorldServer) worldObj;
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
        List<EntityPlayer> near = players(30);
        for (int i = 0; i < 4; i++) {
            int[] s = com.divinerpgextended.world.twilight.WorldGenNests.spring(i);
            double sx = home[0] + s[0] + 0.5, sz = home[2] + s[1] + 0.5, sy = home[1] - 0.5;
            boolean awake = now >= springWake[i];
            if (awake && ticksExisted % 10 == 0) ws.func_147487_a("splash", sx, sy + 0.6, sz, 6, 0.6, 0.1, 0.6, 0);
            for (EntityPlayer p : near) {
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

    /** The spring is asleep (for tests and hints). */
    public boolean springAwake(int i) {
        return worldObj.getTotalWorldTime() >= springWake[i];
    }

    /** A shooter from afar gets a reply: a vine pulls him to the golem (or a boulder if the vine can't reach). */
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

    /** Tear a boulder out of the ground and hurl it in an arc at the target. */
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

    /** A jump to the target (the landing is a stomp). */
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

    /** Vine: windup of {@link #LASH_WINDUP} ticks (a green trail stretches), then the strike. */
    private void lash(EntityPlayer p) {
        if (p == null || p.getDistanceToEntity(this) < 4 || lashTarget != null) return;
        lashTarget = p;
        lashWindup = LASH_WINDUP;
        worldObj.playSoundAtEntity(this, "dig.grass", 2.0F, 0.4F);
    }

    /** The slicer cut the vine during the windup. */
    private void cutVine() {
        lashTarget = null;
        lashWindup = 0;
        worldObj.playSoundAtEntity(this, "mob.sheep.shear", 2.0F, 0.8F);
        ((WorldServer) worldObj).func_147487_a("slime", posX, posY + height * 0.6, posZ, 20, 1, 0.5, 1, 0.1);
        com.divinerpgextended.debug.Debug.noteNear(this, 40, "Golem: vine cut");
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
        double k = 1 - lashWindup / (double) LASH_WINDUP; // the vine reaches
        ws.func_147487_a("slime", posX + (p.posX - posX) * k, posY + height * 0.6 + (p.posY + 1 - posY - height * 0.6) * k,
            posZ + (p.posZ - posZ) * k, 3, 0.1, 0.1, 0.1, 0);
        if (--lashWindup > 0) return;
        lashTarget = null;
        lashHit(p);
    }

    /** Vine strike: hits, pulls toward the golem and entangles (slowness). */
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

    /** Fist: only with a living arm on the target's side (or any living one), with a cooldown. */
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
