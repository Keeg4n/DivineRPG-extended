package com.divinerpgextended.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

/**
 * Наковальня Странника в мире: место для модели и проверки расстояния. Вещей не хранит - рабочие слоты у каждого окна
 * свои ({@link AnvilWork}). До 0.99.69 вещи лежали в самом блоке: такие вещи отдаются первому, кто откроет наковальню,
 * или выпадают, если её сломать.
 */
public class TileWandererAnvil extends TileEntity {

    /** Вещи, оставшиеся в блоке со старых версий. */
    private final List<ItemStack> legacy = new ArrayList<ItemStack>();

    @Override
    public boolean canUpdate() {
        return false;
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    /** Забрать старые вещи из блока (сначала из блока, потом их кто-то получает). */
    List<ItemStack> takeLegacy() {
        List<ItemStack> out = new ArrayList<ItemStack>(legacy);
        if (!legacy.isEmpty()) {
            legacy.clear();
            markDirty();
        }
        return out;
    }

    /** Отдать старые вещи тому, кто открывает наковальню (сервер). */
    public void releaseLegacy(EntityPlayer p) {
        List<ItemStack> out = takeLegacy();
        for (ItemStack s : out) AnvilWork.give(p, s);
        if (!out.isEmpty()) com.divinerpgextended.audit.Audit.log(p, "Anvil", "выданы вещи, лежавшие в наковальне со старой версии: " + out.size());
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
