package com.divinerpgextended.accessory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Броню с аксессуарами съедает крафт (рецепт, где часть брони - ингредиент) или ванильная наковальня (правая вещь при
 * починке) - аксессуары возвращаются игроку, а не пропадают вместе с бронёй. Если результат унёс гнёзда с собой (рецепт
 * копирует данные брони) - ничего не возвращаем, иначе вышел бы дюп.
 */
public class SocketGuard {

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent e) {
        if (e.player == null || e.player.worldObj.isRemote || e.craftMatrix == null || Sockets.count(e.crafting) > 0) return;
        for (int i = 0; i < e.craftMatrix.getSizeInventory(); i++) refund(e.player, e.craftMatrix.getStackInSlot(i), "крафт");
    }

    /**
     * Forge 1.7.10 путает поля события: в output - левая вещь, в left - правая (её съедает наковальня), в right - результат
     * (ForgeHooks.onAnvilRepair передаёт их в конструктор не по порядку).
     */
    @SubscribeEvent
    public void onAnvil(AnvilRepairEvent e) {
        ItemStack kept = e.output, eaten = e.left, result = e.right;
        if (e.entityPlayer == null || e.entityPlayer.worldObj.isRemote || Sockets.count(result) > Sockets.count(kept)) return;
        refund(e.entityPlayer, eaten, "наковальня");
    }

    private static void refund(EntityPlayer p, ItemStack armor, String why) {
        if (Sockets.count(armor) == 0) return;
        List<ItemStack> out = Sockets.extractAll(armor); // сначала из брони
        for (ItemStack s : out) {
            com.divinerpgextended.audit.Audit.log(p, "Sockets", why + " съел " + armor.getDisplayName() + " - вернул " + s.getDisplayName());
            if (!p.inventory.addItemStackToInventory(s) || s.stackSize > 0) p.dropPlayerItemWithRandomChoice(s, false); // потом игроку
        }
    }
}
