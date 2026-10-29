package com.divinerpgextended.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.iceika.EntityHastreus;
import net.divinerpg.utils.items.IceikaItems;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.Config;
import com.divinerpgextended.block.BlockFrostIce;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Ледяной Колосс - огромный (~25 блоков) ледяной голем, на плече которого стоит Король-лич.
 * <p>
 * Колосс бьёт кулаками, топает (кольцо шипов - перепрыгнуть) и пускает дорожки шипов. Король-лич с плеча с замахом
 * в полторы секунды готовит Воющий буран или ледяную тюрьму и поднимает мертвецов. Ниже 50% - Беспощадная зима
 * (гасит жаровни, холод вдвое, сосульки с купола), ниже 20% - ледяные глыбы. Когда Колосс падает, лич спрыгивает.
 * <p>
 * Чем бить: ледяное тело держит удар (обычное оружие x0.6), а Frozen Maul, Glacier Sword и Frostking Sword
 * раскалывают его (x2.5, x2, x1.6). Луки Divine: Inferno - поджог и x1.5, но три адские стрелы за 10 секунд
 * приводят его в ярость: он хватает Хастреуса и швыряет в стрелка (Хастреус обездвиживает всех, кого видит, -
 * уворачиваться или сразу убивать). Bluefire - оглушение на 6 секунд (потом 12 секунд не действует). Snowstorm
 * срывает заклинание, если попасть во время замаха, и вносит смятение: поднятые мертвецы с ускорением и силой
 * бросаются на хозяина, новых он 8 секунд не поднимает.
 */
public class EntityFrostColossus extends EntityMob implements IBossDisplayData {

    public static final float SCALE = 9.0F;
    private static final int CAST_BLAST = 1, CAST_PRISON = 2, CAST_TICKS = 30;

    private int attackTimer, attackCooldown;
    private int stompTimer = 160, lineTimer = 100, blastTimer = 220, prisonTimer = 320, deadTimer = 260,
        winterTimer = 60, icicleTimer = 100, boulderTimer = 60;
    private int stunLeft, stunImmune, confusedLeft, castType, castLeft;
    private int rageLeft, rageCooldown;
    private EntityLivingBase rageTarget;
    private final List<Long> infernoHits = new ArrayList<Long>();
    private boolean winterAnnounced;

    /** Кольцевые волны: {x, y, z, радиус}. Дорожки: {x, y, z, dx, dz, пройдено}. Сосульки: {x, z, тиков, y}. */
    private final List<double[]> waves = new ArrayList<double[]>(), lines = new ArrayList<double[]>(),
        icicles = new ArrayList<double[]>();

