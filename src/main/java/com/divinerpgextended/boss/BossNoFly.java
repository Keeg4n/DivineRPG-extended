package com.divinerpgextended.boss;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Near a living boss (within {@link #RADIUS} blocks) flying is not allowed: from the air almost any ground boss can't reach you.
 * Boss means Divine and addon bosses (with bosses of other mods - the Wither, the dragon, etc. - flying is allowed). Except Skythern (the fight is in the sky) and
 * creative mode. Only flying players are checked - for everyone else it costs nothing.
 */
public class BossNoFly {

    public static final int RADIUS = 48;
    private static final String TOLD = "dreNoFlyTold";

    /** There is a living boss near the player that forbids flying. */
    public static boolean near(EntityPlayer p) {
        if (p.worldObj.provider.dimensionId == net.divinerpg.utils.config.ConfigurationHelper.skythern) return false;
        for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(RADIUS, RADIUS, RADIUS))) {
            EntityLiving e = (EntityLiving) o;
            String cls = e.getClass().getName(); // only Divine and addon bosses: with bosses of other mods flying is allowed
            if (e.isEntityAlive() && (e instanceof net.minecraft.entity.boss.IBossDisplayData || e instanceof net.divinerpg.entities.base.EntityDivineRPGBoss)
                && (cls.startsWith("net.divinerpg.") || cls.startsWith("com.divinerpgextended."))) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || !Config.bossNoFly || !p.capabilities.isFlying || p.capabilities.isCreativeMode
            || p.ticksExisted % 10 != 0) return; // boss search within 48 blocks - once every half second
        if (!near(p)) return;
        p.capabilities.isFlying = false;
        p.sendPlayerAbilities();
        long now = p.worldObj.getTotalWorldTime();
        if (now - p.getEntityData().getLong(TOLD) > 200) { // no more than once every 10 seconds
            p.getEntityData().setLong(TOLD, now);
            p.addChatMessage(new ChatComponentTranslation("dre.chat.boss_no_fly"));
        }
    }
}
