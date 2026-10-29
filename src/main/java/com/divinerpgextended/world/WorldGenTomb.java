package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.divinerpg.entities.vanilla.EntityAridWarrior;
import net.divinerpg.entities.vanilla.EntityDesertCrawler;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Древняя гробница под ванильной пирамидой пустыни - логово Древней сущности (Ancient Entity).
 * <p>
 * Пирамиду узнаём по ловушке в комнате с сокровищами: каменная нажимная плита, под ней через блок - TNT. Гробницу
 * строим не сразу, а когда вся её площадь (около 120 x 60 блоков) уже заселена: иначе поздние части пирамиды и
 * озёра соседних чанков ломали бы её (засыпанная лестница, висящая вода). Пока ждём - пирамида в списке, который
 * хранится вместе с миром и проверяется раз в 5 секунд.
 * <p>
 * Ловушку пирамиды разминируем, восточный сундук комнаты переносим в угол, из центра вниз уходит широкая лестница в
 * лабиринт на ~25 блоков ниже: прямые коридоры как в египетских гробницах, стены из песчаника с полосой
 * "иероглифов" из оранжевой и синей глины, золотые лампы Divine в потолке. В тупиках - саркофаги с сундуками и
 * спавнеры мертвецов; в коридорах - крошащийся пол, неотличимый от обычного, а под ним яма с паутиной. Из дальнего
 * конца лабиринта лестница ведёт в погребальный зал; на помосте у восточной стены - алтарь для Таинственных часов.
 */
public class WorldGenTomb implements IWorldGenerator {

    private static final int CELLS = 9, PITCH = 6, W = CELLS * PITCH + 1, H = 5;
    /** Алтарь стоит на помосте в ALTAR_DX блоках восточнее центра зала: призванный босс встаёт в центре. */
    public static final int ALTAR_DX = 10, ALTAR_DY = 2;

    // ---------------------------------------------------------------- ожидание достройки окрестностей

    /** Пирамиды, под которыми гробница ещё не построена (x, y, z нажимной плиты). Хранятся вместе с миром. */
    public static class Pending extends WorldSavedData {

        private static final String NAME = "divinerpgextended_tombs_pending";
        final List<int[]> list = new ArrayList<int[]>();

        public Pending(String name) {
            super(name);
        }

        static Pending get(World w) {
            Pending p = (Pending) w.perWorldStorage.loadData(Pending.class, NAME);
            if (p == null) {
                p = new Pending(NAME);
                w.perWorldStorage.setData(NAME, p);
            }
            return p;
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            list.clear();
            NBTTagList l = tag.getTagList("P", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound t = l.getCompoundTagAt(i);
                list.add(new int[] { t.getInteger("x"), t.getInteger("y"), t.getInteger("z") });
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            NBTTagList l = new NBTTagList();
            for (int[] p : list) {
                NBTTagCompound t = new NBTTagCompound();
                t.setInteger("x", p[0]);
                t.setInteger("y", p[1]);
                t.setInteger("z", p[2]);
                l.appendTag(t);
            }
            tag.setTag("P", l);
        }
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != 0) return;
        for (int x = chunkX * 16 + 8; x < chunkX * 16 + 24; x++) {
            for (int z = chunkZ * 16 + 8; z < chunkZ * 16 + 24; z++) {
                for (int y = 70; y >= 30; y--) {
                    if (world.getBlock(x, y, z) == Blocks.stone_pressure_plate && world.getBlock(x, y - 2, z) == Blocks.tnt) {
                        Pending p = Pending.get(world);
                        p.list.add(new int[] { x, y, z });
                        p.markDirty();
                    }
                }
            }
        }
    }

