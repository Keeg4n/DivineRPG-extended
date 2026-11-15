package com.divinerpgextended.world.twilight;

import java.util.Map;
import java.util.WeakHashMap;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.BossMinions;
import com.divinerpgextended.perk.HomeArmor;
import com.divinerpgextended.vethea.Nightmare;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Skythern - a world of weak gravity and wind:
 * <ul>
 * <li>the full Skythern set: instead of Divine's high jump, gravity is half as strong, jumps are long and the fall is slow; on sneak you fall as usual;</li>
 * <li>a mob hit pushes like a Wind Lord gust, no more than once per {@link #GUST_EVERY} ticks from one mob;</li>
 * <li>ground mobs are also in weak gravity and jump in an arc toward a target that is higher or on another island.</li>
 * </ul>
 * Bosses and minions are not affected: the fight with the Wind Lord works its own way. Archers shoot from afar and have no need to jump after the target.
 */
public class SkythernWinds {

    /** Half of the normal gravity 0.08, with the correction for the 0.98 damping after it. */
    public static final double GRAVITY_CUT = 0.0392;
    /** Divine's addition to the jump in the Skythern set. */
    private static final double DIVINE_JUMP = 0.5, VANILLA_JUMP = 0.42;
    public static final double GUST_UP = 0.35;
    public static final int GUST_EVERY = 80, LEAP_EVERY = 60;
    /** Mob leap: the target is farther or higher than this, but not farther than {@link #LEAP_FAR}. */
    private static final double LEAP_NEAR = 6, LEAP_ABOVE = 2.5, LEAP_FAR = 16;
    private static final String GUST_AT = "dreGustAt", LEAP_AT = "dreLeapAt";
    /** The push waits for the player's next tick: right after the damage event the game itself cancels half the speed with its knockback. */
    private static final Map<EntityPlayer, double[]> PENDING = new WeakHashMap<EntityPlayer, double[]>();

    private static boolean wearsSet(EntityPlayer p) {
        return HomeArmor.wears(p, TwilightWorld.SKYTHERN, false) && !Nightmare.active(p);
    }

    /** Player tick in the air. Player movement is computed by their client, so this is called there. */
    public static void floatStep(EntityPlayer p) {
        if (p.onGround || p.isSneaking() || p.capabilities.isFlying || p.isInWater() || p.handleLavaMovement() || p.isOnLadder() || p.isRiding() || !wearsSet(p)) return;
        p.motionY += GRAVITY_CUT;
    }

    /** Divine adds 0.5 to the jump in this set: we remove exactly that, others' additions remain. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJump(LivingEvent.LivingJumpEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || !wearsSet((EntityPlayer) e.entityLiving)) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        double base = VANILLA_JUMP;
        if (p.isPotionActive(Potion.jump)) base += (p.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1;
        if (p.motionY >= base + DIVINE_JUMP - 0.01) p.motionY -= DIVINE_JUMP;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        Entity a = e.source.getEntity();
        if (!(a instanceof EntityMob) || a.dimension != ConfigurationHelper.skythern || e.source.getSourceOfDamage() != a || special(a)) return;
        long now = a.worldObj.getTotalWorldTime();
        if (a.getEntityData().hasKey(GUST_AT) && now - a.getEntityData().getLong(GUST_AT) < GUST_EVERY) return;
        a.getEntityData().setLong(GUST_AT, now);
        double dx = e.entityLiving.posX - a.posX, dz = e.entityLiving.posZ - a.posZ, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
        PENDING.put((EntityPlayer) e.entityLiving, new double[] { dx / d, dz / d });
    }

    private static boolean special(Entity e) {
        return e instanceof IBossDisplayData || e instanceof com.divinerpgextended.entity.mini.EntityMiniBoss || com.divinerpgextended.boss.Boss.of(e) != null || BossMinions.isMinion(e);
    }

    /** A deferred gust; knockback resistance 1.0 (Stone Wall, indestructibility) holds in place, as with the Wind Lord. */
    public static void flush(EntityPlayer p) {
        double[] dir = PENDING.remove(p);
        if (dir == null) return;
        p.worldObj.playSoundAtEntity(p, "mob.enderdragon.wings", 1.2F, 0.9F);
        if (p.worldObj instanceof WorldServer) ((WorldServer) p.worldObj).func_147487_a("cloud", p.posX, p.posY + 1, p.posZ, 15, 0.6, 0.6, 0.6, 0.15);
        if (p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue() >= 1.0) return;
        p.motionX += dir[0] * Config.skythernGust;
        p.motionZ += dir[1] * Config.skythernGust;
        p.motionY += GUST_UP;
        p.velocityChanged = true;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (e.player.worldObj.isRemote) floatStep(e.player);
        else if (!PENDING.isEmpty()) flush(e.player);
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (e.entityLiving.dimension != ConfigurationHelper.skythern || e.entityLiving.worldObj.isRemote || !(e.entityLiving instanceof EntityMob) || special(e.entityLiving)) return;
        EntityMob m = (EntityMob) e.entityLiving;
        if (!m.onGround && !m.isInWater()) {
            m.motionY += GRAVITY_CUT;
            m.fallDistance = 0;
        } else if (m.ticksExisted % 5 == 0) leap(m);
    }

    /** A leap toward the target if it is higher or far; the arc is calculated for weak gravity and air damping. true - jumped. */
    public static boolean leap(EntityMob m) {
        if (m instanceof net.divinerpg.entities.twilight.EntitySkythernArcher || special(m)) return false;
        Entity t = m.getEntityToAttack() != null ? m.getEntityToAttack() : m.getAttackTarget();
        if (!(t instanceof EntityPlayer) || !t.isEntityAlive()) return false;
        double dx = t.posX - m.posX, dy = t.posY - m.posY, dz = t.posZ - m.posZ, far = Math.sqrt(dx * dx + dz * dz);
        if (far > LEAP_FAR || far < LEAP_NEAR && dy < LEAP_ABOVE) return false;
        long now = m.worldObj.getTotalWorldTime();
        if (m.getEntityData().hasKey(LEAP_AT) && now - m.getEntityData().getLong(LEAP_AT) < LEAP_EVERY) return false;
        m.getEntityData().setLong(LEAP_AT, now);
        double push = Math.min(1.5, far / 10.5), d = Math.max(0.5, far);
        m.motionX = dx / d * push;
        m.motionZ = dz / d * push;
        m.motionY = Math.min(1.0, Math.sqrt(0.08 * (Math.max(dy, 0) + 3)));
        m.isAirBorne = true;
        m.worldObj.playSoundAtEntity(m, "mob.enderdragon.wings", 0.8F, 1.3F);
        if (m.worldObj instanceof WorldServer) ((WorldServer) m.worldObj).func_147487_a("cloud", m.posX, m.posY + 0.2, m.posZ, 10, 0.4, 0.1, 0.4, 0.05);
        return true;
    }
}
