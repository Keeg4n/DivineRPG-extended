package com.divinerpgextended.world.twilight;

import java.util.Random;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Boss lairs of the twilight dimensions - they hang above the islands on their own patch of land, the spot is computed from the seed (one per
 * nestSpacing x nestSpacing square in each world).
 * <ul>
 * <li>Sacred Grove of the Great Cadillion (Eden, Wildwood, Apalachia, Skythern): an island with six giant world
 * trees, and under the vault of their crowns a clearing with a colonnade of an ancient sanctum, a spring and a platform with the altar in the north. The Call of the
 * cadillion works only on this altar; the boss stands in the center of the clearing.</li>
 * <li>Mortum arenas ({@link MortumArenas}): each boss has its own (the Demonic Circle belongs to the Twilight Demon), the crystal
 * works only on its own. In Mortum the squares are half the size - there are six kinds of arenas.</li>
 * </ul>
 */
public class WorldGenNests implements IWorldGenerator {

    /** Top block of the floor (you stand at height TOP + 1). */
    public static final int TOP = 140;
    /** Mortum is a cave world 128 high with a bedrock ceiling (like the Nether): the Demonic Circle stands in a hall inside the cave. */
    public static final int MORTUM_TOP = MortumWorld.LEVEL + 4;

    /** Height of the arena floor in this dimension. */
    public static int top(int dim) {
        return TwilightWorld.of(dim) == TwilightWorld.MORTUM ? MORTUM_TOP : TOP;
    }
    public static final int NEST_R = 34, CIRCLE_R = 22;
    /** The grove crowns extend past the edge of the island. */
    static final int CANOPY_REACH = 12;
    /** The nest altar: ALTAR_DZ to the north of the center, on the platform (TOP + 2). */
    public static final int ALTAR_DZ = 10;
    /** Inside the circle (horizontally) the Mortum crystals work. */
    public static final int CIRCLE_ZONE = 18;

    // ---------------------------------------------------------------- location

    public static int radius(int dim) {
        return TwilightWorld.of(dim) == TwilightWorld.MORTUM ? MORTUM_R : NEST_R + CANOPY_REACH;
    }

    /** Mortum arenas with tunnels take up to MORTUM_R from the center. */
    public static final int MORTUM_R = 52;

    /** Side of the square that gets one lair: in Mortum it is half the size (six kinds of arenas - they are found closer together). */
    public static int spacing(int dim) {
        int g = Config.nestSpacing;
        if (g <= 0) return g;
        return TwilightWorld.of(dim) == TwilightWorld.MORTUM ? Math.max(160, g / 2) : g;
    }

    /** Center of the lair in area (rx, rz) of world dim, or null if lairs are disabled. */
    public static int[] center(long seed, int dim, int rx, int rz) {
        int g = spacing(dim);
        if (g <= 0) return null;
        Random r = new Random(seed ^ (rx * 341873128712L + rz * 132897987541L) ^ (dim * 0x9E3779B97F4A7C15L) ^ 0x4E35L);
        int margin = Math.min(g / 3, NEST_R + 40);
        int x = rx * g + margin + r.nextInt(Math.max(1, g - 2 * margin)), z = rz * g + margin + r.nextInt(Math.max(1, g - 2 * margin));
        if (TwilightWorld.of(dim) == TwilightWorld.MORTUM) {
            int ext = MortumArenas.extent(MortumArenas.type(seed, rx, rz)), m2 = ext + 6; // it can be shifted toward the river across almost the whole square
            int[] near = nearRiver(seed, x, z, rx * g + m2, rz * g + m2, rx * g + g - m2, rz * g + g - m2, ext);
            if (near != null) {
                x = near[0];
                z = near[1];
            }
        }
        return new int[] { x, top(dim), z };
    }

    private static final java.util.Map<Long, int[]> RIVER_SPOT = new java.util.concurrent.ConcurrentHashMap<Long, int[]>();

