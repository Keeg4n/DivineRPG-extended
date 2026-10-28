package com.divinerpgextended.magic;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Окно арканной стойки: три склянки, ингредиент, топливо (как у ванильной стойки, плюс слот арканиума слева). */
public class ContainerArcaneBrewer extends Container {

    public final TileArcaneBrewer tile;
    private int lastBrew = -1, lastCharges = -1;

    public ContainerArcaneBrewer(InventoryPlayer inv, TileArcaneBrewer t) {
        tile = t;
        addSlotToContainer(new Valid(t, 0, 56, 46));
        addSlotToContainer(new Valid(t, 1, 79, 53));
        addSlotToContainer(new Valid(t, 2, 102, 46));
        addSlotToContainer(new Valid(t, 3, 79, 17));
        addSlotToContainer(new Valid(t, 4, 17, 17));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(inv, c, 8 + c * 18, 142));
    }

    static class Valid extends Slot {
        Valid(TileArcaneBrewer t, int i, int x, int y) {
            super(t, i, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack s) {
            return inventory.isItemValidForSlot(getSlotIndex(), s);
        }

        @Override
        public int getSlotStackLimit() {
            return getSlotIndex() < 3 ? 1 : 64;
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return tile.isUseableByPlayer(p);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        for (Object o : crafters) {
            ICrafting c = (ICrafting) o;
            if (lastBrew != tile.brewTime) c.sendProgressBarUpdate(this, 0, tile.brewTime);
            if (lastCharges != tile.charges) c.sendProgressBarUpdate(this, 1, tile.charges);
        }
        lastBrew = tile.brewTime;
        lastCharges = tile.charges;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int v) {
        if (id == 0) tile.brewTime = v;
        if (id == 1) tile.charges = v;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack s = slot.getStack(), copy = s.copy();
        if (index < 5) {
            if (!mergeItemStack(s, 5, 41, true)) return null;
        } else {
            boolean moved = false;
            for (int i = 0; i < 5 && !moved; i++) {
                Slot t = (Slot) inventorySlots.get(i);
                if (!t.isItemValid(s)) continue;
                if (i < 3) {
                    if (t.getHasStack()) continue;
                    t.putStack(s.splitStack(1));
                    moved = true;
                } else moved = mergeItemStack(s, i, i + 1, false);
            }
            if (!moved) return null;
        }
        if (s.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        return copy;
    }
}
