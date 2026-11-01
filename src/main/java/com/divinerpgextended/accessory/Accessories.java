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
import com.divinerpgextended.trinket.Trinket;

/**
 * Аксессуары игрока. Работают встроенными в надетую броню ({@link Sockets}); здесь же - старые слоты аксессуаров и
 * боеприпасов (до 0.99.67): в них больше ничего не кладётся и ничего не работает, вещи из них только забирают -
 * вкладка видна, пока там что-то лежит. Хранятся в постоянных данных игрока.
 * <p>
 * Защита от дюпа: на сервере один объект на игрока (по UUID), и он привязан к текущей сущности игрока. После смерти
 * или перезахода сущность новая - объект пересоздаётся из её данных, а старый становится недействительным, и все
 * окна на нём (у игрока или у админа) закрываются.
 */
public class Accessories implements IInventory {

    public static final int SIZE = 6, BASE = 2;
    /** Слоты боеприпасов идут после аксессуаров: SIZE .. TOTAL - 1. */
    public static final int AMMO = 9, TOTAL = SIZE + AMMO;
    /** Боссы, каждый из которых открывает второе гнездо в своей части брони Странника ({@link Sockets#SECOND}). */
    public static final Boss[] UNLOCKS = { Boss.LICH_KING, Boss.WATCHER, Boss.TWILIGHT_DEMON, Boss.KAROT };
    public static final String TAG = "dre_accessories";
    private static final Map<UUID, Accessories> LIVE = new HashMap<UUID, Accessories>();

    /** Владелец (null - снимок из файла оффлайн-игрока, только чтение). */
    final EntityPlayer player;
    private final ItemStack[] stacks = new ItemStack[TOTAL];
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

    /** Встроен ли такой аксессуар в надетую броню. В Ветее работают только ветейские. */
    public static boolean wears(EntityPlayer p, Trinket t) {
        if (p == null || p.worldObj.isRemote) return false;
        if (!t.vethean() && com.divinerpgextended.Config.vetheaNightmare
            && p.dimension == net.divinerpg.utils.config.ConfigurationHelper.vethea && !com.divinerpgextended.vethea.VetheaFloors.allBosses(p)) return false;
        return Sockets.wears(p, t);
    }

    /** Клиент: в старых слотах своего игрока что-то лежит (присылает сервер, {@link com.divinerpgextended.perk.RangedRoles.QuiverSync}). */
    public static boolean clientLeftovers;

    /** В старых слотах что-то осталось (вкладка видна, пока не заберут). */
    public boolean leftovers() {
        for (ItemStack s : stacks) if (s != null) return true;
        return false;
    }

    /** Вещи Divine, которые носятся в слоте аксессуаров (а не работают из инвентаря): Пояс хейвы, Шахтёрский амулет. */
    public static boolean divineAccessory(ItemStack s) {
        return s != null && (s.getItem() == net.divinerpg.utils.items.VetheaItems.heivaBelt || s.getItem() == net.divinerpg.utils.items.VetheaItems.minersAmulet);
    }

    /** Встроена ли вещь Divine-аксессуар в надетую броню. */
    public static boolean wearsItem(EntityPlayer p, net.minecraft.item.Item item) {
        if (p == null || p.worldObj.isRemote) return false;
        return Sockets.wearsItem(p, item);
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
            if (slot >= 0 && slot < TOTAL) stacks[slot] = ItemStack.loadItemStackFromNBT(c);
        }
    }

    private void save() {
        if (player == null || readOnly || player.worldObj.isRemote || !valid()) return;
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < TOTAL; i++) {
            if (stacks[i] == null) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setByte("Slot", (byte) i);
            stacks[i].writeToNBT(c);
            list.appendTag(c);
        }
        persisted(player).setTag(TAG, list);
    }

    /** В старые слоты ничего не кладётся - только забрать то, что осталось. */
    public boolean canPlace(int slot, ItemStack s) {
        return false;
    }

    @Override
    public int getSizeInventory() {
        return TOTAL;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return stacks[i];
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        if (readOnly || stacks[i] == null) return null;
        ItemStack s;
        if (i >= SIZE && stacks[i].stackSize > n) { // боеприпасы - часть стака
            s = stacks[i].splitStack(n);
        } else {
            s = stacks[i];
            stacks[i] = null;
        }
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
        return 64; // у аксессуаров слот сам ограничивает до 1
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
