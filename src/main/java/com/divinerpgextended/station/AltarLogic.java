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
 * Алтарь порчи Divine по правилам аддона.
 * <ul>
 * <li>Чары бросаются, когда вещь кладут в алтарь ({@link #rollAll}): вынул и положил - новые, узнанное пропало.</li>
 * <li>Слот задаёт только силу броска: ванильная цена при полном столе, умноженная в 5/3 (первый 3-17, второй 10-35,
 * последний 50). Уровень чара - высший, на который хватает силы (у ванили сверху ещё потолок, и при большой силе
 * Защита пропадает совсем). Какой чар - по ванильным весам, так что лучший в последнем слоте не гарантирован.</li>
 * <li>Цена - по выпавшим чарам ({@link #price}): очки тира ({@link #tier}) за полный уровень, за неполный - по доле.
 * Вещь - сумма, не дороже {@link Config#altarTopLevels}; книга - {@link Config#altarBookBase} + сумма, не дороже
 * {@link Config#altarBookMax}. Внутри тира цена одна: иначе цена выдала бы чар без оплаты.</li>
 * <li>Опыт списывается так, будто уровни считаются с нуля ({@link #takeLevels}).</li>
 * <li>Книга: сначала выбирается вещь ({@link #BOOK_TARGETS}), все чары книги - для неё; книга получает все чары броска.</li>
 * <li>Узнать чары слота: оплата по виду вещи ({@link Config#altarRevealItems}) - осколки за первый и второй слоты,
 * камень за последний, у книги - рупиевые слитки.</li>
 * </ul>
 */
public class AltarLogic {

    public static final int SLOTS = 3;
    /** Сила последнего слота: хватает на Остроту V, Защиту IV, Эффективность V. */
    private static final int TOP_POWER = 50;
    /** Зерно игрока из 0.99.70 в PERSISTED: больше не нужно, стирается при открытии алтаря. */
    private static final String OLD_TAG = "dreAltar";

    /** Лучшие чары (тир 0). */
    public static final Set<Enchantment> BEST = new HashSet<Enchantment>(Arrays.asList(Enchantment.protection, Enchantment.sharpness,
        Enchantment.looting, Enchantment.efficiency, Enchantment.silkTouch, Enchantment.fortune, Enchantment.power, Enchantment.infinity));
    /** Слабые чары (тир 2); остальные, в том числе чары других модов, - средние. */
    public static final Set<Enchantment> WEAK = new HashSet<Enchantment>(Arrays.asList(Enchantment.smite, Enchantment.baneOfArthropods,
        Enchantment.knockback, Enchantment.punch, Enchantment.fireProtection, Enchantment.blastProtection, Enchantment.projectileProtection));

    /** Вещи, под которые зачаровывается книга, и их веса (броня всего 4, меч и кирка по 3, лук 2, удочка 1). */
    public static final Item[] BOOK_TARGETS = { Items.diamond_helmet, Items.diamond_chestplate, Items.diamond_leggings, Items.diamond_boots,
        Items.diamond_sword, Items.diamond_pickaxe, Items.bow, Items.fishing_rod };
    private static final int[] BOOK_WEIGHTS = { 1, 1, 1, 1, 3, 3, 2, 1 };

    /** Оплата "узнать" по видам вещей из конфига; разбирается при первом обращении (предметы уже зарегистрированы). */
    private static Map<String, Item[]> payments;

    public static boolean altar(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) == VanillaBlocks.altarOfCorruption;
    }

    public static boolean book(ItemStack s) {
        return s != null && s.getItem() == Items.book;
    }

    /** Тир чара для цены: 0 - лучшие, 1 - средние, 2 - слабые. */
    public static int tier(Enchantment e) {
        return BEST.contains(e) ? 0 : WEAK.contains(e) ? 2 : 1;
    }

    /** Цена последнего слота (самая высокая). */
    public static int price(List<EnchantmentData> list, boolean book) {
        return price(list, book, SLOTS - 1);
    }

    /** Цена слота в уровнях по выпавшим чарам, не выше потолка слота ({@link #cap}); 0 - зачаровать нечем. */
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
     * Потолок цены слота: последний - весь потолок, первые - треть и две трети пути от базы (вещь 17 / 33 / 50, книга
     * 60 / 90 / 120). Иначе второй слот с кучей средних чаров стоит как последний, а узнаётся за осколки.
     */
    public static int cap(boolean book, int slot) {
        int lo = book ? Config.altarBookBase : 0, hi = book ? Config.altarBookMax : Config.altarTopLevels;
        return lo + Math.round((hi - lo) * (slot + 1) / (float) SLOTS);
    }

    /** Броски трёх слотов для вещи (пустой - слот закрыт). */
    public static List<List<EnchantmentData>> rollAll(Random r, ItemStack s) {
        List<List<EnchantmentData>> out = new ArrayList<List<EnchantmentData>>();
        for (int i = 0; i < SLOTS; i++) out.add(roll(r, s, i));
        return out;
    }

    /** Чары слота: ровно то, что покажет "узнать" и что ляжет на вещь. Пусто - зачаровать нечем. */
    public static List<EnchantmentData> roll(Random r, ItemStack s, int slot) {
        List<EnchantmentData> out = new ArrayList<EnchantmentData>();
        if (s == null || !s.isItemEnchantable()) return out;
        int base = EnchantmentHelper.calcItemStackEnchantability(r, slot, 15, s);
        if (base <= 0) return out;
        int cost = Math.max(1, Math.round(base * TOP_POWER / 30F));
        // сила как у ванили: сила слота + немного от зачаруемости вещи, разброс 15%
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

    /** Чары, которые встают на вещь при этой силе: у каждого высший уровень, на который хватает силы. */
    private static Map<Integer, EnchantmentData> pool(ItemStack target, boolean book, int power) {
        Map<Integer, EnchantmentData> m = new LinkedHashMap<Integer, EnchantmentData>();
        for (Enchantment e : Enchantment.enchantmentsList) {
            if (e == null || e.getWeight() <= 0) continue;
            if (!e.canApplyAtEnchantingTable(target) || book && !e.isAllowedOnBooks()) continue;
            int lvl = 0;
            for (int l = e.getMinLevel(); l <= e.getMaxLevel(); l++) if (power >= e.getMinEnchantability(l)) lvl = l;
            if (lvl > 0) m.put(e.effectId, new EnchantmentData(e, lvl));
        }
        return m;
    }

    /** Вещь под книгу: по весам, из тех, на которые при этой силе что-то встаёт (удочке на малой силе дать нечего). */
    private static ItemStack bookTarget(Random r, int power) {
        int[] w = new int[BOOK_TARGETS.length];
        int total = 0;
        for (int i = 0; i < w.length; i++) total += w[i] = pool(new ItemStack(BOOK_TARGETS[i]), true, power).isEmpty() ? 0 : BOOK_WEIGHTS[i];
        if (total == 0) return null;
        int x = r.nextInt(total);
        for (int i = 0; i < w.length; i++) if ((x -= w[i]) < 0) return new ItemStack(BOOK_TARGETS[i]);
        return null;
    }

    /** Положить чары на вещь; книга становится зачарованной книгой (имя и данные остаются). */
    public static void apply(ItemStack s, List<EnchantmentData> list) {
        boolean book = book(s);
        if (book) s.func_150996_a(Items.enchanted_book);
        for (EnchantmentData d : list) {
            if (book) Items.enchanted_book.addEnchantment(s, d);
            else s.addEnchantment(d.enchantmentobj, d.enchantmentLevel);
        }
    }

    /** Опыт с уровня level до следующего (1.7.10: до 15-го по 17, до 30-го +3 за уровень, дальше +7). */
    public static int barCap(int level) {
        return level >= 30 ? 62 + (level - 30) * 7 : level >= 15 ? 17 + (level - 15) * 3 : 17;
    }

    /** Опыт от нуля до уровня. */
    public static long xpTo(int level) {
        long t = 0;
        for (int i = 0; i < level; i++) t += barCap(i);
        return t;
    }

    /**
     * Списать цену. В 1.7.10 уровни снимаются сверху, и 50 уровней со 150-го стоят вдесятеро больше опыта, чем с 50-го;
     * здесь уходит столько опыта, сколько нужно от нуля до цены ({@link Config#altarXpFromZero}).
     */
    public static void takeLevels(EntityPlayer p, int cost) {
        if (!Config.altarXpFromZero) {
            p.addExperienceLevel(-cost);
            return;
        }
        long left = Math.max(0, xpTo(p.experienceLevel) + (long) (p.experience * barCap(p.experienceLevel)) - xpTo(cost));
        int lvl = 0;
        while (left >= barCap(lvl)) left -= barCap(lvl++);
        p.experienceLevel = lvl;
        p.experience = left / (float) barCap(lvl);
        p.addExperienceLevel(0); // игроку на сервере - разослать новый опыт
    }

    /** Вид вещи для оплаты: weapon, tool, armor, bow, book или other. */
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

    /** Чем платить за "узнать" слот этой вещи: первое из пары вида - за первый и второй слоты, второе - за последний. */
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

    /** Годится в ячейку оплаты: этим платят хоть за один вид вещей. */
    public static boolean payment(ItemStack s) {
        if (s == null) return false;
        if (s.getItem() == VanillaItemsOther.corruptedShards || s.getItem() == VanillaItemsOther.corruptedStone) return true;
        for (Item[] p : payments().values()) if (p[0] == s.getItem() || p[1] == s.getItem()) return true;
        return false;
    }

    /**
     * Правый клик по алтарю порчи открывает окно аддона. Divine тоже открывает окно только с сервера, поэтому хватает
     * отменить клик на сервере. Присев с блоком в руке - ставить блок, как у любого стола.
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
                p.triggerAchievement(net.divinerpg.libs.DivineRPGAchievements.perfectlyCorrupted); // как у алтаря Divine
            } catch (Throwable t) {
                // в другой версии Divine ачивки может не быть
            }
        }
    }
}
