package com.divinerpgextended.station;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int ANVIL = 0, ACCESSORIES = 1, BREWER = 2;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (id == ANVIL && te instanceof TileWandererAnvil) {
            return new ContainerWandererAnvil(player.inventory, (TileWandererAnvil) te);
        }
        if (id == ACCESSORIES) {
            // админ (/dreinv) смотрит чужие - иначе свои
            com.divinerpgextended.accessory.Accessories target = com.divinerpgextended.accessory.CommandDreInv.takePending(player);
            return new com.divinerpgextended.accessory.ContainerAccessories(player,
                target != null ? target : com.divinerpgextended.accessory.Accessories.of(player));
        }
        if (id == BREWER && te instanceof com.divinerpgextended.magic.TileArcaneBrewer) {
            return new com.divinerpgextended.magic.ContainerArcaneBrewer(player.inventory, (com.divinerpgextended.magic.TileArcaneBrewer) te);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (id == ANVIL && te instanceof TileWandererAnvil) {
            return new GuiWandererAnvil(player.inventory, (TileWandererAnvil) te);
        }
        if (id == ACCESSORIES) return new com.divinerpgextended.client.GuiAccessories(player, x, y == 1);
        if (id == BREWER && te instanceof com.divinerpgextended.magic.TileArcaneBrewer) {
            return new com.divinerpgextended.client.GuiArcaneBrewer(player.inventory, (com.divinerpgextended.magic.TileArcaneBrewer) te);
        }
        return null;
    }
}
