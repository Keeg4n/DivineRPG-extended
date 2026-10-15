package com.divinerpgextended.perk;

import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.entity.FrostPowers;
import com.divinerpgextended.entity.ThroneCombat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Снаряжение Iceika в бою.
 * <ul>
 * <li>Санта в Iceika: поверх её собственных -80% режет урон от существ ещё на 40% (итого ~-88%) - её долго фармить,
 * она должна держать удар.</li>
 * <li>Мертвецы, поднятые боссами Трона: Snowslash бьёт их втрое, ледяные пушки - вчетверо (разбивают в осколки).</li>
 * <li>Фроссивенс (лечащий меч): лечит не чаще раза в 2 секунды (чаще - лечение отменяется), в сете Frozen или
 * Санты - на половину здоровья. Каждое лечение студит кровь: копится переохлаждение (замедление, потом
 * слабость и урон), ледяные сеты копят его медленнее, но не до нуля. Спадает само.</li>
 * </ul>
 */
public class IceikaCombat {

    private static final Item[] SANTA = { IceikaItems.santaCap, IceikaItems.santaTunic, IceikaItems.santaPants,
        IceikaItems.santaBoots };
    private static final Item[] FROZEN = { VanillaItemsArmor.frozenHelmet, VanillaItemsArmor.frozenBody,
        VanillaItemsArmor.frozenLegs, VanillaItemsArmor.frozenBoots };
    private static final String HEAL_TICK = "dreFrossivence", CHILL = "dreChill", LAST_HP = "dreLastHp";
    private static final int COOLDOWN = 40;

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        if (event.entityLiving instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) event.entityLiving;
            if (attacker != null && attacker != p && ColdHandler.inIceika(p) && PerkEffects.wears(p, SANTA)) {
                event.ammount *= 0.6F;
            }
            return;
        }
        if (!event.entityLiving.getEntityData()
            .getBoolean(ThroneCombat.MINION_TAG)) return;
        Item melee = ThroneCombat.melee(event.source);
        if (melee == IceikaItems.snowSlash) event.ammount *= 3.0F;
        if (ThroneCombat.iceCannon(event.source)) {
            event.ammount *= 4.0F;
            FrostPowers.particles(event.entityLiving.worldObj, "snowshovel", event.entityLiving.posX,
                event.entityLiving.posY + 1, event.entityLiving.posZ, 20, 0.4, 0.1);
        }
    }

    /**
     * Фроссивенс: Divine лечит на каждый клик без перезарядки. Здесь клик чаще раза в 2 секунды отменяет лечение
     * (возвращаем здоровье, бывшее в конце прошлого тика), а каждое настоящее лечение добавляет переохлаждение.
     */
    @SubscribeEvent
    public void onUse(PlayerUseItemEvent.Start event) {
        EntityPlayer p = event.entityPlayer;
        ItemStack s = event.item;
        if (p.worldObj.isRemote || s == null || s.getItem() != IceikaItems.frossivence) return;
        NBTTagCompound d = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (now - d.getLong(HEAL_TICK) < COOLDOWN) {
            float before = d.getFloat(LAST_HP);
            if (before > 0 && p.getHealth() > before) p.setHealth(before);
            return;
        }
        d.setLong(HEAL_TICK, now);
        boolean santa = PerkEffects.wears(p, SANTA), frozen = PerkEffects.wears(p, FROZEN);
        if ((santa || frozen) && p.getHealth() < p.getMaxHealth()) p.heal(p.getMaxHealth() / 2);
        float chill = d.getFloat(CHILL) + 3.0F * (santa ? 0.4F : frozen ? 0.6F : 1.0F);
        if (d.getFloat(CHILL) < 7 && chill >= 7) p.addChatMessage(new ChatComponentTranslation("dre.chat.frossivence_chill"));
        d.setFloat(CHILL, Math.min(12, chill));
        FrostPowers.particles(p.worldObj, "snowshovel", p.posX, p.posY + 1.2, p.posZ, 8, 0.4, 0.05);
    }

    /** Запоминаем здоровье в конце тика (для отмены лишнего лечения) и раз в секунду обрабатываем переохлаждение. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        if (event.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        NBTTagCompound d = p.getEntityData();
        d.setFloat(LAST_HP, p.getHealth());
        if (p.ticksExisted % 20 != 0) return;
        float chill = d.getFloat(CHILL);
        if (chill <= 0) return;
        if (chill >= 10) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 3, true));
            p.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, 0, true));
            if (p.ticksExisted % 40 == 0) p.attackEntityFrom(ColdHandler.FROSTBITE, 1.0F);
        } else if (chill >= 7) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1, true));
            p.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 40, 0, true));
        } else if (chill >= 4) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 0, true));
        }
        d.setFloat(CHILL, Math.max(0, chill - 0.5F));
    }
}
