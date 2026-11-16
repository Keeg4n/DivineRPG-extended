package com.divinerpgextended.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

/**
 * Wanderer Anvil in the world: a place for the model and distance checks. Stores no items - work slots belong to each window
 * ({@link AnvilWork}). Before 0.99.69 items lay in the block itself: such items are handed to whoever first opens the anvil,
 * or drop if it is broken.
 */
public class TileWandererAnvil extends TileEntity {

    /** Items left in the block from old versions. */
    private final List<ItemStack> legacy = new ArrayList<ItemStack>();

    @Override
    public boolean canUpdate() {
        return false;
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return player.worldObj == worldObj && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this // the same world: portals keep x and z
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    /** Take the old items out of the block (first from the block, then someone receives them). */
    List<ItemStack> takeLegacy() {
        List<ItemStack> out = new ArrayList<ItemStack>(legacy);
        if (!legacy.isEmpty()) {
            legacy.clear();
            markDirty();
        }
        return out;
    }

    /** Hand the old items to whoever opens the anvil (server). */
    public void releaseLegacy(EntityPlayer p) {
        List<ItemStack> out = takeLegacy();
        for (ItemStack s : out) AnvilWork.give(p, s);
        if (!out.isEmpty()) com.divinerpgextended.audit.Audit.log(p, "Anvil", "returned items that were left in the anvil from an old version: " + out.size());
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (legacy.isEmpty()) return;
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < legacy.size(); i++) {
            NBTTagCompound t = new NBTTagCompound();
            t.setByte("Slot", (byte) i);
            legacy.get(i).writeToNBT(t);
            list.appendTag(t);
        }
        tag.setTag("Items", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        legacy.clear();
        NBTTagList list = tag.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (s != null) legacy.add(s);
        }
    }
}
