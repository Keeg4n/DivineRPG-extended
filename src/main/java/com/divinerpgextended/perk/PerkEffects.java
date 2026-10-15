package com.divinerpgextended.perk;

import java.util.List;

import net.divinerpg.entities.vanilla.EntityCrab;
import net.divinerpg.entities.vanilla.EntityKingCrab;
import net.divinerpg.entities.vanilla.EntityLiopleurodon;
import net.divinerpg.entities.vanilla.EntityShark;
import net.divinerpg.entities.vanilla.EntityWhale;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.entity.EntityCarcinus;
import com.divinerpgextended.entity.EntityLairCrab;
import com.divinerpgextended.item.ArmorData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Эффекты перков полного сета брони Странника и бонусы водных сетов Divine (Kraken, Aquastrive). */
public class PerkEffects {

    private static final Item[] KRAKEN = { VanillaItemsArmor.krakenHelmet, VanillaItemsArmor.krakenBody,
        VanillaItemsArmor.krakenLegs, VanillaItemsArmor.krakenBoots };
    private static final Item[] AQUASTRIVE = { VanillaItemsArmor.aquastriveHelmet, VanillaItemsArmor.aquastriveBody,
        VanillaItemsArmor.aquastriveLegs, VanillaItemsArmor.aquastriveBoots };

    /** Полный сет Divine: set = [шлем, нагрудник, поножи, ботинки], слоты игрока 0 ботинки ... 3 шлем. */
    public static boolean wears(EntityPlayer p, Item[] set) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || s.getItem() != set[3 - i]) return false;
        }
        return true;
    }

    /** Водные существа для бонуса Kraken. */
    public static boolean isAquatic(Entity e) {
        return e instanceof EntityShark || e instanceof EntityCrab || e instanceof EntityKingCrab
            || e instanceof EntityLiopleurodon || e instanceof EntityWhale || e instanceof EntityWaterMob
            || e instanceof EntityCarcinus || e instanceof EntityLairCrab;
    }

    private static boolean aquaMiner(EntityPlayer p) {
        return wears(p, AQUASTRIVE) || ArmorData.hasEquippedPerk(p, Perk.AQUA_MINER);
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (event.source.isFireDamage() && ArmorData.hasEquippedPerk(player, Perk.FIRE_PROTECTION)) {
            player.extinguish();
            event.setCanceled(true);
        } else if (event.source.isExplosion() && ArmorData.hasEquippedPerk(player, Perk.EXPLOSION_PROTECTION)) {
            event.setCanceled(true);
        }
    }

    private static final Item[] INFERNO = { VanillaItemsArmor.infernoHelmet, VanillaItemsArmor.infernoBody,
        VanillaItemsArmor.infernoLegs, VanillaItemsArmor.infernoBoots };
    private static final Item[] NETHERITE = { VanillaItemsArmor.netheriteHelmet, VanillaItemsArmor.netheriteBody,
        VanillaItemsArmor.netheriteLegs, VanillaItemsArmor.netheriteBoots };

    /** Огненная броня: полный сет Inferno или Netherite, либо броня Странника с огнезащитой. */
    public static boolean wearsFireArmor(EntityPlayer p) {
        return wears(p, INFERNO) || wears(p, NETHERITE) || ArmorData.hasEquippedPerk(p, Perk.FIRE_PROTECTION);
    }

    /** Водная броня: полный сет Kraken или Aquastrive, либо броня Странника с водным перком. */
    public static boolean wearsWaterArmor(EntityPlayer p) {
        return wears(p, KRAKEN) || wears(p, AQUASTRIVE) || ArmorData.hasEquippedPerk(p, Perk.WATER_BREATHING)
            || ArmorData.hasEquippedPerk(p, Perk.AQUA_MINER);
    }

    /**
     * "Охотник глубин": полный сет Kraken - +50% урона по водным существам и -30% урона от них. Карцинус в водную
     * броню клешнями почти не пробивает: она сделана как раз против обитателей глубин. Песчаный вихрь бьёт полностью.
     */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        if (attacker instanceof EntityPlayer && !attacker.worldObj.isDaytime()
            && ArmorData.hasEquippedPerk((EntityPlayer) attacker, Perk.NIGHT_REAPER)) {
            event.ammount *= 1.2F;
        }
        if (attacker instanceof EntityCarcinus && "mob".equals(event.source.damageType)
            && event.entityLiving instanceof EntityPlayer
            && wearsWaterArmor((EntityPlayer) event.entityLiving)) {
            event.ammount *= (float) Config.carcinusWaterArmorFactor;
        }
        if (attacker instanceof EntityPlayer && isAquatic(event.entityLiving) && wears((EntityPlayer) attacker, KRAKEN)) {
            event.ammount *= 1.5F;
        } else if (event.entityLiving instanceof EntityPlayer && isAquatic(attacker)
            && wears((EntityPlayer) event.entityLiving, KRAKEN)) {
                event.ammount *= 0.7F;
            }
    }

    /** Без урона от падения встроено в ступень R1 (элитный реалмит). */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (event.entityLiving instanceof EntityPlayer && ArmorData.equippedTier((EntityPlayer) event.entityLiving) >= 1) {
            event.distance = 0;
        }
    }

    /** "Подводный шахтёр": в воде копание без замедления (как с Подводником) и даже без опоры под ногами. */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        EntityPlayer p = event.entityPlayer;
        if (!p.isInsideOfMaterial(Material.water) || !aquaMiner(p)) return;
        float speed = event.newSpeed;
        if (!EnchantmentHelper.getAquaAffinityModifier(p)) speed *= 5.0F;
        if (!p.onGround) speed *= 5.0F;
        event.newSpeed = speed;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.worldObj.isRemote) return;
        EntityPlayer player = event.player;

        if (player.isInsideOfMaterial(Material.water) && aquaMiner(player)) {
            PotionEffect nv = player.getActivePotionEffect(Potion.nightVision);
            if (nv == null || nv.getDuration() < 220) player.addPotionEffect(new PotionEffect(Potion.nightVision.id, 300, 0, true));
        }

        if (ArmorData.equippedTier(player) == 0) return;
        if (player.isInsideOfMaterial(Material.water) && ArmorData.hasEquippedPerk(player, Perk.WATER_BREATHING)) {
            player.setAir(300);
        }
        if (player.isPotionActive(Potion.poison) && ArmorData.hasEquippedPerk(player, Perk.POISON_RESISTANCE)) {
            player.removePotionEffect(Potion.poison.id);
        }
        if (player.isPotionActive(Potion.wither) && ArmorData.hasEquippedPerk(player, Perk.WITHER_PROTECTION)) {
            player.removePotionEffect(Potion.wither.id);
        }
        if (player.ticksExisted % 40 == 0 && player.getFoodStats().needFood() && ArmorData.hasEquippedPerk(player, Perk.SATIETY)) {
            player.getFoodStats().addStats(1, 0.0F);
        }
        if (player.ticksExisted % 20 == 0 && ArmorData.hasEquippedPerk(player, Perk.COLD_AURA)) {
            @SuppressWarnings("unchecked")
            List<EntityLivingBase> near = player.worldObj.getEntitiesWithinAABB(EntityLivingBase.class,
                player.boundingBox.expand(6, 3, 6));
            for (EntityLivingBase e : near) {
                if (e instanceof IMob) e.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1));
            }
        }
    }
}
