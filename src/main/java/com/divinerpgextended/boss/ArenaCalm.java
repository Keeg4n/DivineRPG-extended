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
 * Arena calm: while a fight is going on in a lair arena (the boss has a leash {@link LairSummons#home}), ordinary mobs do not
 * spawn there, and on summon the ones already standing scatter - the boss's minions should get in the way, not a random crowd of the grove. In a fight
 * the carpet does not work (except in the Wind Lord arena in the Skythern set - otherwise the totems cannot be reached).
 */
public class ArenaCalm {

    /** Active arenas: boss -> {dimension, x, y, z, radius, wind (1 - Wind Lord arena)} and when it was marked. */
    private static final Map<Integer, long[]> ARENAS = new ConcurrentHashMap<Integer, long[]>();
    /** Margin around the arena where it is quiet too. */
    public static final int MARGIN = 8;

    /** A boss with a leash is alive and fighting: mark the arena (called from the leash once per second). */
    public static void mark(Entity boss, int[] home) {
        long wind = boss instanceof com.divinerpgextended.entity.EntityWindLord ? 1 : 0;
        ARENAS.put(boss.getEntityId(), new long[] { boss.dimension, home[0], home[1], home[2], home[3], wind, boss.worldObj.getTotalWorldTime() });
    }

    private static long[] arenaAt(World w, double x, double y, double z) {
        long now = w.getTotalWorldTime();
        for (Iterator<long[]> it = ARENAS.values().iterator(); it.hasNext();) {
            long[] a = it.next();
            if (now - a[6] > 60 || now < a[6]) { // the boss is gone (killed, unloaded) - the arena is free
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

    /** Carpet is forbidden in a fight; in the Wind Lord arena - allowed in the Skythern set. */
    public static boolean carpetBlocked(EntityPlayer p) {
        long[] a = arenaAt(p.worldObj, p.posX, p.posY, p.posZ);
        return a != null && !(a[5] == 1 && com.divinerpgextended.trinket.DoubleJump.skyMaster(p));
    }

    @SubscribeEvent
    public void onSpawn(LivingSpawnEvent.CheckSpawn e) {
        if (e.world.isRemote || !(e.entityLiving instanceof IMob) || e.entityLiving instanceof IBossDisplayData) return;
        if (arenaAt(e.world, e.x, e.y, e.z) != null) e.setResult(Event.Result.DENY);
    }

    /** Boss summon: ordinary hostile mobs in the arena scatter (vanish with smoke), except minions and bosses. */
    public static int clear(World w, int cx, int cy, int cz, int r) {
        int n = 0;
        int rr = r + MARGIN;
        @SuppressWarnings("unchecked")
        List<EntityLiving> list = w.getEntitiesWithinAABB(EntityLiving.class,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(cx - rr, cy - 20, cz - rr, cx + rr + 1, cy + 30, cz + rr + 1));
        for (EntityLiving m : list) {
            if (!(m instanceof IMob) || m instanceof IBossDisplayData || m instanceof net.divinerpg.entities.base.EntityDivineRPGBoss) continue;
            if (m.getEntityData().hasKey(com.divinerpgextended.entity.EntityGroveGuardian.ADD_TAG) || m.getEntityData().hasKey("dreCadillionAdd")
                || m.getEntityData().hasKey("dreDemonAdd") || m.getEntityData().hasKey(AyeracoHorde.ADD)) continue;
            ((WorldServer) w).func_147487_a("largesmoke", m.posX, m.posY + m.height / 2, m.posZ, 10, 0.3, 0.5, 0.3, 0.02);
            m.setDead();
            n++;
        }
        return n;
    }
}
