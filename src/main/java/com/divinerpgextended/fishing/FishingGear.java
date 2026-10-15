package com.divinerpgextended.fishing;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/** Снаряжение рыбака: сила рыбалки целиком (удочка, приманка, набор рыбака), сумка приманок, реликвии. */
public final class FishingGear {

    /** Каждая часть набора рыбака +5, полный набор ещё +10. */
    public static final int PIECE_POWER = 5, SET_POWER = 10;
    /** Бездонная сумка приманок: доля приманок, которые не тратятся (поверх удочки). */
    public static final float BAG_SAVE = 0.25F;

    private FishingGear() {}

    public static int armorPower(EntityPlayer p) {
        if (p == null) return 0;
        int n = 0;
        for (ItemStack s : p.inventory.armorInventory) if (s != null && s.getItem() instanceof ItemAnglerArmor) n++;
        return n * PIECE_POWER + (n >= 3 ? SET_POWER : 0);
    }

    public static boolean hasBaitBag(EntityPlayer p) {
        return p != null && com.divinerpgextended.trinket.TrinketEffects.wears(p, com.divinerpgextended.trinket.Trinket.TACKLE_BOX);
    }

    public static int power(World w, EntityPlayer p, Rod rod, Bait bait, Zone zone) {
        int n = rod.power + (bait == null ? 0 : bait.power) + armorPower(p);
        if (rod == Rod.JACK && !w.isDaytime()) n += 20;
        if (bait == Bait.LAMONA && (!w.isDaytime() || zone == Zone.CAVE)) n += 15;
        if (bait == Bait.AQUAMARINE && zone == Zone.OCEAN) n += 15;
        return n;
    }

    /** Лучшая удочка на хотбаре (для справочника) или null. */
    public static Rod bestRod(EntityPlayer p) {
        Rod best = null;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || !(s.getItem() instanceof ItemFishingRod)) continue;
            Rod r = s.getItem() instanceof ItemDreRod ? ((ItemDreRod) s.getItem()).rod : Rod.VANILLA;
            if (best == null || r.power > best.power) best = r;
        }
        return best;
    }

    public static void announceRelic(EntityPlayer p, ItemStack relic) {
        ChatComponentTranslation c = new ChatComponentTranslation("dre.chat.relic", relic.getDisplayName());
        c.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE));
        p.addChatMessage(c);
        p.worldObj.playSoundAtEntity(p, "random.levelup", 1.0F, 0.6F);
    }
}
