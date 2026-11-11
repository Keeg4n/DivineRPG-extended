package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.Vec3;

import com.divinerpgextended.trinket.Trinket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Ласты (с Лиоплевродона): в воде плывёшь быстрее (лишний толчок в сторону движения), рывок вперёд по клавише бега - раз в
 * {@link #DASH_COOLDOWN} тиков. Движение игрока считает клиент, поэтому всё здесь.
 */
@SideOnly(Side.CLIENT)
public class FinsClient {

    public static final float BOOST = 0.025F;
    public static final double DASH = 0.9;
    public static final int DASH_COOLDOWN = 40;
    private int dashCd;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP p = mc.thePlayer;
        if (dashCd > 0) dashCd--;
        if (p == null || mc.isGamePaused() || !p.isInWater() || !com.divinerpgextended.accessory.Accessories.wears(p, Trinket.LIO_FINS)) return;
        p.moveFlying(p.movementInput.moveStrafe, p.movementInput.moveForward, BOOST);
        if (dashCd <= 0 && mc.gameSettings.keyBindSprint.isPressed() && p.movementInput.moveForward > 0) {
            Vec3 look = p.getLookVec();
            p.motionX += look.xCoord * DASH;
            p.motionY += look.yCoord * DASH * 0.6;
            p.motionZ += look.zCoord * DASH;
            dashCd = DASH_COOLDOWN;
            p.playSound("random.splash", 0.4F, 1.4F);
        }
    }
}