    /** Раз в 5 секунд пробуем построить ожидающие гробницы. */
    public static class Ticker {

        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            World w = event.world;
            if (event.phase != TickEvent.Phase.END || w.isRemote || w.provider.dimensionId != 0
                || w.getTotalWorldTime() % 100 != 0) return;
            Pending p = Pending.get(w);
            if (p.list.isEmpty()) return;
            for (Iterator<int[]> it = p.list.iterator(); it.hasNext();) {
                int[] t = it.next();
                if (!ready(w, t[0], t[1], t[2])) {
                    // игрок у пирамиды, а площадь гробницы (до 110 блоков на восток) не вся заселена: при малой дальности
                    // прорисовки она так и не построилась бы - догружаем недостающие чанки понемногу
                    if (w.getClosestPlayer(t[0], t[1], t[2], 96) != null) fill(w, t[0], t[1], t[2], 12);
                    else release(w, t[0], t[1], t[2]); // игрок ушёл - догруженное не держим (вернётся - догрузим снова)
                    continue;
                }
                it.remove();
                p.markDirty();
                if (w.getBlock(t[0], t[1], t[2]) == Blocks.stone_pressure_plate && build(w, w.rand, t[0], t[1], t[2])) {
                    DivineRPGExtended.LOG.info("Древняя гробница: " + t[0] + " " + t[2]);
                }
                release(w, t[0], t[1], t[2]);
            }
        }
    }

    /** Длина лестницы от комнаты с сокровищами до пола лабиринта. */
    private static int stairLength(int py) {
        int floor = py - 1;
        return floor - Math.max(20, floor - 26);
    }

    /** Чанки площади гробницы (лестница, лабиринт, зал): {ax0, ax1, az0, az1}. */
    private static int[] area(int px, int py, int pz) {
        int d = stairLength(py);
        int x0 = px - 12, x1 = px + d + 2 + W + 40, z0 = pz - 32, z1 = pz + 32;
        return new int[] { (x0 - 8) >> 4, (x1 - 8) >> 4, (z0 - 8) >> 4, (z1 - 8) >> 4 };
    }

    /** Вся площадь гробницы загружена и заселена. */
    private static boolean ready(World w, int px, int py, int pz) {
        int[] a = area(px, py, pz);
        for (int ax = a[0]; ax <= a[1]; ax++) {
            for (int az = a[2]; az <= a[3]; az++) {
                if (!w.getChunkProvider()
                    .chunkExists(ax, az) || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) return false;
            }
        }
        return true;
    }

    /** Загрузить (создать) до max недостающих чанков площади и кольца за ней - без кольца крайние не заселятся. */
    private static void fill(World w, int px, int py, int pz, int max) {
        int[] a = area(px, py, pz);
        for (int ax = a[0]; ax <= a[1] + 1 && max > 0; ax++) for (int az = a[2]; az <= a[3] + 1 && max > 0; az++) {
            if (w.getChunkProvider().chunkExists(ax, az)) continue;
            w.getChunkProvider().loadChunk(ax, az);
            max--;
        }
    }

    /** После постройки: чанки площади, которые никто из игроков не видит (догруженные {@link #fill}), - в очередь на выгрузку. */
    private static void release(World w, int px, int py, int pz) {
        if (!(w instanceof net.minecraft.world.WorldServer)) return;
        net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) w;
        int[] a = area(px, py, pz);
        for (int ax = a[0]; ax <= a[1] + 1; ax++) for (int az = a[2]; az <= a[3] + 1; az++)
            if (ws.theChunkProviderServer.chunkExists(ax, az) && !ws.getPlayerManager().func_152621_a(ax, az)) ws.theChunkProviderServer.unloadChunksIfNotNearSpawn(ax, az);
    }

    /** Ждёт ли постройки гробница ближе r блоков (пирамида есть, площадь ещё не готова). */
    static boolean pendingNear(World w, int x, int z, int r) {
        for (int[] t : Pending.get(w).list) if ((long) (t[0] - x) * (t[0] - x) + (long) (t[2] - z) * (t[2] - z) <= (long) r * r) return true;
        return false;
    }

    // ---------------------------------------------------------------- постройка

    private static void set(World w, int x, int y, int z, Block b, int meta) {
        w.setBlock(x, y, z, b, meta, 2);
    }

    private static void sandstone(World w, int x, int y, int z, int meta) {
        set(w, x, y, z, Blocks.sandstone, meta);
    }

    /** Золотая лампа Divine (вместо светокамня и золотых блоков: свет есть, бесплатного золота нет). */
    private static void lamp(World w, int x, int y, int z) {
        set(w, x, y, z, VanillaBlocks.lamp1, 10);
    }

    /** Перенести сундук со всем содержимым. */
    private static void moveChest(World w, int x, int y, int z, int nx, int ny, int nz) {
        if (!(w.getTileEntity(x, y, z) instanceof TileEntityChest)) return;
        TileEntityChest from = (TileEntityChest) w.getTileEntity(x, y, z);
        ItemStack[] items = new ItemStack[from.getSizeInventory()];
        for (int i = 0; i < items.length; i++) {
            items[i] = from.getStackInSlot(i);
            from.setInventorySlotContents(i, null);
        }
        set(w, x, y, z, Blocks.air, 0);
        set(w, nx, ny, nz, Blocks.chest, 0);
        if (w.getTileEntity(nx, ny, nz) instanceof TileEntityChest) {
            TileEntityChest to = (TileEntityChest) w.getTileEntity(nx, ny, nz);
            for (int i = 0; i < items.length && i < to.getSizeInventory(); i++) to.setInventorySlotContents(i, items[i]);
        }
    }

    /** px, py, pz - нажимная плита в комнате с сокровищами. */
    private static boolean build(World w, Random rand, int px, int py, int pz) {
        int floor = py - 1; // пол комнаты с сокровищами
        int d = stairLength(py);
        int t = floor - d; // пол лабиринта
        if (d < 8) return false;

        // разминировать ловушку пирамиды, восточный сундук - в угол комнаты (на его месте пройдёт лестница)
        set(w, px, py, pz, Blocks.air, 0);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) sandstone(w, px + dx, py - 2, pz + dz, 0);
        if (w.getBlock(px + 2, py, pz) == Blocks.chest) moveChest(w, px + 2, py, pz, px - 2, py, pz + 2);

        // лестница вниз на восток: 3 в ширину, ступень на блок вниз за блок вперёд, стены и свод из песчаника
        for (int i = 0; i <= d + 1; i++) {
            int x = px + i, sy = Math.max(t, floor - i);
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = sy - 1; y <= sy + 5; y++) {
                    if (w.getBlock(x, y, pz + dz) == Blocks.chest) continue;
                    boolean inner = Math.abs(dz) <= 1 && y >= sy + 1 && y <= sy + 4;
                    if (inner) set(w, x, y, pz + dz, Blocks.air, 0);
                    else if (y < floor + 1 || i > 2) sandstone(w, x, y, pz + dz, y == sy ? 2 : 0);
                }
            }
            if (i > 2 && i % 5 == 2) lamp(w, x, sy + 5, pz);
        }

        // лабиринт: клетка (0, mid) встречает лестницу с запада
        int mid = CELLS / 2;
        int mx0 = px + d + 2, mz0 = pz - 1 - mid * PITCH - 1;
        boolean[][] open = maze(rand, mid);
        open[0][mid * PITCH + 1] = open[0][mid * PITCH + 2] = open[0][mid * PITCH + 3] = true; // вход с лестницы
        int exitZ = mid * PITCH + 2;
        for (int i = W - 3; i < W; i++) { // выход к погребальному залу сквозь стену в 3 блока
            open[i][exitZ - 1] = open[i][exitZ] = open[i][exitZ + 1] = true;
        }
        for (int i = 0; i < W; i++) {
            for (int k = 0; k < W; k++) {
                int x = mx0 + i, z = mz0 + k;
                if (open[i][k]) {
                    sandstone(w, x, t, z, (i + k) % 7 == 0 ? 1 : 2);
                    for (int y = t + 1; y < t + H; y++) set(w, x, y, z, Blocks.air, 0);
                    if (i % PITCH == 2 && k % PITCH == 2 && rand.nextInt(3) > 0) lamp(w, x, t + H, z);
                    else sandstone(w, x, t + H, z, 2);
                } else {
                    for (int y = t; y <= t + H; y++) {
                        // полоса "иероглифов" на уровне глаз по стенам коридоров
                        if (y == t + 3 && touchesOpen(open, i, k)) {
                            int c = (i + k) % 4;
                            if (c == 0) set(w, x, y, z, Blocks.stained_hardened_clay, 1); // оранжевая
                            else if (c == 2) set(w, x, y, z, Blocks.stained_hardened_clay, 11); // синяя
                            else sandstone(w, x, y, z, 1);
                        } else {
                            sandstone(w, x, y, z, 0);
                        }
                    }
                }
            }
        }

        // тупики: саркофаги с сундуками и спавнеры мертвецов
        List<int[]> ends = deadEnds(open);
        Collections.shuffle(ends, rand);
        String[] undead = { (String) EntityList.classToStringMapping.get(EntityAridWarrior.class),
            (String) EntityList.classToStringMapping.get(EntityDesertCrawler.class), "Zombie", "Skeleton" };
        int chests = 0, spawners = 0;
        List<int[]> used = new ArrayList<int[]>();
        for (int[] e : ends) {
            int x = mx0 + e[0], z = mz0 + e[1];
            if (e[0] <= 3 && Math.abs(e[1] - exitZ) <= 3) continue; // не у самого входа
            used.add(e);
            if (chests < 8) {
                LairLoot.chest(w, rand, x, t + 1, z, Blocks.chest, LairLoot.tomb());
                set(w, x, t + 2, z, Blocks.stone_slab, 1); // саркофаг: песчаниковая плита сверху
                chests++;
            } else if (com.divinerpgextended.Config.dungeonSpawners && spawners < 6) {
                String mob = undead[rand.nextInt(undead.length)];
                if (mob == null) mob = "Zombie";
                set(w, x, t, z, Blocks.mob_spawner, 0);
                TileEntityMobSpawner s = (TileEntityMobSpawner) w.getTileEntity(x, t, z);
                if (s != null) s.func_145881_a()
                    .setEntityName(mob);
                spawners++;
            }
        }

        // ловушки: в клетках-перекрёстках пол крошится, под ним яма на 5 блоков с паутиной
        for (int n = 0, tries = 0; n < 7 && tries < 100; tries++) {
            int ci = 1 + rand.nextInt(CELLS - 1), cj = rand.nextInt(CELLS);
            int x = mx0 + ci * PITCH + 2, z = mz0 + cj * PITCH + 2;
            boolean taken = false;
            for (int[] u : used) if (u[0] == ci * PITCH + 2 && u[1] == cj * PITCH + 2) taken = true;
            if (taken) continue;
            used.add(new int[] { ci * PITCH + 2, cj * PITCH + 2 });
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                for (int y = t - 6; y <= t - 1; y++) {
                    boolean pit = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && y >= t - 5;
                    if (pit) set(w, x + dx, y, z + dz, Blocks.air, 0);
                    else sandstone(w, x + dx, y, z + dz, 0);
                }
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    set(w, x + dx, t - 6, z + dz, Blocks.sand, 0);
                    set(w, x + dx, t, z + dz, ModBlocks.trapFloor, 0);
                }
            }
            set(w, x, t - 5, z, Blocks.web, 0);
            set(w, x + (rand.nextBoolean() ? 1 : -1), t - 5, z + (rand.nextBoolean() ? 1 : -1), Blocks.web, 0);
            n++;
        }

        // спуск к погребальному залу и сам зал
        int ex = mx0 + W, ez = mz0 + exitZ;
        int tb = t - 7; // пол зала
        for (int i = 0; i <= 9; i++) {
            int x = ex + i, sy = Math.max(tb, t - Math.max(0, i - 1));
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = sy - 1; y <= sy + 5; y++) {
                    boolean inner = Math.abs(dz) <= 1 && y >= sy + 1 && y <= sy + 4;
                    if (inner) set(w, x, y, ez + dz, Blocks.air, 0);
                    else sandstone(w, x, y, ez + dz, y == sy ? 2 : 0);
                }
            }
        }
        hall(w, ex + 10 + 14, tb, ez); // западная стена зала сразу за лестницей

        LairData.get(w)
            .add(LairData.TOMB, px, w.getTopSolidOrLiquidBlock(px, pz), pz); // пирамида
        return true;
    }

    private static boolean touchesOpen(boolean[][] open, int i, int k) {
        return i > 0 && open[i - 1][k] || i < W - 1 && open[i + 1][k] || k > 0 && open[i][k - 1]
            || k < W - 1 && open[i][k + 1];
    }

    /**
     * Погребальный зал: 27 x 27, высота 12, по периметру колонны из резного песчаника, внутри четыре массивные
     * колонны с поясом из золотых ламп, лампы в потолке. Вход с запада, помост с алтарём у восточной стены.
     */
    private static void hall(World w, int cx, int tb, int cz) {
        int r = 13, h = 12;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int x = cx + dx, z = cz + dz;
                boolean wall = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
                for (int y = tb - 1; y <= tb + h + 1; y++) {
                    if (wall || y == tb - 1 || y == tb + h + 1) {
                        boolean pillar = wall && (dx + dz) % 4 == 0;
                        sandstone(w, x, y, z, pillar ? 1 : 0);
                    } else if (y == tb) {
                        boolean inlay = (Math.abs(dx) + Math.abs(dz)) % 6 == 0;
                        if (inlay) set(w, x, y, z, Blocks.stained_hardened_clay, 11);
                        else sandstone(w, x, y, z, 2);
                    } else {
                        set(w, x, y, z, Blocks.air, 0);
                    }
                }
                if (!wall && dx % 6 == 0 && dz % 6 == 0) lamp(w, x, tb + h, z);
            }
        }
        // проход с запада
        for (int dz = -1; dz <= 1; dz++) for (int y = tb + 1; y <= tb + 4; y++) set(w, cx - r - 1, y, cz + dz, Blocks.air, 0);
        // четыре массивные колонны 3x3 с поясом из золотых ламп
        for (int[] c : new int[][] { { -6, -6 }, { -6, 6 }, { 6, -6 }, { 6, 6 } }) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                for (int y = tb + 1; y <= tb + h; y++) {
                    boolean belt = y == tb + 6 && (dx == 0 || dz == 0) && !(dx == 0 && dz == 0);
                    if (belt) lamp(w, cx + c[0] + dx, y, cz + c[1] + dz);
                    else sandstone(w, cx + c[0] + dx, y, cz + c[1] + dz, y == tb + 1 || y == tb + h ? 1 : 2);
                }
            }
        }
        // помост с алтарём у восточной стены: босс встаёт в центре зала
        int ax = cx + ALTAR_DX;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -2; dz <= 2; dz++) sandstone(w, ax + dx, tb + 1, cz + dz, 2);
        for (int dz : new int[] { -2, 2 }) lamp(w, ax, tb + 2, cz + dz);
        set(w, ax, tb + ALTAR_DY, cz, ModBlocks.tombAltar, 0);
        // резная стена-"дверь" за алтарём с лампами
        for (int dz = -1; dz <= 1; dz++) for (int y = tb + 1; y <= tb + 5; y++) {
            if ((y + dz) % 2 == 0) lamp(w, cx + r + 1, y, cz + dz);
            else sandstone(w, cx + r + 1, y, cz + dz, 1);
        }
    }

    /** Лабиринт "рекурсивным возвратом" на сетке клеток: true - проход (клетка 3x3 и проёмы между клетками). */
    private static boolean[][] maze(Random rand, int mid) {
        boolean[][] open = new boolean[W][W];
        boolean[][] seen = new boolean[CELLS][CELLS];
        List<int[]> stack = new ArrayList<int[]>();
        stack.add(new int[] { 0, mid });
        seen[0][mid] = true;
        cell(open, 0, mid);
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!stack.isEmpty()) {
            int[] c = stack.get(stack.size() - 1);
            List<int[]> next = new ArrayList<int[]>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < CELLS && nz < CELLS && !seen[nx][nz]) next.add(new int[] { nx, nz });
            }
            if (next.isEmpty()) {
                stack.remove(stack.size() - 1);
                continue;
            }
            int[] n = next.get(rand.nextInt(next.size()));
            seen[n[0]][n[1]] = true;
            cell(open, n[0], n[1]);
            // проём между клетками: стена толщиной 3 между ними пробивается на всю ширину коридора
            int ax = Math.min(c[0], n[0]) * PITCH, az = Math.min(c[1], n[1]) * PITCH;
            if (c[0] != n[0]) {
                for (int s = 4; s <= 6; s++) for (int q = 1; q <= 3; q++) open[ax + s][c[1] * PITCH + q] = true;
            } else {
                for (int s = 4; s <= 6; s++) for (int q = 1; q <= 3; q++) open[c[0] * PITCH + q][az + s] = true;
            }
            stack.add(n);
        }
        return open;
    }

    private static void cell(boolean[][] open, int cx, int cz) {
        for (int a = 1; a <= 3; a++) for (int b = 1; b <= 3; b++) open[cx * PITCH + a][cz * PITCH + b] = true;
    }

    /** Центры клеток с одним выходом. */
    private static List<int[]> deadEnds(boolean[][] open) {
        List<int[]> out = new ArrayList<int[]>();
        for (int cx = 0; cx < CELLS; cx++) {
            for (int cz = 0; cz < CELLS; cz++) {
                int mx = cx * PITCH + 2, mz = cz * PITCH + 2, exits = 0;
                if (mx - 3 >= 0 && open[mx - 3][mz]) exits++;
                if (mx + 3 < W && open[mx + 3][mz]) exits++;
                if (mz - 3 >= 0 && open[mx][mz - 3]) exits++;
                if (mz + 3 < W && open[mx][mz + 3]) exits++;
                if (exits == 1) out.add(new int[] { mx, mz });
            }
        }
        return out;
    }
}
