package com.divinerpgextended.perk;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.divinerpg.entities.base.EntityDivineArrow;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Arrows that cannot be picked up - shot with Infinity, from the Endless Quiver, from bows that need no arrows, extra arrows of
 * multi-shot bows - vanish {@link #GROUND_TICKS} ticks after they stick in the ground instead of the usual minute. Vanilla and Divine
 * arrows alike; arrows that can be picked up stay as usual.
 */
public class ArrowCleanup {

    public static final int GROUND_TICKS = 100, EVERY = 5;
    /** Tracked arrows: true - shot by a player (then "never picked up" (0) counts too, otherwise only "infinite" (2)). */
    private static final Map<Entity, Boolean> ARROWS = new WeakHashMap<Entity, Boolean>();
    private static Field vanillaGround, divinePickup, divineGround;
    private int tick;

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote) return;
        if (e.entity instanceof EntityArrow) ARROWS.put(e.entity, ((EntityArrow) e.entity).shootingEntity instanceof EntityPlayer);
        else if (e.entity instanceof EntityDivineArrow) ARROWS.put(e.entity, ((EntityDivineArrow) e.entity).shootingEntity instanceof EntityPlayer);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ++tick % EVERY == 0) sweep();
    }

    /** Remove the stuck arrows nobody can pick up. @return how many were removed */
    public static int sweep() {
        int n = 0;
        for (Iterator<Map.Entry<Entity, Boolean>> it = ARROWS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Entity, Boolean> en = it.next();
            Entity a = en.getKey();
            if (a == null || a.isDead) {
                it.remove();
                continue;
            }
            int pickup = pickup(a);
            if (pickup == 1 || pickup == 0 && !en.getValue()) continue; // picked up - as usual; a skeleton's arrow - as usual
            if (groundTicks(a) < GROUND_TICKS) continue;
            a.setDead();
            it.remove();
            n++;
        }
        return n;
    }

    /** Pickup mode: 0 - never, 1 - by anyone, 2 - only in creative (Infinity). */
    public static int pickup(Entity a) {
        if (a instanceof EntityArrow) return ((EntityArrow) a).canBePickedUp;
        try {
            if (divinePickup == null) divinePickup = ReflectionHelper.findField(EntityDivineArrow.class, "field_70251_a", "canBePickedUp");
            return divinePickup.getInt(a);
        } catch (Throwable t) {
            return 1;
        }
    }

    /** How many ticks the arrow has been stuck in a block. */
    public static int groundTicks(Entity a) {
        try {
            if (a instanceof EntityArrow) {
                if (vanillaGround == null) vanillaGround = ReflectionHelper.findField(EntityArrow.class, "field_70252_j", "ticksInGround");
                return vanillaGround.getInt(a);
            }
            if (divineGround == null) divineGround = ReflectionHelper.findField(EntityDivineArrow.class, "field_70252_j", "ticksInGround");
            return divineGround.getInt(a);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** For the selftest: put the arrow into the stuck state for the given number of ticks. */
    public static void setGroundTicks(Entity a, int ticks) {
        try {
            if (a instanceof EntityArrow) {
                groundTicks(a);
                vanillaGround.setInt(a, ticks);
            } else {
                groundTicks(a);
                divineGround.setInt(a, ticks);
            }
        } catch (Throwable ignored) {}
    }
}
