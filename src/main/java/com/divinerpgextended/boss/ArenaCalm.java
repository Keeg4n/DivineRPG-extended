package com.divinerpgextended.boss;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Покой арены: пока на арене логова (у босса есть привязь {@link LairSummons#home}) идёт бой, обычные мобы там не
 * спавнятся, а при вызове уже стоящие разбегаются - мешать должны подручные босса, а не случайная толпа рощи. В бою
 * ковёр не работает (кроме арены Повелителя ветров в сете Скайтерна - к тотемам иначе не подняться).
 */
public class ArenaCalm {

    /** Активные арены: босс -> {измерение, x, y, z, радиус, ветер (1 - арена Повелителя ветров)} и когда отмечен. */
    private static final Map<Integer, long[]> ARENAS = new ConcurrentHashMap<Integer, long[]>();
    /** Запас вокруг арены, где тоже тихо. */
    public static final int MARGIN = 8;

    /** Босс с привязью жив и дерётся: отметить арену (зовётся из привязи раз в секунду). */
    public static void mark(Entity boss, int[] home) {
        long wind = boss instanceof com.divinerpgextended.entity.EntityWindLord ? 1 : 0;
        ARENAS.put(boss.getEntityId(), new long[] { boss.dimension, home[0], home[1], home[2], home[3], wind, boss.worldObj.getTotalWorldTime() });
    }

    private static long[] arenaAt(World w, double x, double y, double z) {
        long now = w.getTotalWorldTime();
        for (Iterator<long[]> it = ARENAS.values().iterator(); it.hasNext();) {
            long[] a = it.next();
            if (now - a[6] > 60 || now < a[6]) { // босс пропал (убит, выгружен) - арена свободна
                it.remove();
                continue;
            }
            if (a[0] != w.provider.dimensionId) continue;
            double dx = x - a[1] - 0.5, dz = z - a[3] - 0.5, r = a[4] + MARGIN;
            if (dx * dx + dz * dz <= r * r && Math.abs(y - a[2]) < 40) return a;
        }
        return null;
    }

    public static boolean inFight(EntityPlayer p) {
        return arenaAt(p.worldObj, p.posX, p.posY, p.posZ) != null;
    }

    /** Ковёр в бою запрещён; на арене Повелителя ветров - можно в сете Скайтерна. */
    public static boolean carpetBlocked(EntityPlayer p) {
        long[] a = arenaAt(p.worldObj, p.posX, p.posY, p.posZ);
        return a != null && !(a[5] == 1 && com.divinerpgextended.trinket.DoubleJump.skyMaster(p));
    }

    @SubscribeEvent
    public void onSpawn(LivingSpawnEvent.CheckSpawn e) {
        if (e.world.isRemote || !(e.entityLiving instanceof IMob) || e.entityLiving instanceof IBossDisplayData) return;
        if (arenaAt(e.world, e.x, e.y, e.z) != null) e.setResult(Event.Result.DENY);
    }

    /** Вызов босса: обычные враждебные мобы на арене разбегаются (исчезают с дымом), кроме подручных и боссов. */
    public static int clear(World w, int cx, int cy, int cz, int r) {
        int n = 0;
        int rr = r + MARGIN;
        @SuppressWarnings("unchecked")
        List<EntityLiving> list = w.getEntitiesWithinAABB(EntityLiving.class,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(cx - rr, cy - 20, cz - rr, cx + rr + 1, cy + 30, cz + rr + 1));
        for (EntityLiving m : list) {
            if (!(m instanceof IMob) || m instanceof IBossDisplayData || m instanceof net.divinerpg.entities.base.EntityDivineRPGBoss) continue;
            if (m.getEntityData().hasKey(com.divinerpgextended.entity.EntityGroveGuardian.ADD_TAG) || m.getEntityData().hasKey("dreCadillionAdd")
                || m.getEntityData().hasKey("dreDemonAdd")) continue;
            ((WorldServer) w).func_147487_a("largesmoke", m.posX, m.posY + m.height / 2, m.posZ, 10, 0.3, 0.5, 0.3, 0.02);
            m.setDead();
            n++;
        }
        return n;
    }
}
