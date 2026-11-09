package com.divinerpgextended.perk;

import java.util.ArrayList;

import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.divinerpgextended.Config;
import com.divinerpgextended.item.ArmorData;

/**
 * Противоядие: полный сет джунглей Divine и перк "Сопротивление яду" на броне Странника (донор - тот же сет). Яд не
 * действует, а остальные плохие эффекты не снимаются, а проходят быстрее ({@link Config#jungleDebuffSpeed}) - в том
 * числе чужие, которые не снимает молоко (варп Таумкрафта: солнечная болезнь, неестественный голод и т.д.). Эффекты
 * погоды миров обновляются каждую секунду, поэтому их это почти не задевает.
 */
public final class Antidote {

    /** Раз в столько тиков срезаем накопленное (и шлём клиенту новое время). */
    public static final int PERIOD = 20;
    private static java.lang.reflect.Field duration;

    private Antidote() {}

    /** Полный сет джунглей Divine. */
    public static boolean jungleSet(EntityPlayer p) {
        return is(p, 0, VanillaItemsArmor.jungleBoots) && is(p, 1, VanillaItemsArmor.jungleLegs) && is(p, 2, VanillaItemsArmor.jungleBody) && is(p, 3, VanillaItemsArmor.jungleHelmet);
    }

    private static boolean is(EntityPlayer p, int slot, net.minecraft.item.Item item) {
        ItemStack s = p.inventory.armorItemInSlot(slot);
        return s != null && item != null && s.getItem() == item;
    }

    public static boolean has(EntityPlayer p) {
        return jungleSet(p) || ArmorData.equippedTier(p) > 0 && ArmorData.hasEquippedPerk(p, Perk.POISON_RESISTANCE);
    }

    /**
     * Каждые {@link #PERIOD} тиков: плохим эффектам (кроме яда - его противоядие снимает) срезать ещё (скорость - 1) x
     * PERIOD тиков. Кончились - эффект снят. Только сервер. @return сколько эффектов укорочено
     */
    public static int tick(EntityPlayer p) {
        double speed = Config.jungleDebuffSpeed;
        if (p.worldObj.isRemote || speed <= 1 || !has(p)) return 0;
        int extra = (int) Math.round(PERIOD * (speed - 1)), n = 0;
        for (Object o : new ArrayList<Object>(p.getActivePotionEffects())) {
            PotionEffect pe = (PotionEffect) o;
            int id = pe.getPotionID();
            Potion po = id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
            if (po == null || po.isInstant() || id == Potion.poison.id || !com.divinerpgextended.vethea.Nightmare.bad(po)) continue;
            int left = pe.getDuration() - extra;
            if (left <= 0) p.removePotionEffect(id);
            else if (setDuration(pe, left) && p instanceof EntityPlayerMP)
                ((EntityPlayerMP) p).playerNetServerHandler.sendPacket(new net.minecraft.network.play.server.S1DPacketEntityEffect(p.getEntityId(), pe));
            n++;
        }
        return n;
    }

    private static boolean setDuration(PotionEffect pe, int ticks) {
        try {
            if (duration == null) duration = cpw.mods.fml.relauncher.ReflectionHelper.findField(PotionEffect.class, "duration", "field_76460_b");
            duration.setInt(pe, ticks);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
