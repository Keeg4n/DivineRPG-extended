package com.divinerpgextended.perk;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.divinerpgextended.accessory.Accessories;

/**
 * Слоты боеприпасов под аксессуарами (как в Террарии): стрелы, пыль, пули и гранаты Divine, ванильные стрелы. Оружие
 * берёт патроны сначала из инвентаря, а если там нет - из этих слотов. Само оружие умеет брать только из инвентаря,
 * поэтому перед выстрелом одна штука на миг переносится в инвентарь с меткой {@link #TAG} и тут же тратится; не
 * потратилась (перезарядка, лук отпустили сразу) - на конце тика возвращается в слот ({@link #settle}).
 * <p>
 * Дюп: штука сначала вынимается из слота, потом кладётся в инвентарь (не влезла - обратно в слот); возврат - так же.
 */
public final class Ammo {

    public static final String TAG = "dreAmmo";
    private static Set<Item> all;
    /** Клиент: какие боеприпасы лежат в слотах своего игрока (для натягивания лука без стрел в инвентаре). */
    public static Set<Integer> client = new HashSet<Integer>();

    private Ammo() {}

    /** Какие боеприпасы тратит это оружие (null - никакие или неизвестно). */
    public static Item of(ItemStack held) {
        if (held == null) return null;
        Item i = held.getItem();
        int k = RangedRoles.kind(held);
        if (k == RangedRoles.BLITZ || k == RangedRoles.PHASER) return null; // бесконечные
        if (i instanceof net.divinerpg.items.base.ItemModBow) return field(net.divinerpg.items.base.ItemModBow.class, i, "arrow");
        if (i instanceof ItemBow) return Items.arrow;
        if (i instanceof net.divinerpg.items.base.ItemProjectileShooter) return field(net.divinerpg.items.base.ItemProjectileShooter.class, i, "ammo");
        String cn = i.getClass().getName();
        if (cn.endsWith(".ItemLaVekor")) return net.divinerpg.utils.items.ArcanaItems.grenade;
        if (cn.endsWith(".ItemCorruptedCannon")) return net.divinerpg.utils.items.VanillaItemsOther.corruptedBullet;
        return null;
    }

    /** Что тратит стрелок Divine по своей задумке (у блица - пыль), без наших поправок. */
    public static Item divineAmmo(ItemStack held) {
        if (held == null || !(held.getItem() instanceof net.divinerpg.items.base.ItemProjectileShooter)) return null;
        return field(net.divinerpg.items.base.ItemProjectileShooter.class, held.getItem(), "ammo");
    }

    private static Item field(Class<?> c, Object o, String name) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(o);
            return v instanceof Item ? (Item) v : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Можно ли положить в слот боеприпасов: то, что тратит хоть одно оружие. */
    public static boolean isAmmo(Item a) {
        if (all == null) {
            Set<Item> s = new HashSet<Item>();
            s.add(Items.arrow);
            for (Object o : Item.itemRegistry) {
                if (!(o instanceof Item)) continue;
                try {
                    Item need = of(new ItemStack((Item) o));
                    if (need != null) s.add(need);
                } catch (Throwable t) {
                    // чужой предмет, который не любит пустой стак - пропускаем
                }
            }
            all = s;
        }
        return a != null && all.contains(a);
    }

    /** Есть ли такие боеприпасы в слотах (на клиенте - по последней сводке с сервера). */
    public static boolean has(EntityPlayer p, Item a) {
        if (a == null) return false;
        if (p.worldObj.isRemote) return client.contains(Item.getIdFromItem(a));
        Accessories acc = Accessories.of(p);
        for (int i = Accessories.SIZE; i < Accessories.TOTAL; i++) {
            ItemStack s = acc.getStackInSlot(i);
            if (s != null && s.getItem() == a && s.stackSize > 0) return true;
        }
        return false;
    }

    /** Сводка для клиента: id боеприпасов в слотах. */
    public static int[] ids(EntityPlayer p) {
        Accessories acc = Accessories.of(p);
        java.util.TreeSet<Integer> ids = new java.util.TreeSet<Integer>();
        for (int i = Accessories.SIZE; i < Accessories.TOTAL; i++) {
            ItemStack s = acc.getStackInSlot(i);
            if (s != null && s.stackSize > 0) ids.add(Item.getIdFromItem(s.getItem()));
        }
        int[] r = new int[ids.size()];
        int k = 0;
        for (int id : ids) r[k++] = id;
        return r;
    }