    /**
     * A Mortum arena sits by Lethe: the river point nearest to (x, z) in the square, from it ext + 3 blocks sideways to where there is no river
     * within the circle ext (arena with slopes) - the riverbed runs right at the edge of the arena. {x, z, river point x, z}; null - no river nearby
     * (the arena stays where it was).
     */
    static int[] nearRiver(long seed, int x, int z, int x0, int z0, int x1, int z1, int ext) {
        long key = seed ^ (long) x * 0x9E3779B1L ^ (long) z * 0x85EBCA6BL ^ (long) ext * 0x632BE5ABL;
        int[] cached = RIVER_SPOT.get(key);
        if (cached != null) return cached.length == 0 ? null : cached;
        // the middle of the riverbed around, nearest points first; from each we try 16 directions until the circle ext around the arena turns out dry
        java.util.List<int[]> pts = new java.util.ArrayList<int[]>();
        for (int i = -100; i <= 100; i += 4) for (int k = -100; k <= 100; k += 4)
            if (Math.abs(MortumWorld.riverNoise(seed, x + i, z + k)) < MortumWorld.WIDTH * 0.3) pts.add(new int[] { x + i, z + k, i * i + k * k });
        java.util.Collections.sort(pts, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return a[2] < b[2] ? -1 : a[2] > b[2] ? 1 : 0;
            }
        });
        int[] spot = null;
        int d = ext + 8; // from the middle of the riverbed: the water's edge is a couple of blocks from the edge of the arena
        for (int p = 0; p < pts.size() && p < 120 && spot == null; p++) {
            int[] best = pts.get(p);
            for (int a = 0; a < 16 && spot == null; a++) {
                double ang = a * Math.PI / 8;
                int cx = best[0] + (int) Math.round(Math.cos(ang) * d), cz = best[1] + (int) Math.round(Math.sin(ang) * d);
                if (cx < x0 || cz < z0 || cx > x1 || cz > z1) continue;
                boolean dry = !MortumWorld.river(seed, cx, cz);
                for (int r = 2; r <= ext + 2 && dry; r += 2) for (int b = 0; b < 96 && dry; b++) {
                    double bb = b * Math.PI / 48;
                    if (MortumWorld.river(seed, cx + (int) (Math.cos(bb) * r), cz + (int) (Math.sin(bb) * r))) dry = false;
                }
                if (dry) spot = new int[] { cx, cz, best[0], best[1] };
            }
        }
        RIVER_SPOT.put(key, spot == null ? new int[0] : spot);
        return spot;
    }

    /** The Lethe point by which the Mortum arena of square (rx, rz) stands: {x, z}; null - the arena is not by the river. */
    public static int[] riverPoint(long seed, int dim, int rx, int rz) {
        int g = spacing(dim);
        if (g <= 0) return null;
        Random r = new Random(seed ^ (rx * 341873128712L + rz * 132897987541L) ^ (dim * 0x9E3779B97F4A7C15L) ^ 0x4E35L);
        int margin = Math.min(g / 3, NEST_R + 40);
        int x = rx * g + margin + r.nextInt(Math.max(1, g - 2 * margin)), z = rz * g + margin + r.nextInt(Math.max(1, g - 2 * margin));
        int ext = MortumArenas.extent(MortumArenas.type(seed, rx, rz)), m2 = ext + 6;
        int[] near = nearRiver(seed, x, z, rx * g + m2, rz * g + m2, rx * g + g - m2, rz * g + g - m2, ext);
        return near == null ? null : new int[] { near[2], near[3] };
    }

    /** The lair of this world nearest to the point (its own and neighboring areas). */
    public static int[] nearest(long seed, int dim, double x, double z) {
        int g = spacing(dim);
        if (g <= 0) return null;
        int rx = (int) Math.floor(x / g), rz = (int) Math.floor(z / g);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] c = center(seed, dim, rx + i, rz + k);
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** A point inside the Mortum Demonic Circle (the Twilight Demon crystal works there). */
    public static boolean inCircle(World w, double x, double y, double z) {
        return MortumArenas.inArena(w, MortumArenas.Type.DEMON, x, y, z);
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        TwilightWorld tw = TwilightWorld.of(world.provider.dimensionId);
        if (tw == null || Config.nestSpacing <= 0) return;
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8; // the population area of this chunk
        int[] c = nearest(world.getSeed(), world.provider.dimensionId, x0 + 8, z0 + 8);
        int r = radius(world.provider.dimensionId);
        if (c == null || Math.abs(c[0] - (x0 + 8)) > r + 16 || Math.abs(c[2] - (z0 + 8)) > r + 16) return;
        ColumnStructure s = tw == TwilightWorld.MORTUM ? MortumArenas.shape(MortumArenas.typeAt(world, c)) : shape(tw, c);
        s.buildSlice(world, c[0], c[2], x0, z0);
        int top = top(world.provider.dimensionId);
        s.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, top + 1, tw == TwilightWorld.SKYTHERN ? 255 : Math.min(250, top + 100));
    }

    /**
     * Above the Skythern temple island the sky is open, and the surface there is the temple itself: Skythern trees (they are planted at the height of
     * the surface) would grow on the colonnade and the cornice. They don't belong here.
     */
    public static boolean templeIsland(World w, int x, int z) {
        if (TwilightWorld.of(w.provider.dimensionId) != TwilightWorld.SKYTHERN) return false;
        int[] c = nearest(w.getSeed(), w.provider.dimensionId, x, z);
        return c != null && (x - c[0]) * (x - c[0]) + (z - c[2]) * (z - c[2]) <= (Temple.R_ISLAND + 10) * (Temple.R_ISLAND + 10);
    }

    public static ColumnStructure shape(TwilightWorld tw, int[] c) {
        if (tw == TwilightWorld.SKYTHERN) return new Temple(c[0], c[2]);
        if (tw == TwilightWorld.WILDWOOD) return new Henge(c[0], c[2]);
        if (tw == TwilightWorld.APALACHIA) return new Mound(c[0], c[2]);
        return tw == TwilightWorld.MORTUM ? new Circle() : new Nest(tw, c[0], c[2]);
    }

    // ---------------------------------------------------------------- common

    static final int STONE = 1, DIRT = 2, GRASS = 3, LOG = 4, LEAVES = 5, PLANKS = 6, GEM = 7, GLOW = 8, ALTAR = 9, WATER = 10, ORE = 11;

    static Block palette(TwilightWorld w, int code) {
        switch (code) {
            case STONE: return TwilightBlocks.twilightStone;
            case DIRT: return w.dirt();
            case GRASS: return w.grass();
            case LOG: return w.log();
            case LEAVES: return w.leaves();
            case PLANKS: return w.planks();
            case GEM: return w.gemBlock();
            case GLOW: return w.lamp();
            case ALTAR: return ModBlocks.nestAltar;
            case ORE: return w.ore();
            default: return Blocks.air;
        }
    }

    /** A lamp of any twilight dimension (for recalculating the light around). */
    static boolean isLamp(Block b) {
        for (TwilightWorld w : TwilightWorld.values()) if (w.isLamp(b)) return true;
        return false;
    }

    /** The bowl under the floor: world dirt on top, stone below, deeper toward the center; a few hanging roots. */
    static void bowl(int dx, int dz, double r, double R, int[] col, boolean roots) {
        bowl(dx, dz, r, R, col, roots, TOP);
    }

    static void bowl(int dx, int dz, double r, double R, int[] col, boolean roots, int TOP) {
        int bottom = TOP - 2 - (int) (13 * Math.sqrt(Math.max(0, 1 - (r / R) * (r / R))));
        for (int y = bottom; y <= TOP - 3; y++) ColumnStructure.put(col, y, STONE, 0);
        ColumnStructure.put(col, TOP - 2, DIRT, 0);
        ColumnStructure.put(col, TOP - 1, DIRT, 0);
        if (roots && r < R - 4 && ColumnStructure.hash(dx, dz) % 17 == 0) {
            int len = 2 + ColumnStructure.hash(dz, dx) % 7;
            for (int y = bottom - len; y < bottom; y++) ColumnStructure.put(col, y, LOG, 0);
        }
    }

    /** The structure of the world's sacred grove (for checks and rendering). */
    public static ColumnStructure grove(TwilightWorld w, int cx, int cz) {
        return new Nest(w, cx, cz);
    }

    /** Nearest center of a ring item (radius ring, count pieces) - the distance to it. */
    static double ringDist(double dx, double dz, double ring, int count, double phase) {
        double a = Math.atan2(dz, dx) - phase, step = Math.PI * 2 / count;
        double k = Math.round(a / step);
        double ca = k * step + phase;
        double px = Math.cos(ca) * ring, pz = Math.sin(ca) * ring;
        return Math.sqrt((dx - px) * (dx - px) + (dz - pz) * (dz - pz));
    }

    /** Passage in the side: the four cardinal directions, about 14 degrees each. */
    static boolean gate(int dx, int dz) {
        double ang = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 90;
        return ang < 7 || ang > 83;
    }

    // ---------------------------------------------------------------- nest

    /**
     * Sacred Grove of the Great Cadillion: an island with six giant world trees in a circle (visible from afar), and under
     * the vault of their crowns a clearing with fragments of the ancient sanctum colonnade, entwined with roots, a spring and a platform with
     * the altar. From below the island is wrapped in tree roots ({@link #underRoots}).
     */
    static class Nest extends ColumnStructure {

        protected final TwilightWorld w;
        protected final int salt;
        /** The side from which the piece is torn away from the land (a sheer cliff). */
        protected final double torn;
        private static final int TREES = 6, TRUNK_RING = 24, CANOPY_R = 13, CANOPY_H = 6;
        private final double[] tx = new double[TREES], tz = new double[TREES];
        private final int[] th = new int[TREES];

        Nest(TwilightWorld w, int cx, int cz) {
            this.w = w;
            salt = hash(cx, cz) % 1000;
            torn = salt / 1000.0 * Math.PI * 2;
            for (int i = 0; i < TREES; i++) {
                double a = Math.PI * 2 * i / TREES + (hash(i, salt) % 100) / 100.0 * 0.5;
                double rr = TRUNK_RING + hash(salt, i) % 4 - 1;
                tx[i] = Math.cos(a) * rr;
                tz[i] = Math.sin(a) * rr;
                th[i] = 36 + hash(i * 7, salt) % 15;
            }
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? w.lampMeta() : meta;
        }

        @Override
        protected Block block(int code, int meta) {
            if (code == WATER) return Blocks.water;
            return palette(w, code);
        }

        @Override
        protected boolean lit(Block b) {
            return isLamp(b) || b == ModBlocks.nestAltar;
        }

        /** Smooth noise 0..1 over cells cell (bilinear over the corner hashes). */
        static double noise(int x, int z, int cell, int salt) {
            int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
            double fx = (x - gx * cell) / (double) cell, fz = (z - gz * cell) / (double) cell;
            fx = fx * fx * (3 - 2 * fx);
            fz = fz * fz * (3 - 2 * fz);
            double a = hash(gx + salt, gz) % 1000 / 1000.0, b = hash(gx + 1 + salt, gz) % 1000 / 1000.0;
            double c = hash(gx + salt, gz + 1) % 1000 / 1000.0, d = hash(gx + 1 + salt, gz + 1) % 1000 / 1000.0;
            return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
        }

        static final int ROOTS = 5;

        /**
         * Tree roots under the island: from the base of each tree five roots creep along the underside of the island toward the center, winding and
         * tapering, - they wrap the island from below; at the ends there are short hanging tips.
         */
        boolean underRoots(int dx, int dz, int bottom, int[] col) {
            boolean any = false;
            for (int i = 0; i < TREES; i++) for (int k = 0; k < ROOTS; k++) {
                double base = Math.atan2(-tz[i], -tx[i]); // toward the center of the island
                double a = base + (k - (ROOTS - 1) / 2.0) * 0.55 + (hash(i * 11 + k, salt) % 100 / 100.0 - 0.5) * 0.3;
                double len = 10 + hash(i * 5 + k, salt + 3) % 9, wig = (hash(k * 3 + i, salt) % 100 / 100.0 - 0.5) * 3;
                double best = 99, bt = 0;
                for (int n = 0; n <= 24; n++) {
                    double t = n / 24.0, along = t * len, side = Math.sin(t * Math.PI * 1.5) * wig;
                    double px = tx[i] + Math.cos(a) * along - Math.sin(a) * side, pz = tz[i] + Math.sin(a) * along + Math.cos(a) * side;
                    double dd = Math.hypot(dx + 0.5 - px, dz + 0.5 - pz);
                    if (dd < best) {
                        best = dd;
                        bt = t;
                    }
                }
                double thick = 1.4 * (1 - bt) + 0.5;
                if (best > thick) continue;
                put(col, bottom - 1, LOG, 0);
                if (bt < 0.35 && best < thick * 0.6) put(col, bottom - 2, LOG, 0); // the root is thicker near the trunk
                if (bt > 0.92 && best < 0.7) for (int y = bottom - 4 - hash(i, k) % 3; y < bottom - 1; y++) put(col, y, LOG, 0); // tip
                any = true;
            }
            return any;
        }

        /** The clearing under the crowns: a stone ring path, a colonnade, a spring, a platform with the altar, light. */
        protected void glade(int dx, int dz, double r, int[] col) {
            // clearing: a stone ring path, a colonnade, a spring, a platform with the altar
            if (r > 11.5 && r < 13.5 && hash(dz, dx) % 4 != 0) put(col, TOP, STONE, 0);
            double colDist = ringDist(dx, dz, 15, 8, Math.PI / 8 + salt % 7 * 0.1);
            if (colDist < 1.0) {
                int k = (int) Math.round((Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2 / 8)) % 8;
                int ch = 3 + hash(k, salt) % 8; // broken off differently
                for (int y = TOP + 1; y <= TOP + ch; y++) put(col, y, STONE, 0);
                if (ch >= 8) put(col, TOP + ch + 1, GEM, 0);
            } else if (colDist < 1.9) {
                int k = (int) Math.round((Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2 / 8)) % 8;
                // roots wrap the column on one side
                if (hash(dx, dz + salt) % 3 == 0) for (int y = TOP + 1; y <= TOP + 2 + hash(k, 3) % 4; y++) put(col, y, LOG, 0);
                else if (hash(dz, dx + salt) % 5 == 0) put(col, TOP + 1, STONE, 0); // fragment
            }
            double sp = Math.sqrt(dx * dx + (dz - 7) * (dz - 7));
            if (sp < 2.3) put(col, TOP, WATER, 0);
            else if (sp < 3.3) put(col, TOP, STONE, 0);
            if (sp < 3.3) put(col, TOP - 1, STONE, 0);
            double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ) * (dz + ALTAR_DZ));
            if (ad <= 2.5) put(col, TOP + 1, GEM, 0);
            if (dx == 0 && dz == -ALTAR_DZ) put(col, TOP + 2, ALTAR, 0);
            if (r < 1.2) put(col, TOP, GLOW, 0);
            if (r > 6 && r < 30 && hash(dx * 3, dz * 5 + salt) % 97 == 0) put(col, TOP, GLOW, 0);
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            double rOut = NEST_R - 1 + noise(dx, dz, 7, 11 + salt) * 3;
            boolean any = false;
            int bottom = TOP; // the underside of the island in this column
            if (r <= rOut) {
                any = true;
                tornBase(dx, dz, r, rOut, torn, salt, col, 1.0);
                for (int y = Math.max(1, TOP - 80); y < TOP; y++) if (col[y * 2] != KEEP && col[y * 2] != AIR) {
                    bottom = y;
                    break;
                }
                put(col, TOP, GRASS, 0);
                for (int y = TOP + 1; y <= TOP + 60; y++) put(col, y, AIR, 0);
                glade(dx, dz, r, col);
                any |= underRoots(dx, dz, bottom, col);
            }
            // trees: trunks lean toward the clearing, buttress roots at the base, roots going down under the island;
            // crowns are wide and flat, shifted toward the center - they join into a ragged vault over the clearing
            for (int i = 0; i < TREES; i++) {
                int top = TOP + th[i];
                double len = Math.sqrt(tx[i] * tx[i] + tz[i] * tz[i]), ix = -tx[i] / len, iz = -tz[i] / len;
                double baseD = Math.sqrt((dx - tx[i]) * (dx - tx[i]) + (dz - tz[i]) * (dz - tz[i]));
                if (baseD > CANOPY_R + 10) continue;
                for (int y = TOP - 30 - 18; y <= top + CANOPY_H + 4; y++) {
                    double lean = Math.max(0, y - TOP) / (double) th[i] * 5; // toward the top the trunk shifts 5 blocks toward the center
                    double cx = tx[i] + ix * lean, cz = tz[i] + iz * lean;
                    double ddx = dx - cx, ddz = dz - cz, d = Math.sqrt(ddx * ddx + ddz * ddz);
                    if (y <= top + 2) {
                        double rad = 2.8;
                        if (y > TOP && y <= TOP + 5) {
                            // buttress roots along the diagonals, wider lower down
                            double ang = Math.atan2(ddz, ddx);
                            double ridge = Math.abs(Math.cos(2 * (ang - salt % 10 * 0.1)));
                            rad += (TOP + 6 - y) * 0.9 * ridge * ridge;
                        }
                        // the trunk goes into the island, but not below its underside (it used to stick out from below like a column, like a stalactite)
                        if (y <= TOP && d <= 2.8) {
                            if (y >= bottom) { put(col, y, LOG, 0); any = true; }
                            continue;
                        }
                        if (y > TOP && d <= rad) { put(col, y, LOG, 0); any = true; continue; }
                    }
                    // branches on two levels, tilted upward, on all four sides
                    for (int lvl = 0; lvl < 2; lvl++) {
                        int by = top - 6 + lvl * 4;
                        for (int k = 0; k < 4; k++) {
                            double ba = Math.PI / 2 * k + lvl * Math.PI / 4 + salt % 10 * 0.2;
                            double bx = Math.cos(ba), bz = Math.sin(ba);
                            double along = ddx * bx + ddz * bz, across = Math.abs(-ddx * bz + ddz * bx);
                            if (along >= 2 && along <= 8 && across < 0.7 && y == by + (int) (along * 0.4)) {
                                put(col, y, LOG, Math.abs(bx) > Math.abs(bz) ? 4 : 8);
                                any = true;
                            }
                        }
                    }
                    if (y < top - CANOPY_H + 2 || col[y * 2] == LOG) continue;
                    // crown: a main flat tier shifted toward the center of the grove, and a small upper one
                    double mx = dx - (cx + ix * 4), mz = dz - (cz + iz * 4);
                    double e1 = (mx * mx + mz * mz) / (double) (CANOPY_R * CANOPY_R) + ((y - top) / 5.0) * ((y - top) / 5.0);
                    double e2 = (ddx * ddx + ddz * ddz) / 36.0 + ((y - top - 6) / 4.0) * ((y - top - 6) / 4.0);
                    double e = Math.min(e1, e2);
                    if (e > 1) continue;
                    if (e > 0.7 && hash(dx * 7 + y, dz * 3 - y + salt) % 4 == 0) continue; // ragged edge
                    put(col, y, hash(dx + y * 13, dz + salt) % 71 == 0 ? GLOW : LEAVES, 0);
                    any = true;
                }
            }
            return any;
        }
    }

    // ---------------------------------------------------------------- common to the arenas

    /**
     * The underside of the island - a piece of land torn out of the world (as with the Eden grove): deep ragged stone with veins of dirt and
     * scattered ore; one side (torn) is a sheer cliff, on the other sides it narrows; long icicles and drips. Places
     * up to and including TOP - 1; scale - depth (1 - as in the grove).
     */
    static void tornBase(int dx, int dz, double r, double rOut, double torn, int salt, int[] col, double scale) {
        double t = Math.min(1, r / rOut), ang = Math.atan2(dz, dx);
        double side = Math.max(0, Math.cos(ang - torn));
        side = side * side;
        double base = 30 * (1 - Math.pow(t, 1.5)), cliff = 27 * (1 - Math.pow(t, 5));
        double depth = (base + (cliff - base) * side + Nest.noise(dx, dz, 6, 23 + salt) * 9 * Math.sqrt(1 - t)
            + Nest.noise(dx, dz, 2, 37 + salt) * 3) * scale;
        int bottom = TOP - 1 - (int) depth;
        for (int y = bottom; y < TOP; y++) {
            int k = ColumnStructure.hash(dx * 31 + y, dz - y * 7 + salt) % 100;
            ColumnStructure.put(col, y, y >= TOP - 3 ? DIRT : k < 18 ? DIRT : k < 21 ? ORE : STONE, 0);
        }
        int h = ColumnStructure.hash(dx + salt, dz);
        double drip = Nest.noise(dx, dz, 3, 99 + salt);
        if (drip > 0.74 && t < 0.9) {
            int len = (int) (((drip - 0.74) * 50 + 2) * scale); // wide drips
            for (int y = bottom - len; y < bottom; y++) ColumnStructure.put(col, y, STONE, 0);
        } else if (t < 0.92 && h % 11 == 0) {
            int len = (int) ((2 + h / 11 % 9) * scale); // icicles
            for (int y = bottom - len; y < bottom; y++) ColumnStructure.put(col, y, STONE, 0);
        }
    }

    /** Outline of the arena island: large lobes and fine raggedness so that the edge is not an even circle. */
    static double outline(int dx, int dz, double R, int salt) {
        return R + (Nest.noise(dx, dz, 11, 19 + salt) - 0.5) * 12 + Nest.noise(dx, dz, 4, 53 + salt) * 4;
    }

    /**
     * Island edge: from rIn to the drop-off the land slopes gently down in uneven ledges, along the edge there are stone outcrops and
     * scree. Rewrites the top of the column (up to TOP) over tornBase; true - the column is in the edge zone (structures don't belong there).
     */
    static boolean slope(int dx, int dz, double r, double rIn, double rOut, int salt, int[] col, int grass) {
        if (r <= rIn) return false;
        double e = Math.min(1, (r - rIn) / Math.max(1, rOut - rIn));
        // ledges: the descent goes in steps of 1-2 blocks, the step height varies with noise
        double drop = Math.pow(e, 1.4) * 7 + Nest.noise(dx, dz, 5, 61 + salt) * 3 * e;
        int top = TOP - (int) Math.round(drop);
        for (int y = top + 1; y <= TOP; y++) ColumnStructure.put(col, y, ColumnStructure.AIR, 0);
        ColumnStructure.put(col, top, grass, 0);
        // stone outcrops and scree on the slope
        double rock = Nest.noise(dx, dz, 3, 71 + salt);
        if (rock > 0.74) {
            int hgt = 1 + (int) ((rock - 0.74) * 14);
            for (int y = top; y <= top + hgt; y++) ColumnStructure.put(col, y, STONE, 0);
        } else if (e > 0.75 && ColumnStructure.hash(dx * 5 + salt, dz) % 6 == 0) {
            ColumnStructure.put(col, top, STONE, 0); // scree at the drop-off
        }
        return true;
    }

    /** Springs of the Ancient Golem shrine: 4 of them at radius SPRING_R along the diagonals. */
    public static final int SPRING_R = 7;

    public static int[] spring(int i) {
        double a = Math.PI / 4 + Math.PI / 2 * i;
        return new int[] { (int) Math.round(Math.cos(a) * SPRING_R), (int) Math.round(Math.sin(a) * SPRING_R) };
    }

    /** Leash radius of the grove guardian: on the Apalachia mound the arena is wider (the palisade is farther out). */
    public static int leashRadius(TwilightWorld w) {
        return w == TwilightWorld.APALACHIA ? 28 : 20;
    }

    /** Altar offset from the center to the north: farther on the mound (the center is taken by the mound). */
    public static int altarDz(TwilightWorld w) {
        return w == TwilightWorld.APALACHIA ? MOUND_ALTAR : ALTAR_DZ;
    }

    // ---------------------------------------------------------------- shrine (Wildwood)

    /**
     * The Wildwood shrine - the Wildwood Ancient Golem arena: a Wildwood grove (an island torn out of the land, six giant trees,
     * a vault of crowns), and in the clearing instead of a colonnade - a ring of ten tall mossy menhirs with crossbeams and Wildwood
     * torches, six low sheltering stones (the golem's boulders crumble them), four springs ({@link #spring}), a stone path and
     * the altar.
     */
    static class Henge extends Nest {

        static final int MOSSY = 30, BRICK = 31, TORCH = 33;
        static final int RING = 17, MENHIRS = 10, COVER_R = 11, COVERS = 6;

        Henge(int cx, int cz) {
            super(TwilightWorld.WILDWOOD, cx, cz);
        }

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case MOSSY: return Blocks.mossy_cobblestone;
                case BRICK: return Blocks.stonebrick;
                case TORCH: return ModBlocks.worldTorch[TwilightWorld.WILDWOOD.ordinal()];
                default: return super.block(code, meta);
            }
        }

        @Override
        protected boolean lit(Block b) {
            return b == ModBlocks.worldTorch[TwilightWorld.WILDWOOD.ordinal()] || super.lit(b);
        }

        private int menhirH(int k) {
            return 8 + hash(k, salt) % 5;
        }

        @Override
        protected void glade(int dx, int dz, double r, int[] col) {
            int h = hash(dx + salt, dz);
            // patches of moss and a stone path in a circle
            if (Nest.noise(dx, dz, 3, 41 + salt) > 0.72) put(col, TOP, MOSSY, 0);
            if (Math.abs(r - 13.5) < 0.9 && h % 3 != 0) put(col, TOP, BRICK, h % 2 == 0 ? 1 : 2);
            // ring of menhirs with crossbeams over the pairs
            double step = Math.PI * 2 / MENHIRS, a = Math.atan2(dz, dx) + Math.PI;
            int k = (int) Math.round(a / step) % MENHIRS;
            double md = ringDist(dx, dz, RING, MENHIRS, 0);
            if (md < 1.5) {
                int mh = menhirH(k);
                for (int y = TOP + 1; y <= TOP + mh; y++) put(col, y, hash(dx * 3 + y, dz + salt) % 3 == 0 ? BRICK : MOSSY, hash(y, k) % 3 == 0 ? 1 : 0);
                if (md < 0.6) put(col, TOP + mh + 1, TORCH, 5);
            }
            int base = (int) Math.floor(a / step) % MENHIRS;
            double frac = a / step - Math.floor(a / step);
            if (base % 2 == 0 && Math.abs(r - RING) < 1.1 && frac > 0.04 && frac < 0.96) {
                int lh = Math.min(menhirH(base), menhirH((base + 1) % MENHIRS));
                put(col, TOP + lh, MOSSY, 0);
                put(col, TOP + lh + 1, MOSSY, 0);
            }
            // sheltering stones
            if (ringDist(dx, dz, COVER_R, COVERS, 0) < 1.5) {
                int ch = 3 + hash(dx, dz + salt) % 2;
                for (int y = TOP + 1; y <= TOP + ch; y++) put(col, y, MOSSY, 0);
            }
            // springs in a mossy setting
            for (int i = 0; i < 4; i++) {
                int[] sp = spring(i);
                double sd = Math.sqrt((dx - sp[0]) * (dx - sp[0]) + (dz - sp[1]) * (dz - sp[1]));
                if (sd < 1.6) {
                    put(col, TOP, WATER, 0);
                    put(col, TOP - 1, MOSSY, 0);
                } else if (sd < 2.6) put(col, TOP, MOSSY, 0);
            }
            // platform with the altar
            double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ) * (dz + ALTAR_DZ));
            if (ad <= 2.5) put(col, TOP + 1, BRICK, 3);
            if (dx == 0 && dz == -ALTAR_DZ) put(col, TOP + 2, ALTAR, 0);
        }
    }

    // ---------------------------------------------------------------- sacred mountain (Apalachia)

    /** The mound: tiers (half-width of the square and height of the top above the floor); 0 - the arena floor. */
    public static final int[] TIER_R = { 0, 13, 8 }, TIER_H = { 0, 5, 10 };
    public static final int MOUND_ALTAR = 20;

    /**
     * The Sacred Mountain of Apalachia - the Warrior King arena (inspired by the mounds of the Appalachian natives): a large summit island
     * torn out of the land, with a deep rocky underside; in the center a stepped earthen mound with a flat top and
     * log stairs on all sides (each new encounter the King starts one tier higher), around it totem poles,
     * fire pits, wigwam huts and a log palisade with gates and four watchtowers.
     */
    static class Mound extends ColumnStructure {

        static final int STAIRS = 40, FENCE = 41, WOOL = 42, LAMP = 43, BRICK = 44;
        static final int R_ISLAND = 44, PALISADE = 37, TOTEM_R = 21, TOTEMS = 8, HUT_R = 30, HUTS = 6;
        private final TwilightWorld w = TwilightWorld.APALACHIA;
        private final int salt;
        private final double torn;

        Mound(int cx, int cz) {
            salt = hash(cx, cz) % 1000;
            torn = salt / 1000.0 * Math.PI * 2;
        }

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case STAIRS: return TwilightBlocks.apalachiaStairs;
                case FENCE: return Blocks.fence;
                case WOOL: return Blocks.wool;
                case LAMP: return w.lamp();
                case BRICK: return Blocks.stonebrick;
                default: return palette(w, code);
            }
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == LAMP || code == GLOW ? w.lampMeta() : meta;
        }

        @Override
        protected boolean lit(Block b) {
            return isLamp(b) || b == ModBlocks.nestAltar;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            double rOut = outline(dx, dz, R_ISLAND + 2, salt);
            if (r > rOut) return false;
            tornBase(dx, dz, r, rOut, torn, salt, col, 1.5);
            put(col, TOP, GRASS, 0);
            for (int y = TOP + 1; y <= TOP + 40; y++) put(col, y, AIR, 0);
            if (slope(dx, dz, r, PALISADE + 2.5, rOut, salt, col, GRASS)) return true; // behind the palisade - a slope down to the drop-off
            int h = hash(dx + salt, dz);
            // the mound: earthen tiers with grass on top, log stairs on the cardinal directions
            int m = Math.max(Math.abs(dx), Math.abs(dz));
            int tier = 0;
            for (int i = 1; i < TIER_R.length; i++) if (m <= TIER_R[i]) tier = i;
            for (int y = TOP + 1; y <= TOP + TIER_H[tier]; y++) put(col, y, y == TOP + TIER_H[tier] ? GRASS : DIRT, 0);
            if (tier == TIER_R.length - 1 && m == TIER_R[tier] && h % 3 == 0) put(col, TOP + TIER_H[tier] + 1, FENCE, 0); // fence of the summit
            for (int i = 1; i < TIER_R.length; i++) {
                int rise = TIER_H[i] - TIER_H[i - 1];
                for (int st = 0; st < rise; st++) {
                    int dist = TIER_R[i] + 1 + st, y = TOP + TIER_H[i] - st;
                    if (Math.abs(dz) <= 1 && Math.abs(dx) == dist) put(col, y, STAIRS, dx > 0 ? 1 : 0);
                    if (Math.abs(dx) <= 1 && Math.abs(dz) == dist) put(col, y, STAIRS, dz > 0 ? 3 : 2);
                }
            }
            // a fire pit on the summit and four at the foot
            if (m <= 1 && tier == TIER_R.length - 1) put(col, TOP + TIER_H[tier], m == 0 ? LAMP : STONE, 0);
            for (int i = 0; i < 4; i++) {
                double fa = Math.PI / 4 + Math.PI / 2 * i;
                double fd = Math.sqrt(Math.pow(dx - Math.cos(fa) * 17, 2) + Math.pow(dz - Math.sin(fa) * 17, 2));
                if (fd < 0.7) put(col, TOP, LAMP, 0);
                else if (fd < 1.8) put(col, TOP + 1, STONE, 0);
            }
            // totem poles: a log with "faces" made of world stones and wings of planks at the top
            double td = ringDist(dx, dz, TOTEM_R, TOTEMS, Math.PI / TOTEMS);
            int tk = (int) Math.round((Math.atan2(dz, dx) - Math.PI / TOTEMS) / (Math.PI * 2 / TOTEMS));
            int th = 10 + hash(tk & 7, salt) % 5;
            if (td < 0.7) {
                for (int y = TOP + 1; y <= TOP + th; y++) put(col, y, (y - TOP) % 4 == 3 ? GEM : LOG, 0);
                put(col, TOP + th + 1, GEM, 0);
            } else if (td < 1.8 && Math.abs(r - TOTEM_R) < 0.6) {
                put(col, TOP + th - 1, PLANKS, 0); // wings
            }
            // wigwam huts: cones of hides (wool) on a frame
            double hd = ringDist(dx, dz, HUT_R, HUTS, Math.PI / HUTS + 0.2);
            if (hd < 3.2 && !gate(dx, dz)) {
                int hh = (int) Math.round(6 - hd * 1.6);
                for (int y = TOP + 1; y <= TOP + hh; y++) {
                    boolean shell = hd > 3.2 - 1.0 - (y - TOP) * 0.45;
                    if (shell || y == TOP + hh) put(col, y, WOOL, (hash(y, dx + dz) % 3 == 0) ? 12 : 1); // brown/orange
                }
                if (hd < 0.6) put(col, TOP + hh + 1, FENCE, 0); // poles
            }
            // palisade: logs in a row, pointed tops, gates on the cardinal directions
            if (Math.abs(r - PALISADE) < 0.8 && !gate(dx, dz)) {
                int ph = 5 + h % 2;
                for (int y = TOP + 1; y <= TOP + ph; y++) put(col, y, LOG, 0);
                put(col, TOP + ph + 1, FENCE, 0);
            }
            // watchtowers on the palisade diagonals
            for (int i = 0; i < 4; i++) {
                double va = Math.PI / 4 + Math.PI / 2 * i;
                int vx = (int) Math.round(Math.cos(va) * PALISADE), vz = (int) Math.round(Math.sin(va) * PALISADE);
                int ox = Math.abs(dx - vx), oz = Math.abs(dz - vz);
                if (ox <= 2 && oz <= 2) {
                    boolean corner = ox == 2 && oz == 2;
                    if (corner) for (int y = TOP + 1; y <= TOP + 12; y++) put(col, y, LOG, 0);
                    put(col, TOP + 9, PLANKS, 0); // platform
                    if ((ox == 2 || oz == 2) && !corner) put(col, TOP + 10, FENCE, 0);
                    put(col, TOP + 13, PLANKS, 0); // roof
                    if (ox == 0 && oz == 0) put(col, TOP + 10, LAMP, 0);
                }
            }
            // platform with the altar by the north gate of the mound
            double ad = Math.sqrt(dx * dx + (dz + MOUND_ALTAR) * (dz + MOUND_ALTAR));
            if (ad <= 2.5) put(col, TOP + 1, BRICK, 3);
            if (dx == 0 && dz == -MOUND_ALTAR) put(col, TOP + 2, ALTAR, 0);
            return true;
        }
    }

    // ---------------------------------------------------------------- wind temple (Skythern)

    /**
     * The Skythern Wind Temple - the Skythern Wind Lord arena instead of the grove: an island torn out of the land (as with the Eden grove), a marble
     * stylobate, a ring of sixteen thick columns as tall as Eden trees under a shared entablature (no roof - the fight is under the
     * sky), an altar on a platform by the north side and four pedestal columns for wind totems at different heights
     * ({@link com.divinerpgextended.entity.EntityWindLord#HEIGHTS}), with a staircase on the outer face and a cloud platform
     * on top. Around the stylobate there is a grass terrace so that a gust does not throw you off the island at once. Above the colonnade the upper
     * layers of the world are punched through with a shaft to the sky.
     */
    static class Temple extends ColumnStructure {

        static final int MARBLE = 20, LADDER = 21, CLOUD = 22, SLAB = 23;
        static final int R_ISLAND = 40, STYLO = 31, PERI = 26, COLS = 16, COL_H = 32;
        private final int salt;
        private final double torn;

        Temple(int cx, int cz) {
            salt = hash(cx, cz) % 1000;
            torn = salt / 1000.0 * Math.PI * 2;
        }

        @Override
        protected Block block(int code, int meta) {
            switch (code) {
                case MARBLE: return Blocks.quartz_block;
                case LADDER: return Blocks.ladder;
                case CLOUD: return ModBlocks.cloud;
                case SLAB: return Blocks.stone_slab;
                default: return palette(TwilightWorld.SKYTHERN, code);
            }
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? TwilightWorld.SKYTHERN.lampMeta() : meta;
        }

        @Override
        protected boolean lit(Block b) {
            return isLamp(b) || b == ModBlocks.nestAltar;
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            double rOut = outline(dx, dz, R_ISLAND + 2, salt);
            if (r > rOut) return false;
            tornBase(dx, dz, r, rOut, torn, salt, col, 1.3);
            // above the whole temple island - open sky up to the top of the world: the upper layers of Skythern are punched through, light falls into the temple
            for (int y = TOP + 1; y <= 255; y++) put(col, y, AIR, 0);
            if (r > STYLO + 1.5) {
                put(col, TOP, GRASS, 0); // terrace, then a slope down to the drop-off
                slope(dx, dz, r, STYLO + 4, rOut, salt, col, GRASS);
                return true;
            }
            // stylobate: marble, pattern rings, a border and a step along the edge
            boolean border = r > STYLO - 0.5 || Math.abs(r - 10) < 0.5 || Math.abs(r - 20) < 0.5;
            put(col, TOP, MARBLE, border ? 1 : 0);
            if (r < 1.2 || ringDist(dx, dz, 10, 8, Math.PI / 8) < 0.6) put(col, TOP, GLOW, 0);
            if (r > STYLO - 0.5) put(col, TOP + 1, SLAB, 7);
            // colonnade: base, thick trunk of pillar marble, capital, entablature on top
            double cd = ringDist(dx, dz, PERI, COLS, 0);
            if (cd < 3.0) put(col, TOP + 1, MARBLE, 1);
            if (cd < 2.6) put(col, TOP + 2, MARBLE, 1);
            if (cd < 2.1) for (int y = TOP + 3; y < TOP + COL_H - 1; y++) put(col, y, MARBLE, 2);
            if (cd < 2.6) put(col, TOP + COL_H - 1, MARBLE, 1);
            if (cd < 3.0) put(col, TOP + COL_H, MARBLE, 1);
            if (Math.abs(r - PERI) < 2.6) {
                put(col, TOP + COL_H + 1, MARBLE, 0);
                put(col, TOP + COL_H + 2, MARBLE, ((int) Math.round(Math.atan2(dz, dx) * 24)) % 3 == 0 ? 1 : 0); // frieze
                put(col, TOP + COL_H + 3, MARBLE, 0);
            }
            if (Math.abs(r - PERI) < 3.4) put(col, TOP + COL_H + 4, SLAB, 7); // cornice
            // platform with the altar to the north
            double ad = Math.sqrt(dx * dx + (dz + ALTAR_DZ) * (dz + ALTAR_DZ));
            if (ad <= 2.5) put(col, TOP + 1, MARBLE, 1);
            if (dx == 0 && dz == -ALTAR_DZ) put(col, TOP + 2, ALTAR, 0);
            // totem pedestals: 3x3 marble, staircase outside, cloud platform on top
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 4 + Math.PI / 2 * i;
                int px = (int) Math.round(Math.cos(a) * com.divinerpgextended.entity.EntityWindLord.TOTEM_R);
                int pz = (int) Math.round(Math.sin(a) * com.divinerpgextended.entity.EntityWindLord.TOTEM_R);
                int ph = com.divinerpgextended.entity.EntityWindLord.HEIGHTS[i];
                int sx = px > 0 ? 1 : -1;
                int ox = dx - px, oz = dz - pz;
                if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) {
                    for (int y = TOP + 1; y < TOP + ph; y++) put(col, y, MARBLE, 2);
                    put(col, TOP + ph, CLOUD, 0);
                } else if (ox == 2 * sx && oz == 0) {
                    for (int y = TOP + 1; y < TOP + ph; y++) put(col, y, LADDER, sx > 0 ? 5 : 4);
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- Demonic Circle

    static class Circle extends ColumnStructure {

        /** The floor of the circle is in the Mortum cave. */
        static final int TOP = MORTUM_TOP;
        /** The hall above the circle: a dome of radius HALL_R and height HALL_H. */
        static final int HALL_R = CIRCLE_R + 6, HALL_H = 26;

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? TwilightWorld.MORTUM.lampMeta() : meta;
        }

        @Override
        protected Block block(int code, int meta) {
            return palette(TwilightWorld.MORTUM, code);
        }

        @Override
        protected boolean lit(Block b) {
            return isLamp(b);
        }

        @Override
        protected Block foundation() {
            return palette(TwilightWorld.MORTUM, STONE);
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz), R = CIRCLE_R;
            if (r > HALL_R + 0.5) return false;
            // dome hall: higher toward the center, along the edge the cave floor is raised by a stone ledge
            // cleared above the circle (no vault: Mortum has open caves, a vault would hang like an umbrella); stands on a foundation down to the ground
            for (int y = TOP + 1; y <= TOP + 14; y++) put(col, y, AIR, 0);
            put(col, TOP - 5, FOUNDATION, 0);
            if (r > R + 0.5) {
                put(col, TOP, STONE, 0);
                for (int y = TOP - 4; y < TOP; y++) put(col, y, STONE, 0);
                return true;
            }
            bowl(dx, dz, r, R, col, false, TOP);
            int ri = (int) r;
            put(col, TOP, ri % 4 == 0 || r < 2.5 ? GEM : STONE, 0);
            if (r >= R - 1.5 && !gate(dx, dz)) {
                put(col, TOP + 1, STONE, 0);
                if (hash(dx, dz) % 3 != 0) put(col, TOP + 2, STONE, 0);
            }
            // six obelisks with a light on top
            double ob = ringDist(dx, dz, 16, 6, 0);
            if (ob < 1.6) {
                int h = ob < 0.9 ? 10 : 7;
                for (int y = TOP + 1; y <= TOP + h; y++) put(col, y, GEM, 0);
                if (ob < 0.9) put(col, TOP + 11, GLOW, 0);
            }
            // inner ring of light
            if (ringDist(dx, dz, 6, 12, 0) < 0.5) put(col, TOP, GLOW, 0);
            return true;
        }
    }
}
