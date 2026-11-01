package com.divinerpgextended.accessory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.item.ItemWandererArmor;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.ItemTrinket;
import com.divinerpgextended.trinket.Trinket;

/**
 * Гнёзда аксессуаров в броне. Встраивает и вынимает только Наковальня Странника ({@link com.divinerpgextended.station.AnvilLogic}):
 * внутри брони нет инвентаря и окна, только запись в её NBT, поэтому дюпать нечем - вставка работает как крафт.
 * <ul>
 * <li>броня Divine - по гнезду в каждой части; броня Странника - по гнезду и второе за босса части ({@link #SECOND});</li>
 * <li>аксессуар встаёт только в свою часть ({@link Trinket#armorParts}), один эффект в части - один раз;</li>
 * <li>работает, пока часть надета; смерть и выпадение брони аксессуары не трогают - они внутри.</li>
 * </ul>
 */
public final class Sockets {

    public static final String TAG = "dreSockets";
    /** Боссы, открывающие второе гнездо в части брони Странника: [шлем, нагрудник, поножи, ботинки]. */
    public static final Boss[] SECOND = { Boss.KAROT, Boss.LICH_KING, Boss.WATCHER, Boss.TWILIGHT_DEMON };
    private static final Trinket[] ALL = Trinket.values();

    /** Почему аксессуар не встаёт в часть брони. */
    public enum Fit {
        OK, PART, FULL, SAME, BOSS
    }

    private Sockets() {}

    /** Броня с гнёздами: Странника и любая Divine (ванильная и чужих модов - без гнёзд). */
    public static boolean socketable(ItemStack s) {
        if (s == null || !(s.getItem() instanceof ItemArmor)) return false;
        int t = ((ItemArmor) s.getItem()).armorType;
        return t >= 0 && t <= 3 && (s.getItem() instanceof ItemWandererArmor || s.getItem() instanceof net.divinerpg.items.base.ItemDivineArmor);
    }

    public static int type(ItemStack armor) {
        return ((ItemArmor) armor.getItem()).armorType;
    }

    /** Сколько гнёзд может быть у части вообще (для подсказки). */
    public static int maxCapacity(ItemStack armor) {
        return armor.getItem() instanceof ItemWandererArmor ? 2 : 1;
    }

    /** Сколько гнёзд открыто этому игроку: второе у брони Странника - за босса части. */
    public static int capacity(ItemStack armor, EntityPlayer p) {
        if (!(armor.getItem() instanceof ItemWandererArmor)) return 1;
        return secondOpen(p, type(armor)) ? 2 : 1;
    }

    public static boolean secondOpen(EntityPlayer p, int type) {
        return !Config.requireBossUnlocks || BossProgress.has(p, SECOND[type]);
    }

    /** Аксессуар аддона или вещь Divine, которая носится как аксессуар. */
    public static boolean accessory(ItemStack s) {
        return s != null && (s.getItem() == ModItems.trinket || Accessories.divineAccessory(s));
    }

    /** В какие части встраивается (биты {@link Trinket#HEAD}..{@link Trinket#FEET}). */
    public static int parts(ItemStack acc) {
        if (acc.getItem() == ModItems.trinket) return ItemTrinket.of(acc).armorParts();
        if (acc.getItem() == net.divinerpg.utils.items.VetheaItems.minersAmulet) return Trinket.HEAD;
        if (acc.getItem() == net.divinerpg.utils.items.VetheaItems.heivaBelt) return Trinket.LEGS;
        return 0;
    }

    /** Цена встраивания, уровней. */
    public static int levels(ItemStack acc) {
        if (acc.getItem() == ModItems.trinket) return ItemTrinket.of(acc).levels();
        return acc.getItem() == net.divinerpg.utils.items.VetheaItems.heivaBelt ? 15 : 10;
    }

