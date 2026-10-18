package com.divinerpgextended.accessory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Окно аксессуаров: 6 слотов сверху (закрытые не принимают вещи), под ними 9 слотов боеприпасов, ниже инвентарь смотрящего. Смотреть может и сам
 * игрок, и админ (/dreinv); в режиме просмотра слоты аксессуаров не трогаются. Окно закрывается, как только объект
 * аксессуаров устарел (смерть или перезаход владельца) - чтобы нельзя было вынуть вещь из старой копии.
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

    /** Слот боеприпасов: только то, что тратит оружие, стаком. */
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

    /** Любое перекладывание в слотах аксессуаров - в журнал аудита: кто, у кого, что было и что стало. */
    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer p) {
        // обмен с хотбаром цифрой: ванильный не проверяет, подходит ли вещь в чужой слот
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
                    com.divinerpgextended.audit.Audit.log(p, "Accessory", (acc.player == p ? "" : "у " + owner + " ") + (i < Accessories.SIZE ? "слот " + (i + 1) : "боеприпасы " + (i - Accessories.SIZE + 1)) + ": " + before[i]
                        + " > " + now);
                }
            }
        }
        return r;
    }

    private static String name(ItemStack s) {
        return s == null ? "пусто" : s.getDisplayName() + (s.stackSize > 1 ? " x" + s.stackSize : "");
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

    /** Shift-клик: из аксессуаров и боеприпасов - в инвентарь, из инвентаря - боеприпасы в их слоты, бижутерия - в первый подходящий слот аксессуара. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        if (acc.readOnly || !acc.valid()) return null;
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < Accessories.TOTAL) {
            if (!mergeItemStack(stack, Accessories.TOTAL, inventorySlots.size(), true)) return null;
        } else if (com.divinerpgextended.perk.Ammo.isAmmo(stack.getItem())) {
            if (!mergeItemStack(stack, Accessories.SIZE, Accessories.TOTAL, false)) return null;
        } else {
            boolean moved = false;
            for (int i = 0; i < Accessories.SIZE && !moved; i++) {
                Slot target = (Slot) inventorySlots.get(i);
                if (!target.getHasStack() && target.isItemValid(stack)) {
                    ItemStack one = stack.splitStack(1);
                    target.putStack(one);
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
