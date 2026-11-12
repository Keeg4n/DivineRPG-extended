package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import com.divinerpgextended.perk.AquatoothEffects;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Перезарядка молотов на клиенте: раньше {@link AquatoothEffects#MAUL_COOLDOWN} тиков после замаха нажатие атаки молотом
 * съедается до того, как игра его посчитает, - нет ни замаха, ни "удара без урона".
 * <p>
 * Клик мыши игра читает и сразу же обрабатывает в одном тике, поэтому снимать нажатие в начале тика было поздно (до 0.99.120
 * перезарядка так и не работала): клик ловится событием мыши, ещё до того, как он дойдёт до кнопки атаки. Если атака
 * переназначена на клавишу - нажатие снимается в событии клавиатуры, оно приходит раньше обработки атаки.
 */
@SideOnly(Side.CLIENT)
public class MaulSwingClient {

    private long last = -1000;

    /** Можно ли махнуть сейчас; да - запомнить замах. */
    private boolean swing() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || !AquatoothEffects.maul(mc.thePlayer.getHeldItem())) return true;
        long now = mc.theWorld.getTotalWorldTime();
        if (now - last < AquatoothEffects.MAUL_COOLDOWN && now >= last) return false;
        last = now;
        return true;
    }

    @SubscribeEvent
    public void onMouse(net.minecraftforge.client.event.MouseEvent e) {
        if (!e.buttonstate || e.button < 0) return;
        KeyBinding atk = Minecraft.getMinecraft().gameSettings.keyBindAttack;
        if (e.button - 100 != atk.getKeyCode()) return;
        if (!swing()) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onKey(cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent e) {
        KeyBinding atk = Minecraft.getMinecraft().gameSettings.keyBindAttack;
        if (atk.getKeyCode() <= 0 || !org.lwjgl.input.Keyboard.getEventKeyState() || org.lwjgl.input.Keyboard.getEventKey() != atk.getKeyCode()) return;
        if (!swing()) while (atk.isPressed()) {}
    }
}
