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

    /** Кадры за текущую секунду: число, самый долгий кадр (нс), время прошлого кадра. */
    private int frames;
    private long worstFrame, lastFrame, lastSecond, lastChunkUpdates;

    /** Замер плавности: каждый кадр - его длительность; раз в секунду - строка perf в журнал. */
    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Debug.clientOn) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        long now = System.nanoTime();
        if (lastFrame != 0) worstFrame = Math.max(worstFrame, now - lastFrame);
        lastFrame = now;
        frames++;
        if (now - lastSecond < 1000000000L) return;
        if (lastSecond != 0) write("perf", "second", perf(mc));
        lastSecond = now;
        frames = 0;
        worstFrame = 0;
    }

    /**
     * Кадров за секунду, худший кадр, сколько участков чанков пересобрано и сколько ждёт пересборки (так видно, тормозит
     * ли отрисовка новых чанков), дальность прорисовки, графика; в одиночной игре - средний тик своего сервера.
     */
    private String perf(Minecraft mc) {
        int updates = net.minecraft.client.renderer.WorldRenderer.chunksUpdated;
        StringBuilder b = new StringBuilder();
        b.append("кадров ").append(frames).append(", худший кадр ").append(worstFrame / 1000000).append(" мс");
        b.append(", пересобрано участков ").append(Math.max(0, updates - lastChunkUpdates));
        lastChunkUpdates = updates;
        try {
            java.util.List<?> queue = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.RenderGlobal.class,
                mc.renderGlobal, "worldRenderersToUpdate", "field_72767_j");
            b.append(", ждут пересборки ").append(queue.size());
        } catch (Throwable t) {
            // другой рендер (OptiFine) - без очереди
        }
        b.append(", прорисовка ").append(mc.gameSettings.renderDistanceChunks).append(mc.gameSettings.fancyGraphics ? ", графика детальная" : ", графика быстрая");
        b.append(", загружено чанков ").append(mc.theWorld.getChunkProvider().getLoadedChunkCount());
        net.minecraft.server.integrated.IntegratedServer s = mc.getIntegratedServer();
        if (s != null) {
            long sum = 0;
            for (long t : s.tickTimeArray) sum += t;
            b.append(", тик своего сервера ").append(String.format(java.util.Locale.ROOT, "%.1f", sum / 1e6 / s.tickTimeArray.length)).append(" мс");
        }
        return b.toString();
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
