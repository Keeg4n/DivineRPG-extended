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
 * Divine ender weapons against ender mobs (the enderman, ender watcher and Divine ender spider are all endermen: they teleport
 * away from any projectile):
 * <ul>
 * <li>the ender bow is the only one whose arrow hits them: while the arrow is in flight, teleporting is cancelled and the arrow's damage
 * (as Divine computes it) goes through directly;</li>
 * <li>ender swords (all colors) and the ender bow arrow - after a hit the target cannot teleport for {@link #PIN_TICKS} ticks.
 * A player too (where PvP is allowed): no Ender pearl, no void step, no Divine scepter or crystal. Ender armor's rescue from
 * the void is not forbidden - otherwise a hit at the island edge would kill.</li>
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

    /** Ender bow arrow: tag from the bow in the shooter's hand. */
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

    /** A hit with an ender sword pins the ender mob in place. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        if (e.entityLiving instanceof EntityPlayer) {
            pinPlayer((EntityPlayer) e.entityLiving, e.source);
            return;
        }
        if (!(e.entityLiving instanceof EntityEnderman)) return;
        if (e.source.getSourceOfDamage() != e.source.getEntity() || !(e.source.getEntity() instanceof EntityPlayer)) return;
        if (!enderSword(((EntityPlayer) e.source.getEntity()).getHeldItem())) return;
        e.entityLiving.getEntityData().setLong(PIN, e.entityLiving.worldObj.getTotalWorldTime() + PIN_TICKS);
    }

    /** A player hit by another player's ender sword or ender bow arrow (where PvP is allowed) is pinned, like an enderman. */
    static void pinPlayer(EntityPlayer v, net.minecraft.util.DamageSource s) {
        Entity by = s.getEntity(), direct = s.getSourceOfDamage();
        if (!(by instanceof EntityPlayer) || by == v || com.divinerpgextended.perk.Protection.spare(by, v)) return;
        boolean sword = direct == by && enderSword(((EntityPlayer) by).getHeldItem());
        boolean arrow = direct instanceof EntityArrow && direct.getEntityData().getBoolean(ARROW);
        if (!sword && !arrow) return;
        boolean was = pinned(v);
        v.getEntityData().setLong(PIN, v.worldObj.getTotalWorldTime() + PIN_TICKS);
        if (!was) v.addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.chat.ender_pinned", PIN_TICKS / 20));
    }

    /** A vanilla enderman with nowhere to jump accepts any projectile - ender mobs are wounded only by ender bow arrows. */
    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!e.entityLiving.worldObj.isRemote && e.entityLiving instanceof EntityEnderman && e.source instanceof net.minecraft.util.EntityDamageSourceIndirect)
            e.setCanceled(true);
    }

    /** Ender bow arrow hits (for the selftest). */
    public static int hits;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTeleport(EnderTeleportEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (!v.worldObj.isRemote && v instanceof EntityPlayer && pinned(v)) { // Ender pearl of a pinned player
            e.setCanceled(true);
            ((EntityPlayer) v).addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.chat.teleport_pinned"));
            return;
        }
        if (v.worldObj.isRemote || !(v instanceof EntityEnderman)) return;
        EntityArrow a = incoming(v);
        if (a != null) {
            hit(v, a);
            e.setCanceled(true);
            return;
        }
        if (pinned(v)) {
            e.setCanceled(true);
            // a burning enderman jumps every tick: the red sword ignites - we hold it until the fire goes out
            if (v.isBurning()) v.getEntityData().setLong(PIN, v.worldObj.getTotalWorldTime() + 2);
        }
    }

    /** An ender bow arrow that flies into the target this tick (null - none). */
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
            if (sp < 0.3) continue; // lying or stuck in a block
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

    /** Arrow damage as Divine computes it (speed x minimum, no more than the maximum, crit on top), - directly. */
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
        // pins in place like an ender sword hit (also suppresses up to 64 teleport attempts during this hit)
        long pin = v.worldObj.getTotalWorldTime() + PIN_TICKS;
        if (v.getEntityData().getLong(PIN) < pin) v.getEntityData().setLong(PIN, pin);
        v.attackEntityFrom(new EntityDamageSource("arrow", shooter).setProjectile(), dmg);
        a.worldObj.playSoundAtEntity(a, "random.bowhit", 1.0F, 1.2F / (a.worldObj.rand.nextFloat() * 0.2F + 0.9F));
    }
}
