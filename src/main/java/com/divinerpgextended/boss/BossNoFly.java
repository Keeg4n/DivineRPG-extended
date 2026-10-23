package com.divinerpgextended.boss;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Рядом с живым боссом (в {@link #RADIUS} блоках) летать нельзя: с полёта почти любой наземный босс не достаёт.
 * Босс - всё, у чего полоска босса (Divine, аддон, другие моды) и боссы Divine. Кроме Скайтерна (там бой в небе) и
 * творческого режима. Проверяем только летящих - остальным это ничего не стоит.
 */
public class BossNoFly {

    public static final int RADIUS = 48;
    private static final String TOLD = "dreNoFlyTold";

    /** Рядом с игроком живой босс, при котором летать нельзя. */
    public static boolean near(EntityPlayer p) {
        if (p.worldObj.provider.dimensionId == net.divinerpg.utils.config.ConfigurationHelper.skythern) return false;
        for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(RADIUS, RADIUS, RADIUS))) {
            EntityLiving e = (EntityLiving) o;
            if (e.isEntityAlive() && (e instanceof net.minecraft.entity.boss.IBossDisplayData || e instanceof net.divinerpg.entities.base.EntityDivineRPGBoss)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || !Config.bossNoFly || !p.capabilities.isFlying || p.capabilities.isCreativeMode) return;
        if (!near(p)) return;
        p.capabilities.isFlying = false;
        p.sendPlayerAbilities();
        long now = p.worldObj.getTotalWorldTime();
        if (now - p.getEntityData().getLong(TOLD) > 200) { // не чаще раза в 10 секунд
            p.getEntityData().setLong(TOLD, now);
            p.addChatMessage(new ChatComponentTranslation("dre.chat.boss_no_fly"));
        }
    }
}
