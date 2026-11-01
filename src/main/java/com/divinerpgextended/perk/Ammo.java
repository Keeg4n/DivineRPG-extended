package com.divinerpgextended.perk;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Заряд внутри оружия: у каждого стрелка один вид боеприпасов ({@link #of}), поэтому внутри хранится не инвентарь, а одно
 * число в NBT ({@link #LOADED}). Нет окна и вещей внутри - нечего дюпать.
 * <ul>
 * <li>зарядить - присесть и ПКМ оружием ({@link #load}): всё подходящее из инвентаря, до {@link #STACKS} стопок;</li>
 * <li>выстрел берёт сначала заряд, потом инвентарь: оружие само спрашивает инвентарь, а
 * {@link com.divinerpgextended.asm.AmmoTransformer} подменяет этот вопрос на {@link com.divinerpgextended.asm.AmmoHooks};</li>
 * <li>разрядить - на Наковальне Странника ({@link #unload}); сломалось оружие - заряд высыпается игроку ({@link #spill}).</li>
 * </ul>
 * Каждый перенос - сначала минус в источнике, потом плюс в цели, в одном вызове на сервере.
 */
public final class Ammo {

    public static final String LOADED = "dreLoaded";
    /** Вместимость: столько стопок боеприпасов своего вида. */
    public static final int STACKS = 4;
    /** Метка патрона, одолженного на выстрел до 0.99.67 (остался после сбоя - снимаем). */
    public static final String TAG = "dreAmmo";
    static final String STASH = "dreAmmoStash";

    private static Field bowArrow, shooterAmmo;

    private Ammo() {}

    /** Какие боеприпасы тратит это оружие (null - никакие или неизвестно). */
    public static Item of(ItemStack held) {
        if (held == null) return null;
        Item i = held.getItem();
        Item dust = RangedRoles.dust(i);
        if (dust != null) return dust; // фазер и блиц - пыль своего мира
        if (i instanceof net.divinerpg.items.base.ItemModBow) return value(bowArrow(), i);
        if (i instanceof ItemBow) return Items.arrow;
        if (i instanceof net.divinerpg.items.base.ItemProjectileShooter) return value(shooterAmmo(), i);
        String cn = i.getClass().getName();
        if (cn.endsWith(".ItemLaVekor")) return net.divinerpg.utils.items.ArcanaItems.grenade;
        if (cn.endsWith(".ItemCorruptedCannon")) return net.divinerpg.utils.items.VanillaItemsOther.corruptedBullet;
        return null;
    }

    /** Что тратит стрелок Divine по своей задумке (у блица - пыль), без наших поправок. */
    public static Item divineAmmo(ItemStack held) {
        if (held == null || !(held.getItem() instanceof net.divinerpg.items.base.ItemProjectileShooter)) return null;
        return value(shooterAmmo(), held.getItem());
    }

    private static Field bowArrow() {
        if (bowArrow == null) bowArrow = field(net.divinerpg.items.base.ItemModBow.class, "arrow");
        return bowArrow;
    }

    private static Field shooterAmmo() {
        if (shooterAmmo == null) shooterAmmo = field(net.divinerpg.items.base.ItemProjectileShooter.class, "ammo");
        return shooterAmmo;
    }

    private static Field field(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Item value(Field f, Object o) {
        try {
            Object v = f == null ? null : f.get(o);
            return v instanceof Item ? (Item) v : null;
        } catch (Exception ex) {
            return null;
        }
    }

    public static int loaded(ItemStack w) {
        return w != null && w.hasTagCompound() ? Math.max(0, w.getTagCompound().getInteger(LOADED)) : 0;
    }

    /** Записать заряд (сервер: зарядка, выстрел, разрядка; самопроверка ставит напрямую). */
    public static void setLoaded(ItemStack w, int n) {
        if (n > 0) {
            if (!w.hasTagCompound()) w.setTagCompound(new NBTTagCompound());
            w.getTagCompound().setInteger(LOADED, n);
        } else if (w.hasTagCompound()) {
            w.getTagCompound().removeTag(LOADED);
            if (w.getTagCompound().hasNoTags()) w.setTagCompound(null);
        }
    }

    /** Сколько влезает в оружие: {@link #STACKS} стопок его боеприпасов. */
    public static int capacity(ItemStack w) {
        Item a = of(w);
        return a == null ? 0 : STACKS * new ItemStack(a).getMaxStackSize();
    }

    /** Заряжено ли оружие этими боеприпасами. */
    public static boolean has(ItemStack w, Item a) {
        return a != null && loaded(w) > 0 && of(w) == a;
    }

    /** Выстрел: один заряд из оружия под этот патрон. @return true - был */
    public static boolean take(ItemStack w, Item a) {
        if (!has(w, a)) return false;
        setLoaded(w, loaded(w) - 1);
        return true;
    }

    /** Годится в заряд: тот же предмет без своих данных и подвида (иначе пропали бы при превращении в число). */
    static boolean plain(ItemStack s, Item a) {
        return s != null && s.getItem() == a && s.stackSize > 0 && s.getItemDamage() == 0 && !s.hasTagCompound();
    }

    /** Сколько патронов из инвентаря можно зарядить в оружие в руке (0 - нечего или некуда). */
    public static int loadable(EntityPlayer p) {
        ItemStack w = p.getHeldItem();
        Item a = of(w);
        if (a == null) return 0;
        int room = capacity(w) - loaded(w), n = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length && n < room; i++) if (i != p.inventory.currentItem && plain(inv[i], a)) n += inv[i].stackSize;
        return Math.min(n, Math.max(0, room));
    }

    /** Зарядить оружие в руке из инвентаря - всё подходящее, пока влезает. Только сервер. @return сколько вошло */
    public static int load(EntityPlayer p) {
        if (p.worldObj.isRemote) return 0;
        ItemStack w = p.getHeldItem();
        Item a = of(w);
        if (a == null) return 0;
        int got = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            int room = capacity(w) - loaded(w);
            if (room <= 0) break;
            if (i == p.inventory.currentItem || !plain(inv[i], a)) continue;
            int n = Math.min(room, inv[i].stackSize);
            inv[i].stackSize -= n; // сначала минус в инвентаре
            if (inv[i].stackSize <= 0) inv[i] = null;
            setLoaded(w, loaded(w) + n); // потом плюс в оружии
            got += n;
        }
        if (got > 0) p.inventory.markDirty();
        return got;
    }

    /** Сколько таких патронов ещё влезет в инвентарь игрока. */
    public static int room(EntityPlayer p, Item a) {
        int max = new ItemStack(a).getMaxStackSize(), n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s == null) n += max;
            else if (plain(s, a)) n += Math.max(0, max - s.stackSize);
        }
        return n;
    }

    /** Положить в инвентарь ровно count (место проверено {@link #room}): сначала к таким же, потом в пустые. */
    private static void put(EntityPlayer p, Item a, int count) {
        int max = new ItemStack(a).getMaxStackSize();
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length && count > 0; i++) {
            if (!plain(inv[i], a)) continue;
            int n = Math.min(count, max - inv[i].stackSize);
            if (n <= 0) continue;
            inv[i].stackSize += n;
            count -= n;
        }
        for (int i = 0; i < inv.length && count > 0; i++) {
            if (inv[i] != null) continue;
            int n = Math.min(count, max);
            inv[i] = new ItemStack(a, n);
            count -= n;
        }
        p.inventory.markDirty();
    }

    /** Разрядить в инвентарь игрока, сколько влезет; остальное остаётся в оружии. @return сколько вышло */
    public static int unload(EntityPlayer p, ItemStack w) {
        Item a = of(w);
        if (a == null || p.worldObj.isRemote) return 0;
        int n = Math.min(loaded(w), room(p, a));
        if (n <= 0) return 0;
        setLoaded(w, loaded(w) - n); // сначала минус в оружии
        put(p, a, n); // потом плюс в инвентаре
        return n;
    }

    /** Оружие сломалось: заряд - игроку в инвентарь, что не влезло - под ноги. */
    public static void spill(EntityPlayer p, ItemStack w) {
        Item a = of(w);
        int n = loaded(w);
        if (a == null || n <= 0) return;
        setLoaded(w, 0);
        int in = Math.min(n, room(p, a));
        if (in > 0) put(p, a, in);
        int max = new ItemStack(a).getMaxStackSize();
        for (int rest = n - in; rest > 0; rest -= max) p.dropPlayerItemWithRandomChoice(new ItemStack(a, Math.min(rest, max)), false);
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** Вернуть вещь, отложенную на выстрел версией 0.86 (если такая осталась в данных игрока): в её ячейку, в инвентарь или под ноги. */
    public static void unstash(EntityPlayer p) {
        NBTTagCompound pd = persisted(p);
        if (!pd.hasKey(STASH)) return;
        NBTTagCompound st = pd.getCompoundTag(STASH);
        pd.removeTag(STASH);
        ItemStack orig = ItemStack.loadItemStackFromNBT(st);
        if (orig == null) return;
        int i = st.getInteger("slot");
        ItemStack[] inv = p.inventory.mainInventory;
        if (i >= 0 && i < inv.length && inv[i] == null) inv[i] = orig;
        else if (!p.inventory.addItemStackToInventory(orig) || orig.stackSize > 0) p.entityDropItem(orig, 0.5F);
    }

    /** Конец тика: патрону, одолженному на выстрел до 0.99.67 и оставшемуся после сбоя, - снять метку; старый тайник 0.86 - вернуть. */
    public static void settle(EntityPlayer p) {
        for (ItemStack s : p.inventory.mainInventory) {
            if (s == null || !s.hasTagCompound() || !s.getTagCompound().getBoolean(TAG)) continue;
            s.getTagCompound().removeTag(TAG);
            if (s.getTagCompound().hasNoTags()) s.setTagCompound(null);
        }
        unstash(p);
    }
}
