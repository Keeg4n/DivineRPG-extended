package com.divinerpgextended.selftest;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Замер генерации миров (только с -Ddre.genbench=true на тестовом сервере). В каждом мире грузит одинаковый квадрат
 * чанков вдали от спавна и пишет в logs/dre-genbench.txt: время на чанк, сколько чанков подтянулось за пределами
 * квадрата (цепная генерация - заселение лезет в соседей, которых ещё нет), и отдельно время голого рельефа без
 * заселения. После замера выключает сервер.
 */
public class GenBench {

    private static final int R = 8, ORIGIN = 4000;
    private int ticks;

    public static boolean enabled() {
        return Boolean.getBoolean("dre.genbench");
    }

    public static void start() {
        FMLCommonHandler.instance().bus().register(new GenBench());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks != 40) return;
        MinecraftServer server = MinecraftServer.getServer();
        List<String> out = new ArrayList<String>();
        out.add("Замер генерации: квадрат " + (2 * R + 1) + "x" + (2 * R + 1) + " чанков от (" + ORIGIN + ", " + ORIGIN + ")");
        out.add(String.format("%-10s %10s %10s %12s %12s %10s", "мир", "мс/чанк", "рельеф мс", "заселение мс", "лишних чанков", "всего"));
        String[] names = { "Overworld", "Eden", "Wildwood", "Apalachia", "Skythern", "Mortum", "Iceika", "Vethea", "Arcana" };
        int[] dims = { 0, ConfigurationHelper.eden, ConfigurationHelper.wildwood, ConfigurationHelper.apalachia,
            ConfigurationHelper.skythern, ConfigurationHelper.mortum, ConfigurationHelper.iceika, ConfigurationHelper.vethea,
            ConfigurationHelper.arcana };
        for (int d = 0; d < dims.length; d++) {
            try {
                out.add(bench(server.worldServerForDimension(dims[d]), names[d]));
            } catch (Throwable t) {
                out.add(names[d] + ": ошибка " + t);
            }
        }
        out.add("");
        out.add("Телепорт порталом, мс: Divine > быстрый (Divine создаёт новый телепорт на каждый вход)");
        out.add(String.format("%-10s %18s %18s %18s", "мир", "1-й вход", "повторный", "назад"));
        net.minecraft.block.Block[] portals = { net.divinerpg.utils.blocks.TwilightBlocks.edenPortal,
            net.divinerpg.utils.blocks.TwilightBlocks.wildwoodPortal, net.divinerpg.utils.blocks.TwilightBlocks.apalachiaPortal,
            net.divinerpg.utils.blocks.TwilightBlocks.skythernPortal, net.divinerpg.utils.blocks.TwilightBlocks.mortumPortal };
        for (int i = 0; i < portals.length; i++) {
            try {
                out.add(portal(server, names[i + 1], dims[i + 1], portals[i]));
            } catch (Throwable t) {
                out.add(names[i + 1] + ": ошибка " + t);
            }
        }
        try {
            PrintWriter w = new PrintWriter(new File(server.getFile("logs"), "dre-genbench.txt"), "UTF-8");
            for (String s : out) {
                w.println(s);
                System.out.println("[dre-genbench] " + s);
            }
            w.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        server.initiateShutdown();
    }

    /**
     * Порталы: первый вход на новом месте (генерация + поиск + постройка портала), повторный вход и выход назад - у
     * Divine и у быстрого телепорта; оба должны поставить в одну точку.
     */
    private static String portal(MinecraftServer server, String name, int dim, net.minecraft.block.Block portal) {
        net.minecraft.block.Block frame = ReflectionHelper.getPrivateValue(net.divinerpg.blocks.base.portal.BlockModPortal.class,
            (net.divinerpg.blocks.base.portal.BlockModPortal) portal, "blockFrame");
        WorldServer to = server.worldServerForDimension(dim), home = server.worldServerForDimension(0);
        double x = -6000 - dim * 300, z = 6000;
        double oldFirst = time(new net.divinerpg.dimensions.base.DivineTeleporter(to, dim, portal, frame), to, x, z, null);
        double newFirst = time(new com.divinerpgextended.portal.FastTeleporter(to, dim, portal, frame), to, x + 600, z, null);
        double[] a = new double[3], b = new double[3];
        double oldAgain = time(new net.divinerpg.dimensions.base.DivineTeleporter(to, dim, portal, frame), to, x + 3, z - 2, a);
        double newAgain = time(new com.divinerpgextended.portal.FastTeleporter(to, dim, portal, frame), to, x + 3, z - 2, b);
        boolean same = java.util.Arrays.equals(a, b);
        time(new net.divinerpg.dimensions.base.DivineTeleporter(home, 0, portal, frame), home, x, z, null);
        double oldBack = time(new net.divinerpg.dimensions.base.DivineTeleporter(home, 0, portal, frame), home, x - 2, z + 1, a);
        double newBack = time(new com.divinerpgextended.portal.FastTeleporter(home, 0, portal, frame), home, x - 2, z + 1, b);
        same &= java.util.Arrays.equals(a, b);
        return String.format("%-10s %9.0f > %-6.0f %9.0f > %-6.0f %9.0f > %-6.0f %s", name, oldFirst, newFirst, oldAgain, newAgain,
            oldBack, newBack, same ? "та же точка" : "РАЗНЫЕ ТОЧКИ");
    }

    /** Телепорт свиньи с (x, 70, z): время в мс, куда поставило - в at. */
    private static double time(net.minecraft.world.Teleporter t, WorldServer w, double x, double z, double[] at) {
        net.minecraft.entity.passive.EntityPig pig = new net.minecraft.entity.passive.EntityPig(w);
        pig.setPosition(x, 70, z);
        long t0 = System.nanoTime();
        t.placeInPortal(pig, x, 70, z, 0);
        double ms = (System.nanoTime() - t0) / 1e6;
        if (at != null) {
            at[0] = pig.posX;
            at[1] = pig.posY;
            at[2] = pig.posZ;
        }
        return ms;
    }

    private static String bench(WorldServer w, String name) {
        ChunkProviderServer cps = w.theChunkProviderServer;
        IChunkProvider gen = ReflectionHelper.getPrivateValue(ChunkProviderServer.class, cps, "currentChunkProvider", "field_73246_d");
        int dim = w.provider.dimensionId;
        int base = ORIGIN + dim * 64; // у каждого мира своё место: не мешают друг другу
        // голый рельеф без заселения - генератором напрямую, в мир не попадает
        long t0 = System.nanoTime();
        int n = 0;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                gen.provideChunk(base + 1000 + x, base + 1000 + z);
                n++;
            }
        double terrain = (System.nanoTime() - t0) / 1e6 / n;
        int before = cps.loadedChunks.size();
        t0 = System.nanoTime();
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++)
                cps.loadChunk(base + x, base + z);
        double total = (System.nanoTime() - t0) / 1e6 / n;
        int outside = 0, all = 0;
        for (Object o : cps.loadedChunks) {
            Chunk c = (Chunk) o;
            if (Math.abs(c.xPosition - base) > R + 40 || Math.abs(c.zPosition - base) > R + 40) continue;
            all++;
            if (Math.abs(c.xPosition - base) > R || Math.abs(c.zPosition - base) > R) outside++;
        }
        return String.format("%-10s %10.1f %10.1f %12.1f %12d %10d", name, total, terrain, Math.max(0, total - terrain), outside,
            cps.loadedChunks.size() - before);
    }
}
