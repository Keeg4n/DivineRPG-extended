package com.divinerpgextended.station;

import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.WeightedRandom;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.DivineRPGExtended;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Алтарь порчи Divine по правилам аддона. В 1.7.10 чары выбрасываются в момент клика, и показать их заранее нельзя;
 * здесь, как в 1.8, у игрока своё зерно ({@link #seed}): цены слотов и чары зависят только от зерна и вещи, меняются
 * лишь после зачарования. Вынуть и вставить вещь ничего не меняет.
 * <ul>
 * <li>Полки не нужны (как и у Divine). Последний слот стоит {@link Config#altarTopLevels} уровней, первые два - ванильные
 * цены, умноженные во столько же раз (50: 3-17 и 10-35). Сила броска равна цене.</li>
 * <li>Уровень чара - высший, на который хватает силы. У ванили сверху ещё потолок, и при большой силе Защита
 * пропадает совсем.</li>
 * <li>Последний слот - только лучшие чары ({@link #TOP}); если у вещи таких нет - любые.</li>
 * <li>Книга: сначала выбирается вещь ({@link #BOOK_TARGETS}), и все чары книги - для неё. Книга получает все чары
 * броска (ваниль один выкидывает).</li>
 * <li>Узнать чары слота: осколки порчи за первый и второй, камень порчи за последний ({@link Config#altarRevealCost}).
 * Узнанное помнится до следующего зачарования.</li>
 * </ul>
 */
public class AltarLogic {

    public static final int SLOTS = 3;
    /** Данные игрока в PERSISTED (переживают смерть): зерно и что уже узнано. */
    static final String TAG = "dreAltar";
    private static final int SEEN_MAX = 64;

    /** Лучшие чары: только они выпадают в последнем слоте. */
    public static final Set<Enchantment> TOP = new HashSet<Enchantment>(Arrays.asList(Enchantment.protection, Enchantment.featherFalling,
        Enchantment.respiration, Enchantment.aquaAffinity, Enchantment.sharpness, Enchantment.looting, Enchantment.fireAspect,
        Enchantment.efficiency, Enchantment.silkTouch, Enchantment.fortune, Enchantment.unbreaking, Enchantment.power, Enchantment.flame,
        Enchantment.infinity, Enchantment.field_151370_z, Enchantment.field_151369_A));

    /** Вещи, под которые зачаровывается книга, и их веса (броня всего 4, меч и кирка по 3, лук 2, удочка 1). */
    public static final Item[] BOOK_TARGETS = { Items.diamond_helmet, Items.diamond_chestplate, Items.diamond_leggings, Items.diamond_boots,
        Items.diamond_sword, Items.diamond_pickaxe, Items.bow, Items.fishing_rod };
    private static final int[] BOOK_WEIGHTS = { 1, 1, 1, 1, 3, 3, 2, 1 };

    public static boolean altar(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) == VanillaBlocks.altarOfCorruption;
    }

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound root = p.getEntityData();
        NBTTagCompound kept = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        root.setTag(EntityPlayer.PERSISTED_NBT_TAG, kept);
        NBTTagCompound d = kept.getCompoundTag(TAG);
        kept.setTag(TAG, d);
        return d;
    }

    /** Зерно игрока (нет - заводится). */
    public static int seed(EntityPlayer p) {
        NBTTagCompound d = data(p);
        if (!d.hasKey("seed")) d.setInteger("seed", p.getRNG().nextInt());
        return d.getInteger("seed");
    }

    /** После зачарования: новое зерно, узнанное забывается. */
    public static void reseed(EntityPlayer p) {
        NBTTagCompound d = data(p);
        d.setInteger("seed", p.getRNG().nextInt());
        d.removeTag("seen");
    }

    /** Чары зависят от предмета, а не от износа: у вещей с прочностью мета не учитывается. */
    private static String key(ItemStack s, int slot) {
        Item i = s.getItem();
        return Item.itemRegistry.getNameForObject(i) + "@" + (i.getHasSubtypes() ? s.getItemDamage() : 0) + "#" + slot;
    }

    /** Чары слота для этой вещи уже оплачены. */
    public static boolean seen(EntityPlayer p, ItemStack s, int slot) {
        if (s == null) return false;
        NBTTagList l = data(p).getTagList("seen", 8);
        String k = key(s, slot);
        for (int i = 0; i < l.tagCount(); i++) if (k.equals(l.getStringTagAt(i))) return true;
        return false;
    }

    public static void see(EntityPlayer p, ItemStack s, int slot) {
        NBTTagCompound d = data(p);
        NBTTagList l = d.getTagList("seen", 8);
        if (l.tagCount() >= SEEN_MAX) l.removeTag(0);
        l.appendTag(new NBTTagString(key(s, slot)));
        d.setTag("seen", l);
    }

    /** Зерно для Random: у соседних зёрен первые броски Random почти одинаковы - перемешиваем (murmur3). */
    private static long mix(long s) {
        s = (s ^ s >>> 33) * 0xff51afd7ed558ccdL;
        s = (s ^ s >>> 33) * 0xc4ceb9fe1a85ec53L;
        return s ^ s >>> 33;
    }

    /** Цены трёх слотов в уровнях (0 - слот закрыт): ванильные при полной силе стола, умноженные под последний слот. */
    public static int[] costs(int seed, ItemStack s) {
        int[] out = new int[SLOTS];
        if (s == null || !s.isItemEnchantable()) return out;
        Random r = new Random(mix(seed));
        for (int i = 0; i < SLOTS; i++) {
            int base = EnchantmentHelper.calcItemStackEnchantability(r, i, 15, s);
            out[i] = base <= 0 ? 0 : Math.max(1, Math.round(base * Config.altarTopLevels / 30F));
        }
        return out;
    }

    /** Чары слота: ровно то, что покажет "узнать" и что ляжет на вещь. Пусто - зачаровать нечем. */
    public static List<EnchantmentData> roll(int seed, ItemStack s, int slot) {
        List<EnchantmentData> out = new ArrayList<EnchantmentData>();
        int cost = costs(seed, s)[slot];
        if (cost <= 0) return out;
        Random r = new Random(mix(seed * 31L + slot + 1));
        boolean book = s.getItem() == Items.book;
        ItemStack target = book ? bookTarget(r) : s;
        // сила как у ванили: цена + немного от зачаруемости вещи, разброс 15%
        int k = s.getItem().getItemEnchantability(s) / 2;
        k = 1 + r.nextInt((k >> 1) + 1) + r.nextInt((k >> 1) + 1);
        float f = (r.nextFloat() + r.nextFloat() - 1.0F) * 0.15F;
        int power = Math.max(1, (int) ((k + cost) * (1.0F + f) + 0.5F));
        boolean top = slot == SLOTS - 1;
        Map<Integer, EnchantmentData> pool = pool(target, book, power, top);
        if (pool.isEmpty() && top) pool = pool(target, book, power, false);
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
    private static Map<Integer, EnchantmentData> pool(ItemStack target, boolean book, int power, boolean topOnly) {
        Map<Integer, EnchantmentData> m = new LinkedHashMap<Integer, EnchantmentData>();
        for (Enchantment e : Enchantment.enchantmentsList) {
            if (e == null || e.getWeight() <= 0 || topOnly && !TOP.contains(e)) continue;
            if (!e.canApplyAtEnchantingTable(target) || book && !e.isAllowedOnBooks()) continue;
            int lvl = 0;
            for (int l = e.getMinLevel(); l <= e.getMaxLevel(); l++) if (power >= e.getMinEnchantability(l)) lvl = l;
            if (lvl > 0) m.put(e.effectId, new EnchantmentData(e, lvl));
        }
        return m;
    }

    private static ItemStack bookTarget(Random r) {
        int total = 0;
        for (int w : BOOK_WEIGHTS) total += w;
        int x = r.nextInt(total);
        for (int i = 0; i < BOOK_TARGETS.length; i++) if ((x -= BOOK_WEIGHTS[i]) < 0) return new ItemStack(BOOK_TARGETS[i]);
        return new ItemStack(Items.diamond_sword);
    }

    /** Положить чары на вещь; книга становится зачарованной книгой (имя и данные остаются). */
    public static void apply(ItemStack s, List<EnchantmentData> list) {
        boolean book = s.getItem() == Items.book;
        if (book) s.func_150996_a(Items.enchanted_book);
        for (EnchantmentData d : list) {
            if (book) Items.enchanted_book.addEnchantment(s, d);
            else s.addEnchantment(d.enchantmentobj, d.enchantmentLevel);
        }
    }

    /** Чем платить за "узнать": осколки порчи за первый и второй слоты, камень порчи за последний. */
    public static Item revealItem(int slot) {
        return slot == SLOTS - 1 ? VanillaItemsOther.corruptedStone : VanillaItemsOther.corruptedShards;
    }

    public static int revealCount(int slot) {
        int[] c = Config.altarRevealCost;
        return c.length == 0 ? 0 : Math.max(0, c[Math.min(slot, c.length - 1)]);
    }

    public static boolean payment(ItemStack s) {
        return s != null && (s.getItem() == VanillaItemsOther.corruptedShards || s.getItem() == VanillaItemsOther.corruptedStone);
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
            p.openGui(DivineRPGExtended.instance, GuiHandler.ALTAR, e.world, e.x, e.y, e.z);
            try {
                p.triggerAchievement(net.divinerpg.libs.DivineRPGAchievements.perfectlyCorrupted); // как у алтаря Divine
            } catch (Throwable t) {
                // в другой версии Divine ачивки может не быть
            }
        }
    }
}
