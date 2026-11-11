package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import com.divinerpgextended.perk.AquatoothEffects;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Перезарядка молотов на клиенте: раньше {@link AquatoothEffects#MAUL_COOLDOWN} тиков после удара нажатие атаки молотом
 * съедается до того, как игра его обработает, - нет ни замаха, ни "удара без урона".
 */
@SideOnly(Side.CLIENT)
public class MaulSwingClient {

    private long last = -1000;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || !AquatoothEffects.maul(mc.thePlayer.getHeldItem())) return;
        KeyBinding atk = mc.gameSettings.keyBindAttack;
        if (!atk.isPressed()) return;
        while (atk.isPressed()) {} // лишние нажатия за тик - туда же
        long now = mc.theWorld.getTotalWorldTime();
        if (now - last < AquatoothEffects.MAUL_COOLDOWN && now >= last) return; // перезарядка: нажатие съедено
        last = now;
        KeyBinding.onTick(atk.getKeyCode()); // вернуть одно нажатие - игра ударит
    }
}
