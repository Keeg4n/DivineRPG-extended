package com.divinerpgextended.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.divinerpg.utils.blocks.VanillaBlocks;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandom;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.DivineRPGExtended;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Corruption altar from Divine, with the addon's rules.
 * <ul>
 * <li>Enchantments are rolled when an item is placed in the altar ({@link #rollAll}): take it out and put it back - new ones, what was revealed is gone.</li>
 * <li>The slot sets only the strength of the roll: the vanilla price with a full table, multiplied by 5/3 (first 3-17, second 10-35,
 * last 50). The enchantment level is the highest the strength can afford (vanilla also has a cap on top, and at high strength
 * Protection disappears entirely). Which enchantment - by vanilla weights, so the best in the last slot is not guaranteed.</li>
 * <li>The price is by the rolled enchantments ({@link #price}): tier points ({@link #tier}) for a full level, for a partial one - proportionally.
 * Item - the sum, at most {@link Config#altarTopLevels}; book - {@link Config#altarBookBase} + the sum, at most
 * {@link Config#altarBookMax}. Within a tier the price is the same: otherwise the price would hand out an enchantment without payment.</li>
 * <li>The price is taken in levels from the top, like the vanilla table.</li>
 * <li>Book: the item is chosen first ({@link #BOOK_TARGETS}), all the book's enchantments are for it; the book gets all enchantments of the roll.</li>
 * <li>Reveal a slot's enchantments: payment by item kind ({@link Config#altarRevealItems}) - shards for the first and second slots,
 * a stone for the last, rupee ingots for a book.</li>
 * </ul>
 */
public class AltarLogic {

    public static final int SLOTS = 3;
    /** Strength of the last slot: enough for Sharpness V, Protection IV, Efficiency V. */
    private static final int TOP_POWER = 50;
    /** The player's seed from 0.99.70 in PERSISTED: no longer needed, erased when the altar is opened. */
    private static final String OLD_TAG = "dreAltar";

    /** The best enchantments (tier 0). */
    public static final Set<Enchantment> BEST = new HashSet<Enchantment>(Arrays.asList(Enchantment.protection, Enchantment.sharpness,
        Enchantment.looting, Enchantment.efficiency, Enchantment.silkTouch, Enchantment.fortune, Enchantment.power, Enchantment.infinity));
    /** Weak enchantments (tier 2); the rest, including enchantments from other mods, are medium. */
    public static final Set<Enchantment> WEAK = new HashSet<Enchantment>(Arrays.asList(Enchantment.smite, Enchantment.baneOfArthropods,
        Enchantment.knockback, Enchantment.punch, Enchantment.fireProtection, Enchantment.blastProtection, Enchantment.projectileProtection));

    /** Items a book is enchanted for, and their weights (armor 4 in total, sword and pickaxe 3 each, bow 2, fishing rod 1). */
    public static final Item[] BOOK_TARGETS = { Items.diamond_helmet, Items.diamond_chestplate, Items.diamond_leggings, Items.diamond_boots,
        Items.diamond_sword, Items.diamond_pickaxe, Items.bow, Items.fishing_rod };
    private static final int[] BOOK_WEIGHTS = { 1, 1, 1, 1, 3, 3, 2, 1 };

    /** Payment for "reveal" by item kind from the config; parsed on first access (items are already registered). */
    private static Map<String, Item[]> payments;

    public static boolean altar(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) == VanillaBlocks.altarOfCorruption;
    }

    public static boolean book(ItemStack s) {
        return s != null && s.getItem() == Items.book;
    }

    /** Enchantment tier for the price: 0 - best, 1 - medium, 2 - weak. */
    public static int tier(Enchantment e) {
        if (e instanceof AltarEnchant) return ((AltarEnchant) e).altarTier();
        return BEST.contains(e) ? 0 : WEAK.contains(e) ? 2 : 1;
    }

    /** Price of the last slot (the highest). */
    public static int price(List<EnchantmentData> list, boolean book) {
        return price(list, book, SLOTS - 1);
    }

    /** Slot price in levels by the rolled enchantments, at most the slot cap ({@link #cap}); 0 - nothing to enchant with. */
    public static int price(List<EnchantmentData> list, boolean book, int slot) {
        if (list.isEmpty()) return 0;
        int[] pts = book ? Config.altarBookPoints : Config.altarItemPoints;
        float sum = 0;
        for (EnchantmentData d : list) {
            int t = Math.min(tier(d.enchantmentobj), pts.length - 1);
            sum += t < 0 ? 0 : pts[t] * (float) d.enchantmentLevel / Math.max(1, d.enchantmentobj.getMaxLevel());
        }
        int p = Math.round(sum) + (book ? Config.altarBookBase : 0);
        return Math.max(1, Math.min(p, cap(book, slot)));
    }

    /**
     * Slot price cap: the last one is the whole cap, the first ones are a third and two thirds of the way from the base (item 40 / 80 / 120, book
     * 70 / 110 / 150). Otherwise the second slot with a pile of medium enchantments costs as much as the last, yet is revealed for shards.
     */
    public static int cap(boolean book, int slot) {
        int lo = book ? Config.altarBookBase : 0, hi = book ? Config.altarBookMax : Config.altarTopLevels;
        return lo + Math.round((hi - lo) * (slot + 1) / (float) SLOTS);
    }

    /** Rolls of the three slots for an item (empty - slot closed). */
    public static List<List<EnchantmentData>> rollAll(Random r, ItemStack s) {
        List<List<EnchantmentData>> out = new ArrayList<List<EnchantmentData>>();
        for (int i = 0; i < SLOTS; i++) out.add(roll(r, s, i));
        return out;
    }

    /** Slot enchantments: exactly what "reveal" will show and what will go on the item. Empty - nothing to enchant with. */
    public static List<EnchantmentData> roll(Random r, ItemStack s, int slot) {
        List<EnchantmentData> out = new ArrayList<EnchantmentData>();
        if (s == null || !s.isItemEnchantable()) return out;
        int base = EnchantmentHelper.calcItemStackEnchantability(r, slot, 15, s);
        if (base <= 0) return out;
        int cost = Math.max(1, Math.round(base * TOP_POWER / 30F));
        // strength as in vanilla: slot strength + a bit from the item's enchantability, 15% spread
        int k = s.getItem().getItemEnchantability(s) / 2;
        k = 1 + r.nextInt((k >> 1) + 1) + r.nextInt((k >> 1) + 1);
        float f = (r.nextFloat() + r.nextFloat() - 1.0F) * 0.15F;
        int power = Math.max(1, (int) ((k + cost) * (1.0F + f) + 0.5F));
        boolean book = book(s);
        ItemStack target = book ? bookTarget(r, power) : s;
        if (target == null) return out;
        Map<Integer, EnchantmentData> pool = pool(target, book, power);
        if (pool.isEmpty()) return out;
        out.add((EnchantmentData) WeightedRandom.getRandomItem(r, pool.values()));
        for (int extra = power; r.nextInt(50) <= extra; extra >>= 1) {
            for (Iterator<EnchantmentData> it = pool.values().iterator(); it.hasNext();) {
                Enchantment e = it.next().enchantmentobj;
                for (EnchantmentData d : out) {
                    if (d.enchantmentobj == e || !d.enchantmentobj.canApplyTogether(e) || !e.canApplyTogether(d.enchantmentobj)) {
                        it.remove();
                        break;
                    }
                }
            }
            if (!pool.isEmpty()) out.add((EnchantmentData) WeightedRandom.getRandomItem(r, pool.values()));
        }
        return out;
    }

    /**
     * Enchantments that go on the item at this strength: each at the highest level the strength can afford. Besides the table ones -
     * the altar-only ones ({@link AltarEnchant}, the addon's own weapon enchantments).
     */
    public static Map<Integer, EnchantmentData> pool(ItemStack target, boolean book, int power) {
        Map<Integer, EnchantmentData> m = new LinkedHashMap<Integer, EnchantmentData>();
        for (Enchantment e : Enchantment.enchantmentsList) {
            if (e == null || e.getWeight() <= 0) continue;
            if (!fits(e, target) || book && !e.isAllowedOnBooks()) continue;
            int lvl = 0;
            for (int l = e.getMinLevel(); l <= e.getMaxLevel(); l++) if (power >= e.getMinEnchantability(l)) lvl = l;
            if (lvl > 0) m.put(e.effectId, new EnchantmentData(e, lvl));
        }
        return m;
    }

    /** The altar may roll the enchantment for the item: like the table, plus the altar-only ones. */
    public static boolean fits(Enchantment e, ItemStack target) {
        return e instanceof AltarEnchant ? ((AltarEnchant) e).canApplyAtAltar(target) : e.canApplyAtEnchantingTable(target);
    }

    /** Item for a book: by weights, among those that get something at this strength (a fishing rod has nothing to get at low strength). */
    private static ItemStack bookTarget(Random r, int power) {
        int[] w = new int[BOOK_TARGETS.length];
        int total = 0;
        for (int i = 0; i < w.length; i++) total += w[i] = pool(new ItemStack(BOOK_TARGETS[i]), true, power).isEmpty() ? 0 : BOOK_WEIGHTS[i];
        if (total == 0) return null;
        int x = r.nextInt(total);
        for (int i = 0; i < w.length; i++) if ((x -= w[i]) < 0) return new ItemStack(BOOK_TARGETS[i]);
        return null;
    }

    /** Put enchantments on the item; a book becomes an enchanted book (name and data remain). */
    public static void apply(ItemStack s, List<EnchantmentData> list) {
        boolean book = book(s);
        if (book) s.func_150996_a(Items.enchanted_book);
        for (EnchantmentData d : list) {
            if (book) Items.enchanted_book.addEnchantment(s, d);
            else s.addEnchantment(d.enchantmentobj, d.enchantmentLevel);
        }
    }

    /** Item kind for payment: weapon, tool, armor, bow, book or other. */
    public static String kind(ItemStack s) {
        Item i = s == null ? null : s.getItem();
        if (i == null) return "other";
        if (i == Items.book) return "book";
        if (EnumEnchantmentType.weapon.canEnchantItem(i)) return "weapon";
        if (EnumEnchantmentType.bow.canEnchantItem(i)) return "bow";
        if (EnumEnchantmentType.armor.canEnchantItem(i)) return "armor";
        if (EnumEnchantmentType.digger.canEnchantItem(i) || EnumEnchantmentType.fishing_rod.canEnchantItem(i)) return "tool";
        return "other";
    }

    private static Map<String, Item[]> payments() {
        if (payments != null) return payments;
        Map<String, Item[]> m = new HashMap<String, Item[]>();
        for (String line : Config.altarRevealItems) {
            String[] kv = line.split("=");
            if (kv.length != 2) continue;
            String[] names = kv[1].split(",");
            Object first = Item.itemRegistry.getObject(names[0].trim()), last = Item.itemRegistry.getObject(names[names.length - 1].trim());
            if (first instanceof Item && last instanceof Item) m.put(kv[0].trim(), new Item[] { (Item) first, (Item) last });
        }
        return payments = m;
    }

    /** What to pay for "reveal" of this item's slot: the first of the kind's pair - for the first and second slots, the second - for the last. */
    public static Item revealItem(ItemStack s, int slot) {
        Item[] p = payments().get(kind(s));
        if (p == null) p = payments().get("other");
        if (p == null) return slot == SLOTS - 1 ? VanillaItemsOther.corruptedStone : VanillaItemsOther.corruptedShards;
        return p[slot == SLOTS - 1 ? 1 : 0];
    }

    public static int revealCount(ItemStack s, int slot) {
        int[] c = book(s) ? Config.altarBookRevealCost : Config.altarRevealCost;
        return c.length == 0 ? 0 : Math.max(0, c[Math.min(slot, c.length - 1)]);
    }

    /** Fits the payment cell: this pays for at least one item kind. */
    public static boolean payment(ItemStack s) {
        if (s == null) return false;
        if (s.getItem() == VanillaItemsOther.corruptedShards || s.getItem() == VanillaItemsOther.corruptedStone) return true;
        for (Item[] p : payments().values()) if (p[0] == s.getItem() || p[1] == s.getItem()) return true;
        return false;
    }

    /**
     * Right-click on the corruption altar opens the addon's window. Divine also opens its window only from the server, so it is enough to
     * cancel the click on the server. Sneaking with a block in hand - place the block, like on any table.
     */
    public static class Open {

        @SubscribeEvent
        public void onInteract(PlayerInteractEvent e) {
            if (!Config.corruptAltar || e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote) return;
            if (!altar(e.world, e.x, e.y, e.z)) return;
            EntityPlayer p = e.entityPlayer;
            ItemStack held = p.getHeldItem();
            if (p.isSneaking() && held != null && !held.getItem().doesSneakBypassUse(e.world, e.x, e.y, e.z, p)) return;
            e.setCanceled(true);
            p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).removeTag(OLD_TAG);
            p.openGui(DivineRPGExtended.instance, GuiHandler.ALTAR, e.world, e.x, e.y, e.z);
            try {
                p.triggerAchievement(net.divinerpg.libs.DivineRPGAchievements.perfectlyCorrupted); // as with the Divine altar
            } catch (Throwable t) {
                // the achievement may not exist in another version of Divine
            }
        }
    }
}
