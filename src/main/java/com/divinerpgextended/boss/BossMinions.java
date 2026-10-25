package com.divinerpgextended.boss;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Приспешники, которых босс зовёт в бою: без лута и опыта, умирают вместе с боссом. Хозяин записан в приспешнике
 * (UUID босса): босс умер, пропал или выгрузился - приспешник через несколько секунд погибает сам.
 */
public class BossMinions {

    public static final String OWNER = "dreBossOwner";
    /** Как далеко ищем хозяина и приспешников. */
    private static final double RANGE = 96;

    /** Пометить приспешника босса (звать до spawnEntityInWorld). */
    public static void mark(Entity minion, Entity boss) {
        minion.getEntityData().setString(OWNER, boss.getUniqueID().toString());
        if (minion instanceof EntityLiving) noXp((EntityLiving) minion);
    }

    public static boolean isMinion(Entity e) {
        return e != null && e.getEntityData().hasKey(OWNER);
    }

    /** Живые приспешники этого босса рядом с ним. */
    public static int count(Entity boss) {
        String id = boss.getUniqueID().toString();
        int n = 0;
        for (Object o : boss.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, boss.boundingBox.expand(RANGE, RANGE / 2, RANGE))) {
            EntityLivingBase e = (EntityLivingBase) o;
            if (e.isEntityAlive() && id.equals(e.getEntityData().getString(OWNER))) n++;
        }
        return n;
    }

    private static void noXp(EntityLiving e) {
        try {
            ReflectionHelper.setPrivateValue(EntityLiving.class, e, 0, "experienceValue", "field_70728_aV");
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent e) {
        if (isMinion(e.entityLiving)) e.drops.clear();
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote) return;
        if (isMinion(v)) {
            // опыт падает, пока недавно бил игрок, - убираем отметку удара
            try {
                ReflectionHelper.setPrivateValue(EntityLivingBase.class, v, 0, "recentlyHit", "field_70718_bc");
            } catch (Throwable ignored) {}
            if (v instanceof EntityLiving) noXp((EntityLiving) v);
            return;
        }
        String id = v.getUniqueID().toString();
        @SuppressWarnings("unchecked")
        List<EntityLivingBase> l = v.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, v.boundingBox.expand(RANGE, RANGE / 2, RANGE));
        for (EntityLivingBase m : l) if (m != v && m.isEntityAlive() && id.equals(m.getEntityData().getString(OWNER))) kill(m);
    }

    /** Раз в 5 секунд приспешник ищет хозяина: нет рядом живого - погибает. */
    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        EntityLivingBase m = e.entityLiving;
        if (m.worldObj.isRemote || m.ticksExisted % 100 != 50 || !isMinion(m) || !m.isEntityAlive()) return;
        String id = m.getEntityData().getString(OWNER);
        for (Object o : m.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, m.boundingBox.expand(RANGE, RANGE / 2, RANGE))) {
            EntityLivingBase b = (EntityLivingBase) o;
            if (b.isEntityAlive() && id.equals(b.getUniqueID().toString())) return;
        }
        kill(m);
    }

    private static void kill(EntityLivingBase m) {
        if (m instanceof EntityLiving) noXp((EntityLiving) m);
        m.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
        if (m.isEntityAlive()) m.setDead();
    }
}
