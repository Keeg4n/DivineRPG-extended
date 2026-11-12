package com.divinerpgextended.perk;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Метательное оружие Divine (сюрикены, Подлый ураган, сумеречные слайсеры) копится в одном предмете до {@link #MAX}: в
 * 1.7.10 стопка не бывает больше 64, а сюрикенов нужно много. Собранная стопка - один предмет с числом {@link #COUNT} в NBT
 * (две и больше штуки); вещей внутри нет, дюпать нечего.
 * <ul>
 * <li>присесть и ПКМ - собрать в предмет в руке все такие же из инвентаря ({@link #gather});</li>
 * <li>бросок тратит одну из числа ({@link #consume}, вызывается из бросков Divine через
 * {@link com.divinerpgextended.asm.ThrowableTransformer});</li>
 * <li>подобранные с земли такие же сами докладываются в собранную стопку ({@link #absorb});</li>
 * <li>собранная стопка не складывается с другими (предел стопки 1), число - на значке.</li>
 * </ul>
 */
public final class ThrowStack {

    public static final String COUNT = "dreThrown";
    public static final int MAX = 256;

    private ThrowStack() {}

    public static boolean is(Item i) {
        return i instanceof net.divinerpg.items.base.ItemThrowable || i instanceof net.divinerpg.items.vanilla.ItemVileStorm;
    }

    public static boolean is(ItemStack s) {
        return s != null && is(s.getItem());
    }

    /** Число собранной стопки (0 - обычная стопка). */
    public static int count(ItemStack s) {
        return s != null && s.hasTagCompound() && s.getTagCompound().hasKey(COUNT) ? Math.max(0, s.getTagCompound().getInteger(COUNT)) : 0;
    }

    /** Сколько всего штук в этой стопке. */
    public static int total(ItemStack s) {
        if (s == null) return 0;
        int c = count(s);
        return c > 0 ? c : s.stackSize;
    }

    /** Записать число: две и больше - собранная стопка (один предмет), одна - обычный предмет, ноль - пусто. */
    public static void setTotal(ItemStack s, int n) {
        if (n >= 2) {
            if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
            s.getTagCompound().setInteger(COUNT, n);
            s.stackSize = 1;
            return;
        }
        if (s.hasTagCompound()) {
            s.getTagCompound().removeTag(COUNT);
            if (s.getTagCompound().hasNoTags()) s.setTagCompound(null);
        }
        s.stackSize = Math.max(0, n);
    }

    /** Убрать n штук: из числа собранной стопки или из обычной стопки (обычная собранной не становится). */
    static void take(ItemStack s, int n) {
        if (count(s) > 0) setTotal(s, count(s) - n);
        else s.stackSize -= n;
    }

    /** Бросок: одна штука из числа (собранная стопка) или из стопки, как у Divine. */
    public static void consume(ItemStack s) {
        if (s == null) return;
        int c = count(s);
        if (c > 0) setTotal(s, c - 1);
        else s.stackSize--;
    }

    /** Предел стопки: собранная - 1 (не сливается с другими), обычная - как у предмета. */
    public static int limit(Item item, ItemStack s) {
        return count(s) > 0 ? 1 : item.getItemStackLimit();
    }

    /** Годится в сбор к held: тот же предмет и подвид, без своих данных (кроме числа). */
    static boolean same(ItemStack held, ItemStack s) {
        if (s == null || s == held || s.getItem() != held.getItem() || s.getItemDamage() != held.getItemDamage() || s.stackSize <= 0) return false;
        if (!s.hasTagCompound()) return true;
        NBTTagCompound t = (NBTTagCompound) s.getTagCompound().copy();
        t.removeTag(COUNT);
        return t.hasNoTags();
    }

    /** Сколько можно собрать в предмет в руке (0 - нечего или некуда). */
    public static int gatherable(EntityPlayer p) {
        ItemStack held = p.getHeldItem();
        if (!is(held)) return 0;
        int room = MAX - total(held), n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (same(held, s)) n += total(s);
        return Math.max(0, Math.min(room, n));
    }

    /** Собрать в предмет в руке такие же из инвентаря, пока влезает. Только сервер. @return сколько вошло */
    public static int gather(EntityPlayer p) {
        if (p.worldObj.isRemote) return 0;
        ItemStack held = p.getHeldItem();
        if (!is(held)) return 0;
        int have = total(held), got = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length && have < MAX; i++) {
            if (i == p.inventory.currentItem || !same(held, inv[i])) continue;
            int n = Math.min(MAX - have, total(inv[i]));
            take(inv[i], n); // сначала минус в источнике (обычная стопка остаётся обычной)
            if (inv[i].stackSize <= 0) inv[i] = null;
            have += n; // потом плюс в руке
            got += n;
        }
        if (got > 0) {
            setTotal(held, have);
            p.inventory.markDirty();
        }
        return got;
    }

    /** Подобранная с земли стопка: в собранную стопку того же вида, сколько влезет. @return сколько вошло */
    public static int absorb(EntityPlayer p, EntityItem e) {
        ItemStack in = e.getEntityItem();
        if (!is(in) || count(in) > 0 || in.hasTagCompound()) return 0;
        int got = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (in.stackSize <= 0) break;
            if (s == null || count(s) <= 0 || s.getItem() != in.getItem() || s.getItemDamage() != in.getItemDamage()) continue;
            int n = Math.min(MAX - count(s), in.stackSize);
            if (n <= 0) continue;
            in.stackSize -= n;
            setTotal(s, count(s) + n);
            got += n;
        }
        if (got > 0) {
            e.setEntityItemStack(in);
            p.inventory.markDirty();
        }
        return got;
    }

    /** Вернуть одну штуку в предмет в руке (перевязь метателя): в собранную стопку или в обычную, иначе в инвентарь. */
    public static void giveBack(EntityPlayer p, ItemStack held) {
        int c = count(held);
        if (c > 0 && c < MAX) setTotal(held, c + 1);
        else if (c == 0 && held.stackSize > 0 && held.stackSize < held.getMaxStackSize()) held.stackSize++;
        else {
            ItemStack one = new ItemStack(held.getItem(), 1, held.getItemDamage());
            if (!p.inventory.addItemStackToInventory(one)) p.dropPlayerItemWithRandomChoice(one, false);
        }
    }

    /** Число на значке собранной стопки (null - обычная). */
    public static String label(ItemStack s) {
        int c = count(s);
        return c > 0 ? String.valueOf(c) : null;
    }
}
