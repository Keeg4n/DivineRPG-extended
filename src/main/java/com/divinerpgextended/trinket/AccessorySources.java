package com.divinerpgextended.trinket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.entities.arcana.EntityCaptianMerik;
import net.divinerpg.entities.base.InfiniteTrade;
import net.divinerpg.entities.iceika.EntityWorkshopMerchant;
import net.divinerpg.entities.iceika.EntityWorkshopTinkerer;
import net.divinerpg.entities.vanilla.EntityAyeraco;
import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.IceikaItems;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.entity.EntityFrostColossus;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Where accessories come from (besides Jack's orders):
 * <ul>
 * <li>bosses: guaranteed for the first victory, then 10% from each (Ayeraco - when the last of the five falls);</li>
 * <li>Captain Merik - Master Scabbard, Iceika workshop merchant - Warm Scarf (trades are appended on both sides:
 * the Divine window builds the list on the client);</li>
 * <li>Arcana mobs - Dungeon Lamp (1% + 0.5% per Looting level);</li>
 * <li>the Iceika workshop master merges two accessories into one (sneak + right-click holding one of them, the other in the backpack,
 * repeat within 5 seconds - do it; 16 snowflakes).</li>
 * </ul>
 */
public class AccessorySources {

    private static final String GOT = "dre_acc_got";
    private static final int COMBINE_SNOWFLAKES = 16;
    private static final Map<UUID, long[]> PENDING = new HashMap<UUID, long[]>();

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    private static ItemStack stack(Trinket t) {
        return new ItemStack(ModItems.trinket, 1, t.ordinal());
    }

    private static void give(EntityPlayer p, ItemStack s) {
        if (!p.inventory.addItemStackToInventory(s)) p.worldObj.spawnEntityInWorld(new EntityItem(p.worldObj, p.posX, p.posY + 0.5, p.posZ, s));
    }

    // ---------------- Bosses ----------------

    public static Trinket forBoss(Boss b) {
        switch (b) {
            case CARCINUS: return Trinket.CARCINUS_SHELL;
            case SNOW_GLACON: return Trinket.ICE_BROOCH;
            case LICH_KING: return Trinket.LICH_CROWN;
            case ANCIENT_ENTITY: return Trinket.ANCIENT_CHARM;
            case WATCHER: return Trinket.WATCHER_EYE;
            case KING_OF_SCORCHERS: return Trinket.BLUE_FLAME;
            case DRAMIX: return Trinket.DRAMIX_SEAL;
            case PARASECTA: return Trinket.PARASECTA_STING;
            case LIOPLEURODON: return Trinket.LIO_FINS;
            default: return null;
        }
    }

    /** A second accessory for a boss: Flying Carpet - from the Ancient Entity, to the first twilight world. */
    public static Trinket extraForBoss(Boss b) {
        return b == Boss.ANCIENT_ENTITY ? Trinket.MAGIC_CARPET : null;
    }

    /**
     * Accessory for a boss: the first time (for this player) - guaranteed, then 10%. Not put into the inventory: it drops by the boss's
     * body (at; null - at the player), marked with an owner - only they can pick it up, invulnerable, like all boss loot.
     * @return the dropped item (null - did not drop)
     */
    public static EntityItem reward(EntityPlayer p, Trinket t, net.minecraft.entity.Entity at) {
        if (t == null || p.worldObj.isRemote) return null;
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        NBTTagCompound ps = d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        long got = ps.getLong(GOT), bit = 1L << t.ordinal();
        boolean first = (got & bit) == 0;
        if (!first && p.worldObj.rand.nextFloat() >= 0.1F) {
            com.divinerpgextended.debug.Debug.note(p, "Accessory " + t.name().toLowerCase() + ": did not drop (repeat win, chance 10%)");
            return null;
        }
        com.divinerpgextended.debug.Debug.note(p, "Accessory " + t.name().toLowerCase() + ": dropped" + (first ? " (first win)" : " (repeat, 10%)"));
        ps.setLong(GOT, got | bit);
        ItemStack s = com.divinerpgextended.halite.Halite.own(stack(t), p);
        net.minecraft.entity.Entity src = at != null && at.worldObj == p.worldObj ? at : p;
        EntityItem it = new EntityItem(p.worldObj, src.posX, src.posY + 1, src.posZ, s);
        it.delayBeforeCanPickup = 10;
        NBTTagCompound tag = new NBTTagCompound();
        it.writeToNBT(tag);
        tag.setBoolean("Invulnerable", true); // as in {@link com.divinerpgextended.boss.BossLoot}
        it.readFromNBT(tag);
        p.worldObj.spawnEntityInWorld(it);
        say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.chat.accessory_drop", s.getDisplayName());
        return it;
    }

    /**
     * The Colossus is not a separate boss in the records (counted together with the Lich King), its pauldron goes to those nearby.
     * Ayeraco is five dragons: the wing is given when the last of the pack falls.
     */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Trinket t;
        if (e.entityLiving instanceof EntityFrostColossus) {
            t = Trinket.COLOSSUS_PAULDRON;
        } else if (e.entityLiving instanceof EntityAyeraco) {
            List<?> flock = e.entityLiving.worldObj.getEntitiesWithinAABB(EntityAyeraco.class,
                e.entityLiving.boundingBox.expand(128, 96, 128));
            for (Object o : flock) if (o != e.entityLiving && ((EntityAyeraco) o).isEntityAlive()) return;
            t = Trinket.AYERACO_WING;
        } else {
            return;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = e.entityLiving.worldObj.getEntitiesWithinAABB(EntityPlayer.class,
            e.entityLiving.boundingBox.expand(64, 48, 64));
        for (EntityPlayer p : near) reward(p, t, e.entityLiving);
    }

    /** Dungeon Lamp is a rare drop from Arcana mobs. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote || !e.recentlyHit || !(e.source.getEntity() instanceof EntityPlayer)) return;
        if (!(e.entityLiving instanceof IMob) || !e.entityLiving.getClass().getName().startsWith("net.divinerpg.entities.arcana")) return;
        int looting = EnchantmentHelper.getLootingModifier((EntityPlayer) e.source.getEntity());
        if (e.entityLiving.worldObj.rand.nextFloat() >= 0.01F + 0.005F * looting) return;
        e.drops.add(new EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY, e.entityLiving.posZ,
            stack(Trinket.DUNGEON_LAMP)));
    }

    // ---------------- Merchants ----------------

    private static void trade(MerchantRecipeList list, MerchantRecipe offer) {
        if (list == null) return;
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            ItemStack sell = ((MerchantRecipe) it.next()).getItemToSell();
            if (sell != null && sell.getItem() == ModItems.trinket) it.remove();
        }
        list.add(offer);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (e.target instanceof EntityWorkshopTinkerer && p.isSneaking()) {
            e.setCanceled(true);
            if (!p.worldObj.isRemote) combine(p);
            return;
        }
        if (p.isSneaking()) return; // sneaking means Jack's orders and merchant services, not trading
        if (e.target instanceof EntityCaptianMerik) {
            trade(((EntityCaptianMerik) e.target).getRecipes(p), new InfiniteTrade(new ItemStack(ArcanaItems.arcanium, 30),
                new ItemStack(ArcanaItems.dungeonTokens, 10), stack(Trinket.MASTER_SCABBARD)));
        } else if (e.target instanceof EntityWorkshopMerchant) {
            trade(((EntityWorkshopMerchant) e.target).getRecipes(p), new InfiniteTrade(new ItemStack(IceikaItems.snowflake, 6),
                new ItemStack(IceikaItems.iceShards, 16), stack(Trinket.WARM_SCARF)));
        }
    }

    // ---------------- Merging at the workshop master ----------------

    private static int find(EntityPlayer p, Trinket t, int skipSlot) {
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (i != skipSlot && s != null && s.getItem() == ModItems.trinket && ItemTrinket.of(s) == t) return i;
        }
        return -1;
    }

    private static int snowflakes(EntityPlayer p) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == IceikaItems.snowflake) n += s.stackSize;
        return n;
    }

    private static void combine(EntityPlayer p) {
        ItemStack held = p.getCurrentEquippedItem();
        int hand = p.inventory.currentItem;
        Trinket a = held != null && held.getItem() == ModItems.trinket ? ItemTrinket.of(held) : null;
        Trinket result = null, b = null;
        if (a != null) {
            for (Trinket t : Trinket.values()) {
                Trinket r = Trinket.combine(a, t);
                if (r != null) {
                    b = t;
                    result = r;
                    break;
                }
            }
        }
        if (result == null) {
            say(p, EnumChatFormatting.GOLD, "dre.tinker.menu");
            for (Trinket t : Trinket.values()) {
                Trinket[] parts = t.parts();
                if (parts == null) continue;
                say(p, EnumChatFormatting.YELLOW, "dre.tinker.line", name(t), name(parts[0]), name(parts[1]));
            }
            return;
        }
        int other = find(p, b, hand);
        if (other < 0) {
            say(p, EnumChatFormatting.GRAY, "dre.tinker.need", name(b), name(result));
            return;
        }
        long now = p.worldObj.getTotalWorldTime();
        long[] pend = PENDING.get(p.getUniqueID());
        if (pend == null || now - pend[0] > 100 || pend[1] != result.ordinal()) {
            PENDING.put(p.getUniqueID(), new long[] { now, result.ordinal() });
            say(p, EnumChatFormatting.YELLOW, "dre.tinker.offer", name(a), name(b), name(result), String.valueOf(COMBINE_SNOWFLAKES));
            return;
        }
        PENDING.remove(p.getUniqueID());
        if (!p.capabilities.isCreativeMode && snowflakes(p) < COMBINE_SNOWFLAKES) {
            say(p, EnumChatFormatting.RED, "dre.tinker.no_money", String.valueOf(COMBINE_SNOWFLAKES));
            return;
        }
        // both items are taken from specific slots (their presence was just checked), then the result is handed out
        p.inventory.setInventorySlotContents(hand, null);
        p.inventory.setInventorySlotContents(other, null);
        if (!p.capabilities.isCreativeMode) {
            int n = COMBINE_SNOWFLAKES;
            for (int i = 0; i < p.inventory.mainInventory.length && n > 0; i++) {
                ItemStack s = p.inventory.mainInventory[i];
                if (s == null || s.getItem() != IceikaItems.snowflake) continue;
                int t = Math.min(n, s.stackSize);
                s.stackSize -= t;
                n -= t;
                if (s.stackSize <= 0) p.inventory.mainInventory[i] = null;
            }
        }
        give(p, stack(result));
        p.inventoryContainer.detectAndSendChanges();
        p.worldObj.playSoundAtEntity(p, "random.anvil_use", 0.6F, 1.3F);
        say(p, EnumChatFormatting.GREEN, "dre.tinker.done", name(result));
    }

    private static ChatComponentTranslation name(Trinket t) {
        return new ChatComponentTranslation("item.dre.trinket." + t.key() + ".name");
    }
}
