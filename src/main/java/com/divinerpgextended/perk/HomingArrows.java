package com.divinerpgextended.perk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import net.divinerpg.entities.base.EntityDivineArrow;
import net.divinerpg.utils.items.TwilightItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Луки сумеречных миров (Эдем, Вайлдвуд, Апалачия, Скайтерн, Мортум, Сумеречный, Halite) наводят стрелу на летучего
 * моба (Кори и прочие парящие): если такой есть в 24 блоках впереди (в конусе ~40 градусов), стрела плавно
 * доворачивает на него. По наземным - как обычно.
 */
public class HomingArrows {

    private static final double RANGE = 24, CONE = Math.cos(Math.toRadians(40)), TURN = 0.25;
    private final Set<EntityDivineArrow> arrows = Collections.newSetFromMap(new WeakHashMap<EntityDivineArrow, Boolean>());

    static boolean twilightBow(ItemStack s) {
        if (s == null) return false;
        Item i = s.getItem();
        return i == TwilightItemsWeapons.edenBow || i == TwilightItemsWeapons.wildwoodBow || i == TwilightItemsWeapons.apalachiaBow
            || i == TwilightItemsWeapons.skythernBow || i == TwilightItemsWeapons.mortumBow || i == TwilightItemsWeapons.twilightBow
            || i == TwilightItemsWeapons.haliteBow;
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityDivineArrow)) return;
        EntityDivineArrow a = (EntityDivineArrow) e.entity;
        if (a.shootingEntity instanceof EntityPlayer && twilightBow(((EntityPlayer) a.shootingEntity).getHeldItem())) arrows.add(a);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || arrows.isEmpty()) return;
        for (EntityDivineArrow a : new ArrayList<EntityDivineArrow>(arrows)) {
            if (a.isDead || a.ticksExisted > 120 || inGround(a)) {
                arrows.remove(a);
                continue;
            }
            steer(a);
        }
    }

    private static boolean inGround(EntityDivineArrow a) {
        try {
            return ReflectionHelper.<Boolean, EntityDivineArrow>getPrivateValue(EntityDivineArrow.class, a, "field_70254_i");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Довернуть стрелу на ближайшего летучего моба впереди. */
    public static boolean steer(EntityDivineArrow a) {
        double v = Math.sqrt(a.motionX * a.motionX + a.motionY * a.motionY + a.motionZ * a.motionZ);
        if (v < 0.3) return false;
        double fx = a.motionX / v, fy = a.motionY / v, fz = a.motionZ / v;
        Entity best = null;
        double bestD = RANGE * RANGE;
        @SuppressWarnings("unchecked")
        List<Entity> near = a.worldObj.getEntitiesWithinAABB(EntityFlying.class, a.boundingBox.expand(RANGE, RANGE, RANGE));
        for (Entity t : near) {
            if (t.isDead) continue;
            double dx = t.posX - a.posX, dy = t.posY + t.height / 2 - a.posY, dz = t.posZ - a.posZ;
            double d = dx * dx + dy * dy + dz * dz;
            if (d >= bestD) continue;
            double len = Math.sqrt(d);
            if ((dx * fx + dy * fy + dz * fz) / len < CONE) continue;
            best = t;
            bestD = d;
        }
        if (best == null) return false;
        double dx = best.posX - a.posX, dy = best.posY + best.height / 2 - a.posY, dz = best.posZ - a.posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double nx = fx * (1 - TURN) + dx / len * TURN, ny = fy * (1 - TURN) + dy / len * TURN, nz = fz * (1 - TURN) + dz / len * TURN;
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        a.motionX = nx / n * v;
        a.motionY = ny / n * v;
        a.motionZ = nz / n * v;
        a.velocityChanged = true;
        return true;
    }
}
