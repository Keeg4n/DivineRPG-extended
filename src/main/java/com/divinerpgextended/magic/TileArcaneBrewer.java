package com.divinerpgextended.magic;

import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityBrewingStand;

/**
 * Арканная стойка зельеварения. Слоты: 0-2 склянки, 3 ингредиент, 4 топливо (арканиум: 4 варки).
 * <ul>
 * <li>склянка воды + ингредиент эффекта ({@link ArcaneEffect#ingredient}) = арканное зелье;</li>
 * <li>арканное зелье + светопыль = сильное, + редстоун = долгое, + порох = общее.</li>
 * </ul>
 * Варка {@link #BREW_TICKS} тиков. Наследует ванильную стойку, чтобы блок ванильной стойки (вид, разлом с выпадением
 * содержимого) работал с ней как со своей; вся логика - своя.
 */
public class TileArcaneBrewer extends TileEntityBrewingStand {

    public static final int BREW_TICKS = 400, CHARGES_PER_ARCANIUM = 4;
    private final ItemStack[] slots = new ItemStack[5];
    public int brewTime, charges;

    /** Что получится из этой склянки с этим ингредиентом (null - ничего). */
    public static ItemStack result(ItemStack bottle, ItemStack ing) {
        if (bottle == null || ing == null) return null;
        if (bottle.getItem() == Items.potionitem && bottle.getItemDamage() == 0) {
            ArcaneEffect e = ArcaneEffect.byIngredient(ing.getItem());
            return e == null ? null : ItemArcanePotion.of(e, false, false, false);
        }
        if (bottle.getItem() != com.divinerpgextended.registry.ModItems.arcanePotion) return null;
        int m = bottle.getItemDamage();
        ArcaneEffect e = ArcaneEffect.of(m);
        int add;
        if (ing.getItem() == Items.glowstone_dust && e.leveled()) add = ItemArcanePotion.STRONG;
        else if (ing.getItem() == Items.redstone && !e.instant()) add = ItemArcanePotion.LONG;
        else if (ing.getItem() == Items.gunpowder) add = ItemArcanePotion.SHARED;
        else return null;
        if ((m & add) != 0) return null;
        return new ItemStack(bottle.getItem(), 1, m | add);
    }

    boolean canBrew() {
        if (slots[3] == null) return false;
        for (int i = 0; i < 3; i++) if (result(slots[i], slots[3]) != null) return true;
        return false;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        boolean can = canBrew();
        if (brewTime > 0) {
            if (!can) brewTime = 0;
            else if (--brewTime == 0) brew();
        } else if (can) {
            if (charges <= 0 && slots[4] != null && slots[4].getItem() == ArcanaItems.arcanium) {
                charges = CHARGES_PER_ARCANIUM;
                decrStackSize(4, 1);
            }
            if (charges > 0) brewTime = BREW_TICKS;
        }
        int meta = 0;
        for (int i = 0; i < 3; i++) if (slots[i] != null) meta |= 1 << i;
        if (worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != meta) worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 2);
    }

    void brew() {
        for (int i = 0; i < 3; i++) {
            ItemStack r = result(slots[i], slots[3]);
            if (r != null) slots[i] = r;
        }
        decrStackSize(3, 1);
        charges--;
        worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.levelup", 0.4F, 1.8F);
        markDirty();
    }

    @Override
    public int getSizeInventory() {
        return slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return i >= 0 && i < slots.length ? slots[i] : null;
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        if (i < 0 || i >= slots.length || slots[i] == null) return null;
        ItemStack s;
        if (slots[i].stackSize <= n) {
            s = slots[i];
            slots[i] = null;
        } else {
            s = slots[i].splitStack(n);
            if (slots[i].stackSize == 0) slots[i] = null;
        }
        markDirty();
        return s;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        ItemStack s = getStackInSlot(i);
        if (s != null) slots[i] = null;
        return s;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack s) {
        if (i < 0 || i >= slots.length) return;
        slots[i] = s;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "tile.dre.arcaneBrewer.name";
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
    public boolean isItemValidForSlot(int i, ItemStack s) {
        if (s == null) return false;
        if (i == 4) return s.getItem() == ArcanaItems.arcanium;
        if (i == 3) return ArcaneEffect.byIngredient(s.getItem()) != null || s.getItem() == Items.glowstone_dust || s.getItem() == Items.redstone
            || s.getItem() == Items.gunpowder;
        return s.getItem() == Items.potionitem || s.getItem() == com.divinerpgextended.registry.ModItems.arcanePotion || s.getItem() == Items.glass_bottle;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return side == 1 ? new int[] { 3 } : side == 0 ? new int[] { 0, 1, 2 } : new int[] { 0, 1, 2, 4 };
    }

    @Override
    public boolean canInsertItem(int i, ItemStack s, int side) {
        return isItemValidForSlot(i, s);
    }

    @Override
    public boolean canExtractItem(int i, ItemStack s, int side) {
        return i < 3;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this && p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public int getBrewTime() {
        return brewTime;
    }

    /** Склянки в стойке (для вида блока и компаратора): биты 0-2. */
    @Override
    public int getFilledSlots() {
        int m = 0;
        for (int i = 0; i < 3; i++) if (slots[i] != null) m |= 1 << i;
        return m;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t); // координаты; свои слоты ванильная стойка прочтёт в свой (пустой, ненужный) массив
        NBTTagList l = t.getTagList("Items", 10);
        for (int k = 0; k < l.tagCount(); k++) {
            NBTTagCompound c = l.getCompoundTagAt(k);
            int i = c.getByte("Slot");
            if (i >= 0 && i < slots.length) slots[i] = ItemStack.loadItemStackFromNBT(c);
        }
        brewTime = t.getShort("BrewTime");
        charges = t.getShort("Charges");
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t); // id и координаты (свой список слотов перезапишем ниже)
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setByte("Slot", (byte) i);
            slots[i].writeToNBT(c);
            l.appendTag(c);
        }
        t.setTag("Items", l);
        t.setShort("BrewTime", (short) brewTime);
        t.setShort("Charges", (short) charges);
    }
}
