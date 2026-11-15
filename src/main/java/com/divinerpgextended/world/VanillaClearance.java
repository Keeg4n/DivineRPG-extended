package com.divinerpgextended.world;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Overworld lairs do not intersect vanilla underground generation: lair sites are known in advance from the seed
 * ({@link LairSites}, {@link ScatterGen}), and next to them the game does not place abandoned mineshafts (their start - no closer than
 * {@link #MINESHAFT_REACH} to the lair area, mineshaft corridors spread up to ~105 blocks: 80 to the start of a piece + the piece itself up to 20) or dungeons with a spawner and chest.
 * The lairs themselves stand where they were: the lair map does not go astray.
 */
public class VanillaClearance {

    /** How far corridors spread from a mineshaft start (piece start - up to 80 from the mineshaft start, the corridor itself - up to 20; measured - 105) + margin. */
    public static final int MINESHAFT_REACH = 112;
    /** A dungeon with a spawner: up to 9 blocks across, placed within a chunk with an offset of 8. */
    static final int DUNGEON_REACH = 24;

    /**
     * Whether point (bx, bz) falls into the area of some lair, expanded by margin. Areas: Carcinus and the tomb - a circle
     * of ~45 blocks, the ice cave - stretched east by ~180, the jungle lair - under the temple.
     */
    public static boolean nearLair(World w, int bx, int bz, int margin) {
        if (w == null || w.provider.dimensionId != 0) return false;
        if (near(w, LairSites.CARCINUS, bx, bz, -48, 48, -48, 48, margin)) return true;
        if (near(w, LairSites.SNOW, bx, bz, -32, 192, -80, 80, margin)) return true;
        if (near(w, LairSites.TOMB, bx, bz, -40, 40, -40, 40, margin)) return true;
        // the jungle temple (and the desert one, with the tomb under it) - by the grid of vanilla structures
        int crx = Math.floorDiv(bx >> 4, ScatterGen.MAX), crz = Math.floorDiv(bz >> 4, ScatterGen.MAX);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] c = ScatterGen.spot(w, crx + i, crz + k);
            int x = c[0] * 16 + 8, z = c[1] * 16 + 8;
            int reach = LairDomes.J_R + LairDomes.J_TOWER_DX + 8; // under a jungle temple is the underground city ({@link LairDomes}), under a desert one - the tomb
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

    /** Mineshafts: the ordinary generator, but without a mineshaft start by a lair. */
    public static class Mineshafts extends MapGenMineshaft {

        @Override
        protected boolean canSpawnStructureAtCoords(int cx, int cz) {
            if (!super.canSpawnStructureAtCoords(cx, cz)) return false; // the same random number usage as vanilla
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
