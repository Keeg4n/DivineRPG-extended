package com.divinerpgextended.perk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.base.EntityDivineArrow;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Эндер-оружие Divine против эндер-мобов (эндермен, эндер-наблюдатель и эндер-паук Divine - все эндермены: от любого снаряда
 * телепортируются):
 * <ul>
 * <li>эндер-лук - единственный, чья стрела по ним попадает: пока стрела долетает, телепорт отменяется и урон стрелы
 * (как считает Divine) проходит напрямую;</li>
 * <li>эндер-мечи (все цвета) - после удара цель {@link #PIN_TICKS} тиков не может телепортироваться.</li>
 * </ul>
 */
public class EnderGear {

    public static final String ARROW = "dreEnderArrow", PIN = "dreNoTeleport";
    public static final int PIN_TICKS = 60;
    private static final List<EntityArrow> ARROWS = new ArrayList<EntityArrow>();

    public static boolean enderSword(ItemStack s) {
        if (s == null) return false;
        Item i = s.getItem();
        return i == VanillaItemsWeapons.enderSword || i == VanillaItemsWeapons.blueEnderSword || i == VanillaItemsWeapons.grayEnderSword
            || i == VanillaItemsWeapons.greenEnderSword || i == VanillaItemsWeapons.redEnderSword || i == VanillaItemsWeapons.yellowEnderSword;
    }

    public static boolean pinned(EntityLivingBase e) {
        return e.getEntityData().getLong(PIN) > e.worldObj.getTotalWorldTime();
    }

    /** Стрела эндер-лука: метка по луку в руке стрелка. */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityArrow)) return;
        EntityArrow a = (EntityArrow) e.entity;
        if (a.getEntityData().getBoolean(ARROW)) {
            ARROWS.add(a);
            return;
        }
        if (!(a.shootingEntity instanceof EntityPlayer)) return;
        ItemStack held = ((EntityPlayer) a.shootingEntity).getHeldItem();
        if (held == null || held.getItem() != VanillaItemsWeapons.enderBow) return;
        a.getEntityData().setBoolean(ARROW, true);
        ARROWS.add(a);
    }

    /** Удар эндер-мечом прибивает эндер-моба к месту. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.entityLiving instanceof EntityEnderman)) return;
        if (e.source.getSourceOfDamage() != e.source.getEntity() || !(e.source.getEntity() instanceof EntityPlayer)) return;
        if (!enderSword(((EntityPlayer) e.source.getEntity()).getHeldItem())) return;
        e.entityLiving.getEntityData().setLong(PIN, e.entityLiving.worldObj.getTotalWorldTime() + PIN_TICKS);
    }

    /** Ванильный эндермен, которому некуда прыгнуть, принимает любой снаряд - эндер-мобов ранят только стрелы эндер-лука. */
    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!e.entityLiving.worldObj.isRemote && e.entityLiving instanceof EntityEnderman && e.source instanceof net.minecraft.util.EntityDamageSourceIndirect)
            e.setCanceled(true);
    }

    /** Попаданий стрелой эндер-лука (для самопроверки). */
    public static int hits;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTeleport(EnderTeleportEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !(v instanceof EntityEnderman)) return;
        EntityArrow a = incoming(v);
        if (a != null) {
            hit(v, a);
            e.setCanceled(true);
            return;
        }
        if (pinned(v)) e.setCanceled(true);
    }

    /** Стрела эндер-лука, которая в этот тик влетает в цель (null - нет такой). */
    static EntityArrow incoming(EntityLivingBase v) {
        EntityArrow best = null;
        double bd = Double.MAX_VALUE;
        for (Iterator<EntityArrow> it = ARROWS.iterator(); it.hasNext();) {
            EntityArrow a = it.next();
            if (a.isDead) {
                it.remove();
                continue;
            }
            if (a.worldObj != v.worldObj) continue;
            double sp = Math.sqrt(a.motionX * a.motionX + a.motionY * a.motionY + a.motionZ * a.motionZ);
            if (sp < 0.3) continue; // лежит или воткнулась
            double dx = v.posX - a.posX, dy = v.posY + v.height / 2 - a.posY, dz = v.posZ - a.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > sp + v.height / 2 + v.width + 0.5 || dx * a.motionX + dy * a.motionY + dz * a.motionZ < 0 && d > v.width + 0.5) continue;
            if (d < bd) {
                bd = d;
                best = a;
            }
        }
        return best;
    }

    /** Урон стрелы, как его считает Divine (скорость x минимальный, не больше максимального, крит сверху), - напрямую. */
    static void hit(EntityLivingBase v, EntityArrow a) {
        double sp = Math.sqrt(a.motionX * a.motionX + a.motionY * a.motionY + a.motionZ * a.motionZ);
        int dmg;
        if (a instanceof EntityDivineArrow) {
            EntityDivineArrow da = (EntityDivineArrow) a;
            dmg = MathHelper.ceiling_double_int(2 * sp * da.damageMin);
            if (dmg > da.damageMax) dmg = MathHelper.ceiling_double_int(da.damageMax);
        } else {
            dmg = MathHelper.ceiling_double_int(sp * a.getDamage());
        }
        if (a.getIsCritical()) dmg += a.worldObj.rand.nextInt(dmg / 3 + 2);
        Entity shooter = a.shootingEntity != null ? a.shootingEntity : a;
        a.setDead();
        hits++;
        // Эндермен перебирает до 64 попыток телепорта за удар - до конца тика их тоже не пускаем, урон уже прошёл
        long pin = v.worldObj.getTotalWorldTime() + 1;
        if (v.getEntityData().getLong(PIN) < pin) v.getEntityData().setLong(PIN, pin);
        v.attackEntityFrom(new EntityDamageSource("arrow", shooter).setProjectile(), dmg);
        a.worldObj.playSoundAtEntity(a, "random.bowhit", 1.0F, 1.2F / (a.worldObj.rand.nextFloat() * 0.2F + 0.9F));
    }
}
