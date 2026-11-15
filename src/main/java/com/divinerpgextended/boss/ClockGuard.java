package com.divinerpgextended.boss;

import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Divine's Mysterious Clock summons the Ancient Entity by right-clicking any block. Now - only on the Ancient
 * Tomb Altar under the desert pyramid (the altar itself handles its click); elsewhere the clock does not
 * work and hints where to look for the tomb.
 */
public class ClockGuard {

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.entityPlayer.getHeldItem();
        if (held == null || held.getItem() != VanillaItemsOther.mysteriousClock) return;
        if (event.world.getBlock(event.x, event.y, event.z) == ModBlocks.tombAltar) return;
        event.useItem = Event.Result.DENY;
        if (!event.world.isRemote) {
            event.entityPlayer.addChatMessage(new ChatComponentTranslation("dre.chat.clock_only_tomb"));
        }
    }
}
