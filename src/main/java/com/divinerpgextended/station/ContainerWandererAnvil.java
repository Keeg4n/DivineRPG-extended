package com.divinerpgextended.station;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.divinerpgextended.accessory.Sockets;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.perk.Ammo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Wanderer Anvil window. Three tabs ({@link AnvilLogic#UPGRADE}, {@link AnvilLogic#SOCKETS},
 * {@link AnvilLogic#UNLOAD}) on the same 9 slots: the tab decides which slots are visible, where they stand and what can be
 * placed in them. When the tab changes, everything that does not fit it goes to the player's inventory (no room - the tab does not change).
 * Under each armor part in the sockets tab are its sockets: not slots (armor has no inventory inside) but pictures; a click on an
 * occupied socket is the "take out" button ({@link #enchantItem}, id {@link #EXTRACT} + part * 2 + socket).
 * <p>
 * The slots are this window's own ({@link AnvilWork}), nothing is stored in the block: when the window closes, everything drops on the ground, like from a crafting grid.
 */
public class ContainerWandererAnvil extends Container {

    public static final int GUI_W = 176, GUI_H = 232;
    public static final int ROW1_Y = 24, ROW2_Y = 48, FUEL_Y = 72, SOCKET_Y = 44, ACC_Y = 86, STATUS_Y = 106, INV_Y = 150, HOTBAR_Y = 208;
    /** The "take out of socket" button: EXTRACT + armor slot * 2 + socket number. */
    public static final int EXTRACT = 100;
    /** Tab button: TAB + tab (the button number goes as a byte - less than 128). */
    public static final int TAB = 110;
    /** The player's last open tab (in entity data, until leaving the game). */
    public static final String LAST_TAB = "dreAnvilTab";
    private static final int HIDDEN = -10000;

    private final TileWandererAnvil tile;
    private final EntityPlayer player;
    private final AnvilWork work = new AnvilWork();

    // synchronized state (the server computes, the client shows)
    public int status = -1, param, bossMask, fuelItem, mode, targets, fuelMeta = -1;
    private int sentMode = -1, sentTargets;

    /** Station slot: what can be placed in it is decided by the current tab. */
    private class StationSlot extends Slot {

        StationSlot(int index) {
            super(work, index, HIDDEN, HIDDEN);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return visible(mode, getSlotIndex()) && valid(mode, getSlotIndex(), stack);
        }

        @Override
        public int getSlotStackLimit() {
            return getSlotIndex() == AnvilLogic.FUEL ? 64 : 1;
        }

        /** An empty armor slot shows the silhouette of its part (like in the player's inventory). */
        @Override
        @SideOnly(Side.CLIENT)
        public net.minecraft.util.IIcon getBackgroundIconIndex() {
            int i = getSlotIndex();
            if (i == AnvilLogic.FUEL || i >= AnvilLogic.DONOR && mode != AnvilLogic.UPGRADE) return null;
            return ItemArmor.func_94602_b(i % 4);
        }
    }

    public ContainerWandererAnvil(InventoryPlayer inv, TileWandererAnvil tile) {
        this.tile = tile;
        this.player = inv.player;
        for (int i = 0; i < AnvilLogic.SIZE; i++) addSlotToContainer(new StationSlot(i));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inv, col, 8 + col * 18, HOTBAR_Y));
        int last = player.getEntityData().getInteger(LAST_TAB); // slots are empty on opening - open the last tab
        mode = last >= 0 && last < AnvilLogic.MODES ? last : AnvilLogic.UPGRADE;
        layout();
    }

    /** The station slot is visible in the tab: upgrade - all, sockets - armor and donors, unload - only fuel (weapon). */
    public static boolean visible(int mode, int slot) {
        return mode == AnvilLogic.UPGRADE || (mode == AnvilLogic.SOCKETS ? slot != AnvilLogic.FUEL : slot == AnvilLogic.FUEL);
    }

    /**
     * What can be placed in a slot in the tab. Armor (and the donor set) - by parts: helmet in the first slot of the row, boots in the
     * last. Upgrade fuel - anything except a loaded weapon; in the unload tab a weapon goes there.
     */
    public static boolean valid(int mode, int slot, ItemStack s) {
        if (s == null) return true;
        if (slot == AnvilLogic.FUEL) return mode == AnvilLogic.UNLOAD ? Ammo.of(s) != null : Ammo.of(s) == null;
        if (slot >= AnvilLogic.DONOR && mode == AnvilLogic.SOCKETS) return Sockets.accessory(s);
        if (mode == AnvilLogic.SOCKETS && com.divinerpgextended.accessory.Goggles.is(s)) return false; // accessories go to the bottom row, on top they would be armor without sockets
        return s.getItem() instanceof ItemArmor && ((ItemArmor) s.getItem()).armorType == slot % 4;
    }

    /** Arrange the station slots for the tab (hidden ones - far off screen). */
    private void layout() {
        for (int i = 0; i < AnvilLogic.SIZE; i++) {
            Slot s = (Slot) inventorySlots.get(i);
            int x = HIDDEN, y = HIDDEN;
            if (visible(mode, i)) {
                if (i == AnvilLogic.FUEL) {
                    x = 8;
                    y = mode == AnvilLogic.UNLOAD ? ROW1_Y : FUEL_Y;
                } else {
                    x = 8 + i % 4 * 18;
                    y = i < AnvilLogic.DONOR ? ROW1_Y : mode == AnvilLogic.SOCKETS ? ACC_Y : ROW2_Y;
                }
            }
            s.xDisplayPosition = x;
            s.yDisplayPosition = y;
        }
    }

    /**
     * Change the tab (server): what does not fit it goes to the player's inventory. Did not fit - stays in the anvil, the tab
     * stays the same. The stack shrinks when moved to the inventory, the remainder is put back into the slot - nothing duplicates.
     */
    public boolean select(EntityPlayer p, int m) {
        if (m < 0 || m >= AnvilLogic.MODES) return false;
        if (m == mode) return true;
        boolean ok = true;
        for (int i = 0; i < AnvilLogic.SIZE; i++) {
            ItemStack s = work.getStackInSlot(i);
            if (s == null || visible(m, i) && valid(m, i, s)) continue;
            // in creative addItemStackToInventory without room zeroes the stack without placing it anywhere - we do not hand it over
            if (p.capabilities.isCreativeMode && p.inventory.getFirstEmptyStack() < 0) {
                ok = false;
                continue;
            }
            p.inventory.addItemStackToInventory(s);
            if (s.stackSize <= 0) work.setInventorySlotContents(i, null);
            else {
                work.setInventorySlotContents(i, s);
                ok = false;
            }
        }
        if (!ok) {
            p.addChatMessage(new ChatComponentTranslation("dre.chat.anvil_tab_full"));
            return false;
        }
        mode = m;
        layout();
        p.getEntityData().setInteger(LAST_TAB, m);
        return true;
    }

    public TileWandererAnvil tile() {
        return tile;
    }

    /** Work slots of this window. */
    public AnvilWork work() {
        return work;
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return tile.isUseableByPlayer(p);
    }

    /** The window closed (including walking far away, the block broken, leaving the server, death) - everything from the slots drops on the ground, like from a crafting grid. */
    @Override
    public void onContainerClosed(EntityPlayer p) {
        super.onContainerClosed(p);
        if (!p.worldObj.isRemote) work.dropAll(p);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        AnvilLogic.Result r = AnvilLogic.evaluate(work, player, mode);
        int s = r.status.ordinal(), p = r.param, m = BossProgress.mask(player), t = r.targets;
        int f = r.fuel == null ? 0 : net.minecraft.item.Item.getIdFromItem(r.fuel), fm = r.fuelMeta;
        for (Object o : crafters) {
            ICrafting c = (ICrafting) o;
            if (s != status) c.sendProgressBarUpdate(this, 0, s);
            if (p != param) c.sendProgressBarUpdate(this, 1, p);
            if (m != bossMask) c.sendProgressBarUpdate(this, 2, m);
            if (f != fuelItem) c.sendProgressBarUpdate(this, 3, f);
            if (mode != sentMode) c.sendProgressBarUpdate(this, 4, mode);
            if (t != sentTargets) c.sendProgressBarUpdate(this, 5, t);
            if (fm != fuelMeta) c.sendProgressBarUpdate(this, 6, fm);
        }
        fuelMeta = fm;
        status = s;
        param = p;
        bossMask = m;
        fuelItem = f;
        targets = sentTargets = t;
        sentMode = mode;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int value) {
        if (id == 0) status = value;
        else if (id == 1) param = value;
        else if (id == 2) bossMask = value;
        else if (id == 3) fuelItem = value;
        else if (id == 4 && value >= 0 && value < AnvilLogic.MODES) {
            mode = value;
            layout();
        } else if (id == 5) targets = value;
        else if (id == 6) fuelMeta = value;
    }

    /**
     * Buttons arrive here via the standard button packet (like the enchanting table): 0 - main (upgrade, embed,
     * unload), {@link #EXTRACT}.. - take an accessory out of a socket, {@link #TAB}.. - tab.
     */
    @Override
    public boolean enchantItem(EntityPlayer p, int id) {
        if (p.worldObj.isRemote) return false;
        double x = tile.xCoord + 0.5, y = tile.yCoord + 0.5, z = tile.zCoord + 0.5;
        if (id >= TAB && id < TAB + AnvilLogic.MODES) {
            boolean ok = select(p, id - TAB);
            detectAndSendChanges();
            return ok;
        }
        if (id >= EXTRACT && id < EXTRACT + 8) {
            if (mode != AnvilLogic.SOCKETS || !AnvilLogic.extract(work, p, (id - EXTRACT) / 2, (id - EXTRACT) % 2)) return false;
            p.worldObj.playSoundEffect(x, y, z, "random.pop", 0.8F, 0.8F);
            detectAndSendChanges();
            return true;
        }
        if (id != 0) return false;
        AnvilLogic.Status before = AnvilLogic.evaluate(work, p, mode).status;
        if (AnvilLogic.execute(work, p, mode)) {
            if (before == AnvilLogic.Status.READY_UNLOAD) {
                ItemStack w = work.getStackInSlot(AnvilLogic.FUEL);
                String snd = w != null ? com.divinerpgextended.perk.AmmoLoad.sound(w) : null;
                p.worldObj.playSoundEffect(x, y, z, snd != null ? snd : "random.click", 0.9F, 0.8F);
            } else {
                p.worldObj.playSoundEffect(x, y, z, "random.anvil_use", 1.0F, before == AnvilLogic.Status.READY_SOCKET ? 1.4F : 1.0F);
                p.addChatMessage(new ChatComponentTranslation(before == AnvilLogic.Status.READY_SOCKET ? "dre.chat.socketed" : "dre.chat.upgraded"));
            }
            detectAndSendChanges();
            return true;
        }
        return false;
    }

    /**
     * Shift-click: into the first visible station slot that accepts the item (armor - into its part's slot, then into the donor
     * row; accessory - into a free accessory slot; fuel and weapon - into the bottom slot); from the station - into the player's inventory.
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < AnvilLogic.SIZE) {
            if (!mergeItemStack(stack, AnvilLogic.SIZE, inventorySlots.size(), true)) return null;
        } else {
            boolean moved = false;
            for (int i = 0; i < AnvilLogic.SIZE && !moved; i++) {
                Slot target = (Slot) inventorySlots.get(i);
                if (!target.isItemValid(stack)) continue; // tab check: mergeItemStack does not do it
                if (i == AnvilLogic.FUEL) moved = mergeItemStack(stack, i, i + 1, false);
                else if (!target.getHasStack()) {
                    target.putStack(stack.splitStack(1));
                    moved = true;
                }
            }
            if (!moved) return null;
        }
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        return copy;
    }
}
