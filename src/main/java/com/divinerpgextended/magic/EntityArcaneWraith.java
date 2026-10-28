package com.divinerpgextended.magic;

import net.divinerpg.entities.arcana.EntityWraith;
import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Призрак кости призрака (Ghostbane) - призыв: летает за хозяином сквозь блоки, бьёт арканным уроном то, что бьёт хозяин
 * (или того, кто напал на хозяина), не получает урона и не сохраняется в мире. Держится, пока у хозяина есть аркана:
 * {@link #UPKEEP} в секунду за каждого. Вид и звуки - призрака Divine.
 */
public class EntityArcaneWraith extends EntityWraith {

    public static final float BASE_DAMAGE = 10F, UPKEEP = 3F;
    public static final int HIT_EVERY = 15, LEASH = 48;
    private static java.lang.reflect.Field age;
    private int cooldown, life;
    /** Хозяин напрямую: у Divine он ищется по имени среди игроков мира, каждый тик. */
    private EntityPlayer master;

    public EntityArcaneWraith(World w) {
        super(w);
        noClip = true;
    }

    public EntityArcaneWraith(World w, EntityPlayer owner) {
        super(w, owner);
        master = owner;
        noClip = true;
    }

    EntityPlayer owner() {
        if (master != null && !master.isDead) return master;
        EntityLivingBase o = func_70902_q();
        return o instanceof EntityPlayer ? (EntityPlayer) o : null;
    }

    @Override
    public void onUpdate() {
        // срок Divine (6 с) не идёт: держится, пока платят арканой
        try {
            if (age == null) age = cpw.mods.fml.relauncher.ReflectionHelper.findField(EntityWraith.class, "age");
            age.setInt(this, 120);
        } catch (Throwable ignored) {}
        noClip = true;
        setIsBatHanging(false);
        super.onUpdate();
    }

    /** Свой полёт: к цели хозяина или вокруг хозяина, сквозь блоки. */
    @Override
    protected void updateAITasks() {
        if (worldObj.isRemote) return;
        EntityPlayer o = owner();
        if (o == null || o.isDead || o.dimension != dimension || getDistanceSqToEntity(o) > LEASH * LEASH) {
            setDead();
            return;
        }
        if (++life % 20 == 0 && !upkeep(o)) {
            ArcaneSummons.dismissAll(o);
            return;
        }
        Entity t = ArcaneSummons.target(o);
        double tx, ty, tz, speed;
        if (t != null) {
            tx = t.posX;
            ty = t.posY + t.height * 0.5;
            tz = t.posZ;
            speed = 0.55;
            if (--cooldown <= 0 && getDistanceSqToEntity(t) < 2.2 * 2.2 && t instanceof EntityLivingBase) {
                cooldown = HIT_EVERY;
                ((EntityLivingBase) t).hurtResistantTime = 0;
                t.attackEntityFrom(ArcanaCombat.arcaneSource(o), BASE_DAMAGE * ArcanaRules.stage(o));
                swingItem();
            }
        } else {
            int n = ArcaneSummons.index(this);
            double a = life * 0.06 + n * Math.PI;
            tx = o.posX + Math.cos(a) * 1.6;
            ty = o.posY + 1.8;
            tz = o.posZ + Math.sin(a) * 1.6;
            speed = 0.35;
        }
        double dx = tx - posX, dy = ty - posY, dz = tz - posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 24) { // отстал - рядом с хозяином
            setPosition(o.posX, o.posY + 1.8, o.posZ);
            return;
        }
        double v = Math.min(speed, len * 0.35);
        if (len > 0.01) {
            motionX = dx / len * v;
            motionY = dy / len * v;
            motionZ = dz / len * v;
            rotationYaw = renderYawOffset = rotationYawHead = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        }
    }

    /** Плата арканой за секунду жизни. */
    boolean upkeep(EntityPlayer o) {
        if (o.capabilities.isCreativeMode) return true;
        ArcanaHelper h = ArcanaHelper.getProperties(o);
        if (h == null || h.getBarValue() < UPKEEP) return false;
        h.setBarValue(h.getBarValue() - UPKEEP);
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource s, float a) {
        if (s == DamageSource.outOfWorld) {
            setDead();
            return true;
        }
        return false; // призрак: не ранить
    }

    @Override
    public boolean isEntityInsideOpaqueBlock() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void collideWithEntity(Entity e) {}

    /** Не сохраняется: призыв живёт только при хозяине. */
    @Override
    public boolean writeToNBTOptional(NBTTagCompound t) {
        return false;
    }

    @Override
    protected void fall(float d) {}
}
