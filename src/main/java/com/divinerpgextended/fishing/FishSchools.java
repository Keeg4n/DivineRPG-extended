package com.divinerpgextended.fishing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Косяки: иногда на воде недалеко от игрока появляется бурлящее пятно (брызги и рябь) на {@link #LIFE} тиков (минута). Поплавок
 * в пятне ({@link #RADIUS} блока) - клюёт в {@link #BITE} раза быстрее и в {@link #RARE} раза чаще редкое. Раз в 30 секунд у
 * каждого игрока шанс 1 из {@link #ODDS}, что рядом (8-20 блоков) на открытой воде появится косяк; в мире не больше
 * {@link #MAX} косяков сразу. Косяки не сохраняются (минута жизни).
 */
public class FishSchools {

    public static final float BITE = 3F, RARE = 1.5F;
    public static final int LIFE = 1200, RADIUS = 3, ODDS = 3, MAX = 4;

    static final class School {

        final int dim;
        final double x, y, z;
        final long until;

        School(int dim, double x, double y, double z, long until) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.until = until;
        }
    }

    private static final List<School> ACTIVE = new ArrayList<School>();

    /** Поплавок в косяке. */
    public static synchronized boolean near(World w, double x, double y, double z) {
        long now = w.getTotalWorldTime();
        for (School s : ACTIVE)
            if (s.dim == w.provider.dimensionId && s.until > now && Math.abs(s.y - y) < 3 && (s.x - x) * (s.x - x) + (s.z - z) * (s.z - z) <= RADIUS * RADIUS)
                return true;
        return false;
    }

    /** Поставить косяк (и для самопроверки). */
    public static synchronized void add(World w, double x, double y, double z) {
        ACTIVE.add(new School(w.provider.dimensionId, x, y, z, w.getTotalWorldTime() + LIFE));
    }

    private static synchronized int count(World w) {
        int n = 0;
        long now = w.getTotalWorldTime();
        for (School s : ACTIVE) if (s.dim == w.provider.dimensionId && s.until > now) n++;
        return n;
    }

    /** Открытая вода у игрока: поверхность (вода, над ней воздух) в 8-20 блоках. null - не нашлось. */
    static double[] spot(EntityPlayer p) {
        World w = p.worldObj;
        for (int tries = 0; tries < 24; tries++) {
            double a = w.rand.nextDouble() * Math.PI * 2, d = 8 + w.rand.nextDouble() * 12;
            int x = MathHelper.floor_double(p.posX + Math.cos(a) * d), z = MathHelper.floor_double(p.posZ + Math.sin(a) * d);
            if (!w.blockExists(x, 64, z)) continue;
            for (int y = MathHelper.floor_double(p.posY) + 4; y >= MathHelper.floor_double(p.posY) - 10; y--) {
                if (w.getBlock(x, y, z).getMaterial() != Material.water || !w.isAirBlock(x, y + 1, z)) continue;
                if (w.getBlock(x, y - 1, z).getMaterial() != Material.water) break; // лужа в блок - не косяк
                return new double[] { x + 0.5, y + 0.9, z + 0.5 };
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || !Config.fishSchools || p.ticksExisted % 600 != 0) return;
        if (p.worldObj.rand.nextInt(ODDS) != 0 || count(p.worldObj) >= MAX) return;
        double[] s = spot(p);
        if (s != null) add(p.worldObj, s[0], s[1], s[2]);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.world instanceof WorldServer) || e.world.getTotalWorldTime() % 8 != 0) return;
        WorldServer ws = (WorldServer) e.world;
        long now = ws.getTotalWorldTime();
        synchronized (FishSchools.class) {
            for (Iterator<School> it = ACTIVE.iterator(); it.hasNext();) {
                School s = it.next();
                if (s.until <= now) {
                    it.remove();
                    continue;
                }
                if (s.dim != ws.provider.dimensionId) continue;
                ws.func_147487_a("splash", s.x, s.y, s.z, 6, RADIUS * 0.5, 0.05, RADIUS * 0.5, 0.05);
                ws.func_147487_a("wake", s.x, s.y, s.z, 3, RADIUS * 0.4, 0.0, RADIUS * 0.4, 0.05);
            }
        }
    }
}
