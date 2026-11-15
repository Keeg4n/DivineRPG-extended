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

/** Client part of debugging: keys, mouse buttons and windows go to logs/dre-debug/...-client.csv while the server has debugging on. */
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
        write("mouse", Mouse.getEventButtonState() ? "down" : "up", "button " + b + (binding(b - 100).isEmpty() ? "" : " (" + binding(b - 100) + ")"));
    }

    @SubscribeEvent
    public void onGui(GuiOpenEvent e) {
        if (!Debug.clientOn || Minecraft.getMinecraft().thePlayer == null) return;
        GuiScreen g = e.gui;
        write("gui", g == null ? "close" : "open", g == null ? "" : g.getClass().getName());
    }

    /** Frames in the current second: count, the longest frame (ns), time of the previous frame. */
    private int frames;
    private long worstFrame, lastFrame, lastSecond, lastChunkUpdates;

    /**
     * Stutter sampler: a background thread every 10 ms takes the stack of the game's main thread (while debugging is on). A frame longer than
     * 150 ms - a lag line in the log: where the game spent this time (the most frequent places in the stack).
     */
    private static final int SAMPLE_MS = 10, LAG_MS = 150;
    private static volatile Thread main;
    private static final java.util.ArrayDeque<Object[]> SAMPLES = new java.util.ArrayDeque<Object[]>();

    static {
        Thread t = new Thread("dre-lag-sampler") {

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(SAMPLE_MS);
                        Thread m = main;
                        if (m == null || !Debug.clientOn) continue;
                        StackTraceElement[] st = m.getStackTrace();
                        synchronized (SAMPLES) {
                            SAMPLES.addLast(new Object[] { System.nanoTime(), st });
                            while (SAMPLES.size() > 300) SAMPLES.removeFirst();
                        }
                    } catch (Throwable ignored) {}
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /** The most frequent places in the stack over a period: a game/mod method (without java, lwjgl) and the path to it of 4 calls. */
    private static String hotspots(long from, long to) {
        java.util.Map<String, Integer> top = new java.util.HashMap<String, Integer>();
        int n = 0;
        StackTraceElement[] deep = null;
        long mid = (from + to) / 2;
        synchronized (SAMPLES) {
            for (Object[] s : SAMPLES) {
                long t = (Long) s[0];
                if (t < from || t > to) continue;
                n++;
                if (deep == null && t >= mid) deep = (StackTraceElement[]) s[1];
                StackTraceElement[] st = (StackTraceElement[]) s[1];
                StringBuilder b = new StringBuilder();
                int taken = 0;
                for (StackTraceElement el : st) {
                    String c = el.getClassName();
                    if (taken == 0 && (c.startsWith("java.") || c.startsWith("sun.") || c.startsWith("org.lwjgl"))) continue;
                    if (taken > 0) b.append(" < ");
                    b.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(el.getMethodName());
                    if (++taken == 4) break;
                }
                String k = b.toString();
                top.put(k, top.containsKey(k) ? top.get(k) + 1 : 1);
            }
        }
        java.util.List<java.util.Map.Entry<String, Integer>> l = new java.util.ArrayList<java.util.Map.Entry<String, Integer>>(top.entrySet());
        java.util.Collections.sort(l, new java.util.Comparator<java.util.Map.Entry<String, Integer>>() {

            @Override
            public int compare(java.util.Map.Entry<String, Integer> a, java.util.Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        StringBuilder b = new StringBuilder("samples " + n);
        for (int i = 0; i < Math.min(4, l.size()); i++) b.append(" | ").append(l.get(i).getValue()).append("x ").append(l.get(i).getKey());
        // the full path of one sample from the middle of a stutter: you can see who started the heavy work
        if (deep != null) {
            b.append(" || path:");
            for (int i = 0; i < Math.min(30, deep.length); i++) {
                String c = deep[i].getClassName();
                b.append(' ').append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(deep[i].getMethodName());
            }
        }
        return b.toString();
    }

    /** Smoothness measurement: every frame - its duration; once a second - a perf line in the log. */
    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Debug.clientOn) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        main = Thread.currentThread();
        long now = System.nanoTime();
        if (lastFrame != 0) {
            worstFrame = Math.max(worstFrame, now - lastFrame);
            if (now - lastFrame > LAG_MS * 1000000L) write("lag", (now - lastFrame) / 1000000 + " ms", hotspots(lastFrame, now));
        }
        lastFrame = now;
        frames++;
        if (now - lastSecond < 1000000000L) return;
        if (lastSecond != 0) write("perf", "second", perf(mc));
        lastSecond = now;
        frames = 0;
        worstFrame = 0;
    }

    /**
     * Frames per second, the worst frame, how many chunk sections were rebuilt and how many are waiting for a rebuild (so you can see whether the
     * drawing of new chunks lags), render distance, graphics; in singleplayer - the average tick of its own server.
     */
    private String perf(Minecraft mc) {
        int updates = net.minecraft.client.renderer.WorldRenderer.chunksUpdated;
        StringBuilder b = new StringBuilder();
        b.append("frames ").append(frames).append(", worst frame ").append(worstFrame / 1000000).append(" ms");
        b.append(", rebuilt chunks ").append(Math.max(0, updates - lastChunkUpdates));
        lastChunkUpdates = updates;
        try {
            java.util.List<?> queue = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.RenderGlobal.class,
                mc.renderGlobal, "worldRenderersToUpdate", "field_72767_j");
            b.append(", awaiting rebuild ").append(queue.size());
        } catch (Throwable t) {
            // another renderer (OptiFine) - no queue
        }
        b.append(", render distance ").append(mc.gameSettings.renderDistanceChunks).append(mc.gameSettings.fancyGraphics ? ", fancy graphics" : ", fast graphics");
        b.append(", loaded chunks ").append(mc.theWorld.getChunkProvider().getLoadedChunkCount());
        net.minecraft.server.integrated.IntegratedServer s = mc.getIntegratedServer();
        if (s != null) {
            long sum = 0;
            for (long t : s.tickTimeArray) sum += t;
            b.append(", own server tick ").append(String.format(java.util.Locale.ROOT, "%.1f", sum / 1e6 / s.tickTimeArray.length)).append(" ms");
        }
        return b.toString();
    }

    /** Once a second - to disk; debugging turned off - close the file. */
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
