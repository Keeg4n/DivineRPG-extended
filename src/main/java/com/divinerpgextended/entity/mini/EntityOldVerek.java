package com.divinerpgextended.entity.mini;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.world.twilight.TwilightWorld;
import com.divinerpgextended.world.twilight.WorldWeather;

/**
 * Old Verek, Wildwood: the pack leader. At two thirds and one third of health it calls Vereks, jumps at the target from afar. In a downpour it becomes
 * invisible, only a trail on the ground remains.
 */
public class EntityOldVerek extends EntityMiniBoss {

    public static final double HEALTH = 150; // Verek 50 x3, guardian 400
    private int howls, leap = 120;

    public EntityOldVerek(World w) {
        super(w, 1.2F, 1.6F, false);
    }

    @Override
    public String key() {
        return "old_verek";
    }

    @Override
    protected double baseHealth() {
        return HEALTH;
    }

    @Override
    protected double baseDamage() {
        return 14;
    }

    @Override
    protected double baseSpeed() {
        return 0.36D;
    }

    @Override
    public void loot(int looting) {
        twilightLoot(TwilightWorld.WILDWOOD, looting);
    }

    /** Howl at the health thresholds; true - called the pack. */
    public boolean howl() {
        float left = getHealth() / getMaxHealth();
        if (howls == 0 && left < 2F / 3 || howls == 1 && left < 1F / 3) {
            howls++;
            worldObj.playSoundAtEntity(this, "mob.wolf.howl", 2.0F, 0.7F);
            tell("dre.chat.mini.old_verek_howl");
            call(net.divinerpg.entities.twilight.EntityVerek.class, 2);
            return true;
        }
        return false;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        howl();
        if (WorldWeather.rain(worldObj) && WorldWeather.open(this)) {
            addPotionEffect(new PotionEffect(Potion.invisibility.id, 40, 0, true));
            if (ticksExisted % 4 == 0 && worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("footstep", posX, boundingBox.minY + 0.05, posZ, 1, 0.1, 0, 0.1, 0);
        }
        EntityPlayer t = targetPlayer();
        if (t == null || --leap > 0 || !onGround) return;
        double dx = t.posX - posX, dz = t.posZ - posZ, far = Math.sqrt(dx * dx + dz * dz);
        if (far < 5 || far > 12) return;
        leap = 120;
        motionX = dx / far * Math.min(1.1, far / 9);
        motionZ = dz / far * Math.min(1.1, far / 9);
        motionY = 0.5;
        isAirBorne = true;
    }
}
