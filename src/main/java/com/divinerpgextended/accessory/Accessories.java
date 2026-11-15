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
 * Player accessories. They work built into the worn armor ({@link Sockets}); here are also the old accessory and ammo slots (before 0.99.67): nothing
 * is put into them anymore and nothing works, items can only be taken out of them -
 * the tab is visible while something lies there. Stored in the player's persistent data.
 * <p>
 * Dupe protection: on the server there is one object per player (by UUID), and it is bound to the player's current entity. After death
 * or rejoin the entity is new - the object is recreated from its data, and the old one becomes invalid, and all
 * windows on it (the player's or an admin's) are closed.
 */
public class Accessories implements IInventory {

    public static final int SIZE = 6, BASE = 2;
    /** Ammo slots come after the accessories: SIZE .. TOTAL - 1. */
    public static final int AMMO = 9, TOTAL = SIZE + AMMO;
    /** Bosses, each of which opens the second socket in its part of the Wanderer armor ({@link Sockets#SECOND}). */
    public static final Boss[] UNLOCKS = { Boss.LICH_KING, Boss.WATCHER, Boss.TWILIGHT_DEMON, Boss.KAROT };
    public static final String TAG = "dre_accessories";
    private static final Map<UUID, Accessories> LIVE = new HashMap<UUID, Accessories>();

    /** Owner (null - a snapshot from an offline player's file, read only). */
    final EntityPlayer player;
    private final ItemStack[] stacks = new ItemStack[TOTAL];
    /** How many slots are open (on the client it comes from the server when the window opens). */
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

    /** Server player object. If the entity changed (death, rejoin) - it is read again from its data. */
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

    /** View only: a snapshot from offline player data. */
    public static Accessories snapshot(NBTTagList list, int unlocked) {
        Accessories a = new Accessories(null, unlocked, true);
        a.load(list);
        return a;
    }

    /** Windows on this object still make sense: the player is the same, alive and online. */
    public boolean valid() {
        if (player == null || player.worldObj.isRemote) return true; // snapshot for viewing or the client copy of the window
        return !player.isDead && LIVE.get(player.getUniqueID()) == this;
    }

    /** Whether such an accessory is built into the worn armor. In Vethea only the Vethean ones work. */
    public static boolean wears(EntityPlayer p, Trinket t) {
        if (p == null || p.worldObj.isRemote) return false;
        if (!t.vethean() && com.divinerpgextended.Config.vetheaNightmare
            && p.dimension == net.divinerpg.utils.config.ConfigurationHelper.vethea && !com.divinerpgextended.vethea.VetheaFloors.allBosses(p)) return false;
        return Sockets.wears(p, t);
    }

    /** Client: something lies in the old slots of its own player (sent by the server, {@link com.divinerpgextended.perk.RangedRoles.QuiverSync}). */
    public static boolean clientLeftovers;

    /** Something is left in the old slots (the tab is visible until it is taken out). */
    public boolean leftovers() {
        for (ItemStack s : stacks) if (s != null) return true;
        return false;
    }

    /** Divine items worn in the accessory slot (rather than working from the inventory): Heiva Belt, Miner's Amulet. */
    public static boolean divineAccessory(ItemStack s) {
        return s != null && (s.getItem() == net.divinerpg.utils.items.VetheaItems.heivaBelt || s.getItem() == net.divinerpg.utils.items.VetheaItems.minersAmulet);
    }

    /** Whether a Divine accessory item is built into the worn armor. */
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

    /** Nothing is put into the old slots - only take what is left. */
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
        if (i >= SIZE && stacks[i].stackSize > n) { // ammo - part of the stack
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
        return 64; // for accessories the slot itself limits to 1
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
