package com.divinerpgextended.accessory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Armor with accessories is consumed by a craft (a recipe where an armor part is an ingredient) or by the vanilla anvil (the right item during
 * repair) - the accessories are returned to the player instead of vanishing together with the armor. If the result took the sockets with it (the recipe
 * copies the armor data) - we return nothing, otherwise it would be a dupe.
 */
public class SocketGuard {

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent e) {
        if (e.player == null || e.player.worldObj.isRemote || e.craftMatrix == null || Sockets.count(e.crafting) > 0) return;
        for (int i = 0; i < e.craftMatrix.getSizeInventory(); i++) refund(e.player, e.craftMatrix.getStackInSlot(i), "crafting");
    }

    /**
     * Forge 1.7.10 mixes up the event fields: output holds the left item, left holds the right one (consumed by the anvil), right holds the result
     * (ForgeHooks.onAnvilRepair passes them to the constructor out of order).
     */
    @SubscribeEvent
    public void onAnvil(AnvilRepairEvent e) {
        ItemStack kept = e.output, eaten = e.left, result = e.right;
        if (e.entityPlayer == null || e.entityPlayer.worldObj.isRemote || Sockets.count(result) > Sockets.count(kept)) return;
        refund(e.entityPlayer, eaten, "anvil");
    }

    private static void refund(EntityPlayer p, ItemStack armor, String why) {
        if (Sockets.count(armor) == 0) return;
        List<ItemStack> out = Sockets.extractAll(armor); // first from the armor
        for (ItemStack s : out) {
            com.divinerpgextended.audit.Audit.log(p, "Sockets", why + " consumed " + armor.getDisplayName() + " - returned " + s.getDisplayName());
            if (!p.inventory.addItemStackToInventory(s) || s.stackSize > 0) p.dropPlayerItemWithRandomChoice(s, false); // then to the player
        }
    }
}