    /** Что внутри (копии - менять через {@link #insert} и {@link #extract}). */
    public static List<ItemStack> contents(ItemStack armor) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        NBTTagList l = list(armor);
        for (int i = 0; l != null && i < l.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(l.getCompoundTagAt(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    public static int count(ItemStack armor) {
        NBTTagList l = list(armor);
        return l == null ? 0 : l.tagCount();
    }

    private static NBTTagList list(ItemStack armor) {
        if (armor == null || !armor.hasTagCompound() || !armor.getTagCompound().hasKey(TAG)) return null;
        return armor.getTagCompound().getTagList(TAG, 10);
    }

    /** Встанет ли аксессуар в эту часть (с уже запланированными в неё same). */
    public static Fit fit(ItemStack armor, ItemStack acc, EntityPlayer p, List<ItemStack> planned) {
        if (!socketable(armor) || !accessory(acc) || (parts(acc) & 1 << type(armor)) == 0) return Fit.PART;
        List<ItemStack> in = contents(armor);
        if (planned != null) in.addAll(planned);
        for (ItemStack s : in) if (overlap(s, acc)) return Fit.SAME;
        if (in.size() < capacity(armor, p)) return Fit.OK;
        return in.size() < maxCapacity(armor) ? Fit.BOSS : Fit.FULL;
    }

    /** Одинаковый эффект: та же вещь Divine или аксессуары с общим эффектом (объединённый и его часть). */
    static boolean overlap(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        if (a.getItem() != ModItems.trinket) return true;
        for (Trinket t : ItemTrinket.of(a).provides()) if (ItemTrinket.of(b).provides().contains(t)) return true;
        return false;
    }

    /** Записать аксессуар в часть (проверка - {@link #fit}). Вызывает только наковальня на сервере. */
    public static void insert(ItemStack armor, ItemStack acc) {
        ItemStack one = acc.copy();
        one.stackSize = 1;
        if (!armor.hasTagCompound()) armor.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = armor.getTagCompound();
        NBTTagList l = tag.hasKey(TAG) ? tag.getTagList(TAG, 10) : new NBTTagList();
        l.appendTag(one.writeToNBT(new NBTTagCompound()));
        tag.setTag(TAG, l);
    }

    /** Вынуть аксессуар из гнезда (null - пусто). Запись стирается раньше, чем вещь отдаётся. */
    public static ItemStack extract(ItemStack armor, int index) {
        NBTTagList l = list(armor);
        if (l == null || index < 0 || index >= l.tagCount()) return null;
        NBTTagCompound c = l.getCompoundTagAt(index);
        l.removeTag(index);
        if (l.tagCount() == 0) armor.getTagCompound().removeTag(TAG);
        return ItemStack.loadItemStackFromNBT(c);
    }

    /** Вынуть всё (броня уходит в крафт или на переплавку). */
    public static List<ItemStack> extractAll(ItemStack armor) {
        List<ItemStack> out = contents(armor);
        if (armor != null && armor.hasTagCompound()) armor.getTagCompound().removeTag(TAG);
        return out;
    }

    /**
     * Встроен ли аксессуар с этим эффектом в надетую броню. Спрашивают на каждом ударе и тике - читаем NBT без
     * создания вещей.
     */
    public static boolean wears(EntityPlayer p, Trinket t) {
        for (ItemStack armor : p.inventory.armorInventory) {
            NBTTagList l = list(armor);
            for (int i = 0; l != null && i < l.tagCount(); i++) {
                NBTTagCompound c = l.getCompoundTagAt(i);
                if (Item.getItemById(c.getShort("id")) != ModItems.trinket) continue;
                int m = c.getShort("Damage");
                if (m >= 0 && m < ALL.length && ALL[m].provides().contains(t)) return true;
            }
        }
        return false;
    }

    /** Встроена ли вещь (Divine-аксессуар) в надетую броню. */
    public static boolean wearsItem(EntityPlayer p, Item item) {
        for (ItemStack armor : p.inventory.armorInventory) {
            NBTTagList l = list(armor);
            for (int i = 0; l != null && i < l.tagCount(); i++) if (Item.getItemById(l.getCompoundTagAt(i).getShort("id")) == item) return true;
        }
        return false;
    }
}