    public EntityFrostColossus(World world) {
        super(world);
        setSize(7.0F, 25.0F);
        stepHeight = 4.0F;
        experienceValue = 500;
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, false));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.colossusHealth);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(Config.colossusDamage);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.26D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(160.0D); // видит всю арену
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
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
    public boolean canBePushed() {
        return false;
    }

    @Override
    public String getCommandSenderName() {
        return StatCollector.translateToLocal("dre.entity.frost_colossus");
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
        return "mob.irongolem.death";
    }

    @Override
    protected float getSoundPitch() {
        return 0.4F;
    }

    @Override
    protected float getSoundVolume() {
        return 4.0F;
    }

    @SideOnly(Side.CLIENT)
    public int getAttackTimer() {
        return attackTimer;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleHealthUpdate(byte b) {
        if (b == 4) attackTimer = 12;
        else super.handleHealthUpdate(b);
    }

    public boolean isStunned() {
        return stunLeft > 0;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source == DamageSource.inWall || source == DamageSource.cactus || source == DamageSource.fall) return false;
        Item melee = ThroneCombat.melee(source);
        float mult = ThroneCombat.vsColossus(melee);
        if (mult == 1.0F) mult = 0.6F; // ледяное тело держит обычное оружие
        // в смятении мобы рвут его втрое сильнее
        if (confusedLeft > 0 && source.getEntity() instanceof EntityLiving
            && !(source.getEntity() instanceof EntityPlayer)) mult = 3.0F;
        if (source.isFireDamage()) mult = 1.5F;
        if (!worldObj.isRemote) {
            if (ThroneCombat.isArrow(source, "infernoArrow")) {
                mult = 1.5F;
                setFire(5);
                infernoRage(source.getEntity());
            } else if (ThroneCombat.isArrow(source, "bluefireArrow")) {
                mult = 1.0F;
                if (stunImmune <= 0) {
                    stunLeft = 120;
                    stunImmune = 120 + 240;
                    FrostPowers.tell(this, "dre.chat.colossus_stunned");
                    worldObj.playSoundAtEntity(this, "random.anvil_land", 2.0F, 0.5F);
                }
            } else if (ThroneCombat.isArrow(source, "snowstormArrow")) {
                mult = 1.0F;
                snowstormHit();
            }
        }
        return super.attackEntityFrom(source, amount * mult);
    }

    /** Snowstorm: срыв заклинания во время замаха и смятение среди мертвецов. */
    private void snowstormHit() {
        if (castLeft > 0) {
            castLeft = 0;
            castType = 0;
            stunLeft = Math.max(stunLeft, 20);
            FrostPowers.tell(this, "dre.chat.cast_interrupted");
            worldObj.playSoundAtEntity(this, "random.break", 2.0F, 0.5F);
        }
        if (confusedLeft <= 0) {
            confusedLeft = 160;
            turnMinions(this, 30);
            FrostPowers.tell(this, "dre.chat.minions_confused");
        }
    }

    /**
     * Смятение: все мобы вокруг (не только поднятые мертвецы) как угорелые бросаются на босса - ускорение и сила;
     * пока смятение длится, цель обновляется каждую секунду, чтобы они не переключались на игрока.
     */
    static void turnMinions(EntityLiving boss, double r) {
        for (Object o : boss.worldObj.getEntitiesWithinAABB(EntityLiving.class, boss.boundingBox.expand(r, r / 2, r))) {
            EntityLiving m = (EntityLiving) o;
            if (m == boss || m instanceof EntityFrostColossus || m instanceof EntityLichKing) continue;
            m.setAttackTarget(boss);
            m.setRevengeTarget(boss);
            if (m instanceof net.minecraft.entity.EntityCreature) ((net.minecraft.entity.EntityCreature) m).setTarget(boss);
            if (m.getActivePotionEffect(Potion.moveSpeed) == null) {
                m.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 160, 3));
                m.addPotionEffect(new PotionEffect(Potion.damageBoost.id, 160, 1));
                FrostPowers.particles(boss.worldObj, "angryVillager", m.posX, m.posY + m.height, m.posZ, 3, 0.3, 0.0);
            }
        }
    }

    /** Три адские стрелы за 10 секунд - ярость: через полторы секунды швыряет Хастреуса в стрелка. */
    private void infernoRage(Entity shooter) {
        long now = worldObj.getTotalWorldTime();
        infernoHits.add(now);
        for (Iterator<Long> it = infernoHits.iterator(); it.hasNext();) if (now - it.next() > 200) it.remove();
        if (infernoHits.size() < 3 || rageCooldown > 0 || rageLeft > 0 || !(shooter instanceof EntityLivingBase)) return;
        infernoHits.clear();
        rageLeft = 30;
        rageTarget = (EntityLivingBase) shooter;
        FrostPowers.tell(this, "dre.chat.colossus_rage");
        worldObj.playSoundAtEntity(this, "mob.irongolem.death", 3.0F, 0.3F);
    }

    private void throwHastreus() {
        rageCooldown = 300;
        if (rageTarget == null || rageTarget.isDead) return;
        int alive = worldObj.getEntitiesWithinAABB(EntityHastreus.class, boundingBox.expand(48, 20, 48))
            .size();
        if (alive >= 2) return;
        double[] sh = shoulder();
        EntityHastreus h = new EntityHastreus(worldObj);
        h.setLocationAndAngles(sh[0], sh[1], sh[2], rotationYaw, 0);
        double dx = rageTarget.posX - sh[0], dz = rageTarget.posZ - sh[2], d = Math.sqrt(dx * dx + dz * dz);
        // дуга: подбираем скорость под дальность (сопротивление воздуха у мобов заметное)
        double v = Math.min(3.0, 0.35 + d * 0.06);
        h.motionX = dx / Math.max(d, 0.1) * v;
        h.motionZ = dz / Math.max(d, 0.1) * v;
        h.motionY = 0.5;
        h.getEntityData()
            .setBoolean(ThroneCombat.MINION_TAG, true);
        com.divinerpgextended.boss.BossMinions.mark(h, this);
        worldObj.spawnEntityInWorld(h);
        worldObj.playSoundAtEntity(this, "mob.irongolem.throw", 3.0F, 0.4F);
        FrostPowers.tell(this, "dre.chat.hastreus_thrown");
    }

    /** Точка на плече, где стоит Король-лич. */
    public double[] shoulder() {
        double yaw = Math.toRadians(renderYawOffset);
        double side = 0.5 * SCALE;
        return new double[] { posX - Math.cos(yaw) * side, posY + height * 0.86, posZ - Math.sin(yaw) * side };
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (attackTimer > 0) attackTimer--;
        if (worldObj.isRemote) return;
        if (attackCooldown > 0) attackCooldown--;
        if (stunImmune > 0) stunImmune--;
        if (confusedLeft > 0) {
            confusedLeft--;
            if (confusedLeft % 20 == 0) turnMinions(this, 32);
        }
        if (rageCooldown > 0) rageCooldown--;
        tickWaves();
        tickLines();
        tickIcicles();

        if (stunLeft > 0) {
            stunLeft--;
            getNavigator().clearPathEntity();
            motionX = motionZ = 0;
            if (stunLeft % 5 == 0) {
                FrostPowers.particles(worldObj, "magicCrit", posX, posY + height, posZ, 20, 1.5, 0.1);
            }
            return;
        }

        EntityLivingBase target = getAttackTarget();
        if (target != null && (target.isDead || target instanceof EntityPlayer
            && ((EntityPlayer) target).capabilities.isCreativeMode)) target = null;
        if (target == null && worldObj.getClosestPlayerToEntity(this, 160) == null) return;
        float hp = getHealth() / getMaxHealth();
        double[] sh = shoulder();
        // чем меньше здоровья, тем быстрее: до +80% скорости
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.26D * (1 + 0.8 * (1 - hp)));

        if (rageLeft > 0) {
            FrostPowers.particles(worldObj, "angryVillager", sh[0], sh[1], sh[2], 3, 1.0, 0.0);
            if (--rageLeft == 0) throwHastreus();
        }

        if (target != null) {
            getLookHelper().setLookPositionWithEntity(target, 30, 30);
            double dx = target.posX - posX, dz = target.posZ - posZ, dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > SCALE * 0.85) getMoveHelper().setMoveTo(target.posX, target.posY, target.posZ, 1.0D);
            if (dist < SCALE * 1.25 && Math.abs(target.posY - posY) < 10 && attackCooldown == 0) smash(target);
        }

        // замах заклинания лича: видно и слышно, Snowstorm может сорвать
        if (castLeft > 0) {
            FrostPowers.particles(worldObj, castType == CAST_BLAST ? "snowshovel" : "spell", sh[0], sh[1] + 1, sh[2], 15,
                0.8, 0.05);
            if (--castLeft == 0) {
                if (castType == CAST_BLAST && target != null) FrostPowers.howlingBlast(this, target, sh[1]);
                if (castType == CAST_PRISON) {
                    List<EntityPlayer> near = FrostPowers.players(this, 40);
                    if (!near.isEmpty()) {
                        EntityPlayer p = near.get(rand.nextInt(near.size()));
                        if (!p.capabilities.isCreativeMode) FrostPowers.icePrison(p);
                    }
                }
                castType = 0;
            }
        } else if (--blastTimer <= 0 && target != null) {
            startCast(CAST_BLAST, "dre.chat.cast_blast");
            blastTimer = 300;
        } else if (--prisonTimer <= 0) {
            startCast(CAST_PRISON, "dre.chat.cast_prison");
            prisonTimer = 420;
        }

        if (--stompTimer <= 0) {
            waves.add(new double[] { posX, posY, posZ, 3 });
            worldObj.playSoundAtEntity(this, "random.explode", 3.0F, 0.4F);
            FrostPowers.particles(worldObj, "snowshovel", posX, posY + 0.2, posZ, 100, 3.0, 0.2);
            stompTimer = (int) (240 - 120 * (1 - hp));
        }
        if (--lineTimer <= 0 && target != null) {
            double dx = target.posX - posX, dz = target.posZ - posZ, len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.1) lines.add(new double[] { posX, posY, posZ, dx / len, dz / len, 3 });
            lineTimer = 180;
        }
        if (--deadTimer <= 0) {
            if (confusedLeft <= 0) {
                FrostPowers.raiseDead(this, 3, 6);
                FrostPowers.tell(this, "dre.chat.raise_dead");
            }
            deadTimer = 500;
        }

        if (hp <= 0.5F) {
            if (!winterAnnounced) {
                winterAnnounced = true;
                FrostPowers.tell(this, "dre.chat.remorseless_winter");
            }
            if (--winterTimer <= 0) {
                FrostPowers.extinguishBrazier(this, 48);
                winterTimer = 300;
            }
            if (ticksExisted % 20 == 0) {
                for (EntityPlayer p : FrostPowers.players(this, 48)) ColdHandler.addCold(p, ColdHandler.RATE);
            }
            if (--icicleTimer <= 0) {
                for (EntityPlayer p : FrostPowers.players(this, 48)) {
                    for (int i = 0; i < 3; i++) {
                        icicles.add(new double[] { p.posX + (rand.nextDouble() - 0.5) * 8,
                            p.posZ + (rand.nextDouble() - 0.5) * 8, 20, p.posY });
                    }
                }
                icicleTimer = 120;
            }
        }
        if (hp <= 0.2F && --boulderTimer <= 0 && target != null) {
            EntityIcicle b = new EntityIcicle(worldObj, this, EntityIcicle.BOULDER);
            b.setPosition(sh[0], sh[1], sh[2]);
            double dx = target.posX - sh[0], dy = target.posY - sh[1], dz = target.posZ - sh[2];
            b.setThrowableHeading(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.25, dz, 1.2F, 2.0F);
            worldObj.spawnEntityInWorld(b);
            worldObj.playSoundAtEntity(this, "mob.irongolem.throw", 2.0F, 0.5F);
            boulderTimer = 100;
        }
    }

    private void startCast(int type, String chat) {
        castType = type;
        castLeft = CAST_TICKS;
        FrostPowers.tell(this, chat);
        worldObj.playSoundAtEntity(this, "mob.wither.idle", 2.0F, 0.6F);
    }

    private void smash(EntityLivingBase target) {
        attackTimer = 12;
        attackCooldown = (int) (35 - 15 * (1 - getHealth() / getMaxHealth()));
        worldObj.setEntityState(this, (byte) 4);
        if (target.attackEntityFrom(DamageSource.causeMobDamage(this),
            (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue())) {
            target.motionY += 0.25; // слабый подброс: к нему должно быть можно подойти
        }
        worldObj.playSoundAtEntity(this, "mob.irongolem.throw", 2.5F, 0.5F);
    }

    @Override
    public boolean attackEntityAsMob(Entity e) {
        return false; // бьёт только своим ударом (smash), с размахом
    }

    /** Поставить ледяной шип высотой 2 на поверхности возле (x, y, z). */
    private void spike(int x, int y, int z) {
        if (!worldObj.blockExists(x, y, z)) return; // волна дошла до выгруженного края
        for (int dy = 2; dy >= -2; dy--) {
            if (worldObj.isAirBlock(x, y + dy, z) && !worldObj.isAirBlock(x, y + dy - 1, z)) {
                Block below = worldObj.getBlock(x, y + dy - 1, z);
                if (below == ModBlocks.frostIce) return;
                BlockFrostIce.place(worldObj, ModBlocks.frostIce, x, y + dy, z, BlockFrostIce.SPIKE);
                BlockFrostIce.place(worldObj, ModBlocks.frostIce, x, y + dy + 1, z, BlockFrostIce.SPIKE);
                return;
            }
        }
    }

    private void hurt(EntityPlayer p, float dmg) {
        if (p.attackEntityFrom(DamageSource.causeMobDamage(this), dmg)) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1));
            p.motionY += 0.2;
        }
    }

    /** Кольцо шипов от топота: расходится на 24 блока, бьёт тех, кто стоит на земле на гребне волны. */
    private void tickWaves() {
        for (Iterator<double[]> it = waves.iterator(); it.hasNext();) {
            double[] w = it.next();
            double r = w[3];
            int n = (int) (r * 6);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                spike(MathHelper.floor_double(w[0] + Math.cos(a) * r), MathHelper.floor_double(w[1]),
                    MathHelper.floor_double(w[2] + Math.sin(a) * r));
            }
            for (EntityPlayer p : FrostPowers.players(this, 40)) {
                double dx = p.posX - w[0], dz = p.posZ - w[2], d = Math.sqrt(dx * dx + dz * dz);
                if (d > r - 1.2 && d < r + 0.6 && Math.abs(p.posY - w[1]) < 3 && p.onGround) hurt(p, 8.0F);
            }
            w[3] += 0.8;
            if (w[3] > 24) it.remove();
        }
    }

    /** Дорожка шипов к игроку (как у ледяных боссов Террарии): ползёт по земле на 30 блоков. */
    private void tickLines() {
        for (Iterator<double[]> it = lines.iterator(); it.hasNext();) {
            double[] l = it.next();
            double x = l[0] + l[3] * l[5], z = l[2] + l[4] * l[5];
            spike(MathHelper.floor_double(x), MathHelper.floor_double(l[1]), MathHelper.floor_double(z));
            for (EntityPlayer p : FrostPowers.players(this, 40)) {
                double dx = p.posX - x, dz = p.posZ - z;
                if (dx * dx + dz * dz < 1.4 * 1.4 && Math.abs(p.posY - l[1]) < 3) hurt(p, 10.0F);
            }
            if (ticksExisted % 2 == 0) worldObj.playSoundEffect(x, l[1], z, "dig.glass", 0.8F, 0.6F);
            l[5] += 1;
            if (l[5] > 30) it.remove();
        }
    }

    /** Сосульки с купола: секунду на земле метка из снежной пыли, потом падение. */
    private void tickIcicles() {
        for (Iterator<double[]> it = icicles.iterator(); it.hasNext();) {
            double[] c = it.next();
            FrostPowers.particles(worldObj, "snowshovel", c[0], c[3] + 0.1, c[1], 4, 0.4, 0.0);
            if (--c[2] > 0) continue;
            it.remove();
            int x = MathHelper.floor_double(c[0]), z = MathHelper.floor_double(c[1]), y = MathHelper.floor_double(c[3]);
            int top = y + 1;
            while (top < y + 16 && worldObj.isAirBlock(x, top + 1, z)) top++;
            worldObj.spawnEntityInWorld(new EntityIcicle(worldObj, c[0], top, c[1]));
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (worldObj.isRemote) return;
        // Колосс рассыпается, Король-лич спрыгивает
        double[] sh = shoulder();
        EntityLichKing lich = new EntityLichKing(worldObj);
        lich.setLocationAndAngles(sh[0], sh[1], sh[2], rotationYaw, 0);
        lich.motionY = 0.4;
        worldObj.spawnEntityInWorld(lich);
        FrostPowers.particles(worldObj, "snowshovel", posX, posY + height / 2, posZ, 300, 4.0, 0.3);
        worldObj.playSoundAtEntity(this, "random.glass", 4.0F, 0.4F);
        FrostPowers.tell(this, "dre.chat.lich_jumps");
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        entityDropItem(new ItemStack(IceikaItems.snowflake, 6 + rand.nextInt(5 + looting)), 1.0F);
        entityDropItem(new ItemStack(Blocks.packed_ice, 8 + rand.nextInt(9)), 1.0F);
    }
}
