package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.world.CadillionWorld;

/**
 * Повелитель ветров Скайтерна, страж рощи - босс-пазл. Сам неуязвим: парит над поляной, сдувает порывами, стреляет
 * и зовёт извергов. Вокруг на разной высоте ({@link #HEIGHTS}) появляются 4 ветряных тотема на облачных площадках.
 * Тотем заряжается {@link #CHARGE} тиков, пока игрок стоит рядом (правый клик - начать): Повелитель бьёт порывами
 * именно его. Заряженный тотем бьёт Повелителя молнией на четверть здоровья; четыре тотема - конец. До высоких тотемов
 * удобно добраться ковром Хозяина ветров (сет Скайтерна).
 */
public class EntityWindLord extends EntityGroveGuardian {

    public static final int[] HEIGHTS = { 5, 9, 13, 17 };
    public static final int TOTEM_R = 13, CHARGE = 60, HOVER = 8;
    public static final float SCALE = 2.5F;
    /** Свой урон: только от молнии тотема. */
    public static final DamageSource TOTEM = new DamageSource("dreWindTotem").setDamageBypassesArmor();

    private int cx, cy, cz;
    private boolean placed;
    private int chargeX, chargeY, chargeZ, chargeTime = -1;
    private EntityPlayer charger;
    private int gustTimer = 100, shotTimer = 50, fiendTimer = 200;
    private double orbit;

    public EntityWindLord(World world) {
        super(world);
        setSize(1.6F, 4.6F);
    }

    @Override
    public CadillionWorld world() {
        return CadillionWorld.SKYTHERN;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
    }

    /** Центр арены: привязь рощи или место появления. */
    private void center() {
        int[] home = com.divinerpgextended.boss.LairSummons.home(this);
        if (home != null) {
            cx = home[0];
            cy = home[1];
            cz = home[2];
        } else {
            cx = (int) Math.floor(posX);
            cy = (int) Math.floor(posY);
            cz = (int) Math.floor(posZ);
        }
    }

    public int[] totem(int i) {
        double a = Math.PI / 4 + Math.PI / 2 * i;
        return new int[] { cx + (int) Math.round(Math.cos(a) * TOTEM_R), cy + HEIGHTS[i], cz + (int) Math.round(Math.sin(a) * TOTEM_R) };
    }

