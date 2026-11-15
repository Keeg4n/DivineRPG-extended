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
 * Antidote: the full Divine jungle set and the "Poison Resistance" perk on Wanderer armor (the donor is the same set). Poison does not
 * work, and the other bad effects are not removed but pass faster ({@link Config#jungleDebuffSpeed}) - including
 * foreign ones that milk does not remove (Thaumcraft warp: sun scorn, unnatural hunger, etc.). World weather
 * effects are refreshed every second, so they are almost unaffected.
 */
public final class Antidote {

    /** Once per this many ticks we cut off the accumulated time (and send the client the new time). */
    public static final int PERIOD = 20;
    private static java.lang.reflect.Field duration;

    private Antidote() {}

    /** The full Divine jungle set. */
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
     * Every {@link #PERIOD} ticks: cut bad effects (except poison - the antidote removes it) by a further (speed - 1) x
     * PERIOD ticks. When they run out, the effect is removed. Server only. @return how many effects were shortened
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
