package com.divinerpgextended.station;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Work slots of the Wanderer Anvil: each open window has its own, nothing is stored in the block or the world (like a crafting
 * grid). When the anvil window closes, everything drops on the ground in front of the player, like from a crafting grid; the corruption altar
 * returns items to the inventory, what did not fit - onto the ground at the feet. The window also closes on leaving the server and on death,
 * so items do not stay in a forgotten window.
 */
public class AnvilWork extends InventoryBasic {

    public AnvilWork() {
        this("tile.dre.wandererAnvil.name", AnvilLogic.SIZE);
    }

    /** The same slots of another window (corruption altar). */
    public AnvilWork(String name, int size) {
        super(name, false, size);
    }

    /** Return everything to the player (server only). The item first leaves the slot, then is handed out - a second call gives nothing. */
    public void giveBack(EntityPlayer p) {
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack s = getStackInSlotOnClosing(i);
            if (s != null) give(p, s);
        }
    }

    /** Drop everything in front of the player, like a crafting grid (server only). The item first leaves the slot, then drops - a second call gives nothing. */
    public void dropAll(EntityPlayer p) {
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack s = getStackInSlotOnClosing(i);
            if (s != null) p.dropPlayerItemWithRandomChoice(s, false);
        }
    }

    /** Into the inventory, the rest onto the ground at the feet. In creative without room addItemStackToInventory zeroes the stack - we do not put it there. */
    static void give(EntityPlayer p, ItemStack s) {
        if (!(p.capabilities.isCreativeMode && p.inventory.getFirstEmptyStack() < 0)) p.inventory.addItemStackToInventory(s);
        if (s.stackSize > 0) p.dropPlayerItemWithRandomChoice(s, false);
    }

    /** Leaving the server and death with an open anvil or corruption altar - close the window while the player's items are still in place. */
    public static class Events {

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            close(e.player);
        }

        @SubscribeEvent
        public void onDeath(LivingDeathEvent e) {
            if (e.entityLiving instanceof EntityPlayer) close((EntityPlayer) e.entityLiving);
        }

        private static void close(EntityPlayer p) {
            if (!p.worldObj.isRemote && (p.openContainer instanceof ContainerWandererAnvil || p.openContainer instanceof ContainerCorruptAltar)) p.closeScreen();
        }

        /**
         * The server creates a new player entity without closing the old window (leaving the End, some cores on a teleport between worlds):
         * the items of the old window are handed to the new player, otherwise they disappeared with the old entity. The slots are emptied first -
         * a late close of the old window gives nothing.
         */
        @SubscribeEvent
        public void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone e) {
            EntityPlayer old = e.original, now = e.entityPlayer;
            if (now.worldObj.isRemote || old == null) return;
            AnvilWork w = old.openContainer instanceof ContainerWandererAnvil ? ((ContainerWandererAnvil) old.openContainer).work()
                : old.openContainer instanceof ContainerCorruptAltar ? ((ContainerCorruptAltar) old.openContainer).work() : null;
            if (w == null) return;
            w.giveBack(now);
            old.openContainer = old.inventoryContainer;
        }
    }
}
