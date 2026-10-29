package com.divinerpgextended.world;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Логова обычного мира не пересекаются с ванильной подземной генерацией: места логов известны заранее из сида
 * ({@link LairSites}, {@link ScatterGen}), и рядом с ними игра не ставит заброшенные шахты (их начало - не ближе
 * {@link #MINESHAFT_REACH} к площади логова, коридоры шахты расходятся до 80 блоков) и подземелья со спавнером и сундуком.
 * Сами логова стоят там же, где и были: карта к логову не сбивается.
 */
public class VanillaClearance {

    /** Насколько далеко от начала расходятся коридоры шахты (у ванили - до 80 блоков) + запас. */
    static final int MINESHAFT_REACH = 96;
    /** Подземелье со спавнером: до 9 блоков в поперечнике, ставится в пределах чанка со сдвигом 8. */
    static final int DUNGEON_REACH = 24;

    /**
     * Попадает ли точка (bx, bz) в площадь какого-то логова, расширенную на margin. Площади: Карцинус и гробница - круг
     * ~45 блоков, ледяная пещера - вытянута к востоку на ~180, логово джунглей - под храмом.
     */
    public static boolean nearLair(World w, int bx, int bz, int margin) {
        if (w == null || w.provider.dimensionId != 0) return false;
        if (near(w, LairSites.CARCINUS, bx, bz, -48, 48, -48, 48, margin)) return true;
        if (near(w, LairSites.SNOW, bx, bz, -32, 192, -80, 80, margin)) return true;
        if (near(w, LairSites.TOMB, bx, bz, -40, 40, -40, 40, margin)) return true;
        // храм джунглей (и пустынный, под ним гробница) - по сетке ванильных построек
        int crx = Math.floorDiv(bx >> 4, ScatterGen.MAX), crz = Math.floorDiv(bz >> 4, ScatterGen.MAX);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] c = ScatterGen.spot(w, crx + i, crz + k);
            int x = c[0] * 16 + 8, z = c[1] * 16 + 8;
            int reach = LairDomes.J_R + 8; // под храмом джунглей - подземный город ({@link LairDomes}), под пустынным - гробница
            if (Math.abs(bx - x) > reach + margin || Math.abs(bz - z) > reach + margin) continue;
            BiomeGenBase b = w.getWorldChunkManager().getBiomeGenAt(x, z);
            if (lairKind(ScatterGen.kind(b, true)) || lairKind(ScatterGen.kind(b, false))) return true;
        }
        return false;
    }

    private static boolean lairKind(int k) {
        return k == ScatterGen.DESERT || k == ScatterGen.JUNGLE;
    }

    private static boolean near(World w, int type, int bx, int bz, int x0, int x1, int z0, int z1, int margin) {
        int size = LairSites.spacing(type);
        if (size <= 0) return false;
        int rx = Math.floorDiv(bx, size), rz = Math.floorDiv(bz, size);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] s = LairSites.site(w, type, rx + i, rz + k);
            if (s == null) continue;
            if (bx >= s[0] + x0 - margin && bx <= s[0] + x1 + margin && bz >= s[1] + z0 - margin && bz <= s[1] + z1 + margin) return true;
        }
        return false;
    }

    /** Шахты: обычный генератор, но без начала шахты у логова. */
    public static class Mineshafts extends MapGenMineshaft {

        @Override
        protected boolean canSpawnStructureAtCoords(int cx, int cz) {
            if (!super.canSpawnStructureAtCoords(cx, cz)) return false; // тот же расход случайных чисел, что у ванили
            return !nearLair(worldObj, cx * 16 + 8, cz * 16 + 8, MINESHAFT_REACH);
        }
    }

    @SubscribeEvent
    public void onInit(InitMapGenEvent e) {
        if (e.type == InitMapGenEvent.EventType.MINESHAFT && e.newGen != null && e.newGen.getClass() == MapGenMineshaft.class) e.newGen = new Mineshafts();
    }

    @SubscribeEvent
    public void onPopulate(PopulateChunkEvent.Populate e) {
        if (e.type != PopulateChunkEvent.Populate.EventType.DUNGEON || e.world.provider.dimensionId != 0) return;
        if (nearLair(e.world, e.chunkX * 16 + 16, e.chunkZ * 16 + 16, DUNGEON_REACH)) e.setResult(Event.Result.DENY);
    }
}
