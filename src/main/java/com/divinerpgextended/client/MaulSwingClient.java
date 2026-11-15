package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import com.divinerpgextended.perk.AquatoothEffects;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Maul cooldown on the client: for {@link AquatoothEffects#MAUL_COOLDOWN} ticks after a swing, an attack press with a maul
 * is swallowed before the game counts it - no swing and no "hit without damage".
 * <p>
 * The game reads a mouse click and processes it in the same tick, so removing the press at the start of the tick was too late (before 0.99.120
 * the cooldown did not work at all): the click is caught by the mouse event, before it reaches the attack button. If the attack
 * is rebound to a key, the press is removed in the keyboard event, which arrives before the attack is processed.
 */
@SideOnly(Side.CLIENT)
public class MaulSwingClient {

    private long last = -1000;

    /** Whether you can swing now; if yes - remember the swing. */
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
