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
 * Призрак (Венец полёта R8): в полёте - вдвое быстрее творческого, как в ихоровых ботинках Thaumic Tinkerer; с бегом - ещё
 * быстрее. Полёт считает клиент, поэтому и скорость ставится здесь. Свою скорость из /dreflyspeed не трогаем: меняем, только
 * пока стоит обычная, и возвращаем обычную, когда сет сняли.
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
        if (ghost && Math.abs(now - BASE) < 1e-4F) { // и снова, если сервер прислал способности с обычной скоростью
            p.capabilities.setFlySpeed(GHOST);
            ours = true;
        } else if (!ghost && ours) {
            if (Math.abs(now - GHOST) < 1e-4F) p.capabilities.setFlySpeed(BASE);
            ours = false;
        }
    }
}