    /** Вынуть одну штуку из слотов (null - нет). */
    private static ItemStack takeFromSlots(EntityPlayer p, Item a) {
        Accessories acc = Accessories.of(p);
        for (int i = Accessories.SIZE; i < Accessories.TOTAL; i++) {
            ItemStack s = acc.getStackInSlot(i);
            if (s == null || s.getItem() != a || s.stackSize <= 0) continue;
            ItemStack one = s.copy();
            one.stackSize = 1;
            s.stackSize--;
            acc.setInventorySlotContents(i, s.stackSize > 0 ? s : null);
            return one;
        }
        return null;
    }

    /** Перед выстрелом: одна штука из слотов - в инвентарь с меткой (оружие её тут же потратит). */
    public static boolean lend(EntityPlayer p, Item a) {
        if (a == null || p.worldObj.isRemote) return false;
        ItemStack one = takeFromSlots(p, a);
        if (one == null) return false;
        NBTTagCompound t = one.hasTagCompound() ? (NBTTagCompound) one.getTagCompound().copy() : new NBTTagCompound();
        t.setBoolean(TAG, true);
        one.setTagCompound(t);
        if (give(p, one)) return true;
        putBack(p, strip(one)); // положить некуда - обратно в слот
        return false;
    }

    static final String STASH = "dreAmmoStash";

    /**
     * Положить патрон на миг выстрела. Рюкзак полон - стопка боеприпасов из рюкзака (стрелы, пыль...) переезжает в слоты
     * боеприпасов (там ей и место, обратно не возвращается), освободившуюся ячейку занимает патрон. Таких стопок нет или
     * слоты боеприпасов заняты - false: прочие вещи не трогаем вовсе.
     */
    public static boolean give(EntityPlayer p, ItemStack one) {
        if (p.inventory.addItemStackToInventory(one) && one.stackSize <= 0) return true;
        if (one.stackSize <= 0) return true;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = inv.length - 1; i >= 0; i--) {
            ItemStack s = inv[i];
            if (i == p.inventory.currentItem || s == null || marked(s) || !isAmmo(s.getItem()) || room(p, s) < s.stackSize) continue;
            putBack(p, s); // влезает целиком - проверено
            inv[i] = one;
            return true;
        }
        return false;
    }

    /** Сколько таких штук ещё влезет в слоты боеприпасов. */
    static int room(EntityPlayer p, ItemStack s) {
        Accessories acc = Accessories.of(p);
        int n = 0;
        for (int i = Accessories.SIZE; i < Accessories.TOTAL; i++) {
            ItemStack in = acc.getStackInSlot(i);
            if (in == null) n += s.getMaxStackSize();
            else if (in.isItemEqual(s) && ItemStack.areItemStackTagsEqual(in, s)) n += Math.max(0, in.getMaxStackSize() - in.stackSize);
        }
        return n;
    }

    static boolean marked(ItemStack s) {
        return s.hasTagCompound() && (s.getTagCompound().getBoolean(TAG) || s.getTagCompound().getBoolean(RangedRoles.QUIVER_TAG));
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

    private static ItemStack strip(ItemStack s) {
        if (s.hasTagCompound()) {
            s.getTagCompound().removeTag(TAG);
            if (s.getTagCompound().hasNoTags()) s.setTagCompound(null);
        }
        return s;
    }

    /** Положить в слоты боеприпасов (сначала к таким же, потом в пустые); что не влезло - вернуть. */
    private static ItemStack putBack(EntityPlayer p, ItemStack s) {
        Accessories acc = Accessories.of(p);
        for (int pass = 0; pass < 2 && s != null && s.stackSize > 0; pass++) {
            for (int i = Accessories.SIZE; i < Accessories.TOTAL && s.stackSize > 0; i++) {
                ItemStack in = acc.getStackInSlot(i);
                if (pass == 0 && in != null && in.isItemEqual(s) && ItemStack.areItemStackTagsEqual(in, s)) {
                    int n = Math.min(s.stackSize, in.getMaxStackSize() - in.stackSize);
                    if (n <= 0) continue;
                    in.stackSize += n;
                    s.stackSize -= n;
                    acc.setInventorySlotContents(i, in);
                } else if (pass == 1 && in == null) {
                    acc.setInventorySlotContents(i, s.copy());
                    s.stackSize = 0;
                }
            }
        }
        return s.stackSize > 0 ? s : null;
    }

    /** Конец тика: не потраченные одолженные штуки - обратно в слоты (не влезли - остаются в инвентаре без метки). */
    public static void settle(EntityPlayer p) {
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            ItemStack s = inv[i];
            if (s == null || !s.hasTagCompound() || !s.getTagCompound().getBoolean(TAG)) continue;
            inv[i] = putBack(p, strip(s));
        }
        unstash(p);
    }
}
