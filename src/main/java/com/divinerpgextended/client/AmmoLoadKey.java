package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.perk.Ammo;
import com.divinerpgextended.perk.AmmoLoad;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Sneak and right-click with a weapon - load it from the inventory (a throwing weapon - gather the same ones into one stack). Only if there is something
 * to load and somewhere to load it: otherwise it is a normal shot (you can shoot while sneaking when the weapon is full or there is no ammo in the inventory).
 */
@SideOnly(Side.CLIENT)
public class AmmoLoadKey {

    private long sent = -1;

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (!p.worldObj.isRemote || e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        if (p != Minecraft.getMinecraft().thePlayer || !p.isSneaking()) return;
        if (Ammo.loadable(p) <= 0 && com.divinerpgextended.perk.ThrowStack.gatherable(p) <= 0) {
            if (Ammo.of(p.getHeldItem()) != null) note(p, "nothing to load with or nowhere to load - normal shot");
            return;
        }
        e.setCanceled(true);
        // a click on a block gives two events in a row (on the block and into the air) - ask once per tick
        long t = p.worldObj.getTotalWorldTime();
        if (t == sent) return;
        sent = t;
        note(p, "asking the server to load");
        ColdHandler.NET.sendToServer(new AmmoLoad.Load());
    }

    private long noted = -1;

    /** One line in the game log per click (a click on a block gives two events). */
    private void note(EntityPlayer p, String what) {
        long t = p.worldObj.getTotalWorldTime();
        if (t == noted) return;
        noted = t;
        AmmoLoad.LOG.info("Loading (client): " + Ammo.describe(p) + " - " + what);
    }
}
