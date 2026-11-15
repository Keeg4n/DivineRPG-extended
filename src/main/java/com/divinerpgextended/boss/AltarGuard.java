package com.divinerpgextended.boss;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.divinerpgextended.block.BlockArenaAltar;
import com.divinerpgextended.block.BlockBossAltar;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Boss altars (and locked chests) are indestructible, but modpacks have tools that break even the indestructible (the awakened Ichor Pickaxe of
 * Thaumic Tinkerer and similar). A broken altar drops nothing (the block itself), and if the tool did hand it over as an item -
 * the item does not appear on the ground, and in a survival player's inventory it vanishes. In creative mode altars can be carried and placed.
 * Here, every 5 seconds, the horde altar is also placed in Ayeraco aeries where it is missing, and gaps under the floor of old
 * Mortum arenas are filled ({@link com.divinerpgextended.world.twilight.MortumArenas#heal}).
 */
public class AltarGuard {

    public static boolean altar(Block b) {
        return b instanceof BlockBossAltar || b instanceof BlockArenaAltar || b instanceof com.divinerpgextended.block.BlockLockedChest;
    }

    public static boolean altar(ItemStack s) {
        return s != null && s.getItem() != null && altar(Block.getBlockFromItem(s.getItem()));
    }

    /** Remove altars from a player's inventory. How many stacks were removed. */
    public static int sweep(EntityPlayer p) {
        int n = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (!altar(inv[i])) continue;
            inv[i] = null;
            n++;
        }
        if (n > 0) p.inventoryContainer.detectAndSendChanges();
        return n;
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (!e.world.isRemote && e.entity instanceof EntityItem && altar(((EntityItem) e.entity).getEntityItem())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.ticksExisted % 20 != 0) return;
        if (!p.capabilities.isCreativeMode) sweep(p);
        if (p.ticksExisted % 100 == 0) {
            com.divinerpgextended.world.WorldGenOuterLairs.keepAyeracoAltar(p);
            com.divinerpgextended.world.twilight.MortumArenas.heal(p); // and gaps under old Mortum arenas
        }
    }
}
