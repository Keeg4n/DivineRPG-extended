package com.divinerpgextended.perk;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.item.ItemSoulHarvest;
import com.divinerpgextended.item.ItemStormBow;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Эффекты вещей из реликвариев: молнии Грозового лука ({@link ItemStormBow}) и души Жатвы душ ({@link ItemSoulHarvest}).
 * Грозовая стрела бьёт молнией в цель (и +25% урона) или, промахнувшись, в место, куда воткнулась.
 */
public class RelicEffects {

    private static final List<WeakReference<EntityArrow>> ARROWS = new ArrayList<WeakReference<EntityArrow>>();
    private static java.lang.reflect.Field inGround;
    /** Для самопроверки: сколько молний ударило и сколько взрывов душ было. */
    public static int bolts, blasts;

    /** Следить за грозовой стрелой: воткнулась в землю - молния туда. */
    public static void track(EntityArrow a) {
        ARROWS.add(new WeakReference<EntityArrow>(a));
    }

    /** Воткнулась ли стрела (поле inGround своего класса или ванильное). */
    public static boolean stuck(EntityArrow a) {
        try {
            if (inGround == null) {
                for (Class<?> c = a.getClass(); c != null && inGround == null; c = c.getSuperclass()) for (java.lang.reflect.Field f : c.getDeclaredFields())
                    if (f.getType() == boolean.class && ("field_70254_i".equals(f.getName()) || "inGround".equals(f.getName()))) {
                        f.setAccessible(true);
                        inGround = f;
                        break;
                    }
            }
            return inGround != null && inGround.getDeclaringClass().isInstance(a) && inGround.getBoolean(a);
        } catch (Exception ex) {
            return false;
        }
    }

    public static void bolt(World w, double x, double y, double z) {
        w.addWeatherEffect(new com.divinerpgextended.entity.EntitySafeLightning(w, x, y, z)); // не жжёт добычу и не поджигает
        bolts++;
    }

    private static boolean storm(Entity e) {
        return e instanceof EntityArrow && e.getEntityData().getBoolean(ItemStormBow.TAG);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote) return;
        Entity src = e.source.getSourceOfDamage();
        if (storm(src)) {
            src.getEntityData().setBoolean(ItemStormBow.TAG, false);
            e.ammount *= 1F + ItemStormBow.BONUS;
            bolt(v.worldObj, v.posX, v.posY, v.posZ);
        }
        Entity by = e.source.getEntity();
        if (!(by instanceof EntityPlayer) || e.source.isProjectile() || e.source == DamageSource.magic) return;
        EntityPlayer p = (EntityPlayer) by;
        ItemStack held = p.getHeldItem();
        if (held == null || held.getItem() != ModItems.soulHarvest) return;
        long now = p.worldObj.getTotalWorldTime();
        int souls = ItemSoulHarvest.souls(held, now);
        if (souls <= 0) return;
        e.ammount *= 1F + ItemSoulHarvest.PER_SOUL * souls;
        if (souls >= ItemSoulHarvest.MAX) {
            ItemSoulHarvest.setSouls(held, 0, now);
            blast(p, v, e.ammount * ItemSoulHarvest.BLAST_SHARE);
        }
    }

    /** Взрыв душ вокруг цели: мобы (не игроки, не питомцы) получают долю урона удара. */
    static void blast(EntityPlayer p, EntityLivingBase center, float dmg) {
        World w = center.worldObj;
        float r = ItemSoulHarvest.BLAST_RADIUS;
        for (Object o : w.getEntitiesWithinAABB(EntityLivingBase.class, center.boundingBox.expand(r, r / 2, r))) {
            EntityLivingBase t = (EntityLivingBase) o;
            if (t == center || t == p || t instanceof EntityPlayer || t instanceof EntityTameable && ((EntityTameable) t).isTamed() || t.getDistanceToEntity(center) > r) continue;
            t.hurtResistantTime = 0;
            t.attackEntityFrom(DamageSource.causePlayerDamage(p).setDamageBypassesArmor().setMagicDamage(), dmg); // души проходят сквозь броню
        }
        if (w instanceof WorldServer) ((WorldServer) w).func_147487_a("witchMagic", center.posX, center.posY + center.height / 2, center.posZ, 40, r / 2, 0.5, r / 2, 0.05);
        w.playSoundAtEntity(center, "mob.wither.hurt", 0.8F, 0.6F);
        blasts++;
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer) || e.entityLiving instanceof EntityPlayer) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        ItemStack held = p.getHeldItem();
        if (held != null && held.getItem() == ModItems.soulHarvest) ItemSoulHarvest.addSoul(held, p.worldObj.getTotalWorldTime());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || ARROWS.isEmpty()) return;
        for (Iterator<WeakReference<EntityArrow>> it = ARROWS.iterator(); it.hasNext();) {
            EntityArrow a = it.next().get();
            if (a == null || a.isDead || !a.getEntityData().getBoolean(ItemStormBow.TAG)) {
                it.remove();
                continue;
            }
            if (a.worldObj != e.world) continue;
            if (stuck(a) || a.ticksExisted > 400) {
                a.getEntityData().setBoolean(ItemStormBow.TAG, false);
                if (stuck(a)) bolt(a.worldObj, a.posX, a.posY, a.posZ);
                it.remove();
            }
        }
    }
}
