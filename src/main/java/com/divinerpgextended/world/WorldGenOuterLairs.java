package com.divinerpgextended.world;

import java.util.Random;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.world.twilight.ColumnStructure;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Lairs of Nether and End bosses that have no place of their own in Divine. The site is computed from the seed; the structure is defined by
 * columns ({@link ColumnStructure}). The Divine summoning item works only inside the lair (see
 * {@link com.divinerpgextended.boss.LairSummons}), the summoning itself is done by Divine.
 * <ul>
 * <li>The Watcher's lair (Nether): a buried observatory under a lava lake ({@link WatcherVault}) - an obsidian hall at the
 * bottom, inside a rubble pile that the fight clears itself; the entrance is a sloping tunnel from the shore. The Call of the Watcher is in the hall. (Before 0.99.48
 * - a domed observatory of radius 20 with four tunnels: in already created chunks it stayed.)</li>
 * <li>The Fire Chamber of the King of Scorchers (Nether): a race track under a lava lake ({@link ScorcherPit}) - a hall like the
 * observatory's, in the center a checkered dais behind a lava moat. The Infernal Flame is in the chamber. (Before 0.99.51 - a sphere of radius 17
 * in the Nether sky: in already created chunks it stayed and works.)</li>
 * <li>The Ayeraco aerie (End): a large island {@link #AYERACO_DIST} blocks from the center diagonally - entirely inside the world
 * border {@link #WORLD_EDGE} (in 0.99.36-0.99.37 it was at 1400, before that a small one at 1000): a ragged edge, a teardrop base, a flat arena with obsidian rings,
 * five towers with horde pillars, around it a belt of End stone asteroids. The Horde Horn is on the altar at the arena center; within 160 blocks of the center
 * nobody spawns on their own.</li>
 * </ul>
 */
public class WorldGenOuterLairs implements IWorldGenerator {

    public static final int WATCHER = 0, SCORCHERS = 1, AYERACO = 2;
    /** Radius of the old Scorcher chamber (a sphere in the sky, before 0.99.51) - in already created worlds it stayed and works. */
    public static final int OLD_SCORCHER_R = 17;

    /** Floor of a lair of this type at center c (the old Scorcher chamber has its own). */
    public static int floor(World w, int type, int[] c) {
        return type == SCORCHERS && ScorcherPit.isOld(w, c[0], c[2]) ? ScorcherPit.OLD_FLOOR : FLOOR[type];
    }
    /** Hall radius, floor height. */
    public static final int[] RADIUS = { WatcherVault.R, WatcherVault.R, 48 }, FLOOR = { WatcherVault.FLOOR, WatcherVault.FLOOR, 62 };
    private static final long[] SALT = { 0x5A7C4L, 0x5C0BCL, 0xA7E2AL };
    /** The Ayeraco aerie is one per world: a far island at this distance from the End center (reach it in Angelic armor). */
    public static final int AYERACO_DIST = 850;
    /** The server world border: the whole aerie with the asteroid belt is inside the square |x|, |z| <= WORLD_EDGE. */
    public static final int WORLD_EDGE = 900;
    /** Breather islets on the way to the aerie: every so many blocks, as many as there are. */
    public static final int ISLET_STEP = 170, ISLETS = 4;
    /** The aerie: a flat arena at the island center; the asteroid belt is a ring from BELT_IN to BELT_OUT, with stones in it. */
    public static final int ARENA = 32, BELT_IN = 78, BELT_OUT = 112, BELT_ROCKS = 110;
    /** Within this radius of the aerie center nobody spawns on their own: no endermen, no Divine or other mods' mobs. */
    public static final int AYERACO_CALM = 160;

    /** A point in the quiet zone of the Ayeraco aerie. */
    public static boolean nearAerie(World w, double x, double z) {
        if (!enabled(AYERACO) || w.provider.dimensionId != dim(AYERACO)) return false;
        int[] c = nearest(w.getSeed(), AYERACO, x, z);
        double dx = x - c[0] - 0.5, dz = z - c[2] - 0.5;
        return dx * dx + dz * dz <= AYERACO_CALM * AYERACO_CALM;
    }

    /**
     * The horde altar at the arena center. New aeries are built with it; in already built ones (before 0.99.96) it is placed by the player sweep
     * when someone is nearby. true - placed now.
     */
    public static boolean keepAyeracoAltar(net.minecraft.entity.player.EntityPlayer p) {
        World w = p.worldObj;
        if (!nearAerie(w, p.posX, p.posZ)) return false;
        int[] c = nearest(w.getSeed(), AYERACO, p.posX, p.posZ);
        int y = FLOOR[AYERACO] + 1;
        if (!w.blockExists(c[0], y, c[2]) || w.getBlock(c[0], y, c[2]) == com.divinerpgextended.registry.ModBlocks.ayeracoAltar) return false;
        w.setBlock(c[0], y, c[2], com.divinerpgextended.registry.ModBlocks.ayeracoAltar);
        return true;
    }

    /**
     * The lair's world (as in the Divine config); the lair is built only in the Nether / End. Boss world settings exist only since
     * Divine 1.4.2 - in 1.4.1.5 there are none, the worlds are hardwired: the Watcher and scorchers - Nether, the Ayeraco horde - End.
     */
    public static int dim(int type) {
        return type == WATCHER ? option("theWatcherDim", -1) : type == SCORCHERS ? option("kingOfScorchersDim", -1)
            : option("ayeracoHordeDim", 1);
    }

    private static int option(String name, int fallback) {
        try {
            return ConfigurationHelper.class.getField(name).getInt(null);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** A lair exists: this type's world is the Nether (for the Watcher and scorchers) or the End (for Ayeraco), and lairs are enabled. */
    public static boolean enabled(int type) {
        if (Config.netherLairSpacing <= 0) return false;
        return type == AYERACO ? dim(type) == 1 : dim(type) == -1;
    }

    /** Lair center in region (rx, rz); for Ayeraco there is a single region. */
    public static int[] center(long seed, int type, int rx, int rz) {
        Random r = new Random(seed ^ (rx * 341873128712L + rz * 132897987541L) ^ SALT[type]);
        if (type == AYERACO) {
            // diagonally (+-20 degrees): at 850 blocks from zero both the island and the asteroid belt stay within the 900 border
            double a = Math.PI / 4 + r.nextInt(4) * Math.PI / 2 + (r.nextDouble() - 0.5) * Math.toRadians(40);
            return new int[] { (int) (Math.cos(a) * AYERACO_DIST), FLOOR[type], (int) (Math.sin(a) * AYERACO_DIST) };
        }
        int g = Config.netherLairSpacing, margin = Math.min(g / 3, RADIUS[type] + 40);
        int[] c = new int[] { rx * g + margin + r.nextInt(Math.max(1, g - 2 * margin)), FLOOR[type], rz * g + margin + r.nextInt(Math.max(1, g - 2 * margin)) };
        // the scorcher chamber must not overlap the Watcher's lair (its entrance stretches east) - then another site in the region
        for (int tries = 0; type == SCORCHERS && tries < 12 && clashesWithWatcher(seed, c); tries++) {
            c[0] = rx * g + margin + r.nextInt(Math.max(1, g - 2 * margin));
            c[2] = rz * g + margin + r.nextInt(Math.max(1, g - 2 * margin));
        }
        return c;
    }

    /** A scorcher chamber centered at c touches the Watcher's lair in neighboring regions (both have a hall and an entrance to the east). */
    public static boolean clashesWithWatcher(long seed, int[] c) {
        int g = Config.netherLairSpacing, rx = Math.floorDiv(c[0], g), rz = Math.floorDiv(c[2], g);
        int west = WatcherVault.R + 8, east = WatcherVault.tunnelEnd() + 12, side = WatcherVault.R + 8;
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] w = center(seed, WATCHER, rx + i, rz + k);
            if (c[0] - west <= w[0] + east && c[0] + east >= w[0] - west && Math.abs(c[2] - w[2]) <= side * 2) return true;
        }
        return false;
    }

    public static int[] nearest(long seed, int type, double x, double z) {
        if (!enabled(type)) return null;
        if (type == AYERACO) return center(seed, type, 0, 0);
        int g = Config.netherLairSpacing;
        int rx = (int) Math.floor(x / g), rz = (int) Math.floor(z / g);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
            int[] c = center(seed, type, rx + i, rz + k);
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** A point inside a lair of this type (the summoning item works there). */
    public static boolean inside(World w, int type, double x, double y, double z) {
        if (w.provider.dimensionId != dim(type)) return false;
        int[] c = nearest(w.getSeed(), type, x, z);
        if (c == null) return false;
        double dx = x - c[0] - 0.5, dz = z - c[2] - 0.5, R = RADIUS[type] - 2;
        if (type == SCORCHERS && ScorcherPit.isOld(w, c[0], c[2])) { // the old kind of chamber - a sphere in the sky (before 0.99.51)
            R = OLD_SCORCHER_R - 2;
            return dx * dx + dz * dz <= R * R && y >= ScorcherPit.OLD_FLOOR - 2 && y <= ScorcherPit.OLD_FLOOR + 24;
        }
        return dx * dx + dz * dz <= R * R && y >= FLOOR[type] - 2 && y <= FLOOR[type] + 24;
    }

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8;
        for (int type = 0; type < 3; type++) {
            if (!enabled(type) || world.provider.dimensionId != dim(type)) continue;
            int[] c = nearest(world.getSeed(), type, x0 + 8, z0 + 8);
            int reach = type == AYERACO ? AYERACO_DIST + 16 : WatcherVault.tunnelEnd() + 12 + 16;
            if (c == null || Math.abs(c[0] - (x0 + 8)) > reach || Math.abs(c[2] - (z0 + 8)) > reach) continue;
            ColumnStructure s = shape(type, c);
            s.buildSlice(world, c[0], c[2], x0, z0);
            if (type != AYERACO) { // both Nether lairs
                s.clean(world, c[0], c[2], x0 - 16, z0 - 16, 48, FLOOR[type] + 1, WatcherVault.TUNNEL_Y + 12);
                // lava and blood lakes (BoP Nether etc.) cut out the floor and walls of already finished pieces - restore
                s.restore(world, c[0], c[2], x0 - 16, z0 - 16, 48, FLOOR[type] - 1, WatcherVault.TUNNEL_Y + 12, net.minecraft.init.Blocks.netherrack,
                    net.minecraft.init.Blocks.soul_sand, net.minecraft.init.Blocks.gravel);
            }
        }
    }

    /**
     * In the halls of the Nether lairs (under lava) neither ghasts nor others spawn on their own: their fireballs would break the hall. At the Ayeraco aerie
     * ({@link #AYERACO_CALM} blocks) nobody either: the island was overrun by endermen and other mods' mobs.
     */
    public static class NoSpawns {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onCheckSpawn(net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn e) {
            if (e.world.isRemote) return;
            if (nearAerie(e.world, e.x, e.z)) {
                e.setResult(cpw.mods.fml.common.eventhandler.Event.Result.DENY);
                return;
            }
            if (e.y > WatcherVault.TOP + 4) return;
            for (int type = 0; type < 2; type++)
                if (inside(e.world, type, e.x, e.y, e.z)) {
                    e.setResult(cpw.mods.fml.common.eventhandler.Event.Result.DENY);
                    return;
                }
        }
    }

    public static ColumnStructure shape(int type, int[] c) {
        return type == AYERACO ? new Aerie(c[0], c[2]) : type == WATCHER ? new WatcherVault(c[0], c[2]) : new ScorcherPit(c[0], c[2]);
    }

    private static final int BRICK = 1, GLOW = 2, LAVA = 3, OBSIDIAN = 4, END_STONE = 5, QUARTZ = 6, FENCE = 7, CLAY = 8;
    /** Stained clay of a given color in the aerie decoration (nest 1.1.3): code CLAY_COLOR + color, meta = color. */
    private static final int CLAY_COLOR = 16;

    private static Block block(int code) {
        if (code >= CLAY_COLOR && code < CLAY_COLOR + 16) return Blocks.stained_hardened_clay;
        switch (code) {
            case BRICK: return Blocks.nether_brick;
            case GLOW: return net.divinerpg.utils.blocks.VanillaBlocks.lamp1;
            case LAVA: return Blocks.lava;
            case OBSIDIAN: return Blocks.obsidian;
            case END_STONE: return Blocks.end_stone;
            case QUARTZ: return WhiteStone.block(); // milk stone, not quartz (1.1.5)
            case FENCE: return Blocks.nether_brick_fence;
            case CLAY: return Blocks.stained_hardened_clay;
            default: return Blocks.air;
        }
    }

    private static double ringDist(double dx, double dz, double ring, int count, double phase) {
        double a = Math.atan2(dz, dx) - phase, step = Math.PI * 2 / count;
        double k = Math.round(a / step);
        double ca = k * step + phase;
        return Math.sqrt((dx - Math.cos(ca) * ring) * (dx - Math.cos(ca) * ring) + (dz - Math.sin(ca) * ring) * (dz - Math.sin(ca) * ring));
    }

    // ---------------------------------------------------------------- Nether halls

    // ---------------------------------------------------------------- Ayeraco aerie

    static class Aerie extends ColumnStructure {

        private final double toX, toZ;
        /** Belt asteroids: {x, z from the center, height from the floor, radius, flattening}. */
        private final double[][] rocks;

        /** The aerie center (cx, cz): the road goes from it to the End center. */
        Aerie(int cx, int cz) {
            double len = Math.sqrt(cx * cx + cz * cz);
            toX = len < 1 ? 0 : -cx / len;
            toZ = len < 1 ? 0 : -cz / len;
            Random r = new Random(cx * 341873128712L + cz * 132897987541L ^ 0xA57E401DL);
            rocks = new double[BELT_ROCKS][];
            for (int i = 0; i < BELT_ROCKS; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = BELT_IN + r.nextDouble() * (BELT_OUT - BELT_IN);
                double size = 1.5 + r.nextDouble() * r.nextDouble() * 6; // more small ones
                rocks[i] = new double[] { Math.cos(a) * d, Math.sin(a) * d, r.nextGaussian() * 6 + 4, size, 0.5 + r.nextDouble() * 0.5 };
            }
        }

        @Override
        protected Block block(int code, int meta) {
            return WorldGenOuterLairs.block(code);
        }

        @Override
        protected int metaOf(int code, int meta) {
            return code == GLOW ? 8 : meta;
        }

        @Override
        protected boolean lit(Block b) {
            return b == net.divinerpg.utils.blocks.VanillaBlocks.lamp1;
        }

        /** The Horde Altar is above the fire at the arena center. */
        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            place(w, cx, cz, x0, z0, 0, FLOOR[AYERACO] + 1, 0, com.divinerpgextended.registry.ModBlocks.ayeracoAltar, 0);
        }

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            int R = RADIUS[AYERACO], top = FLOOR[AYERACO];
            double r = Math.sqrt(dx * dx + dz * dz);
            // breather islets on the way to the End center: so that in Angelic armor you can catch your breath (arcana)
            double along = dx * toX + dz * toZ, across = Math.abs(dx * toZ - dz * toX);
            for (int k = 1; k <= ISLETS; k++) {
                double at = k * ISLET_STEP, ir = Math.sqrt((along - at) * (along - at) + across * across);
                if (ir > 4.5) continue;
                int depth = 1 + (int) (4 * Math.sqrt(Math.max(0, 1 - (ir / 4.5) * (ir / 4.5))));
                for (int y = top - depth; y <= top; y++) put(col, y, END_STONE, 0);
                if (ir < 0.8) {
                    put(col, top, GLOW, 0);
                    for (int y = top + 1; y <= top + 3; y++) put(col, y, OBSIDIAN, 0); // a landmark: visible from afar
                    put(col, top + 4, GLOW, 0);
                }
                return true;
            }
            boolean any = belt(dx, dz, top, col);
            double edge = edge(dx, dz);
            if (r > edge + 0.5) return any;
            // island: below a drop (deeper toward the center) with obsidian icicles, on top a flat arena and hills toward the edge
            double t = Math.min(1, r / edge);
            int h = hash(dx, dz);
            int depth = 4 + (int) (40 * Math.pow(Math.max(0, 1 - t * t), 0.7)) + h % 3;
            boolean icicle = h % 23 == 0 && t < 0.85;
            if (icicle) depth += 2 + h % 6;
            double ang = Math.atan2(dz, dx);
            int rise = r < ARENA ? 0 : (int) Math.round(Math.max(0, (r - ARENA) / Math.max(1, edge - ARENA)) * (2 + 3 * (0.5 + 0.5 * Math.sin(ang * 9 + r * 0.35)))
                * (t > 0.93 ? 0.4 : 1));
            for (int y = top - depth; y <= top + rise; y++) put(col, y, END_STONE, 0);
            if (icicle) for (int y = top - depth; y < top - depth + 3; y++) put(col, y, OBSIDIAN, 0);
            for (int y = top + rise + 1; y <= top + 40; y++) put(col, y, AIR, 0);
            if (r < ARENA) {
                // arena - the nest cup (1.1.3): obsidian rings every 6 blocks, fires in a circle and at the center, toward the rim rare twigs
                if (((int) r) % 6 == 0 && r > 3) put(col, top, OBSIDIAN, 0);
                if (r < 1.5 || ringDist(dx, dz, 12, 8, 0) < 0.5 || ringDist(dx, dz, 24, 16, Math.PI / 16) < 0.5) put(col, top, GLOW, 0);
                if (r > 28 && ((int) (r * 3)) % 2 == 0 && Math.floorMod(dx + dz, 3) == 0) put(col, top + 1, OBSIDIAN, 0);
            }
            // (before 1.1.3 - obsidian teeth along the edge; now the woven rim of the nest, see nest())
            // horde pillar towers: obsidian with a spiral staircase around, on top a clay pillar of the color
            for (com.divinerpgextended.boss.AyeracoHorde.Hue hue : com.divinerpgextended.boss.AyeracoHorde.Hue.values()) {
                int[] pa = com.divinerpgextended.boss.AyeracoHorde.pillarAt(0, top, 0, hue);
                double px = dx - pa[0], pz = dz - pa[2], pr = Math.sqrt(px * px + pz * pz);
                if (pr > 4.2) continue;
                int lift = com.divinerpgextended.boss.AyeracoHorde.PILLAR_LIFT;
                for (int y = top + 1; y <= top + lift + 12; y++) put(col, y, AIR, 0);
                if (pr < 2.6) {
                    for (int y = top + 1; y <= top + lift; y++) put(col, y, OBSIDIAN, 0);
                    if (pr < 0.5) {
                        for (int y = pa[1]; y < pa[1] + com.divinerpgextended.boss.AyeracoHorde.PILLAR_H; y++) put(col, y, CLAY, hue.clay());
                        put(col, pa[1] + com.divinerpgextended.boss.AyeracoHorde.PILLAR_H, GLOW, 0);
                    }
                } else if (pr < 4.0) {
                    // step: a turn of 8 blocks up
                    double a = Math.atan2(pz, px);
                    int step = 1 + (int) Math.floor((a + Math.PI) / (Math.PI * 2) * lift);
                    for (int y = top + 1; y <= top + Math.min(step, lift); y++) put(col, y, END_STONE, 0);
                }
            }
            com.divinerpgextended.world.twilight.Voxels n = nest();
            if (n.has(dx, dz)) n.apply(dx, dz, col);
            return true;
        }

        private static com.divinerpgextended.world.twilight.Voxels nest;

        /**
         * The nest (1.1.3), from the arena center: a woven rim of obsidian and quartz twigs on the ring 39..47 rising toward the edge (not
         * past the island edge and not on the towers), three obsidian talons around each horde pillar curving up and in, and a big egg of
         * quartz with spots of the tower's color beside each perch, off the arena. Heights are absolute (the arena floor is FLOOR).
         */
        static synchronized com.divinerpgextended.world.twilight.Voxels nest() {
            if (nest != null) return nest;
            com.divinerpgextended.world.twilight.Voxels v = new com.divinerpgextended.world.twilight.Voxels(
                code -> code >= CLAY_COLOR && code < CLAY_COLOR + 16 ? code - CLAY_COLOR : code == GLOW ? 8 : 0);
            int top = FLOOR[AYERACO];
            com.divinerpgextended.boss.AyeracoHorde.Hue[] hues = com.divinerpgextended.boss.AyeracoHorde.Hue.values();
            int[][] towers = new int[hues.length][];
            for (int k = 0; k < hues.length; k++) towers[k] = com.divinerpgextended.boss.AyeracoHorde.pillarAt(0, top, 0, hues[k]);
            Random rnd = new Random(7);
            for (int i = 0; i < 700; i++) { // the woven rim: chords between points of the ring
                double a0 = rnd.nextDouble() * Math.PI * 2, a1 = a0 + (0.15 + rnd.nextDouble() * 0.3) * (rnd.nextBoolean() ? 1 : -1);
                double r0 = 39 + rnd.nextDouble() * 8, r1 = 39 + rnd.nextDouble() * 8;
                double y0 = 1 + rnd.nextDouble() * 3 + (r0 - 39) * 1.1, y1 = 1 + rnd.nextDouble() * 3 + (r1 - 39) * 1.1;
                int mat = rnd.nextDouble() < 0.75 ? OBSIDIAN : QUARTZ;
                for (int j = 0; j <= 40; j++) {
                    double s = j / 40.0, a = a0 + (a1 - a0) * s, rr = r0 + (r1 - r0) * s, y = y0 + (y1 - y0) * s;
                    int x = (int) Math.round(Math.cos(a) * rr), z = (int) Math.round(Math.sin(a) * rr);
                    if (rr > edge(x, z) - 0.5) continue; // not over the void past the island edge
                    boolean nearTower = false;
                    for (int[] t : towers) if ((x - t[0]) * (x - t[0]) + (z - t[2]) * (z - t[2]) < 25) nearTower = true;
                    if (nearTower) continue;
                    int yy = top + (int) Math.round(y);
                    v.put(x, yy, z, mat);
                    v.put(x, yy + 1, z, mat);
                }
            }
            for (int k = 0; k < hues.length; k++) {
                int tx = towers[k][0], tz = towers[k][2];
                double a = Math.atan2(tz, tx);
                for (int c = 0; c < 3; c++) { // talons around the pillar
                    double ca = a + c * Math.PI * 2 / 3;
                    for (int i = 0; i < 10; i++) {
                        double s = i / 9.0, rr = 2.6 - s * 1.6;
                        int y = top + com.divinerpgextended.boss.AyeracoHorde.PILLAR_LIFT + 1 + (int) Math.round(s * 7);
                        v.put((int) Math.round(tx + Math.cos(ca) * rr), y, (int) Math.round(tz + Math.sin(ca) * rr), OBSIDIAN);
                    }
                }
                double ex = Math.cos(a + 0.21) * com.divinerpgextended.boss.AyeracoHorde.PILLAR_RING, ez = Math.sin(a + 0.21) * com.divinerpgextended.boss.AyeracoHorde.PILLAR_RING;
                for (int x = (int) ex - 4; x <= (int) ex + 4; x++) for (int z = (int) ez - 4; z <= (int) ez + 4; z++) for (int y = 1; y <= 8; y++) {
                    double d = ((x - ex) / 2.6) * ((x - ex) / 2.6) + ((z - ez) / 2.6) * ((z - ez) / 2.6) + ((y - 4) / 3.6) * ((y - 4) / 3.6);
                    if (d <= 1) v.put(x, top + y, z, hash(x * 7 + y, z * 13 - y) % 4 == 0 ? CLAY_COLOR + hues[k].clay() : QUARTZ);
                }
            }
            return nest = v;
        }

        /** The island edge in this direction: ragged, with "tentacles" like an End island. */
        static double edge(int dx, int dz) {
            double a = Math.atan2(dz, dx);
            double e = RADIUS[AYERACO] + 3 * Math.sin(3 * a + 0.7) + 2 * Math.sin(7 * a + 2.1) + 1.2 * Math.sin(13 * a + 0.3);
            double arm = Math.sin(5 * a + 1.3);
            return e + 9 * Math.pow(Math.max(0, arm), 10);
        }

        /** The asteroid belt around the island: End stone chunks of different sizes, large ones with fire on the top. */
        private boolean belt(int dx, int dz, int top, int[] col) {
            boolean any = false;
            for (double[] k : rocks) {
                double ddx = dx - k[0], ddz = dz - k[1], hr = Math.sqrt(ddx * ddx + ddz * ddz);
                if (hr > k[3]) continue;
                double half = Math.sqrt(k[3] * k[3] - hr * hr) * k[4];
                int yc = top + (int) Math.round(k[2]);
                int lo = (int) Math.round(yc - half), hi = (int) Math.round(yc + half * 0.8);
                for (int y = lo; y <= hi; y++) put(col, y, END_STONE, 0);
                if (k[3] > 5 && hr < 0.8) put(col, hi, GLOW, 0);
                any = true;
            }
            return any;
        }
    }
}
