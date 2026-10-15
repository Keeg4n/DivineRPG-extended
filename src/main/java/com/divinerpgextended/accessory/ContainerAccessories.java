package com.divinerpgextended.accessory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Окно аксессуаров: 6 слотов сверху (закрытые не принимают вещи), ниже инвентарь смотрящего. Смотреть может и сам
 * игрок, и админ (/dreinv); в режиме просмотра слоты аксессуаров не трогаются. Окно закрывается, как только объект
 * аксессуаров устарел (смерть или перезаход владельца) - чтобы нельзя было вынуть вещь из старой копии.
 */
public class ContainerAccessories extends Container {

    public static final int SLOT_X = 34, SLOT_Y = 26;
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

    public ContainerAccessories(EntityPlayer viewer, Accessories acc) {
        this.acc = acc;
        for (int i = 0; i < Accessories.SIZE; i++) addSlotToContainer(new AccessorySlot(acc, i, SLOT_X + i * 18, SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(viewer.inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(viewer.inventory, col, 8 + col * 18, 142));
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return acc.valid();
    }

    /** Shift-клик: из аксессуаров - в инвентарь, из инвентаря - в первый подходящий слот аксессуара. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        if (acc.readOnly || !acc.valid()) return null;
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < Accessories.SIZE) {
            if (!mergeItemStack(stack, Accessories.SIZE, inventorySlots.size(), true)) return null;
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
