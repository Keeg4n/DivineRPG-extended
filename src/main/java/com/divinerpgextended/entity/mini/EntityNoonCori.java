package com.divinerpgextended.entity.mini;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.world.twilight.EdenSun;
import com.divinerpgextended.world.twilight.TwilightWorld;
import com.divinerpgextended.world.twilight.WorldWeather;

/**
 * Полуденный кори, Эдем: парит над целью и сводит на неё солнечный луч. {@link #WINDUP} тиков луч виден, потом бьёт, если над целью
 * открытое небо: урон, поджог и сразу {@link #BEAM_HEAT} перегрева. Шаг под крышу спасает. Раненый зовёт двух кори.
 */
public class EntityNoonCori extends EntityMiniBoss {

    public static final double HEALTH = 225; // кадиллион Эдема 75 x3, страж 300
    public static final int BEAM_EVERY = 160, WINDUP = 40;
    public static final float BEAM_HEAT = 15F;
    private int beam = BEAM_EVERY;
    private boolean calledHelp;

    public EntityNoonCori(World w) {
        super(w, 1.4F, 2.0F, true);
    }

    @Override
    public String key() {
        return "noon_cori";
    }

    @Override
    protected double baseHealth() {
        return HEALTH;
    }

    @Override
    protected double baseDamage() {
        return 10;
    }

    @Override
    public void loot(int looting) {
        twilightLoot(TwilightWorld.EDEN, looting);
    }

    /** Удар луча; true - попал. */
    public boolean strike(EntityPlayer t) {
        if (!WorldWeather.open(t)) return false;
        t.attackEntityFrom(DamageSource.causeMobDamage(this), (float) baseDamage() * 1.5F);
        t.setFire(3);
        EdenSun.setHeat(t, Math.min(100, EdenSun.heat(t) + BEAM_HEAT * EdenSun.rate(t)));
        worldObj.playSoundAtEntity(t, "mob.ghast.fireball", 1.5F, 0.7F);
        return true;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        EntityPlayer t = targetPlayer();
        if (t == null) {
            motionY = motionY * 0.7 + 0.08;
            return;
        }
        steer(t.posX, t.posY + 7, t.posZ, 0.06, 0.45);
        if (!calledHelp && getHealth() < getMaxHealth() / 2) {
            calledHelp = true;
            call(net.divinerpg.entities.twilight.EntityEdenCori.class, 2);
        }
        if (--beam > WINDUP) return;
        if (worldObj instanceof WorldServer) {
            double dx = t.posX - posX, dy = t.posY + 1 - posY, dz = t.posZ - posZ;
            for (int i = 0; i < 6; i++) ((WorldServer) worldObj).func_147487_a("flame", posX + dx * i / 6, posY + dy * i / 6, posZ + dz * i / 6, 1, 0.05, 0.05, 0.05, 0);
        }
        if (beam > 0) return;
        beam = BEAM_EVERY;
        strike(t);
    }
}
