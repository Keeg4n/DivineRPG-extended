package com.divinerpgextended.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Клиентская часть отладки: клавиши, кнопки мыши и окна - в logs/dre-debug/...-client.csv, пока сервер включил отладку. */
@SideOnly(Side.CLIENT)
public class DebugClient {

    private DebugLog log;

    private DebugLog log() {
        if (log == null) log = new DebugLog(Minecraft.getMinecraft().mcDataDir, "client");
        return log;
    }

    private static String binding(int code) {
        StringBuilder b = new StringBuilder();
        for (KeyBinding k : Minecraft.getMinecraft().gameSettings.keyBindings) {
            if (k.getKeyCode() == code) b.append(b.length() == 0 ? "" : ", ").append(I18n.format(k.getKeyDescription()));
        }
        return b.toString();
    }

    private void write(String category, String event, String details) {
        log().write(Minecraft.getMinecraft().thePlayer, category, event, details);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (!Debug.clientOn || Minecraft.getMinecraft().thePlayer == null) return;
        int k = Keyboard.getEventKey();
        if (k == 0) return;
        write("key", Keyboard.getEventKeyState() ? "down" : "up", Keyboard.getKeyName(k) + (binding(k).isEmpty() ? "" : " (" + binding(k) + ")"));
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent e) {
        if (!Debug.clientOn || Minecraft.getMinecraft().thePlayer == null) return;
        int b = Mouse.getEventButton();
        if (b < 0) return;
        write("mouse", Mouse.getEventButtonState() ? "down" : "up", "кнопка " + b + (binding(b - 100).isEmpty() ? "" : " (" + binding(b - 100) + ")"));
    }

    @SubscribeEvent
    public void onGui(GuiOpenEvent e) {
        if (!Debug.clientOn || Minecraft.getMinecraft().thePlayer == null) return;
        GuiScreen g = e.gui;
        write("gui", g == null ? "close" : "open", g == null ? "" : g.getClass().getName());
    }

    /** Раз в секунду - на диск; отладку выключили - закрыть файл. */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (Minecraft.getMinecraft().thePlayer == null) Debug.clientOn = false;
        if (!Debug.clientOn) {
            if (log != null) {
                log.close();
                log = null;
            }
            return;
        }
        if (Minecraft.getMinecraft().thePlayer.ticksExisted % 20 == 0 && log != null) log.flush();
    }
}
