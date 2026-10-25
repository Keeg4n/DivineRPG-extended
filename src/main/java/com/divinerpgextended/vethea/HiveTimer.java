package com.divinerpgextended.vethea;

import java.util.List;

import net.divinerpg.entities.vethea.EntityHiveQueen;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Королева улья (первый босс Ветеи) - босс-таймер: не убить, а выжить. Как только игрок подходит ({@link #ENGAGE}
 * блоков), начинается отсчёт {@link #DURATION} тиков: Королева неуязвима, её полоска здоровья тает со временем - это и
 * есть таймер. Рой растёт по третям: сначала как в Divine, потом чаще, в последней трети - сон давит (слабость и
 * тошнота). Все ушли с арены - отсчёт сначала. Время вышло - кошмар кончается: Королева погибает (обычные трофеи,
 * засчитана тем, кто выжил рядом).
 */
public class HiveTimer {

    public static final String LEFT = "dreSurvive", WAKE = "dreWake";
    public static final int DURATION = 1800, ENGAGE = 16, LEAVE = 32;

    public static int left(EntityHiveQueen q) {
        return q.getEntityData().getInteger(LEFT);
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityHiveQueen) || e.entityLiving.worldObj.isRemote) return;
        EntityHiveQueen q = (EntityHiveQueen) e.entityLiving;
        if (q.getHealth() <= 0) return;
        NBTTagCompound tag = q.getEntityData();
        int left = tag.getInteger(LEFT);
        List<EntityPlayer> near = players(q, left > 0 ? LEAVE : ENGAGE);
        if (near.isEmpty()) {
            if (left > 0) { // все ушли - сон отпустил, отсчёт сначала
                tag.setInteger(LEFT, 0);
                q.setHealth(q.getMaxHealth());
            }
            return;
        }
        if (left == 0) {
            left = DURATION;
            q.worldObj.playSoundAtEntity(q, "mob.wither.spawn", 1.0F, 1.6F);
        }
        left--;
        tag.setInteger(LEFT, left);
        // полоска здоровья = оставшееся время
        q.setHealth(Math.max(1, q.getMaxHealth() * left / DURATION));
        int third = 2 - left * 3 / DURATION; // 0, 1, 2
        WorldServer ws = (WorldServer) q.worldObj;
        if (third >= 1 && left % 60 == 0) swarm(q, third == 2 ? 2 : 1);
        if (third >= 2 && left % 100 == 0) {
            for (EntityPlayer p : near) {
                p.addPotionEffect(new PotionEffect(Potion.weakness.id, 80, 0));
                p.addPotionEffect(new PotionEffect(Potion.confusion.id, 80, 0));
            }
        }
        if (left % 20 == 0) ws.func_147487_a("witchMagic", q.posX, q.posY + 2, q.posZ, 6, 1, 1, 1, 0);
        if (left == 0) wake(q, near.get(0));
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayer> players(EntityHiveQueen q, double r) {
        return q.worldObj.getEntitiesWithinAABB(EntityPlayer.class, q.boundingBox.expand(r, r / 2, r));
    }

    private static void swarm(EntityHiveQueen q, int n) {
        for (int i = 0; i < n; i++) {
            EntityLiving m = q.worldObj.rand.nextBoolean() ? new net.divinerpg.entities.vethea.EntityHoverStinger(q.worldObj)
                : new net.divinerpg.entities.vethea.EntityHiveSoldier(q.worldObj);
            m.setLocationAndAngles(q.posX + q.worldObj.rand.nextGaussian() * 2, q.posY + 1, q.posZ + q.worldObj.rand.nextGaussian() * 2,
                q.worldObj.rand.nextFloat() * 360, 0);
            com.divinerpgextended.boss.BossMinions.mark(m, q);
            q.worldObj.spawnEntityInWorld(m);
        }
    }

    /** Выжил: Королева гибнет от "пробуждения" - удар от лица выжившего (засчитывание и трофеи как обычно). */
    private static void wake(EntityHiveQueen q, EntityPlayer p) {
        q.getEntityData().setBoolean(WAKE, true);
        q.hurtResistantTime = 0;
        q.attackEntityFrom(DamageSource.causePlayerDamage(p), q.getMaxHealth() * 10);
        q.worldObj.playSoundAtEntity(q, "random.levelup", 1.0F, 0.6F);
    }

    /** Пока идёт отсчёт, урон не проходит (кроме пробуждения). */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityHiveQueen) || e.entityLiving.worldObj.isRemote) return;
        if (e.source == DamageSource.outOfWorld || e.entityLiving.getEntityData().getBoolean(WAKE)) return;
        e.setCanceled(true);
    }
}
