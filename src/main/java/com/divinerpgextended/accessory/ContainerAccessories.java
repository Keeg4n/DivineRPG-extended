package com.divinerpgextended.accessory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Window of the old slots (before 0.99.67): 6 accessory slots and 9 ammo slots - take only, putting in is not allowed; below is the viewer's
 * inventory. Both the player and an admin (/dreinv) can view; in view mode the slots are not touched. The window closes
 * as soon as the accessories object is stale (death or rejoin of the owner) - so that an item cannot be taken out of an old copy.
 */
public class ContainerAccessories extends Container {

    public static final int SLOT_X = 34, SLOT_Y = 26, AMMO_X = 8, AMMO_Y = 48;
    final Accessories acc;

    private static class AccessorySlot extends Slot {

        final Accessories acc;

        AccessorySlot(Accessories acc, int i, int x, int y) {
            super(acc, i, x, y);
            this.acc = acc;
        }

        @Override
        public boolean isItemValid(ItemStack s) {
            return acc.canPlace(getSlotIndex(), s);
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            return !acc.readOnly && acc.valid();
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    /** Ammo slot: only what the weapon consumes, as a stack. */
    private static class AmmoSlot extends Slot {

        final Accessories acc;

        AmmoSlot(Accessories acc, int i, int x, int y) {
            super(acc, i, x, y);
            this.acc = acc;
        }

        @Override
        public boolean isItemValid(ItemStack s) {
            return acc.canPlace(getSlotIndex(), s);
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            return !acc.readOnly && acc.valid();
        }
    }

    /** Any moving of items in the accessory slots goes to the audit log: who, whose, what it was and what it became. */
    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer p) {
        // swap with the hotbar by number key: vanilla does not check whether the item fits into someone else's slot
        if (mode == 2 && slot >= 0 && slot < Accessories.TOTAL && button >= 0 && button < 9) {
            ItemStack hot = p.inventory.getStackInSlot(button);
            if (hot != null && !((Slot) inventorySlots.get(slot)).isItemValid(hot)) return null;
        }
        String[] before = new String[Accessories.TOTAL];
        for (int i = 0; i < Accessories.TOTAL; i++) before[i] = name(acc.getStackInSlot(i));
        ItemStack r = super.slotClick(slot, button, mode, p);
        if (!p.worldObj.isRemote) {
            for (int i = 0; i < Accessories.TOTAL; i++) {
                String now = name(acc.getStackInSlot(i));
                if (!now.equals(before[i])) {
                    String owner = acc.player != null ? acc.player.getCommandSenderName() : "?";
                    com.divinerpgextended.audit.Audit.log(p, "Accessory", (acc.player == p ? "" : "for " + owner + " ") + (i < Accessories.SIZE ? "slot " + (i + 1) : "ammo " + (i - Accessories.SIZE + 1)) + ": " + before[i]
                        + " > " + now);
                }
            }
        }
        return r;
    }

    private static String name(ItemStack s) {
        return s == null ? "empty" : s.getDisplayName() + (s.stackSize > 1 ? " x" + s.stackSize : "");
    }

    public ContainerAccessories(EntityPlayer viewer, Accessories acc) {
        this.acc = acc;
        for (int i = 0; i < Accessories.SIZE; i++) addSlotToContainer(new AccessorySlot(acc, i, SLOT_X + i * 18, SLOT_Y));
        for (int i = 0; i < Accessories.AMMO; i++) addSlotToContainer(new AmmoSlot(acc, Accessories.SIZE + i, AMMO_X + i * 18, AMMO_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(viewer.inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(viewer.inventory, col, 8 + col * 18, 142));
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return acc.valid();
    }

    /** Shift-click: from the old slots - to the inventory; nothing is put back. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        if (acc.readOnly || !acc.valid() || index >= Accessories.TOTAL) return null;
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (!mergeItemStack(stack, Accessories.TOTAL, inventorySlots.size(), true)) return null;
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        return copy;
    }
}
