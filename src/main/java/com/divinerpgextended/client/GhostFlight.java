package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.divinerpgextended.item.ArmorCrown;
import com.divinerpgextended.item.ArmorData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Wraith (Crown of Flight R8): in flight - twice as fast as creative, like in the Thaumic Tinkerer ichor boots; with sprint - even
 * faster. The client calculates flight, so the speed is set here too. We do not touch the speed from /dreflyspeed: we change it only
 * while the normal one is set, and restore the normal one when the set is taken off.
 */
@SideOnly(Side.CLIENT)
public class GhostFlight {

    public static final float BASE = 0.05F, GHOST = 0.1F;
    private boolean ours;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p == null) {
            ours = false;
            return;
        }
        boolean ghost = ArmorData.equippedCrown(p) == ArmorCrown.FLIGHT && !com.divinerpgextended.vethea.Nightmare.inVethea(p);
        float now = p.capabilities.getFlySpeed();
        if (ghost && Math.abs(now - BASE) < 1e-4F) { // and again, if the server sent abilities with the normal speed
            p.capabilities.setFlySpeed(GHOST);
            ours = true;
        } else if (!ghost && ours) {
            if (Math.abs(now - GHOST) < 1e-4F) p.capabilities.setFlySpeed(BASE);
            ours = false;
        }
    }
}
