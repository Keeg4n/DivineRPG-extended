package com.divinerpgextended.station;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Рабочие слоты Наковальни Странника: у каждого открытого окна свои, в блоке и в мире ничего не хранится (как сетка
 * верстака). Окно закрылось - всё вернулось игроку: в инвентарь, что не влезло - на землю у ног. Окно закрывается и при
 * выходе с сервера, и при смерти, поэтому вещи не остаются в забытом окне.
 */
public class AnvilWork extends InventoryBasic {

    public AnvilWork() {
        this("tile.dre.wandererAnvil.name", AnvilLogic.SIZE);
    }

    /** Такие же слоты другого окна (алтарь порчи). */
    public AnvilWork(String name, int size) {
        super(name, false, size);
    }

    /** Вернуть всё игроку (только сервер). Вещь сначала уходит из слота, потом выдаётся - второй вызов ничего не даст. */
    public void giveBack(EntityPlayer p) {
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack s = getStackInSlotOnClosing(i);
            if (s != null) give(p, s);
        }
    }

    /** В инвентарь, остаток - на землю у ног. В творческом без места addItemStackToInventory стак обнуляет - туда не кладём. */
    static void give(EntityPlayer p, ItemStack s) {
        if (!(p.capabilities.isCreativeMode && p.inventory.getFirstEmptyStack() < 0)) p.inventory.addItemStackToInventory(s);
        if (s.stackSize > 0) p.dropPlayerItemWithRandomChoice(s, false);
    }

    /** Выход с сервера и смерть с открытой наковальней или алтарём порчи - закрыть окно, пока вещи игрока ещё на месте. */
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
    }
}
