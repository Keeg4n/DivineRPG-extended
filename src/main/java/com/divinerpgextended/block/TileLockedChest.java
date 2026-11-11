package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Запертый сундук: 27 ячеек, заперт ли, сторона (2-5 как у ванильного сундука), крышка открывается как у ванильного. */
public class TileLockedChest extends TileEntity implements IInventory {

    private final ItemStack[] items = new ItemStack[27];
    public boolean locked = true;
    public int facing = 3;
    public float lidAngle, prevLidAngle;
    public int numPlayersUsing;

    public BlockLockedChest.Kind kind() {
        return BlockLockedChest.Kind.of(getBlockMetadata());
    }

    /** Отпереть: добыча своего вида раскладывается по случайным ячейкам. */
    public void unlock(Random r) {
        if (!locked) return;
        locked = false;
        java.util.List<ItemStack> loot = com.divinerpgextended.world.ArcanaChests.loot(kind(), r);
        for (ItemStack s : loot) {
            for (int tries = 0; tries < 40; tries++) {
                int slot = r.nextInt(items.length);
                if (items[slot] == null) {
                    items[slot] = s;
                    break;
                }
            }
        }
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void updateEntity() {
        prevLidAngle = lidAngle;
        if (numPlayersUsing > 0 && lidAngle == 0.0F)
            worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.chestopen", 0.5F, worldObj.rand.nextFloat() * 0.1F + 0.9F);
        if (numPlayersUsing == 0 && lidAngle > 0.0F || numPlayersUsing > 0 && lidAngle < 1.0F) {
            float was = lidAngle;
            lidAngle += numPlayersUsing > 0 ? 0.1F : -0.1F;
            if (lidAngle > 1.0F) lidAngle = 1.0F;
            if (lidAngle < 0.5F && was >= 0.5F)
                worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.chestclosed", 0.5F, worldObj.rand.nextFloat() * 0.1F + 0.9F);
            if (lidAngle < 0.0F) lidAngle = 0.0F;
        }
    }

    @Override
    public boolean receiveClientEvent(int id, int data) {
        if (id == 1) {
            numPlayersUsing = data;
            return true;
        }
        return super.receiveClientEvent(id, data);
    }

    @Override
    public void openInventory() {
        if (numPlayersUsing < 0) numPlayersUsing = 0;
        numPlayersUsing++;
        worldObj.addBlockEvent(xCoord, yCoord, zCoord, getBlockType(), 1, numPlayersUsing);
    }

    @Override
    public void closeInventory() {
        numPlayersUsing--;
        worldObj.addBlockEvent(xCoord, yCoord, zCoord, getBlockType(), 1, numPlayersUsing);
    }

    @Override
    public int getSizeInventory() {
        return items.length;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return items[i];
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        if (items[i] == null) return null;
        ItemStack out;
        if (items[i].stackSize <= n) {
            out = items[i];
            items[i] = null;
        } else {
            out = items[i].splitStack(n);
            if (items[i].stackSize == 0) items[i] = null;
        }
        markDirty();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        ItemStack s = items[i];
        items[i] = null;
        return s;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack s) {
        items[i] = s;
        if (s != null && s.stackSize > getInventoryStackLimit()) s.stackSize = getInventoryStackLimit();
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "tile.dre.lockedChest." + kind().key + ".name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this && !locked && p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack s) {
        return !locked;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        locked = !t.hasKey("Locked") || t.getBoolean("Locked");
        facing = t.hasKey("Facing") ? t.getByte("Facing") : 3;
        NBTTagList l = t.getTagList("Items", 10);
        for (int i = 0; i < items.length; i++) items[i] = null;
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            int slot = c.getByte("Slot") & 255;
            if (slot < items.length) items[slot] = ItemStack.loadItemStackFromNBT(c);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setBoolean("Locked", locked);
        t.setByte("Facing", (byte) facing);
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setByte("Slot", (byte) i);
            items[i].writeToNBT(c);
            l.appendTag(c);
        }
        t.setTag("Items", l);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("Locked", locked);
        t.setByte("Facing", (byte) facing);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        locked = t.getBoolean("Locked");
        facing = t.getByte("Facing");
    }
}
