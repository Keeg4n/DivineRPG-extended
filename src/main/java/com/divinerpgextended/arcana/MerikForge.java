package com.divinerpgextended.arcana;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.entities.arcana.EntityCaptianMerik;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.events.ArcanaHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Reforging at Captain Merik: sneak and right click with a Divine sword in hand - the price; once more within 5 seconds - reforge
 * for arcanium. The property is written into the item and works in combat (see {@link Reforge}).
 */
public class MerikForge {

    private static final int CONFIRM_TICKS = 100;
    private static final Map<UUID, long[]> PENDING = new HashMap<UUID, long[]>(); // time, slot

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (!(e.target instanceof EntityCaptianMerik) || !p.isSneaking()) return;
        ItemStack held = p.getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) return; // sneaked without a sword - ordinary trading
        e.setCanceled(true);
        if (p.worldObj.isRemote) return;
        WeaponOrigin origin = WeaponOrigin.of(held);
        if (origin == null) {
            say(p, EnumChatFormatting.GRAY, "dre.merik.only_divine");
            return;
        }
        long now = p.worldObj.getTotalWorldTime();
        long[] pend = PENDING.get(p.getUniqueID());
        int slot = p.inventory.currentItem;
        if (pend == null || now - pend[0] > CONFIRM_TICKS || pend[1] != slot) {
            PENDING.put(p.getUniqueID(), new long[] { now, slot });
            Reforge cur = Reforge.of(held);
            say(p, EnumChatFormatting.YELLOW, "dre.merik.offer", held.getDisplayName(), String.valueOf(origin.cost),
                cur == null ? new ChatComponentTranslation("dre.merik.none") : new ChatComponentTranslation("dre.reforge." + cur.key()));
            return;
        }
        PENDING.remove(p.getUniqueID());
        if (!p.capabilities.isCreativeMode && !take(p, origin.cost)) {
            say(p, EnumChatFormatting.RED, "dre.merik.no_money", String.valueOf(origin.cost));
            return;
        }
        Reforge r = Reforge.roll(p.worldObj.rand, origin);
        r.apply(held);
        com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.reforge);
        if (r.bad()) com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.dullReforge);
        if (r == Reforge.LEGENDARY) com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.legendary);
        p.inventoryContainer.detectAndSendChanges();
        p.worldObj.playSoundAtEntity(p, "random.anvil_use", 0.7F, 1.0F + p.worldObj.rand.nextFloat() * 0.3F);
        say(p, r.bad() ? EnumChatFormatting.GRAY : r == Reforge.LEGENDARY ? EnumChatFormatting.GOLD : EnumChatFormatting.GREEN,
            r.bad() ? "dre.merik.result_bad" : "dre.merik.result", new ChatComponentTranslation("dre.reforge." + r.key()));
    }

    private static boolean take(EntityPlayer p, int n) {
        int have = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == ArcanaItems.arcanium) have += s.stackSize;
        if (have < n) return false;
        for (int i = 0; i < p.inventory.mainInventory.length && n > 0; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || s.getItem() != ArcanaItems.arcanium) continue;
            int t = Math.min(n, s.stackSize);
            s.stackSize -= t;
            n -= t;
            if (s.stackSize <= 0) p.inventory.mainInventory[i] = null;
        }
        return true;
    }

    /** Properties in combat: only a direct hit by the player with a reforged sword. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        Entity src = e.source.getEntity();
        if (!(src instanceof EntityPlayer) || e.source.getSourceOfDamage() != src || src.worldObj.isRemote) return;
        if (e.entityLiving instanceof EntityPlayer) return; // reforge properties - only against mobs, not in PvP
        if (com.divinerpgextended.perk.Protection.spare(src, e.entityLiving)) return;
        EntityPlayer p = (EntityPlayer) src;
        ItemStack held = p.getCurrentEquippedItem();
        Reforge r = Reforge.of(held);
        if (r == null || WeaponOrigin.of(held) == null) return;
        EntityLivingBase t = e.entityLiving;
        float mult = 1.0F + r.damage;
        switch (r) {
            case ABYSSAL:
                if (aquatic(t)) mult += 0.30F;
                break;
            case BLAZING:
                t.setFire(3);
                if (icy(t)) mult += 0.25F;
                break;
            case FROSTY:
                t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1));
                if (t.isImmuneToFire()) mult += 0.25F;
                break;
            case ARCANE:
                ArcanaHelper h = ArcanaHelper.getProperties(p);
                if (h != null) h.regen(3.0F);
                break;
            case TWILIT:
                int d = WeaponOrigin.twilightDim(held), here = t.worldObj.provider.dimensionId;
                if (d == -1 ? WeaponOrigin.isTwilight(here) : d == here) mult += 0.20F;
                break;
            case DREAMY:
                if (p.worldObj.rand.nextFloat() < 0.15F) {
                    t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 30, 3));
                    t.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, 0));
                }
                break;
            case LEGENDARY:
                if (p.worldObj.rand.nextFloat() < 0.10F) {
                    mult *= 1.5F;
                    p.onCriticalHit(t);
                }
                break;
            case HEAVY:
                float yaw = p.rotationYaw * (float) Math.PI / 180F;
                t.addVelocity(-MathHelper.sin(yaw) * 0.4, 0.1, MathHelper.cos(yaw) * 0.4);
                break;
            default:
        }
        e.ammount *= mult;
        com.divinerpgextended.debug.Debug.note(p, "Reforging " + r.name() + ": x" + mult);
        if (r == Reforge.BLOODTHIRSTY) p.heal(Math.max(0.5F, e.ammount * 0.04F));
    }

    private static boolean aquatic(EntityLivingBase t) {
        if (t instanceof EntityWaterMob) return true;
        String n = t.getClass().getSimpleName();
        return n.contains("Crab") || n.contains("Shark") || n.contains("Whale") || n.contains("Liopleurodon") || n.contains("Carcinus")
            || n.contains("Kraken");
    }

    private static boolean icy(EntityLivingBase t) {
        if (t.worldObj.provider.dimensionId == ConfigurationHelper.iceika) return true;
        String n = t.getClass().getSimpleName();
        return n.contains("Glacon") || n.contains("Frost") || n.contains("Lich") || n.contains("Snow") || n.contains("Ice");
    }

    /** Tooltip: the property in the name (as in Terraria) and what it does. */
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        Reforge r = Reforge.of(e.itemStack);
        if (r == null || e.toolTip.isEmpty()) return;
        e.toolTip.set(0, r.color + StatCollector.translateToLocal("dre.reforge." + r.key()) + " " + EnumChatFormatting.RESET
            + e.toolTip.get(0));
        e.toolTip.add(1, r.color + StatCollector.translateToLocal("dre.reforge." + r.key() + ".desc"));
    }
}
