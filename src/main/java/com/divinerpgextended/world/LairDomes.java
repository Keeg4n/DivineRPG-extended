package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.world.twilight.ColumnStructure;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Underground domes of overworld lairs - built by columns (like the Frozen Throne): each chunk, when populated, completes
 * its part from the blueprint, without chain-generating neighbors. Foreign hostile mobs never appear inside the domes ({@link NoSpawns}),
 * so the light is rare and for looks, not a solid carpet of lamps.
 * <ul>
 * <li>The Snow Glacon ice dome ({@link Glacon}) under an igloo (site - {@link LairSites#SNOW}): at the center a nest arena of
 * snow and ice with an altar, around it a frozen lake, then a snowy shore; ice stalactites hang from the vault, in places
 * fusing with the floor into stalagnates; some have lamps at the tips. From the igloo a shaft with a spiral staircase and a tunnel into the dome lead down.</li>
 * <li>An abandoned underground city by the jungle temple ({@link JungleCity}): a dome half the size of the Throne, at the center a round
 * arena of the Ancient Dramcryx with an altar behind a ruined fence, around it a ring boulevard, then the city in a ring: streets
 * in a grid, overgrown houses with collapsed walls, gardens. Under the temple (in the city, west of the arena) is a tower with a spiral staircase,
 * the continuation of the well from the temple. Curved roots in vines hang from the vault, tapering downward; some glow at the tip,
 * some grow down to the floor.</li>
 * </ul>
 */
public class LairDomes implements IWorldGenerator {

    // ---------------------------------------------------------------- Glacon

    /** The Glacon dome: radius, floor, vault height; center - G_DX east of the igloo; nest and lake - radii. */
    public static final int G_R = 46, G_FL = 14, G_H = 26, G_DX = 56, G_NEST = 13, G_LAKE = 30;
    // ---------------------------------------------------------------- jungle
    /** The city: radius, floor, vault height; arena at the center with radius J_ARENA; the tower under the temple - J_TOWER_DX west of the center. */
    public static final int J_R = 72, J_FL = 10, J_H = 34, J_ARENA = 20, J_TOWER_DX = 38;

    static final Glacon GLACON = new Glacon();
    static final JungleCity JUNGLE = new JungleCity();

    /** The Glacon dome center by the igloo site {x, z}: {x, floor, z}. */
    public static int[] glaconCenter(int[] site) {
        return new int[] { site[0] + G_DX, G_FL, site[1] };
    }

    /** The jungle temple well in chunk (sx, sz): the temple stands from the chunk corner, the well is inside it. */
    public static int[] jungleWell(int sx, int sz) {
        return new int[] { sx * 16 + 7, J_FL, sz * 16 + 7 };
    }

    /** The city (and arena) center by the jungle temple in chunk (sx, sz): east of the well by J_TOWER_DX. */
    public static int[] jungleCenter(int sx, int sz) {
        return new int[] { sx * 16 + 7 + J_TOWER_DX, J_FL, sz * 16 + 7 };
    }

    /** The vault above the tower (its vault is flat, the well from the temple reaches it). */
    public static int towerCeil() {
        return jungleCeil(J_TOWER_DX - 4);
    }

    public static int glaconCeil(double r) {
        return G_FL + 5 + (int) Math.round((G_H - 5) * Math.sqrt(Math.max(0, 1 - (r / G_R) * (r / G_R))));
    }

    public static int jungleCeil(double r) {
        return J_FL + 7 + (int) Math.round((J_H - 7) * Math.sqrt(Math.max(0, 1 - (r / J_R) * (r / J_R))));
    }

    /** Glacon dome sites in regions around the point. */
    static List<int[]> glaconCenters(World w, int x, int z) {
        List<int[]> out = new ArrayList<int[]>();
        int g = LairSites.spacing(LairSites.SNOW);
        if (g <= 0) return out;
        int rx = Math.floorDiv(x, g), rz = Math.floorDiv(z, g);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] s = LairSites.site(w, LairSites.SNOW, rx + i, rz + k);
            if (s != null && s.length >= 2) out.add(glaconCenter(s));
        }
        return out;
    }

    private static final Map<World, Boolean> JUNGLES = new WeakHashMap<World, Boolean>();

    /** City centers under jungle temples in regions of the vanilla grid around the point. */
    static List<int[]> jungleCenters(World w, int x, int z) {
        List<int[]> out = new ArrayList<int[]>();
        Boolean has = JUNGLES.get(w);
        if (has == null) JUNGLES.put(w, has = !com.divinerpgextended.Config.scatterInModBiomes || ScatterGen.hasJungles(w));
        int span = ScatterGen.MAX * 16;
        int rx = Math.floorDiv(x, span), rz = Math.floorDiv(z, span);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] s = ScatterGen.spot(w, rx + i, rz + k);
            if (ScatterGen.kind(w.getWorldChunkManager().getBiomeGenAt(s[0] * 16 + 8, s[1] * 16 + 8), has) == ScatterGen.JUNGLE && ScatterGen.allowed(w, rx + i, rz + k))
                out.add(jungleCenter(s[0], s[1]));
        }
        return out;
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != 0) return;
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8; // the population area of this chunk
        for (int[] c : glaconCenters(world, x0 + 8, z0 + 8)) {
            // the dome and a tunnel west to the igloo
            if (x0 + 16 <= c[0] - G_DX - 3 || x0 > c[0] + G_R + 4 || z0 + 16 <= c[2] - G_R - 4 || z0 > c[2] + G_R + 4) continue;
            GLACON.buildSlice(world, c[0], c[2], x0, z0);
            GLACON.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, G_FL + 1, G_FL + G_H);
            seal(GLACON, world, c[0], c[2], x0 - 16, z0 - 16, 48, G_FL - 4, G_FL + G_H + 4);
        }
        for (int[] c : jungleCenters(world, x0 + 8, z0 + 8)) {
            if (x0 + 16 <= c[0] - J_R - 4 || x0 > c[0] + J_R + 4 || z0 + 16 <= c[2] - J_R - 4 || z0 > c[2] + J_R + 4) continue;
            JUNGLE.buildSlice(world, c[0], c[2], x0, z0);
            JUNGLE.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, J_FL + 1, J_FL + J_H);
            seal(JUNGLE, world, c[0], c[2], x0 - 16, z0 - 16, 48, J_FL - 4, J_FL + J_H + 4);
        }
    }

    /**
     * Liquids in already finished pieces: springs and lakes that population places in cave walls after the dome flowed
     * inside. In the wall and vault a liquid is again a blueprint block, inside (blueprint air) - remove. Unloaded is not touched.
     */
    static void seal(ColumnStructure s, World w, int cx, int cz, int xFrom, int zFrom, int size, int yFrom, int yTo) {
        int[] col = new int[256 * 2];
        for (int x = xFrom; x < xFrom + size; x++) for (int z = zFrom; z < zFrom + size; z++) {
            if (!w.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
            int ax = (x - 8) >> 4, az = (z - 8) >> 4; // the column piece is already built
            if (!w.getChunkProvider().chunkExists(ax, az) || !w.getChunkFromChunkCoords(ax, az).isTerrainPopulated) continue;
            for (int i = 0; i < col.length; i += 2) col[i] = ColumnStructure.KEEP;
            if (!s.columnAt(x - cx, z - cz, col)) continue;
            for (int y = Math.max(1, yFrom); y <= yTo; y++) {
                int code = col[y * 2];
                if (code == ColumnStructure.KEEP || !w.getBlock(x, y, z).getMaterial().isLiquid()) continue;
                if (code == ColumnStructure.AIR) w.setBlock(x, y, z, Blocks.air, 0, 2);
                else w.setBlock(x, y, z, s.blockAt(code, col[y * 2 + 1]), s.metaAt(code, col[y * 2 + 1]), 2);
            }
        }
    }

    /** Build the whole dome in the world at once (for the selftest): glacon - the Glacon dome, otherwise the city. */
    public static void buildAll(World w, int cx, int cz, boolean glacon) {
        ColumnStructure s = glacon ? GLACON : JUNGLE;
        int r = glacon ? G_R + G_DX + 8 : J_R + 8;
        for (int x0 = cx - r; x0 < cx + r; x0 += 16) for (int z0 = cz - r; z0 < cz + r; z0 += 16) s.buildSlice(w, cx, cz, x0, z0);
    }

    /** A point inside a dome: inside, foreign mobs do not appear. */
    public static boolean inside(World w, double x, double y, double z) {
        if (w.provider.dimensionId != 0 || y > J_FL + J_H + 2) return false;
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        if (y <= G_FL + G_H + 1) for (int[] c : glaconCenters(w, ix, iz)) {
            double dx = x - c[0], dz = z - c[2];
            if (dx * dx + dz * dz < G_R * G_R && y >= G_FL) return true;
        }
        for (int[] c : jungleCenters(w, ix, iz)) {
            double dx = x - c[0], dz = z - c[2];
            if (dx * dx + dz * dz < J_R * J_R && y >= J_FL) return true;
        }
        return false;
    }

    /** Foreign hostile mobs do not appear in domes (the light there is for looks, not from spawning). */
    public static class NoSpawns {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onCheckSpawn(net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn e) {
            if (e.world.isRemote || e.y > J_FL + J_H + 2 || !(e.entityLiving instanceof net.minecraft.entity.monster.IMob)) return;
            if (inside(e.world, e.x, e.y, e.z)) e.setResult(cpw.mods.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    // ---------------------------------------------------------------- common

    static int hash(int x, int z) {
        return ColumnStructure.hash(x, z);
    }

    /** Steps of the 3x3 shaft spiral staircase: at height y the step is in cell RING[y & 7] (the same in the well and in the dome). */
    public static final int[][] RING = { { -1, -1 }, { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 } };

    /** Index of the cell (dx, dz) of the shaft ring or -1 (center and outside the shaft). */
    public static int ringIndex(int dx, int dz) {
        for (int k = 0; k < 8; k++) if (RING[k][0] == dx && RING[k][1] == dz) return k;
        return -1;
    }

    static double ringDist(double dx, double dz, double ring, int count, double phase) {
        double a = Math.atan2(dz, dx) - phase, step = Math.PI * 2 / count;
        double ca = Math.round(a / step) * step + phase;
        return Math.sqrt((dx - Math.cos(ca) * ring) * (dx - Math.cos(ca) * ring) + (dz - Math.sin(ca) * ring) * (dz - Math.sin(ca) * ring));
    }

    // ---------------------------------------------------------------- Glacon ice dome

    static final class Glacon extends ColumnStructure {

        static final int SHELL = 1, SNOW = 2, PACKED = 3, ICE = 4, LAYER = 5, LAMP = 6, LANTERN = 7, ALTAR = 8, SNOW_BRICK = 9, ICY_BRICK = 10;

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case SHELL: return meta == 1 ? Blocks.packed_ice : IceikaBlocks.frozenStone;
                case SNOW: return Blocks.snow;
                case PACKED: return Blocks.packed_ice;
                case ICE: return Blocks.ice;
                case LAYER: return Blocks.snow_layer;
                case LAMP: return IceikaBlocks.workshopLamp;
                case LANTERN: return net.divinerpg.utils.blocks.VanillaBlocks.lightFenceBlueOn;
                case ALTAR: return ModBlocks.glaconAltar;
                case SNOW_BRICK: return IceikaBlocks.snowBricks;
                case ICY_BRICK: return IceikaBlocks.icyBricks;
                default: return Blocks.air;
            }
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == SHELL ? 0 : meta;
        }

        private static void shell(int[] col, int y, int dx, int dz) {
            put(col, y, SHELL, hash(dx * 7 + y, dz * 13 - y) % 10 < 3 ? 1 : 0);
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int sx = dx + G_DX; // from the igloo shaft
            boolean shaft = Math.abs(sx) <= 1 && Math.abs(dz) <= 1;
            boolean tunnel = Math.abs(dz) <= 2 && dx >= -G_DX - 2 && dx <= -G_R + 3;
            if (r > G_R + 3 && !tunnel) return false;
            int h = hash(dx, dz);
            if (r <= G_R + 3) dome(dx, dz, r, h, col);
            if (tunnel && r >= G_R - 3) {
                // tunnel from the igloo shaft to the dome: 3 wide, 3 high, ice and frozen stone around
                if (Math.abs(dz) <= 1 && dx >= -G_DX - 1) {
                    put(col, G_FL, PACKED, 0);
                    for (int y = G_FL + 1; y <= G_FL + 3; y++) put(col, y, AIR, 0);
                    if (!shaft) shell(col, G_FL + 4, dx, dz);
                } else {
                    for (int y = G_FL; y <= G_FL + 4; y++) shell(col, y, dx, dz);
                }
                for (int y = G_FL - 3; y < G_FL; y++) shell(col, y, dx, dz);
            }
            if (shaft) {
                // the bottom of the igloo shaft (the top is dug by the lair generator at the igloo): spiral steps along the 3x3 ring
                int k = ringIndex(sx, dz);
                for (int y = G_FL + 1; y <= G_FL + 8; y++) put(col, y, k >= 0 && (y & 7) == k ? ICY_BRICK : AIR, 0);
            }
            return true;
        }

        private void dome(int dx, int dz, double r, int h, int[] col) {
            for (int y = G_FL - 4; y < G_FL; y++) shell(col, y, dx, dz);
            if (r >= G_R) { // wall
                for (int y = G_FL; y <= G_FL + 9; y++) shell(col, y, dx, dz);
                return;
            }
            int ceil = glaconCeil(r);
            for (int y = G_FL + 1; y <= ceil; y++) put(col, y, AIR, 0);
            for (int y = ceil + 1; y <= ceil + 3; y++) put(col, y, SHELL, 1);
            // floor by rings: nest, frozen lake, snowy shore
            if (r < G_NEST) nest(dx, dz, r, h, col);
            else if (r < G_LAKE) {
                // lake: packed ice near the nest (plain ice would melt from the nest lamps), further transparent ice with cracks
                put(col, G_FL, r < 17 || h % 6 == 0 ? PACKED : ICE, 0);
                put(col, G_FL - 1, PACKED, 0);
                if (h % 13 == 0) put(col, G_FL + 1, LAYER, h % 2);
            } else {
                put(col, G_FL, h % 10 < 7 ? SNOW : SHELL, 0);
                if (h % 9 == 0) put(col, G_FL + 1, LAYER, h % 3);
                if (h % 41 == 0) { // boulder
                    put(col, G_FL + 1, SHELL, 0);
                    if (h % 2 == 0) put(col, G_FL + 2, SNOW, 0);
                }
                // lanterns at the wall: an ice pillar with a lamp
                if (r > G_R - 3 && ringDist(dx, dz, G_R - 2, 14, 0.2) < 0.6) {
                    put(col, G_FL + 1, PACKED, 0);
                    put(col, G_FL + 2, LAMP, 0);
                }
            }
            icicles(dx, dz, r, ceil, col);
        }

        /** The nest: a snow bowl, a rim of snow and ice with gaps to the west and east, ice "feathers", an altar in the center. */
        private void nest(int dx, int dz, double r, int h, int[] col) {
            put(col, G_FL, PACKED, 0);
            put(col, G_FL + 1, SNOW, 0);
            if (r < 1.6) {
                put(col, G_FL + 2, SNOW_BRICK, 0);
                if (r < 0.5) put(col, G_FL + 3, ALTAR, 0);
                return;
            }
            // fires in the nest bowl
            if (ringDist(dx, dz, 6, 4, Math.PI / 4) < 0.5) put(col, G_FL + 1, LAMP, 0);
            if (r < 10.5) {
                if (h % 7 == 0) put(col, G_FL + 2, LAYER, h % 2);
                return;
            }
            boolean gap = Math.abs(dz) <= 1; // passages from the west (tunnel) and east
            if (gap) return;
            int top = 2 + h % 3;
            for (int y = G_FL + 2; y <= G_FL + top; y++) put(col, y, (y + h) % 3 == 0 ? PACKED : (y + h) % 3 == 1 ? SNOW : SHELL, 0);
            // ice feathers along the rim
            if (ringDist(dx, dz, 12, 8, Math.PI / 8) < 0.8) {
                int k = (int) Math.round((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 4));
                for (int y = G_FL + 2; y <= G_FL + 5 + k % 3; y++) put(col, y, PACKED, 0);
            }
        }

        /** Stalactites from the vault (some with a glowing tip), in places fused with the floor into stalagnates; small stalagmites on the shore. */
        private void icicles(int dx, int dz, double r, int ceil, int[] col) {
            final int cell = 7;
            int cx0 = Math.floorDiv(dx, cell), cz0 = Math.floorDiv(dz, cell);
            for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
                int gx = cx0 + i, gz = cz0 + k;
                int ch = hash(gx * 31 + 7, gz * 17 + 3);
                if (ch % 2 != 0) continue; // rare: the vault is not a forest of ice
                double px = gx * cell + ch % 5 + 1.5, pz = gz * cell + (ch >> 3) % 5 + 1.5, pr = Math.sqrt(px * px + pz * pz);
                if (pr > G_R - 3 || pr < 16) continue; // above the nest the vault is clear
                double rb = 0.8 + (ch >> 6) % 3 * 0.5, d = Math.sqrt((dx + 0.5 - px) * (dx + 0.5 - px) + (dz + 0.5 - pz) * (dz + 0.5 - pz));
                if (d > rb + 0.6) continue;
                int top = glaconCeil(pr), floor = G_FL + 1;
                if ((ch >> 9) % 5 == 0 && pr >= 18) {
                    // stalagnate: thick at the vault and at the floor, thin in the middle
                    for (int y = floor; y <= top; y++) {
                        double t = (double) (y - floor) / Math.max(1, top - floor), rad = rb * (0.45 + 0.55 * Math.abs(t - 0.5) * 2) + 0.4;
                        if (d <= rad) put(col, y, PACKED, 0);
                    }
                    continue;
                }
                if (d > rb) continue;
                int len = Math.min(3 + (ch >> 12) % 10, top - floor - 4);
                if (len < 2) continue;
                int tip = top - len + 1;
                for (int y = tip; y <= top; y++) {
                    double rad = rb * (y - tip + 1) / len + 0.3;
                    if (d <= rad) put(col, y, PACKED, 0);
                }
                // a glowing tip on every third (high above the floor - the lake's plain ice does not melt from it)
                if ((ch >> 16) % 3 == 0 && d < 0.5 && tip >= G_FL + 10) put(col, tip, LAMP, 0);
                // a stalagmite under it on the shore
                if ((ch >> 20) % 3 == 0 && pr > G_LAKE + 1 && d <= rb * 0.8) {
                    int hgt = 1 + (ch >> 22) % 3;
                    for (int y = floor; y < floor + hgt; y++) if (d <= rb * 0.8 * (floor + hgt - y) / hgt + 0.2) put(col, y, PACKED, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- abandoned city under the jungle temple

    public static final class JungleCity extends ColumnStructure {

        static final int SHELL = 1, GRASS = 2, DIRT = 3, BRICK = 4, COBBLE = 5, MOSSY = 6, GRAVEL = 7, LOG = 8, LEAVES = 9, GLOW = 10, FENCE = 11, FERN = 12,
            ALTAR = 13, STEP = 14, VINE = 15, ROOT = 16;
        /** A special house block: a whole house, inside by the far wall a Divan RPG (an Easter egg). */
        public static final int HOME_IX = -3, HOME_IZ = 1;
        /** The ring boulevard around the arena: up to this radius it is paved, blocks start further out. */
        static final int RING = J_ARENA + 4;

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case SHELL: return meta == 0 ? Blocks.stone : meta == 1 ? Blocks.cobblestone : Blocks.mossy_cobblestone;
                case GRASS: return Blocks.grass;
                case DIRT: return Blocks.dirt;
                case BRICK: return Blocks.stonebrick;
                case COBBLE: return Blocks.cobblestone;
                case MOSSY: return Blocks.mossy_cobblestone;
                case GRAVEL: return Blocks.gravel;
                case LOG: case ROOT: return Blocks.log;
                case LEAVES: return Blocks.leaves;
                case GLOW: return Blocks.glowstone;
                case FENCE: return Blocks.fence;
                case FERN: return Blocks.tallgrass;
                case ALTAR: return ModBlocks.jungleAltar;
                case STEP: return Blocks.stonebrick;
                case VINE: return Blocks.vine;
                default: return Blocks.air;
            }
        }

        @Override
        protected int metaOf(int code, int meta) {
            if (code == SHELL) return 0;
            if (code == LOG) return 3; // jungle trunk
            if (code == ROOT) return 3 | 12; // root: bark on all sides
            if (code == LEAVES) return 3 | 4; // jungle leaves, do not decay
            if (code == FERN) return 2;
            if (code == STEP) return 1;
            return meta;
        }

        private static void shell(int[] col, int y, int dx, int dz) {
            int v = hash(dx * 5 + y, dz * 11 - y) % 10;
            put(col, y, SHELL, v < 5 ? 0 : v < 8 ? 1 : 2);
        }

        /** The tower under the temple well (coordinates from the city center). */
        static boolean tower(int dx, int dz) {
            int tx = dx + J_TOWER_DX;
            return tx * tx + dz * dz < 4.2 * 4.2;
        }

        static boolean arena(int dx, int dz) {
            return dx * dx + dz * dz < J_ARENA * J_ARENA;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > J_R + 3) return false;
            int h = hash(dx, dz);
            for (int y = J_FL - 4; y < J_FL; y++) shell(col, y, dx, dz);
            if (r >= J_R) {
                for (int y = J_FL; y <= J_FL + 12; y++) shell(col, y, dx, dz);
                return true;
            }
            int ceil = tower(dx, dz) ? towerCeil() : jungleCeil(r);
            for (int y = J_FL + 1; y <= ceil; y++) put(col, y, AIR, 0);
            for (int y = ceil + 1; y <= ceil + 3; y++) shell(col, y, dx, dz);
            // floor: grass and dirt
            put(col, J_FL, h % 3 == 0 ? DIRT : GRASS, 0);
            if (tower(dx, dz)) tower(dx + J_TOWER_DX, dz, ceil, h, col);
            else if (arena(dx, dz)) arena(dx, dz, r, h, col);
            else if (r < RING) ring(dx, dz, h, col);
            else city(dx, dz, r, h, col);
            if (!tower(dx, dz) && r >= J_ARENA + 2) roots(dx, dz, ceil, col);
            return true;
        }

        /** The tower under the temple: the well from the temple continues as a spiral staircase to the floor, around it a wall with passages at the bottom. */
        private void tower(int dx, int dz, int ceil, int h, int[] col) {
            put(col, J_FL, BRICK, 0);
            int k = ringIndex(dx, dz);
            if (dx == 0 && dz == 0) { // a pillar in the middle: so you do not fall down the well to the floor
                for (int y = J_FL + 1; y <= ceil; y++) put(col, y, BRICK, 3);
                for (int y = ceil + 1; y <= ceil + 3; y++) put(col, y, AIR, 0); // the bottom of the well from the temple
                return;
            }
            if (k >= 0) { // steps along the ring
                for (int y = J_FL + 1; y <= ceil + 3; y++) put(col, y, (y & 7) == k ? STEP : AIR, 0);
                return;
            }
            for (int y = J_FL + 1; y <= ceil; y++) {
                boolean door = y <= J_FL + 3 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1);
                boolean broken = hash(dx * 3 + y, dz * 7) % 17 == 0;
                if (door || broken) continue;
                put(col, y, BRICK, hash(dx + y, dz - y) % 3);
            }
        }

        /**
         * The Ancient Dramcryx arena in the center: a paved circle, a ring of broken columns, a dais with an altar in the north, lanterns;
         * along the edge a collapsed fence with openings to the four main streets.
         */
        private void arena(int dx, int dz, double r, int h, int[] col) {
            put(col, J_FL, BRICK, h % 3);
            if (ringDist(dx, dz, 13, 12, 0.13) < 0.8) {
                int top = J_FL + 3 + h % 7;
                for (int y = J_FL + 1; y <= top; y++) put(col, y, BRICK, (y + h) % 3);
                put(col, top + 1, LEAVES, 0);
            }
            if (dz == -WorldGenJungleLair.ALTAR_DZ && Math.abs(dx) <= 1) {
                put(col, J_FL + 1, BRICK, 3);
                if (dx == 0) put(col, J_FL + 2, ALTAR, 0);
            }
            if (Math.abs(dx) == 10 && Math.abs(dz) == 10) {
                put(col, J_FL + 1, FENCE, 0);
                put(col, J_FL + 2, FENCE, 0);
                put(col, J_FL + 3, GLOW, 0);
            }
            if (r >= J_ARENA - 1.5 && Math.abs(dx) > 2 && Math.abs(dz) > 2) { // the fence, in places collapsed to the base
                int top = J_FL + 1 + h % 3 - (hash(dx * 7, dz * 3) % 5 == 0 ? 1 : 0);
                for (int y = J_FL + 1; y <= top; y++) put(col, y, h % 4 == 0 ? MOSSY : BRICK, (y + h) % 3);
                if (top > J_FL && h % 3 == 0) put(col, top + 1, LEAVES, 0);
            }
        }

        /** The ring boulevard between the arena and the blocks: paving, lanterns every 30 degrees, rare ferns. */
        private void ring(int dx, int dz, int h, int[] col) {
            put(col, J_FL, h % 4 == 0 ? GRAVEL : h % 4 == 1 ? MOSSY : BRICK, 2);
            if (ringDist(dx, dz, RING - 1, 12, Math.PI / 12) < 0.6) {
                put(col, J_FL + 1, FENCE, 0);
                put(col, J_FL + 2, FENCE, 0);
                put(col, J_FL + 3, GLOW, 0);
            } else if (h % 29 == 0) fern(col);
        }

        /** A block: a street, a house, ruins or a garden. */
        private void city(int dx, int dz, double r, int h, int[] col) {
            int ix = Math.floorDiv(dx, 16), iz = Math.floorDiv(dz, 16), lx = dx - ix * 16, lz = dz - iz * 16;
            boolean streetX = lx == 15 || lx <= 1, streetZ = lz == 15 || lz <= 1;
            if (streetX || streetZ) {
                put(col, J_FL, h % 4 == 0 ? GRAVEL : h % 4 == 1 ? MOSSY : BRICK, 2);
                // lanterns at every second intersection
                if (lx == 0 && lz == 0 && ((ix + iz) & 1) == 0 && r < J_R - 6) {
                    put(col, J_FL + 1, FENCE, 0);
                    put(col, J_FL + 2, FENCE, 0);
                    put(col, J_FL + 3, GLOW, 0);
                } else if (h % 23 == 0) fern(col); // broke through the paving
                return;
            }
            int lh = hash(ix * 7919 + 13, iz * 104729 - 7);
            // the block is entirely inside the dome, beyond the boulevard and not by the tower
            double far = Math.max(Math.max(Math.hypot(ix * 16, iz * 16), Math.hypot(ix * 16 + 16, iz * 16)), Math.max(Math.hypot(ix * 16, iz * 16 + 16), Math.hypot(ix * 16 + 16, iz * 16 + 16)));
            double nx = Math.max(ix * 16, Math.min(0, ix * 16 + 16)), nz = Math.max(iz * 16, Math.min(0, iz * 16 + 16));
            boolean near = Math.hypot(nx, nz) < RING + 2 || Math.abs(ix * 16 + 8 + J_TOWER_DX) < 16 && Math.abs(iz * 16 + 8) < 16;
            int kind = far > J_R - 4 || near ? 9 : lh % 10;
            int a0 = 2 + (lh >> 4) % 3, a1 = 14 - (lh >> 6) % 3, b0 = 2 + (lh >> 8) % 3, b1 = 14 - (lh >> 10) % 3;
            boolean home = ix == HOME_IX && iz == HOME_IZ;
            if (home) {
                kind = 0;
                a0 = b0 = 3;
                a1 = b1 = 13;
            }
            boolean inRect = lx >= a0 && lx <= a1 && lz >= b0 && lz <= b1, edge = inRect && (lx == a0 || lx == a1 || lz == b0 || lz == b1);
            if (kind <= 4 && inRect) { // house
                int height = home ? 6 : Math.min(5 + (lh >> 12) % 5, jungleCeil(far) - J_FL - 3);
                put(col, J_FL, MOSSY, 0);
                if (!edge) {
                    if (home) put(col, J_FL + height, BRICK, 1); // a special house - under a whole roof
                    else if ((lh >> 15) % 3 == 0 && h % 10 < 7) put(col, J_FL + height, LEAVES, 0); // overgrown roof
                    else if (h % 11 == 0) put(col, J_FL + 1, LEAVES, 0);
                    return;
                }
                int top = J_FL + height - (!home && h % 4 == 0 ? 1 + (h >> 3) % 3 : 0); // collapsed wall edge
                int mid = (a0 + a1) / 2;
                for (int y = J_FL + 1; y <= top; y++) {
                    boolean door = lz == b0 && (lx == mid || lx == mid + 1) && y <= J_FL + 2;
                    boolean corner = (lx == a0 || lx == a1) && (lz == b0 || lz == b1);
                    boolean window = y == J_FL + 3 && (lx + lz) % 3 == 0 && !corner;
                    if (door || window) continue;
                    put(col, y, h % 5 == 0 ? MOSSY : BRICK, (y + h) % 3);
                }
                if (h % 3 == 0) put(col, top + 1, LEAVES, 0); // overgrown wall top
            } else if (kind <= 7 && edge) { // ruins: low wall fragments
                int top = J_FL + (h % 4 == 0 ? 0 : 1 + h % 3);
                for (int y = J_FL + 1; y <= top; y++) put(col, y, h % 2 == 0 ? COBBLE : MOSSY, 0);
            } else { // garden and wasteland
                if (h % 13 == 0) {
                    put(col, J_FL + 1, LOG, 0);
                    put(col, J_FL + 2, LEAVES, 0);
                } else if (h % 5 == 0) put(col, J_FL + 1, LEAVES, 0);
                else if (h % 3 == 0) fern(col);
            }
        }

        /** A fern is always on grass (a garden floor with the same hash is laid with dirt). */
        private static void fern(int[] col) {
            put(col, J_FL, GRASS, 0);
            put(col, J_FL + 1, FERN, 0);
        }

        // ------------------------------------------------------------ roots from the vault

        static final int ROOT_CELL = 11;

        /**
         * A root from grid node (gx, gz) or null: {x, z of the base, vault, length, bend angle, offset, thickness at the vault, to the floor 0/1,
         * glowing tip 0/1}. The root grows from the vault in a flare, tapers downward and goes sideways in an arc; some reach the
         * floor and fan out there as buttresses - only deep in the blocks, streets and the boulevard stay clear.
         */
        static double[] root(int gx, int gz) {
            int ch = hash(gx * 37 + 11, gz * 23 + 5);
            if (ch % 3 == 0) return null;
            double px = gx * ROOT_CELL + 2 + ch % 7 + 0.5, pz = gz * ROOT_CELL + 2 + (ch >> 3) % 7 + 0.5, pr = Math.sqrt(px * px + pz * pz);
            if (pr > J_R - 6 || pr < J_ARENA + 10) return null;
            double tx = px + J_TOWER_DX;
            if (tx * tx + pz * pz < 14 * 14) return null;
            int ix = Math.floorDiv((int) Math.floor(px), 16), iz = Math.floorDiv((int) Math.floor(pz), 16);
            if (Math.abs(ix - HOME_IX) + Math.abs(iz - HOME_IZ) == 0) return null; // we do not pierce a whole house with a sofa
            int top = jungleCeil(pr);
            int lx = (int) Math.floor(px) - ix * 16, lz = (int) Math.floor(pz) - iz * 16;
            boolean floor = pr > RING + 6 && lx >= 6 && lx <= 10 && lz >= 6 && lz <= 10 && (ch >> 5) % 2 == 0;
            int len = floor ? top - J_FL : Math.min(5 + (ch >> 6) % 13, top - J_FL - 5);
            if (len < 3) return null;
            double thick = (ch >> 11) % 3 == 0 ? 1.9 : 1.2;
            return new double[] { px, pz, top, len, ((ch >> 14) % 360) * Math.PI / 180, floor ? 0.3 : 1.5 + (ch >> 9) % 3, thick, floor ? 1 : 0,
                !floor && (ch >> 17) % 4 == 0 ? 1 : 0 };
        }

        /** A root in cell (dx, y, dz) from the center: ROOT, LEAVES (leaves at the vault), GLOW (glowing tip) or -1. */
        static int rootAt(int dx, int y, int dz) {
            int cx0 = Math.floorDiv(dx, ROOT_CELL), cz0 = Math.floorDiv(dz, ROOT_CELL);
            for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
                double[] rt = root(cx0 + i, cz0 + k);
                if (rt == null) continue;
                int top = (int) rt[2], len = (int) rt[3];
                double t = top - y;
                if (t < -4 || t > len) continue; // above the base - the flare goes into the vault, where it is lower
                double tt = Math.max(0, t), f = tt / len;
                double off = rt[5] * f * f * len / 6 + Math.sin(tt * 0.45 + rt[4] * 3) * 0.6;
                double ox = rt[0] + Math.cos(rt[4]) * off, oz = rt[1] + Math.sin(rt[4]) * off;
                double d = Math.sqrt((dx + 0.5 - ox) * (dx + 0.5 - ox) + (dz + 0.5 - oz) * (dz + 0.5 - oz));
                double rad = rt[6] * (1 - 0.7 * f) + 0.35 + 1.6 * Math.max(0, 1 - tt / 3.0); // the flare at the vault
                if (rt[7] > 0) rad += 1.4 * Math.max(0, 1 - (len - tt) / 3.0); // buttresses at the floor
                if (rt[8] > 0 && tt >= len - 1 && d < 0.9) return GLOW;
                if (d <= rad) return ROOT;
                if (tt < 2 && d <= rad + 1.2 && hash(dx * 7 + y, dz * 13) % 4 == 0) return LEAVES;
            }
            return -1;
        }

        /** Column roots and vines on them: a vine holds to the root from the side and hangs below its end. */
        private void roots(int dx, int dz, int ceil, int[] col) {
            int[][] side = { { 1, 0, 8 }, { -1, 0, 2 }, { 0, 1, 1 }, { 0, -1, 4 } }; // the neighbor and the vine bit (it holds to it)
            int vine = 0, below = 0;
            for (int y = ceil; y > J_FL; y--) {
                int c = rootAt(dx, y, dz);
                if (c >= 0) {
                    if (c == ROOT || col[y * 2] == AIR) put(col, y, c, 0); // the root pierces roofs and walls
                    vine = 0;
                    continue;
                }
                if (col[y * 2] != AIR) {
                    vine = 0;
                    continue;
                }
                int bits = 0;
                for (int[] sd : side) if (hash(dx * 31 + sd[2], dz * 17 + y / 3) % 3 == 0 && rootAt(dx + sd[0], y, dz + sd[1]) == ROOT) bits |= sd[2];
                if (bits != 0) {
                    vine = bits;
                    below = 2 + hash(dx, dz * 3) % 4;
                } else if (vine != 0 && below-- > 0) bits = vine; // hangs below the root
                else vine = 0;
                if (bits != 0) put(col, y, VINE, bits);
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            // a special house: a Divan RPG by the far wall, facing the door (north)
            int sx = HOME_IX * 16 + 7, sz = HOME_IZ * 16 + 12;
            if (com.divinerpgextended.Config.easterEggs) place(w, cx, cz, x0, z0, sx, J_FL + 1, sz, ModBlocks.sofa, 2);
            if (com.divinerpgextended.Config.easterEggs) place(w, cx, cz, x0, z0, sx + 1, J_FL + 1, sz, ModBlocks.sofa, 2 | com.divinerpgextended.easter.BlockSofa.SECOND);
            // chests in houses: in the corner of every third house
            for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
                int dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz >= (J_R - 4) * (J_R - 4)) continue;
                int ix = Math.floorDiv(dx, 16), iz = Math.floorDiv(dz, 16), lx = dx - ix * 16, lz = dz - iz * 16;
                int lh = hash(ix * 7919 + 13, iz * 104729 - 7);
                int a0 = 2 + (lh >> 4) % 3, b0 = 2 + (lh >> 8) % 3;
                if (lx != a0 + 1 || lz != b0 + 1 || lh % 10 > 4 || (lh >> 18) % 3 != 0) continue;
                if (w.getBlock(x, J_FL, z) != Blocks.mossy_cobblestone || !w.isAirBlock(x, J_FL + 1, z)) continue; // the house is built here
                LairLoot.chest(w, new Random(lh), x, J_FL + 1, z, false);
            }
        }
    }
}
