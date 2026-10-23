package com.divinerpgextended.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Форма навесного рельса, как у напольного: два направления, куда он соединён (прямой или поворот), держится, пока
 * рельс стоит; и было ли на нём питание (для стрелки). a < 0 - форма не задана (старые рельсы): соединения по соседям.
 */
public class TileSkyRail extends TileEntity {

    byte a = -1, b = -1;
    boolean powered;

    @Override
    public boolean canUpdate() {
        return false;
    }

    public boolean hasShape() {
        return a >= 0 && b >= 0;
    }

    public ForgeDirection[] shape() {
        return new ForgeDirection[] { ForgeDirection.getOrientation(a), ForgeDirection.getOrientation(b) };
    }

    void set(ForgeDirection x, ForgeDirection y) {
        if (a == x.ordinal() && b == y.ordinal()) return;
        a = (byte) x.ordinal();
        b = (byte) y.ordinal();
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        a = tag.hasKey("A") ? tag.getByte("A") : -1;
        b = tag.hasKey("B") ? tag.getByte("B") : -1;
        powered = tag.getBoolean("Powered");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (hasShape()) {
            tag.setByte("A", a);
            tag.setByte("B", b);
        }
        tag.setBoolean("Powered", powered);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }
}
