package com.divinerpgextended.entity.mini;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Небесный хват, Скайтерн: кружит над целью, пикирует, хватает и несёт строго вверх на {@link #LIFT} блоков, а там роняет на то же место.
 * Сет Терна падает мягко, защита от отброса 1.0 (Каменная стена, нерушимость) не даёт себя поднять. Сильный удар в полёте заставляет выпустить.
 */
public class EntitySkyGrabber extends EntityMiniBoss {

    public static final double HEALTH = 180; // моб Скайтерна 80 x2.25, страж 600
    public static final int DIVE_EVERY = 240, MAX_CARRY = 60;
    public static final double LIFT = 15, GRAB_REACH = 3;
    public static final float BREAK_HIT = 0.1F;
    private int dive = DIVE_EVERY, carried;
    private double startY;
    private EntityPlayer held;

    public EntitySkyGrabber(World w) {
        super(w, 1.4F, 2.2F, true);
    }

    @Override
    public String key() {
        return "sky_grabber";
    }

    @Override
    protected double baseHealth() {
        return HEALTH;
    }

    @Override
    protected double baseDamage() {
        return 20;
    }

    @Override
    public void loot(int looting) {
        twilightLoot(TwilightWorld.SKYTHERN, looting);
    }

    public EntityPlayer held() {
        return held;
    }

    /** Схватить, если цель рядом и её можно сдвинуть. */
    public boolean grab(EntityPlayer t) {
        if (held != null || getDistanceToEntity(t) > GRAB_REACH) return false;
        t.attackEntityFrom(DamageSource.causeMobDamage(this), (float) baseDamage() * 0.5F);
        dive = DIVE_EVERY;
        if (t.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue() >= 1.0) return false;
        held = t;
        carried = 0;
        startY = t.posY;
        worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 1.5F, 0.8F);
        tell("dre.chat.mini.sky_grabber_grab");
        return true;
    }

    /** Тик полёта с добычей: вверх на месте, игрок висит под лапами. */
    public void carry() {
        if (held == null) return;
        carried++;
        if (!held.isEntityAlive() || held.isRiding() || carried > MAX_CARRY || held.posY - startY >= LIFT) {
            held = null;
            return;
        }
        motionX = motionZ = 0;
        motionY = 0.3;
        fallDistance = 0;
        held.motionX = (posX - held.posX) * 0.4;
        held.motionZ = (posZ - held.posZ) * 0.4;
        held.motionY = (posY - 2.2 - held.posY) * 0.5 + 0.2;
        held.velocityChanged = true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        boolean hit = super.attackEntityFrom(src, amount);
        if (hit && held != null && amount >= getMaxHealth() * BREAK_HIT) held = null;
        return hit;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        if (held != null) {
            carry();
            return;
        }
        EntityPlayer t = targetPlayer();
        if (t == null) {
            motionY = motionY * 0.7 + 0.08;
            return;
        }
        if (--dive > 0) {
            steer(t.posX, t.posY + 8, t.posZ, 0.06, 0.45);
            return;
        }
        steer(t.posX, t.posY + 1, t.posZ, 0.12, 0.8);
        if (!grab(t) && dive < -100) dive = DIVE_EVERY; // не догнал за 5 секунд - снова наверх
    }
}
