package com.divinerpgextended.accessory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.ItemTrinket;
import com.divinerpgextended.trinket.Trinket;

/**
 * Аксессуары игрока (как в Террарии): свои слоты под бижутерию аддона. Всего 6, открыто 2 и ещё по одному за
 * Короля-лича, Смотрителя, Демона сумерек и Карота. Одинаковые вещи вместе не работают. Хранятся в постоянных данных
 * игрока - переживают смерть.
 * <p>
 * Защита от дюпа: на сервере один объект на игрока (по UUID), и он привязан к текущей сущности игрока. После смерти
 * или перезахода сущность новая - объект пересоздаётся из её данных, а старый становится недействительным, и все
 * окна на нём (у игрока или у админа) закрываются.
 */
public class Accessories implements IInventory {

    public static final int SIZE = 6, BASE = 2;
    /** Боссы, каждый из которых открывает ещё один слот (по порядку). */
    public static final Boss[] UNLOCKS = { Boss.LICH_KING, Boss.WATCHER, Boss.TWILIGHT_DEMON, Boss.KAROT };
    public static final String TAG = "dre_accessories";
    private static final Map<UUID, Accessories> LIVE = new HashMap<UUID, Accessories>();

    /** Владелец (null - снимок из файла оффлайн-игрока, только чтение). */
    final EntityPlayer player;
    private final ItemStack[] stacks = new ItemStack[SIZE];
    /** Сколько слотов открыто (на клиенте приходит с сервера при открытии окна). */
    public int unlocked;
    public final boolean readOnly;

    public Accessories(EntityPlayer player, int unlocked) {
        this(player, unlocked, false);
    }

    public Accessories(EntityPlayer player, int unlocked, boolean readOnly) {
        this.player = player;
        this.unlocked = unlocked;
        this.readOnly = readOnly;
    }

    public static int unlocked(EntityPlayer p) {
        int n = BASE;
        for (Boss b : UNLOCKS) if (BossProgress.has(p, b)) n++;
        return n;
    }

    /** Серверный объект игрока. Если сущность сменилась (смерть, перезаход) - читается заново из её данных. */
    public static Accessories of(EntityPlayer p) {
        Accessories a = LIVE.get(p.getUniqueID());
        if (a == null || a.player != p) {
            a = new Accessories(p, unlocked(p));
            a.load(persisted(p).getTagList(TAG, 10));
            LIVE.put(p.getUniqueID(), a);
        }
        a.unlocked = unlocked(p);
        return a;
    }

    /** Только для просмотра: снимок из данных оффлайн-игрока. */
    public static Accessories snapshot(NBTTagList list, int unlocked) {
        Accessories a = new Accessories(null, unlocked, true);
        a.load(list);
        return a;
    }

    /** Окна на этом объекте ещё имеют смысл: игрок тот же, жив и в сети. */
    public boolean valid() {
        if (player == null || player.worldObj.isRemote) return true; // снимок для просмотра или клиентская копия окна
        return !player.isDead && LIVE.get(player.getUniqueID()) == this;
    }

    /** Надет ли такой аксессуар (только в открытых слотах). */
    public static boolean wears(EntityPlayer p, Trinket t) {
        if (p == null || p.worldObj.isRemote) return false;
        Accessories a = of(p);
        for (int i = 0; i < Math.min(a.unlocked, SIZE); i++) {
            ItemStack s = a.stacks[i];
            if (s != null && s.getItem() == ModItems.trinket && ItemTrinket.of(s) == t) return true;
        }
        return false;
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private void load(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            int slot = c.getByte("Slot");
            if (slot >= 0 && slot < SIZE) stacks[slot] = ItemStack.loadItemStackFromNBT(c);
        }
    }

    private void save() {
        if (player == null || readOnly || player.worldObj.isRemote || !valid()) return;
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SIZE; i++) {
            if (stacks[i] == null) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setByte("Slot", (byte) i);
            stacks[i].writeToNBT(c);
            list.appendTag(c);
        }
        persisted(player).setTag(TAG, list);
    }

    /** Можно ли положить эту вещь в слот: только бижутерия, слот открыт, такой же вещи в других слотах нет. */
    public boolean canPlace(int slot, ItemStack s) {
        if (readOnly || s == null || s.getItem() != ModItems.trinket || slot >= unlocked) return false;
        Trinket t = ItemTrinket.of(s);
        for (int i = 0; i < SIZE; i++) {
            if (i != slot && stacks[i] != null && stacks[i].getItem() == ModItems.trinket && ItemTrinket.of(stacks[i]) == t) return false;
        }
        return true;
    }

    /** Надеть в первый свободный подходящий слот (ПКМ вещью в руке). */
    public boolean equip(ItemStack s) {
        if (!valid()) return false;
        for (int i = 0; i < Math.min(unlocked, SIZE); i++) {
            if (stacks[i] == null && canPlace(i, s)) {
                ItemStack one = s.copy();
                one.stackSize = 1;
                stacks[i] = one;
                markDirty();
                return true;
            }
        }
        return false;
    }

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return stacks[i];
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        if (readOnly || stacks[i] == null) return null;
        ItemStack s = stacks[i];
        stacks[i] = null;
        markDirty();
        return s;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack s) {
        stacks[i] = s;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "dre.accessories";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public void markDirty() {
        save();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return valid();
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int i, ItemStack s) {
        return canPlace(i, s);
    }
}