    /** Поставить тотемы на облачных площадках 3x3 (над ними пусто). */
    public void placeTotems() {
        center();
        for (int i = 0; i < 4; i++) {
            int[] t = totem(i);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (worldObj.isAirBlock(t[0] + dx, t[1] - 1, t[2] + dz) || worldObj.getBlock(t[0] + dx, t[1] - 1, t[2] + dz).isLeaves(worldObj, t[0] + dx, t[1] - 1, t[2] + dz))
                    worldObj.setBlock(t[0] + dx, t[1] - 1, t[2] + dz, Blocks.wool, 0, 3);
                for (int h = 0; h <= 3; h++) if (!(dx == 0 && dz == 0 && h == 0)) worldObj.setBlockToAir(t[0] + dx, t[1] + h, t[2] + dz);
            }
            worldObj.setBlock(t[0], t[1], t[2], ModBlocks.windTotem, 0, 3);
        }
        placed = true;
    }

    public int litTotems() {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            int[] t = totem(i);
            if (worldObj.getBlock(t[0], t[1], t[2]) == ModBlocks.windTotem && worldObj.getBlockMetadata(t[0], t[1], t[2]) == 1) n++;
        }
        return n;
    }

    private void removeTotems() {
        for (int i = 0; i < 4; i++) {
            int[] t = totem(i);
            if (worldObj.getBlock(t[0], t[1], t[2]) == ModBlocks.windTotem) worldObj.setBlockToAir(t[0], t[1], t[2]);
        }
    }

    /** Игрок начал заряжать тотем (клик по нему). */
    public void startCharge(int x, int y, int z, EntityPlayer p) {
        chargeX = x;
        chargeY = y;
        chargeZ = z;
        chargeTime = 0;
        charger = p;
        setAttackTarget(p);
        worldObj.playSoundEffect(x + 0.5, y + 1, z + 0.5, "portal.trigger", 0.4F, 1.8F);
    }

    public boolean charging() {
        return chargeTime >= 0;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (src == TOTEM || src == DamageSource.outOfWorld) return super.attackEntityFrom(src, amount);
        if (!worldObj.isRemote && src.getEntity() instanceof EntityPlayer) {
            // неуязвим: удар расходится ветром
            ((WorldServer) worldObj).func_147487_a("cloud", posX, posY + height * 0.5, posZ, 10, 0.5, 0.8, 0.5, 0.05);
            worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 0.6F, 1.8F);
        }
        return false;
    }

    @Override
    public void onLivingUpdate() {
        motionY = motionY * 0.6 + 0.08; // парит: гасим гравитацию
        super.onLivingUpdate();
        fallDistance = 0;
        if (worldObj.isRemote) return;
        if (!placed) placeTotems();
        WorldServer ws = (WorldServer) worldObj;
        // кружит над поляной на высоте HOVER
        orbit += 0.01;
        double tx = cx + 0.5 + Math.cos(orbit) * 6, tz = cz + 0.5 + Math.sin(orbit) * 6, ty = cy + HOVER;
        motionX += (tx - posX) * 0.02;
        motionZ += (tz - posZ) * 0.02;
        motionY += (ty - posY) * 0.04;
        if (ticksExisted % 3 == 0) ws.func_147487_a("cloud", posX, posY, posZ, 2, 0.4, 0.1, 0.4, 0.01);
        tickCharge(ws);
        EntityLivingBase t = getAttackTarget();
        if (t == null || !t.isEntityAlive()) {
            EntityPlayer p = worldObj.getClosestVulnerablePlayerToEntity(this, 40);
            if (p != null) setAttackTarget(t = p);
            else return;
        }
        getLookHelper().setLookPositionWithEntity(t, 30, 30);
        if (--gustTimer <= 0) {
            gustTimer = charging() ? 40 : 100;
            gust(charging() && charger != null ? charger : t);
        }
        if (--shotTimer <= 0 && canEntityBeSeen(t)) {
            shotTimer = 50 + rand.nextInt(20);
            shoot(t);
        }
        if (--fiendTimer <= 0) {
            fiendTimer = 400;
            for (int i = 0; i < 2 && addsAlive() < 3; i++) summon(net.divinerpg.entities.twilight.EntitySkythernFiend.class, 6);
        }
    }

    private void tickCharge(WorldServer ws) {
        if (chargeTime < 0) return;
        boolean near = charger != null && charger.isEntityAlive() && charger.worldObj == worldObj
            && charger.getDistanceSq(chargeX + 0.5, chargeY + 0.5, chargeZ + 0.5) < 3.0 * 3.0;
        if (!near || worldObj.getBlock(chargeX, chargeY, chargeZ) != ModBlocks.windTotem) {
            chargeTime = -1; // сдуло - начинать сначала
            worldObj.playSoundEffect(chargeX + 0.5, chargeY + 1, chargeZ + 0.5, "random.fizz", 0.5F, 1.2F);
            return;
        }
        chargeTime++;
        double up = (double) chargeTime / CHARGE;
        ws.func_147487_a("magicCrit", chargeX + 0.5, chargeY + 1 + up * 2, chargeZ + 0.5, 4, 0.3, 0.2, 0.3, 0.05);
        if (chargeTime < CHARGE) return;
        chargeTime = -1;
        worldObj.setBlockMetadataWithNotify(chargeX, chargeY, chargeZ, 1, 3);
        // молния бьёт в Повелителя: вспышка и гром для всех, урон - своим источником
        worldObj.addWeatherEffect(new EntityLightningBolt(worldObj, posX, posY, posZ));
        int lit = litTotems();
        hurtResistantTime = 0; // молния тотема проходит всегда
        super.attackEntityFrom(TOTEM, lit >= 4 ? getHealth() + 1 : getMaxHealth() * 0.25F);
        com.divinerpgextended.debug.Debug.note(this, "Повелитель ветров: тотем заряжен, всего " + lit);
    }

    /** Порыв: сдувает цель и всех рядом с ней прочь от Повелителя (Каменная стена держит). */
    private void gust(EntityLivingBase t) {
        worldObj.playSoundAtEntity(t, "mob.enderdragon.wings", 2.0F, 0.7F);
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, t.boundingBox.expand(4, 3, 4));
        for (EntityPlayer p : near) {
            ((WorldServer) worldObj).func_147487_a("cloud", p.posX, p.posY + 1, p.posZ, 15, 0.6, 0.6, 0.6, 0.15);
            if (p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue() >= 1.0) continue;
            double dx = p.posX - posX, dz = p.posZ - posZ, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.motionX += dx / d * 1.6;
            p.motionZ += dz / d * 1.6;
            p.motionY += 0.35;
            p.velocityChanged = true;
        }
    }

    /** Ветряная стрела по цели. */
    private void shoot(EntityLivingBase t) {
        EntityArrow a = new EntityArrow(worldObj, this, t, 1.8F, 4.0F);
        a.setDamage(getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * 0.25);
        worldObj.spawnEntityInWorld(a);
        worldObj.playSoundAtEntity(this, "random.bow", 1.0F, 0.6F);
    }

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!worldObj.isRemote) removeTotems();
    }

    @Override
    protected void fall(float d) {}

    @Override
    protected String getLivingSound() {
        return "mob.blaze.breathe";
    }

    @Override
    protected String getHurtSound() {
        return "mob.blaze.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.enderdragon.end";
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setIntArray("Center", new int[] { cx, cy, cz });
        tag.setBoolean("Placed", placed);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        int[] c = tag.getIntArray("Center");
        if (c.length == 3) {
            cx = c[0];
            cy = c[1];
            cz = c[2];
        }
        placed = tag.getBoolean("Placed");
    }
}
