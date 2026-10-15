package com.divinerpgextended.trinket;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;


import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Эффекты бижутерии Джека. Работает только надетая в слоты аксессуаров (в рюкзаке - нет). */
public class TrinketEffects {

    private static final String DEAD_CD = "dreDeadAmuletCd";

    public static boolean wears(EntityPlayer p, Trinket t) {
        return com.divinerpgextended.accessory.Accessories.wears(p, t);
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        Entity src = e.source.getEntity();
        if (src instanceof EntityPlayer && !src.worldObj.isRemote) {
            EntityPlayer p = (EntityPlayer) src;
            if (e.source.isProjectile() && wears(p, Trinket.BONE_RING)) e.ammount *= 1.2F;
            if (!e.source.isProjectile() && wears(p, Trinket.WITHER_RING) && p.worldObj.rand.nextFloat() < 0.2F) {
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.wither.id, 60, 1));
            }
            if (!p.worldObj.isDaytime() && wears(p, Trinket.JACK_AMULET)) e.ammount *= 1.15F;
        }
        if (e.entityLiving instanceof EntityPlayer && e.source.isExplosion() && wears((EntityPlayer) e.entityLiving, Trinket.BLAST_BELT)) {
            e.ammount *= 0.25F;
        }
    }

    /** Костяное кольцо: 25% шанс, что стрела вернётся. */
    @SubscribeEvent
    public void onLoose(ArrowLooseEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || p.capabilities.isCreativeMode || !wears(p, Trinket.BONE_RING)) return;
        if (p.worldObj.rand.nextFloat() < 0.25F) p.inventory.addItemStackToInventory(new ItemStack(Items.arrow));
    }

    /** Амулет Джека: ночью враги не замечают носителя дальше 8 блоков. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.target;
        if (p.worldObj.isDaytime() || !wears(p, Trinket.JACK_AMULET) || e.entityLiving.getDistanceSqToEntity(p) <= 64) return;
        ((EntityLiving) e.entityLiving).setAttackTarget(null);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.ticksExisted % 10 != 0) return;
        if (p.isPotionActive(Potion.wither) && wears(p, Trinket.WITHER_RING)) p.removePotionEffect(Potion.wither.id);
        if (wears(p, Trinket.DEAD_AMULET)) {
            if (p.isPotionActive(Potion.hunger)) p.removePotionEffect(Potion.hunger.id);
            long now = p.worldObj.getTotalWorldTime();
            if (p.getHealth() < p.getMaxHealth() * 0.3F && now >= p.getEntityData().getLong(DEAD_CD)) {
                p.addPotionEffect(new PotionEffect(Potion.regeneration.id, 100, 1));
                p.getEntityData().setLong(DEAD_CD, now + 600);
            }
        }
        if (!p.worldObj.isDaytime() && wears(p, Trinket.JACK_AMULET)) {
            PotionEffect nv = p.getActivePotionEffect(Potion.nightVision);
            if (nv == null || nv.getDuration() < 220) p.addPotionEffect(new PotionEffect(Potion.nightVision.id, 300, 0, true));
        }
    }
}
