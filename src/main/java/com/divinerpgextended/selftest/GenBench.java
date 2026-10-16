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
    private static final List<String> MOBS = new ArrayList<String>(), GAPS = new ArrayList<String>();
    private int ticks;

    public static boolean enabled() {
        return Boolean.getBoolean("dre.genbench");
    }

    public static void start() {
        FMLCommonHandler.instance().bus().register(new GenBench());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ticks == 39) System.out.println("[dre-genbench] старт");
        if (e.phase != TickEvent.Phase.END || ++ticks != 40) return;
        MinecraftServer server = MinecraftServer.getServer();
        List<String> out = new ArrayList<String>();
        out.add("Замер генерации: квадрат " + (2 * R + 1) + "x" + (2 * R + 1) + " чанков от (" + ORIGIN + ", " + ORIGIN + ")");
        out.add(String.format("%-10s %10s %10s %12s %10s %12s %10s", "мир", "мс/чанк", "рельеф мс", "заселение мс", "свет мс", "лишних чанков", "всего"));
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
        out.add("Блоки, прилетевшие в уже загруженные чанки при генерации кольца вокруг (их клиент пересчитывает по свету)");
        out.addAll(GAPS);
        out.add("");
        out.add("Мобы после заселения квадрата и время одного их тика");
        out.addAll(MOBS);
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

    private static String bench(final WorldServer w, String name) {
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
        // освещение: чанк уходит игроку только когда свет рассчитан; сервер считает его в следующих тиках
        java.lang.reflect.Method light = ReflectionHelper.findMethod(Chunk.class, null, new String[] { "func_150809_p" });
        t0 = System.nanoTime();
        int lit = 0;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                Chunk c = cps.provideChunk(base + x, base + z);
                if (!c.isTerrainPopulated) continue;
                try {
                    light.invoke(c);
                    lit++;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        double lighting = lit == 0 ? 0 : (System.nanoTime() - t0) / 1e6 / lit;
        // какие блоки меняются сами: как сервер возле игрока - случайные тики (3 блока на секцию за тик) и отложенные
        final java.util.Map<String, Integer> changed = new java.util.TreeMap<String, Integer>();
        final int fb = base;
        net.minecraft.world.IWorldAccess spy = new net.minecraft.world.IWorldAccess() {

            public void markBlockForUpdate(int x, int y, int z) {
                if (Math.abs((x >> 4) - fb) > R || Math.abs((z >> 4) - fb) > R) return;
                String k = net.minecraft.block.Block.blockRegistry.getNameForObject(w.getBlock(x, y, z));
                changed.put(k, changed.containsKey(k) ? changed.get(k) + 1 : 1);
            }

            public void markBlockForRenderUpdate(int x, int y, int z) {}

            public void markBlockRangeForRenderUpdate(int a, int b, int c, int d, int e, int f) {}

            public void playSound(String s, double x, double y, double z, float v, float p) {}

            public void playSoundToNearExcept(net.minecraft.entity.player.EntityPlayer pl, String s, double x, double y, double z, float v, float p) {}

            public void spawnParticle(String s, double x, double y, double z, double a, double b, double c) {}

            public void onEntityCreate(net.minecraft.entity.Entity e) {}

            public void onEntityDestroy(net.minecraft.entity.Entity e) {}

            public void playRecord(String s, int x, int y, int z) {}

            public void broadcastSound(int id, int x, int y, int z, int d) {}

            public void playAuxSFX(net.minecraft.entity.player.EntityPlayer pl, int id, int x, int y, int z, int d) {}

            public void destroyBlockPartially(int id, int x, int y, int z, int p) {}

            public void onStaticEntitiesChanged() {}
        };
        w.addWorldAccess(spy);
        // квадрат уже у игрока: чанки готовы к отправке; генерация кольца вокруг - что из него прилетит в квадрат
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                Chunk c = cps.provideChunk(base + x, base + z);
                ReflectionHelper.setPrivateValue(Chunk.class, c, true, "field_150815_m");
                c.isLightPopulated = true;
            }
        for (int x = -R - 3; x <= R + 3; x++)
            for (int z = -R - 3; z <= R + 3; z++)
                if (Math.abs(x) > R || Math.abs(z) > R) cps.loadChunk(base + x, base + z);
        ((java.util.List<?>) ReflectionHelper.getPrivateValue(net.minecraft.world.World.class, w, "worldAccesses", "field_73021_x")).remove(spy);
        int changes = 0;
        for (int v : changed.values()) changes += v;
        java.util.List<java.util.Map.Entry<String, Integer>> top = new java.util.ArrayList<java.util.Map.Entry<String, Integer>>(changed.entrySet());
        java.util.Collections.sort(top, new java.util.Comparator<java.util.Map.Entry<String, Integer>>() {

            public int compare(java.util.Map.Entry<String, Integer> a, java.util.Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        GAPS.add(String.format("%-10s изменений %6d: %s", name, changes, top.subList(0, Math.min(6, top.size()))));
        // мобы, появившиеся при заселении, и цена их тика (без игрока рядом мобы не обновляются - держим тикет на чанках
        // через обычный тик мира не выйдет, поэтому обновляем сущности напрямую)
        int mobs = 0;
        java.util.Map<String, Integer> kinds = new java.util.TreeMap<String, Integer>();
        for (Object o : w.loadedEntityList) {
            net.minecraft.entity.Entity en = (net.minecraft.entity.Entity) o;
            if (!(en instanceof net.minecraft.entity.EntityLiving)) continue;
            if (Math.abs(((int) en.posX >> 4) - base) > R || Math.abs(((int) en.posZ >> 4) - base) > R) continue;
            mobs++;
            String k = en.getClass().getSimpleName();
            kinds.put(k, kinds.containsKey(k) ? kinds.get(k) + 1 : 1);
        }
        t0 = System.nanoTime();
        int ticked = 0;
        for (int i = 0; i < 20; i++) {
            for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
                net.minecraft.entity.Entity en = (net.minecraft.entity.Entity) o;
                if (!(en instanceof net.minecraft.entity.EntityLiving) || en.isDead) continue;
                if (Math.abs(((int) en.posX >> 4) - base) > R || Math.abs(((int) en.posZ >> 4) - base) > R) continue;
                w.updateEntity(en);
                ticked++;
            }
        }
        double mobTick = (System.nanoTime() - t0) / 1e6 / 20;
        MOBS.add(String.format("%-10s мобов %4d, их тик %6.2f мс: %s", name, mobs, mobTick, kinds));
        int outside = 0, all = 0;
        for (Object o : cps.loadedChunks) {
            Chunk c = (Chunk) o;
            if (Math.abs(c.xPosition - base) > R + 40 || Math.abs(c.zPosition - base) > R + 40) continue;
            all++;
            if (Math.abs(c.xPosition - base) > R || Math.abs(c.zPosition - base) > R) outside++;
        }
        return String.format("%-10s %10.1f %10.1f %12.1f %10.1f %12d %10d", name, total, terrain, Math.max(0, total - terrain), lighting,
            outside, cps.loadedChunks.size() - before);
    }
}
