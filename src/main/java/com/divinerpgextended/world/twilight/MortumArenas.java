package com.divinerpgextended.world.twilight;

import java.util.ArrayList;
import java.util.List;

import net.divinerpg.utils.items.TwilightItemsOther;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.world.World;

/**
 * Mortum boss arenas: each crystal has its own, the boss can be summoned only on it. In each Mortum square
 * ({@link WorldGenNests#spacing}) there is one arena, the type is from the seed and the square coordinates so that neighboring squares are always different.
 * All are separate structures at height {@link WorldGenNests#MORTUM_TOP} on their own foundation down to the ground, with a cleared
 * space around (the cave vault above the slopes, no cut passages) ({@link Building}).
 * <ul>
 * <li>Mage Castle - Twilight Demon: walls, corner towers, the mage spire, in the courtyard a ritual circle with obelisks.</li>
 * <li>Hall of Twins - Densos and Reyvor: a fortress of light and dark halves, a rift with bridges, a brother's dais in each.</li>
 * <li>Hall of Mirrors - Karot: a closed building with buttresses, inside walls of black mirrors.</li>
 * <li>Gallery - Eternal Archer: an arcade under a gabled roof, the archer's throne on a dais, columns as cover.</li>
 * <li>Graveyard - Soul Fiend: a cemetery behind a fence, paths, graves in rows, a mausoleum in the center.</li>
 * <li>Bone Pit - Vamacheron: an amphitheater with a pit, stands and bone pillars under the giant's ribcage.</li>
 * </ul>
 */
public final class MortumArenas {

    public enum Type {
        DEMON(WorldGenNests.CIRCLE_ZONE, WorldGenNests.CIRCLE_R - 2), TWINS(21, 22), KAROT(13, 13), ARCHER(25, 26), FIEND(21, 22), VAMACHERON(23, 23);

        /** Inside (horizontally from the center) the crystal works; the boss does not go farther than leash. */
        public final int zone, leash;

        Type(int zone, int leash) {
            this.zone = zone;
            this.leash = leash;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    private MortumArenas() {}

    /** Arena type in square (rx, rz): neighboring squares (including diagonal) are always different. */
    public static Type type(long seed, int rx, int rz) {
        int n = Type.values().length;
        int s = (int) ((seed ^ seed >>> 29) & 0x7fffffff) % n;
        return Type.values()[Math.floorMod(rx * 2 + rz * 3 + s, n)];
    }

    /** The nearest arena: {x, floor, z, type}. */
    public static int[] nearest(World w, double x, double z) {
        int[] c = WorldGenNests.nearest(w.getSeed(), w.provider.dimensionId, x, z);
        return c;
    }

    public static Type typeAt(World w, int[] c) {
        int g = WorldGenNests.spacing(w.provider.dimensionId);
        return type(w.getSeed(), Math.floorDiv(c[0], g), Math.floorDiv(c[2], g));
    }

    /** The nearest arena of the needed type (searching squares around, up to radius squares); null - none. */
    public static int[] nearestOf(World w, Type t, double x, double z, int radius) {
        int g = WorldGenNests.spacing(w.provider.dimensionId);
        if (g <= 0) return null;
        int rx = (int) Math.floor(x / g), rz = (int) Math.floor(z / g);
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int i = -radius; i <= radius; i++) for (int k = -radius; k <= radius; k++) {
            if (type(w.getSeed(), rx + i, rz + k) != t) continue;
            int[] c = WorldGenNests.center(w.getSeed(), w.provider.dimensionId, rx + i, rz + k);
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    /** A point on an arena of this type (its crystal works there). */
    public static boolean inArena(World w, Type t, double x, double y, double z) {
        if (TwilightWorld.of(w.provider.dimensionId) != TwilightWorld.MORTUM) return false;
        int[] c = nearest(w, x, z);
        if (c == null || typeAt(w, c) != t) return false;
        double dx = x - c[0] - 0.5, dz = z - c[2] - 0.5;
        boolean in = t == Type.KAROT || t == Type.ARCHER ? Math.abs(dx) <= halfX(t) && Math.abs(dz) <= halfZ(t) : dx * dx + dz * dz <= t.zone * t.zone;
        return in && Math.abs(y - floorY(t) - 1) <= 8;
    }

    /** Hall of Twins: the brothers' daises at DAIS_X from the center; Karot's Hall of Mirrors: mirrors at MIRROR_H from the center. */
    public static final int TWINS_DAIS = Twins.DAIS_X, MIRROR_H = Mirror.H;

    /** Archer gallery: halves along X and Z inside the walls (what a volley can break through - only inside). */
    public static final int GALLERY_HX = Gallery.HX, GALLERY_HZ = Gallery.HZ;

    static int halfX(Type t) {
        return t == Type.ARCHER ? Gallery.HX - 1 : Mirror.H - 1;
    }

    static int halfZ(Type t) {
        return t == Type.ARCHER ? Gallery.HZ - 1 : Mirror.H - 1;
    }

    /** Arena floor (at the Vamacheron pit - lower). */
    public static int floorY(Type t) {
        if (t == Type.DEMON) return Spire.TOP; // platform on top of the cliff
        return t == Type.VAMACHERON ? WorldGenNests.MORTUM_TOP - Pit.SINK : WorldGenNests.MORTUM_TOP;
    }

    /** Whose crystal (null - not a Mortum boss crystal). */
    public static Type ofCrystal(Item i) {
        if (i == TwilightItemsOther.twilightDemonCrystal) return Type.DEMON;
        if (i == TwilightItemsOther.densosCrystal || i == TwilightItemsOther.reyvorCrystal) return Type.TWINS;
        if (i == TwilightItemsOther.karotCrystal) return Type.KAROT;
        if (i == TwilightItemsOther.eternalArcherCrystal) return Type.ARCHER;
        if (i == TwilightItemsOther.soulFiendCrystal) return Type.FIEND;
        if (i == TwilightItemsOther.vamacheronCrystal) return Type.VAMACHERON;
        return null;
    }

    /** Arenas already checked for voids under the floor in this server run: "world:x:z". */
    private static final java.util.Set<String> HEALED = new java.util.HashSet<String>();

    /**
     * Arenas built before 0.99.96 in places hang over a cave: the floor layer under the walls and stands was not filled. When a player is
     * nearby, only such places are filled according to the blueprint of the same arena - once per server run. How many blocks were filled.
     */
    public static int heal(net.minecraft.entity.player.EntityPlayer p) {
        World w = p.worldObj;
        if (TwilightWorld.of(w.provider.dimensionId) != TwilightWorld.MORTUM) return 0;
        int[] c = nearest(w, p.posX, p.posZ);
        if (c == null) return 0;
        Type t = typeAt(w, c);
        int reach = extent(t);
        double dx = p.posX - c[0], dz = p.posZ - c[2];
        if (dx * dx + dz * dz > (reach + 48) * (reach + 48) || !w.checkChunksExist(c[0] - reach, 0, c[2] - reach, c[0] + reach, 0, c[2] + reach)) return 0;
        if (!HEALED.add(w.provider.dimensionId + ":" + c[0] + ":" + c[2])) return 0;
        return healFloor(w, c);
    }

    /** Fill voids in the floor layer of the arena with center c where the blueprint does not define it. How many blocks were filled. */
    public static int healFloor(World w, int[] c) {
        Type t = typeAt(w, c);
        Building b = (Building) shape(t);
        if (!b.floorLayer()) return 0; // demon cliff: natural slope at this height
        int top = WorldGenNests.MORTUM_TOP, reach = b.reach(), n = 0;
        Block dirt = b.foundation();
        int[] col = new int[256 * 2];
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            if (!b.footprint(dx, dz)) continue;
            for (int i = 0; i < col.length; i += 2) col[i] = ColumnStructure.KEEP;
            if (!b.columnAt(dx, dz, col) || col[top * 2] != ColumnStructure.FOUNDATION) continue;
            for (int y = top; y > top - 6; y--) {
                Block was = w.getBlock(c[0] + dx, y, c[2] + dz);
                if (!(was.isAir(w, c[0] + dx, y, c[2] + dz) || was.getMaterial().isLiquid() || was.getMaterial().isReplaceable())) break;
                w.setBlock(c[0] + dx, y, c[2] + dz, dirt, 0, 2);
                n++;
            }
        }
        return n;
    }

    /**
     * Voids under the structure: air in the foundation and floor layers where the blueprint defines nothing (or defines a block), and above is
     * a block. Air intended by the blueprint (pit, moat, passages under bridges) does not count. {count, dx, dy, dz of the first; then the same for
     * places where the blueprint places a block but it is missing (washed out later, for example by the river)}.
     */
    public static int[] floorHoles(World w, int[] c) {
        Type t = typeAt(w, c);
        Building b = (Building) shape(t);
        int top = holesTop(t), reach = b.reach();
        int[] out = new int[8];
        int[] col = new int[256 * 2];
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            if (!b.footprint(dx, dz)) continue;
            for (int i = 0; i < col.length; i += 2) col[i] = ColumnStructure.KEEP;
            if (!b.columnAt(dx, dz, col)) continue;
            if (!b.floorLayer() && (col[top * 2] == ColumnStructure.KEEP || col[top * 2] == ColumnStructure.AIR)) continue; // cliff slope: natural terrain and dome, not floor
            for (int y = top - 5; y <= top; y++) {
                if (col[y * 2] == ColumnStructure.AIR || !w.isAirBlock(c[0] + dx, y, c[2] + dz)) continue;
                Block up = w.getBlock(c[0] + dx, y + 1, c[2] + dz);
                if (!up.getMaterial().isSolid() || up.getMaterial() == net.minecraft.block.material.Material.leaves) continue;
                int code = col[y * 2], at = code == ColumnStructure.KEEP || code == ColumnStructure.FOUNDATION ? 0 : 4;
                if (!b.floorLayer() && code == ColumnStructure.KEEP) continue; // in the body of the cliff - natural caves, the blueprint does not touch them
                if (out[at]++ == 0) {
                    out[at + 1] = dx;
                    out[at + 2] = y - top;
                    out[at + 3] = dz;
                }
            }
        }
        return out;
    }

    /** Floor height under which {@link #floorHoles} looks for voids: the common arena height, at the demon cliff - its platform. */
    public static int holesTop(Type t) {
        return ((Building) shape(t)).floorLayer() ? WorldGenNests.MORTUM_TOP : floorY(t);
    }

    /** The column (offset from the center) is part of the arena structure itself, without the slopes around. */
    public static boolean footprint(Type t, int dx, int dz) {
        return ((Building) shape(t)).footprint(dx, dz);
    }

    /** The arena boss crystal (for the twins it is Densos's crystal: it summons both). */
    public static Item crystal(Type t) {
        switch (t) {
            case TWINS: return TwilightItemsOther.densosCrystal;
            case KAROT: return TwilightItemsOther.karotCrystal;
            case ARCHER: return TwilightItemsOther.eternalArcherCrystal;
            case FIEND: return TwilightItemsOther.soulFiendCrystal;
            case VAMACHERON: return TwilightItemsOther.vamacheronCrystal;
            default: return TwilightItemsOther.twilightDemonCrystal;
        }
    }

    /** Where the arena boss stands (offset from the center): the archer - on the dais at the end of the gallery; null - where summoned. */
    public static int[] bossSpot(Type t) {
        return t == Type.ARCHER ? new int[] { 0, Gallery.DAIS_H, -(Gallery.HZ - 4) } : null;
    }

    /**
     * The block on which the crystal places the boss (Divine places it in the corner of the block above and silently refuses if it touches
     * a wall): Karot - the center of the Hall of Mirrors, the archer - his dais; null - the altar itself. Arenas built
     * before the altars were moved work this way too (at the mirrors and right next to the throne).
     */
    public static int[] summonBlock(World w, Type t, int[] c) {
        int top = WorldGenNests.MORTUM_TOP;
        if (t == Type.KAROT) return new int[] { c[0], w.getBlock(c[0], top + 1, c[2]) == com.divinerpgextended.registry.ModBlocks.arenaAltar ? top + 1 : top, c[2] };
        if (t == Type.ARCHER) return new int[] { c[0], top + Gallery.DAIS_H, c[2] - (Gallery.HZ - 4) }; // in front of the throne seat (plate at HZ - 1)
        return null;
    }

    /** The Demon tower for the fight (from the arena center): tower center, radius, platform floor, fight floors, crown hall. */
    public static final int SPIRE_TX = Spire.TX, SPIRE_TZ = Spire.TZ, SPIRE_TR = Spire.TR, SPIRE_TOP = Spire.TOP, SPIRE_CROWN = Spire.CROWN,
        SPIRE_BASE = Spire.BASE, SPIRE_COURT = Spire.COURT, SPIRE_LIFT = Spire.LIFT, SPIRE_MOAT = Spire.MOAT_OUT;

    /** The Demon's main power focus: a block on the crown hall pedestal {dx, y, dz} from the arena center. */
    public static int[] crownFocus() {
        return new int[] { Spire.TX, Spire.TOP + Spire.CROWN + 2, Spire.TZ };
    }

    /** What the focus glows with. */
    public static net.minecraft.block.Block focusBlock() {
        return Blocks.glowstone;
    }

    /** Arena altars: {dx, y, dz} (for the twins - one on each brother's dais). */
    public static List<int[]> altars(Type t) {
        List<int[]> l = new ArrayList<int[]>();
        int top = WorldGenNests.MORTUM_TOP;
        switch (t) {
            case TWINS: l.add(new int[] { -Twins.DAIS_X, top + 2, -2 }); l.add(new int[] { Twins.DAIS_X, top + 2, -2 }); break;
            case KAROT: l.add(new int[] { 0, top + 1, 0 }); break;
            case ARCHER: l.add(new int[] { -3, top + Gallery.DAIS_H + 1, -Gallery.HZ + 4 }); l.add(new int[] { 3, top + Gallery.DAIS_H + 1, -Gallery.HZ + 4 }); break;
            case FIEND: l.add(new int[] { 0, top + 1, -1 }); break;
            case VAMACHERON: l.add(new int[] { 0, top - Pit.SINK + 1, 0 }); break;
            default: l.add(new int[] { 0, Spire.TOP + 1, 0 });
        }
        return l;
    }

    /** Graveyard graves (offsets from the center): the Soul Fiend raises souls from them. */
    public static List<int[]> graves() {
        List<int[]> l = new ArrayList<int[]>();
        for (int dx = -18; dx <= 18; dx++) for (int dz = -18; dz <= 18; dz++) if (Graveyard.grave(dx, dz)) l.add(new int[] { dx, dz });
        return l;
    }

    private static final int[] EXTENT = new int[Type.values().length];

    /**
     * How far from the center the arena touches the ground: the structure and slopes (their fill would bury the riverbed). The river flows right
     * behind this circle, there is none on the arena itself.
     */
    public static int extent(Type t) {
        int e = EXTENT[t.ordinal()];
        if (e > 0) return e;
        Building b = (Building) shape(t);
        e = b.reach() + (b.slopes() ? Building.SLOPE + 2 : 2);
        EXTENT[t.ordinal()] = e;
        return e;
    }

    public static ColumnStructure shape(Type t) {
        switch (t) {
            case TWINS: return new Twins();
            case KAROT: return new Mirror();
            case ARCHER: return new Gallery();
            case FIEND: return new Graveyard();
            case VAMACHERON: return new Pit();
            default: return new Spire();
        }
    }

    // ---------------------------------------------------------------- common to structures

    static final int BRICK = 50, CHISEL = 51, BONE = 52, MIRROR = 53, OBSIDIAN = 54, SLAB = 55, MOSSY = 56, CRACKED = 57, PILLAR = 58, FENCE = 59,
        GRAVEL = 60, SOUL = 61, BONE_CHISEL = 62, SLAB_TOP = 63, NETHER = 64, QSLAB = 65, BARS = 66;
    /** Divine blocks (Arcana and the overworld): bricks, stones, lamps, checkerboard, Mortum plants. */
    static final int DDBRICK = 70, DBRICK = 71, ABRICK = 72, ASTONE = 73, ATILE = 74, SSTONE = 75, DLAMP = 76, MILK = 77, DARK = 78, CHECKER = 79,
        MINI = 80, DIVBRICK = 81, VLAMP = 82, MPLANK = 83, BRAMBLE = 84, EYEPLANT = 85, GLOWBONE = 86;
    /** Nether brick stairs (meta - the side, as with vanilla stairs). */
    static final int NSTAIR = 87;

    /**
     * The arena structure in the Mortum cave: stands on its own foundation down to the ground ({@link ColumnStructure#FOUNDATION}), the space above it is
     * cleared (no stone dome - Mortum is almost all open caves, a dome would hang like an umbrella), over the slopes around it
     * the cave vault descends from the structure (there are no cut passages).
     */
    abstract static class Building extends ColumnStructure {

        static final int TOP = WorldGenNests.MORTUM_TOP, TUNNEL = 12;

        @Override
        protected int metaOf(int code, int meta) {
            int sk = skin(code);
            if (sk != code) return sk == WorldGenNests.GLOW ? TwilightWorld.MORTUM.lampMeta() : meta(sk); // the replaced block has its own meta
            return code == WorldGenNests.GLOW ? TwilightWorld.MORTUM.lampMeta() : meta;
        }

        /** Skin of the structure: replace the material code with a Divine block (each arena has its own palette). */
        int skin(int code) {
            return code;
        }

        /** Foundation - Mortum dirt: the arena stands on a hill, not on a gray pillar. */
        @Override
        public Block foundation() {
            return WorldGenNests.palette(TwilightWorld.MORTUM, foundationCode());
        }

        /** What to fill under the structure with: dirt (stone for the cliff). */
        int foundationCode() {
            return WorldGenNests.DIRT;
        }

        @Override
        protected Block block(int code, int meta) {
            switch (skin(code)) {
                case DDBRICK: return net.divinerpg.utils.blocks.VetheaBlocks.blackKarosBricks;
                case DBRICK: return Blocks.stonebrick;
                case ABRICK: return Blocks.nether_brick;
                case ASTONE: return Blocks.stonebrick;
                case ATILE: return Blocks.nether_brick;
                case SSTONE: return Blocks.obsidian;
                case DLAMP: return Blocks.glowstone;
                case MILK: return Blocks.stonebrick;
                case DARK: return Blocks.obsidian; // Vethea dark everstone looked like bedrock
                case CHECKER: return Blocks.nether_brick;
                case MINI: return Blocks.stonebrick;
                case DIVBRICK: return Blocks.nether_brick;
                case VLAMP: return Blocks.glowstone;
                case MPLANK: return net.divinerpg.utils.blocks.TwilightBlocks.mortumPlanks;
                case NSTAIR: return Blocks.nether_brick_stairs;
                case BRAMBLE: return net.divinerpg.utils.blocks.TwilightBlocks.demonBrambles;
                case EYEPLANT: return net.divinerpg.utils.blocks.TwilightBlocks.eyePlant;
                case GLOWBONE: return net.divinerpg.utils.blocks.TwilightBlocks.purpleGlowbone;
                case BRICK: case CHISEL: case MOSSY: case CRACKED: return Blocks.stonebrick;
                case BONE: case PILLAR: case BONE_CHISEL: return Blocks.quartz_block;
                case MIRROR: return Blocks.stained_glass;
                case OBSIDIAN: return Blocks.obsidian;
                case SLAB: case SLAB_TOP: return Blocks.stone_slab;
                case QSLAB: return Blocks.stone_slab;
                case FENCE: return Blocks.nether_brick_fence;
                case GRAVEL: return Blocks.gravel;
                case SOUL: return Blocks.soul_sand;
                case NETHER: return Blocks.nether_brick;
                case BARS: return Blocks.iron_bars;
                default: return WorldGenNests.palette(TwilightWorld.MORTUM, code);
            }
        }

        /** Meta by code: carved/mossy/cracked brick, quartz pillar, black glass, slabs. */
        static int meta(int code) {
            switch (code) {
                case DDBRICK: case DBRICK: case ABRICK: case ASTONE: case ATILE: case SSTONE: case DLAMP: case MILK: case DARK: case CHECKER: case MINI:
                case DIVBRICK: case VLAMP: case MPLANK: case BRAMBLE: case EYEPLANT: case GLOWBONE: return 0;
                case CHISEL: return 3;
                case MOSSY: return 1;
                case CRACKED: return 2;
                case PILLAR: return 2;
                case BONE_CHISEL: return 1;
                case MIRROR: return 15;
                case SLAB: return 5;
                case SLAB_TOP: return 13;
                case QSLAB: return 7;
                default: return 0;
            }
        }

        /** Place the code with its meta. */
        static void set(int[] col, int y, int code) {
            put(col, y, code, meta(code));
        }

        @Override
        protected boolean lit(Block b) {
            return WorldGenNests.isLamp(b) || b.getLightValue() > 0;
        }

        /** Arena altar: the boss crystal is used here (there are no longer boss statues on arenas). */
        static void altar(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz) {
            place(w, cx, cz, x0, z0, dx, y, dz, com.divinerpgextended.registry.ModBlocks.arenaAltar, 0);
        }

        /** Whether the column is part of the structure (without passages). */
        abstract boolean footprint(int dx, int dz);

        /** How much air to clear above the floor (higher - as it was). */
        abstract int clear(int dx, int dz);

        /** The structure itself in the column: floor at TOP, everything above. */
        abstract void build(int dx, int dz, int[] col);

        /** Floor: old brick with moss and cracks. */
        static int worn(int dx, int dz) {
            int h = hash(dx, dz) % 9;
            return h == 0 ? MOSSY : h == 1 ? CRACKED : BRICK;
        }

        /** Whether slopes around are needed (not for the cliff: its edge descends to the ground anyway). */
        boolean slopes() {
            return true;
        }

        /**
         * The structure floor at the common arena height (TOP): then the floor layer that the blueprint did not define is filled. At the demon cliff
         * the platform is higher, and at height TOP there is a natural slope and the clearing dome: filling there is not allowed (it laid stone over the void
         * at the foot and buried Lethe).
         */
        boolean floorLayer() {
            return true;
        }

        /** How far the column is beyond the edge of the structure (by its shape); <= 0 - inside. */
        double outside(int dx, int dz) {
            return Math.sqrt(dx * dx + dz * dz) - reach();
        }

        static final int SLOPE = 10;

        @Override
        protected boolean column(int dx, int dz, int[] col) {
            boolean in = footprint(dx, dz);
            int ax = Math.abs(dx), az = Math.abs(dz), far = Math.max(ax, az);
            boolean tunnel = false; // cut passages to the arena did not join with the terrain - there are none
            double out = outside(dx, dz);
            if (!in && !tunnel && out > 0 && out <= SLOPE && slopes()) {
                // slope around: a fill down to the ground; above - a slope up and the cave vault descending from the structure (a dome, not
                // a sheer cut): where there is rock above, it overhangs as a vault and meets the slope
                int surface = TOP - 1 - (int) (out * 1.3);
                put(col, surface, WorldGenNests.GRASS, 0); // the fill on top is Mortum grass, as around (it used to be bare dirt)
                put(col, surface - 1, FOUNDATION, 0);
                int dome = TOP + dome() - (int) (out * 1.2);
                for (int y = TOP + 1 + (int) (out * 1.5); y <= dome; y++) put(col, y, AIR, 0);
                return true;
            }
            if (!in && !tunnel) return false;
            if (!in) { // passage to the structure: only carve, the floor is dirt with grass
                put(col, TOP - 1, FOUNDATION, 0);
                put(col, TOP, WorldGenNests.GRASS, 0); // grass, as around: no stone paths
                for (int y = TOP + 1; y <= TOP + 4; y++) put(col, y, AIR, 0);
                return true;
            }
            put(col, TOP - 5, FOUNDATION, 0);
            for (int y = TOP - 4; y < TOP; y++) put(col, y, WorldGenNests.DIRT, 0);
            int c = clear(dx, dz);
            for (int y = TOP + 1; y <= TOP + c + (slopes() ? 4 : 0); y++) put(col, y, AIR, 0); // margin above the roof - the start of the vault
            build(dx, dz, col);
            // the floor layer that the structure did not define (under walls and stands): there was a cave - fill it, otherwise the brick hangs over
            // the void (Bone Pit); dirt or grass stays as it was
            if (floorLayer() && col[TOP * 2] == KEEP) put(col, TOP, FOUNDATION, 0);
            return true;
        }

        /** Height of the vault above the floor at the edge of the structure (farther along the slope it descends). */
        int dome() {
            return clear(0, 0) + 4;
        }

        /** How far from the center the structure reaches (for passages). */
        abstract int reach();
    }

    // ---------------------------------------------------------------- Densos and Reyvor

    /**
     * Hall of Twins: a round fortress platform with a crenellated wall. The west is light (quartz, lamps, quartz pillars),
     * the east is dark (obsidian, Mortum stone, obsidian spires). Between the halves is a rift with soul sand at the bottom and
     * three bridges. In each half there is a dais with a spire - the brother's place.
     */
    static class Twins extends Building {

        static final int R = 24, DAIS_X = 12;

        /** Mortum palette: the light half is stone brick (carved pattern, slabs), the dark one is obsidian and black brick. */
        @Override
        int skin(int code) {
            switch (code) {
                case BONE: case PILLAR: return BRICK;
                case QSLAB: return SLAB;
                case BONE_CHISEL: return CHISEL;
                case OBSIDIAN: return DARK;
                case WorldGenNests.STONE: return DDBRICK;
                case CHISEL: return NETHER;
                default: return code;
            }
        }

        /** Statues of the brothers at the spires: Densos on the light dais, Reyvor on the dark one. */
        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            altar(w, cx, cz, x0, z0, -DAIS_X, TOP + 2, -2);
            altar(w, cx, cz, x0, z0, DAIS_X, TOP + 2, -2);
        }

        @Override
        boolean footprint(int dx, int dz) {
            return dx * dx + dz * dz <= (R + 1) * (R + 1);
        }

        @Override
        int reach() {
            return R + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return 14;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            boolean light = dx < 0;
            int wall = light ? BONE : OBSIDIAN;
            if (r > R - 1.5) { // wall with crenellations; gates from the north and south
                set(col, TOP, light ? BONE : OBSIDIAN);
                boolean gate = Math.abs(dx) <= 1;
                if (!gate) {
                    for (int y = TOP + 1; y <= TOP + 4; y++) set(col, y, wall);
                    if ((int) Math.round(Math.atan2(dz, dx) * R) % 2 == 0) set(col, TOP + 5, wall);
                } else set(col, TOP + 5, CHISEL); // gate lintel
                return;
            }
            // rift between the halves
            if (Math.abs(dx) <= 1) {
                boolean bridge = Math.abs(dz) <= 1 || Math.abs(Math.abs(dz) - 12) <= 1;
                if (bridge) {
                    set(col, TOP, SLAB_TOP);
                    if (Math.abs(dz) % 12 == 2 || Math.abs(dz) % 12 == 10) set(col, TOP + 1, FENCE);
                } else {
                    put(col, TOP, AIR, 0);
                    put(col, TOP - 1, AIR, 0);
                    put(col, TOP - 2, AIR, 0);
                    set(col, TOP - 3, SOUL);
                }
                return;
            }
            // floor: the light half is patterned quartz, the dark one is obsidian and world stone
            int ring = (int) r % 6;
            if (light) set(col, TOP, ring == 0 ? BONE_CHISEL : hash(dx, dz) % 9 == 0 ? CRACKED : BONE); // light - only on the columns
            else set(col, TOP, ring == 0 ? WorldGenNests.GEM : hash(dx, dz) % 3 == 0 ? OBSIDIAN : WorldGenNests.STONE);
            // ring of columns
            double cd = WorldGenNests.ringDist(dx, dz, 18, 10, Math.PI / 10);
            if (cd < 0.8) {
                for (int y = TOP + 1; y <= TOP + 7; y++) set(col, y, light ? PILLAR : OBSIDIAN);
                set(col, TOP + 8, light ? WorldGenNests.GLOW : WorldGenNests.GEM);
            }
            // the brother's dais with a spire
            double d = Math.sqrt((Math.abs(dx) - DAIS_X) * (Math.abs(dx) - DAIS_X) + dz * dz);
            if (d < 4.5) set(col, TOP + 1, light ? QSLAB : SLAB);
            if (d < 3.5) set(col, TOP + 1, light ? BONE : OBSIDIAN);
            if (d < 0.8) for (int y = TOP + 2; y <= TOP + (light ? 5 : 10); y++) set(col, y, light ? PILLAR : OBSIDIAN);
            if (d < 0.8) set(col, TOP + (light ? 6 : 11), light ? WorldGenNests.GLOW : WorldGenNests.GEM);
        }
    }

    // ---------------------------------------------------------------- Karot

    /**
     * Hall of Mirrors: a closed building - outside, brick walls with buttresses and a crenellated roof, inside, walls of black
     * glass mirrors between carved columns, four columns to the ceiling, a mosaic in the center, light from windows in the ceiling.
     */
    static class Mirror extends Building {

        static final int H = 14, WALL = 8;

        /** Mortum palette: walls are dark degraded brick, columns are nether brick, the floor is a checkerboard of dark stone and a Mortum block. */
        @Override
        int skin(int code) {
            switch (code) {
                case BRICK: case MOSSY: case CRACKED: return DDBRICK;
                case CHISEL: return NETHER;
                case BONE_CHISEL: return WorldGenNests.GEM;
                case PILLAR: return DARK;
                case CHECKER: return DARK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            altar(w, cx, cz, x0, z0, 0, TOP + 1, 0); // in the center: Karot (3.25 blocks) does not fit at the mirrors
        }

        @Override
        double outside(int dx, int dz) {
            return Math.max(Math.abs(dx), Math.abs(dz)) - reach();
        }

        @Override
        boolean footprint(int dx, int dz) {
            return Math.abs(dx) <= H + 2 && Math.abs(dz) <= H + 2;
        }

        @Override
        int reach() {
            return H + 2;
        }

        @Override
        int clear(int dx, int dz) {
            return WALL + 4;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz), m = Math.max(ax, az), lo = Math.min(ax, az);
            boolean door = lo <= 1 && m >= H;
            if (m > H + 1) { // buttresses outside
                set(col, TOP, BRICK);
                if (lo % 5 == 2 && lo < H) for (int y = TOP + 1; y <= TOP + WALL - 2; y++) set(col, y, CHISEL);
                return;
            }
            if (m == H + 1) { // outer wall
                set(col, TOP, BRICK);
                for (int y = TOP + 1; y <= TOP + WALL + 1; y++) set(col, y, door && y <= TOP + 4 ? AIR : worn(dx, y * 7 + dz));
                if (door) set(col, TOP + 5, CHISEL);
                if ((ax + az) % 2 == 0) set(col, TOP + WALL + 2, BRICK); // crenellations
                return;
            }
            if (m == H) { // mirrors between columns
                set(col, TOP, BRICK);
                boolean pillar = lo % 4 == 0;
                for (int y = TOP + 1; y <= TOP + WALL; y++) set(col, y, door && y <= TOP + 4 ? AIR : pillar ? CHISEL : MIRROR);
                if (pillar && !door) set(col, TOP + WALL - 1, WorldGenNests.GLOW);
                set(col, TOP + WALL + 1, BRICK);
                return;
            }
            // checkerboard floor, mosaic in the center
            double r = Math.sqrt(dx * dx + dz * dz);
            set(col, TOP, r < 4 ? (r < 1.5 ? WorldGenNests.GEM : BONE_CHISEL) : ((dx + dz) & 1) == 0 ? WorldGenNests.GEM : CHECKER);
            if (ax == 7 && az == 7) for (int y = TOP + 1; y <= TOP + WALL; y++) set(col, y, PILLAR);
            if (ax == 7 && az == 7) set(col, TOP + 1, BONE_CHISEL);
            // ceiling with window lamps
            set(col, TOP + WALL + 1, ax % 5 == 0 && az % 5 == 0 ? WorldGenNests.GLOW : BRICK);
            if ((ax + az) % 2 == 1 && m == H - 1) set(col, TOP + WALL + 2, BRICK);
        }
    }

    // ---------------------------------------------------------------- Eternal Archer

    /**
     * Gallery: a long arcade along Z with a gabled roof - pillars and arches on the sides (the cave is visible), hanging
     * lamps under the roof, a nether brick carpet path to the archer's throne on a dais at the north end; rows of cover columns
     * of carved brick (volleys destroy them).
     */
    static class Gallery extends Building {

        static final int HX = 11, HZ = 27, DAIS_H = 3, COL_H = 4, EAVE = 8;

        /** Mortum palette: walls are black brick, the roof is nether brick stairs with an obsidian ridge; cover columns are carved stone (volleys destroy them). */
        @Override
        int skin(int code) {
            switch (code) {
                case BRICK: case MOSSY: case CRACKED: return DDBRICK;
                case SLAB_TOP: return MPLANK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            altar(w, cx, cz, x0, z0, -3, TOP + DAIS_H + 1, -HZ + 4); // from the throne and the end: the archer (3 blocks) needs space
            altar(w, cx, cz, x0, z0, 3, TOP + DAIS_H + 1, -HZ + 4);
        }

        @Override
        double outside(int dx, int dz) {
            return Math.max(Math.abs(dx) - (HX + 1), Math.abs(dz) - (HZ + 1));
        }

        @Override
        boolean footprint(int dx, int dz) {
            return Math.abs(dx) <= HX + 1 && Math.abs(dz) <= HZ + 1;
        }

        @Override
        int reach() {
            return HZ + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return EAVE + HX + 6;
        }

        /** Roof height above the floor: a steep slope (block per block) from the eave to the ridge. */
        static int roof(int ax) {
            return EAVE + (HX + 1 - ax);
        }

        static boolean cover(int dx, int dz) {
            int[] rows = { -14, -7, 0, 7, 14 };
            for (int i = 0; i < rows.length; i++) {
                int sh = i % 2 == 0 ? 0 : 4; // rows staggered
                for (int cx = -8 + sh; cx <= 8; cx += 8) if (dx >= cx && dx <= cx + 1 && dz >= rows[i] && dz <= rows[i] + 1) return true;
            }
            return false;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            int roofY = TOP + roof(ax);
            // roof: nether brick stairs sloping outward, a second layer under them; ridge - obsidian with spikes
            if (ax == 0) {
                set(col, roofY, OBSIDIAN);
                if (Math.floorMod(dz, 4) == 0) set(col, roofY + 1, FENCE);
            } else {
                put(col, roofY, NSTAIR, dx < 0 ? 0 : 1);
                set(col, roofY - 1, NETHER);
            }
            if (ax == HX + 1) { // eave with crenellations
                put(col, roofY - 1, NSTAIR, (dx < 0 ? 0 : 1) | 4);
                if (Math.floorMod(dz, 2) == 0) set(col, roofY + 1, NETHER);
            }
            if (ax <= HX + 1 && az == HZ + 1) { // ends: blank up to the roof, in the south - the entrance
                set(col, TOP, BRICK);
                boolean door = dz > 0 && ax <= 1;
                for (int y = TOP + 1; y < roofY; y++) set(col, y, door && y <= TOP + 4 ? AIR : worn(dx, y * 3 + dz));
                if (door) set(col, TOP + 5, CHISEL);
                if (ax <= 1) for (int y = TOP + 12; y <= TOP + 14; y++) set(col, y, WorldGenNests.GLOW); // lantern window in the gable
                if (ax == 0 && dz < 0) set(col, roofY + 1, OBSIDIAN); // spire above the throne
                if (ax == 0 && dz < 0) set(col, roofY + 2, FENCE);
                return;
            }
            set(col, TOP, ax <= 1 ? NETHER : worn(dx, dz));
            if (ax == HX + 1 || ax == HX) { // arcade: pillars every 4, arches between them
                boolean pier = Math.floorMod(dz, 4) == 0;
                if (ax == HX + 1) {
                    for (int y = TOP + 1; y < roofY; y++) {
                        boolean arch = !pier && y <= TOP + 5 - (Math.floorMod(dz, 4) == 2 ? 0 : 1) + 1;
                        set(col, y, pier ? CHISEL : arch ? AIR : BRICK);
                    }
                    if (pier) set(col, TOP + EAVE - 2, WorldGenNests.GLOW);
                }
                return;
            }
            // hanging lamps along the axis
            if (ax == 0 && Math.floorMod(dz, 6) == 3) {
                for (int y = TOP + EAVE + 3; y < roofY; y++) set(col, y, FENCE);
                set(col, TOP + EAVE + 2, WorldGenNests.GLOW);
            }
            // dais and throne at the north end
            if (dz <= -(HZ - 5)) {
                boolean step = dz == -(HZ - 5);
                for (int y = TOP + 1; y <= TOP + DAIS_H; y++) set(col, y, step && y == TOP + DAIS_H ? SLAB : BRICK);
                if (dz == -HZ && ax <= 2) for (int y = TOP + DAIS_H + 1; y <= TOP + DAIS_H + (ax == 0 ? 5 : 3); y++) set(col, y, ax == 0 ? CHISEL : BRICK);
                if (dz == -HZ + 1 && ax <= 1) set(col, TOP + DAIS_H + 1, ax == 0 ? SLAB : AIR);
            }
            if (cover(dx, dz)) for (int y = TOP + 1; y <= TOP + COL_H; y++) set(col, y, CHISEL);
        }
    }

    // ---------------------------------------------------------------- Soul Fiend

    /**
     * Graveyard: a cemetery behind a nether brick fence on a brick plinth (lanterns on the posts), a gate from the south, paths of
     * gravel in a cross and in a circle, graves in rows (a mound + a carved stone headstone with a slab), dry trees with branches,
     * patches of soul sand, in the center a mausoleum with a pyramidal roof.
     */
    static class Graveyard extends Building {

        static final int R = 24, MAUS = 4;

        /** Fence and mausoleum are dark degraded brick, the roof is dark stone; headstones are carved stone (mechanics). */
        @Override
        int skin(int code) {
            switch (code) {
                case BRICK: case MOSSY: case CRACKED: return DDBRICK;
                case SLAB_TOP: return DARK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            altar(w, cx, cz, x0, z0, 0, TOP + 1, -1);
        }

        @Override
        boolean footprint(int dx, int dz) {
            return dx * dx + dz * dz <= (R + 1) * (R + 1);
        }

        @Override
        int reach() {
            return R + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return 10;
        }

        static boolean path(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            return Math.abs(dx) <= 1 || Math.abs(dz) <= 1 || Math.abs(r - 13) < 0.8;
        }

        /** Grave mound (the raised soul stands here); the headstone is one block south. */
        static boolean grave(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            return r > 6.5 && r < 20 && Math.floorMod(dx, 4) == 2 && Math.floorMod(dz, 4) == 0 && !path(dx, dz) && !path(dx, dz + 1);
        }

        @Override
        void build(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int ax = Math.abs(dx), az = Math.abs(dz);
            if (r > R - 0.5) { // fence
                set(col, TOP, BRICK);
                boolean gate = dz > 0 && ax <= 1;
                set(col, TOP + 1, gate ? AIR : BRICK);
                boolean post = Math.round(Math.atan2(dz, dx) * R) % 6 == 0;
                if (!gate) {
                    set(col, TOP + 2, post ? BRICK : FENCE);
                    set(col, TOP + 3, post ? WorldGenNests.GLOW : FENCE);
                }
                if (gate) set(col, TOP + 5, CHISEL);
                if (dz > 0 && ax == 2 && r > R - 0.5) for (int y = TOP + 1; y <= TOP + 5; y++) set(col, y, CHISEL); // gate posts
                return;
            }
            // mausoleum
            if (ax <= MAUS && az <= MAUS) {
                set(col, TOP, BRICK);
                boolean wallX = ax == MAUS, wallZ = az == MAUS, door = dz == MAUS && ax <= 1;
                if (wallX || wallZ) for (int y = TOP + 1; y <= TOP + 4; y++) set(col, y, door && y <= TOP + 3 ? AIR : (ax == MAUS && az == MAUS ? CHISEL : worn(dx, y + dz)));
                int roof = TOP + 5 + (MAUS - Math.max(ax, az)); // pyramid
                set(col, roof, Math.max(ax, az) == 0 ? WorldGenNests.GEM : SLAB_TOP);
                if (ax == 0 && az == 0) set(col, TOP + 4, WorldGenNests.GLOW); // light under the roof, a statue below
                return;
            }
            // ground, paths, patches of soul sand
            set(col, TOP - 1, WorldGenNests.DIRT);
            if (path(dx, dz)) set(col, TOP, GRAVEL);
            else set(col, TOP, hash(dx, dz) % 23 == 0 ? SOUL : WorldGenNests.GRASS);
            if (grave(dx, dz)) set(col, TOP, WorldGenNests.DIRT);
            else if (!path(dx, dz) && !grave(dx, dz - 1) && hash(dz, dx) % 13 == 0) set(col, TOP + 1, hash(dx, dz) % 3 == 0 ? EYEPLANT : BRAMBLE); // thickets
            if (grave(dx, dz - 1)) { // headstone
                set(col, TOP + 1, CHISEL);
                set(col, TOP + 2, SLAB);
            }
            // dry trees: trunk and branches
            double td = WorldGenNests.ringDist(dx, dz, 20, 6, 0.4);
            if (td < 0.6) for (int y = TOP + 1; y <= TOP + 6; y++) set(col, y, WorldGenNests.LOG);
            else if (td < 1.6 && hash(dx, dz) % 2 == 0) put(col, TOP + 4 + hash(dz, dx) % 2, WorldGenNests.LOG, 4 + 4 * (hash(dx, dz) % 2));
        }
    }

    // ---------------------------------------------------------------- Vamacheron

    /**
     * Bone Pit - an amphitheater: a pit (floor SINK lower) with bone pillars and scattered bones, a sheer pit wall with
     * descents from the north and south, stepped stands around, an outer wall with arches. Above the pit is the giant's ribcage:
     * a spine across and quartz arc ribs.
     */
    static class Pit extends Building {

        static final int R = 28, PIT = 14, SINK = 4, RING = 9, PILLARS = 6, PILLAR_H = 6;

        /** Divine dark stone and dark degraded brick; bones are quartz. */
        @Override
        int skin(int code) {
            switch (code) {
                case BRICK: case MOSSY: case CRACKED: return DARK;
                case CHISEL: return DDBRICK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            altar(w, cx, cz, x0, z0, 0, TOP - SINK + 1, 0);
        }
        static final int[] RIBS = { -12, -8, -4, 0, 4, 8, 12 };

        @Override
        boolean footprint(int dx, int dz) {
            return dx * dx + dz * dz <= (R + 1) * (R + 1);
        }

        @Override
        int reach() {
            return R + 1;
        }

        @Override
        int clear(int dx, int dz) {
            return 18;
        }

        static boolean pillar(int dx, int dz) {
            return WorldGenNests.ringDist(dx + 0.5, dz + 0.5, RING, PILLARS, Math.PI / PILLARS) < 1.1;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int ax = Math.abs(dx), floor = TOP - SINK;
            boolean ramp = ax <= 1 && r > PIT - 6; // descents from the north and south
            if (r <= PIT) { // pit
                int f = ramp ? Math.min(TOP, floor + (int) Math.max(0, (r - (PIT - 6)) * SINK / 6)) : floor;
                for (int y = f + 1; y <= TOP; y++) put(col, y, AIR, 0);
                set(col, f, hash(dx, dz) % 6 == 0 ? BONE : hash(dx, dz) % 5 == 0 ? SOUL : WorldGenNests.STONE);
                if (pillar(dx, dz)) for (int y = f + 1; y <= f + PILLAR_H; y++) set(col, y, PILLAR);
            } else if (r <= PIT + 1.5) { // pit wall
                if (ramp) set(col, TOP, BRICK);
                else for (int y = floor; y <= TOP + 1; y++) set(col, y, y == TOP + 1 ? CHISEL : BRICK);
            } else if (r <= R - 1.5) { // stands: steps going up
                int tier = (int) ((r - PIT - 1.5) / 2.2);
                for (int y = TOP + 1; y <= TOP + tier; y++) set(col, y, BRICK);
                set(col, TOP + tier, (int) ((r - PIT - 1.5) * 10) % 22 < 11 ? BRICK : SLAB_TOP);
                if (ramp) for (int y = TOP + 1; y <= TOP + tier; y++) put(col, y, AIR, 0); // passage
            } else { // outer wall with arches
                boolean arch = Math.round(Math.atan2(dz, dx) * R) % 5 == 0 || ramp;
                for (int y = TOP + 1; y <= TOP + 9; y++) set(col, y, arch && y <= TOP + 4 ? AIR : worn(dx, y + dz));
                if (Math.round(Math.atan2(dz, dx) * R) % 10 == 0) set(col, TOP + 10, WorldGenNests.GLOW);
            }
            if (r <= PIT && !ramp && hash(dx, dz) % 31 == 0) set(col, floor, WorldGenNests.GLOW);
            // the giant's ribcage above the pit: the spine along X, ribs are arcs in the YZ plane
            int base = floor, spineY = base + 13;
            if (dz == 0 && ax <= 15) put(col, spineY, PILLAR, 3); // spine (a pillar lying along X)
            for (int rib : RIBS) {
                if (Math.abs(dx - rib) > 0) continue;
                for (int y = base; y <= spineY; y++) {
                    double d = Math.sqrt(dz * dz + (y - base) * (y - base));
                    boolean broken = rib == 8 && dz < 0 && y < base + 6; // one rib is broken off
                    if (Math.abs(d - 13) < 0.6 && !broken) set(col, y, BONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Twilight Demon

    /**
     * Mage Castle: a square nether brick wall (2 blocks, 10 high, crenellations, a gate with a portcullis in the south), four
     * round corner towers with conical roofs; the northwest one is the mage spire, twice as high with crimson fire on top.
     * In the courtyard - the Demon's ritual circle: rings of obsidian and Mortum stone, a ring of light, six obelisks, braziers.
     */
    static class Castle extends Building {

        static final int WALL = 23, WALL_H = 10, TOWER = 23, TOWER_R = 5, TOWER_H = 18, SPIRE_H = 32, COURT = 19;

        /** Mortum palette: walls and towers are dark degraded brick, belts and rim are nether brick, roofs and obelisks are dark stone. */
        @Override
        int skin(int code) {
            switch (code) {
                case NETHER: return DDBRICK;
                case CHISEL: return NETHER;
                case OBSIDIAN: return DARK;
                case BRICK: case MOSSY: case CRACKED: return DARK;
                default: return code;
            }
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            altar(w, cx, cz, x0, z0, 0, TOP + 1, -11);
        }

        @Override
        double outside(int dx, int dz) {
            return Math.max(Math.abs(dx), Math.abs(dz)) - reach();
        }

        @Override
        boolean footprint(int dx, int dz) {
            return Math.max(Math.abs(dx), Math.abs(dz)) <= WALL + TOWER_R;
        }

        @Override
        int reach() {
            return WALL + TOWER_R;
        }

        @Override
        int clear(int dx, int dz) {
            return dx < -WALL + TOWER_R && dz < -WALL + TOWER_R ? SPIRE_H + 8 : TOWER_H + 6;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz), m = Math.max(ax, az);
            double r = Math.sqrt(dx * dx + dz * dz);
            // corner towers
            double tr = Math.sqrt((ax - TOWER) * (ax - TOWER) + (az - TOWER) * (az - TOWER));
            if (tr <= TOWER_R) {
                boolean spire = dx < 0 && dz < 0;
                int h = spire ? SPIRE_H : TOWER_H;
                set(col, TOP, NETHER);
                if (tr > TOWER_R - 1.3) { // tower wall, window lamps
                    for (int y = TOP + 1; y <= TOP + h; y++) set(col, y, (y - TOP) % 6 == 4 && (ax + az) % 3 == 0 ? WorldGenNests.GLOW : NETHER);
                    if ((ax * 3 + az) % 2 == 0) set(col, TOP + h + 1, NETHER); // crenellations
                }
                // conical roof
                int cone = TOP + h + 1 + (int) Math.round((TOWER_R - tr) * (spire ? 2.4 : 1.4));
                set(col, cone, OBSIDIAN);
                if (tr < 0.8) {
                    set(col, cone + 1, spire ? WorldGenNests.GEM : OBSIDIAN);
                    if (spire) set(col, cone + 2, WorldGenNests.GLOW);
                }
                return;
            }
            // wall with crenellations and a gate
            if (m >= WALL && m <= WALL + 1) {
                set(col, TOP, NETHER);
                boolean gate = dz > 0 && ax <= 1;
                for (int y = TOP + 1; y <= TOP + WALL_H; y++) set(col, y, gate && y <= TOP + 4 ? AIR : (y - TOP) % 5 == 0 ? CHISEL : NETHER);
                if (gate) set(col, TOP + 4, BARS); // gate portcullis
                if (m == WALL + 1 && (ax + az) % 2 == 0) set(col, TOP + WALL_H + 1, NETHER);
                if (m == WALL && (ax + az) % 8 == 0 && !gate) set(col, TOP + WALL_H - 3, WorldGenNests.GLOW);
                return;
            }
            if (m > WALL + 1) { // behind the wall
                set(col, TOP, worn(dx, dz));
                return;
            }
            // courtyard
            if (r > COURT) {
                set(col, TOP, ((dx + dz) & 1) == 0 ? NETHER : OBSIDIAN);
                if (WorldGenNests.ringDist(dx, dz, 21, 12, 0) < 0.5) {
                    set(col, TOP + 1, FENCE);
                    set(col, TOP + 2, WorldGenNests.GLOW); // braziers
                }
                return;
            }
            int ri = (int) r;
            set(col, TOP, r < 2.5 ? WorldGenNests.GEM : ri % 4 == 0 ? OBSIDIAN : ri % 4 == 2 ? WorldGenNests.GEM : WorldGenNests.STONE);
            if (r < 0.7) set(col, TOP, WorldGenNests.GLOW);
            if (WorldGenNests.ringDist(dx, dz, 6, 12, 0) < 0.5) set(col, TOP, WorldGenNests.GLOW);
            // rim of the circle with passages
            if (r > COURT - 0.6 && !(ax <= 1 || az <= 1)) set(col, TOP + 1, CHISEL);
            // obelisks
            double ob = WorldGenNests.ringDist(dx, dz, 15, 6, 0);
            if (ob < 1.6) {
                int oh = ob < 0.9 ? 9 : 6;
                for (int y = TOP + 1; y <= TOP + oh; y++) set(col, y, ob < 0.9 ? OBSIDIAN : WorldGenNests.GEM);
                if (ob < 0.9) set(col, TOP + oh + 1, WorldGenNests.GLOW);
            }
        }
    }

    // ---------------------------------------------------------------- Twilight Demon: the mage tower

    /**
     * The Twilight Mage tower on a cliff above Lethe - both a dungeon and the Demon's source of power.
     * <ul>
     * <li>The cliff from the terrain: ragged precipices, stone peaks, dark cracks. From the south a path winds up to the platform, and at the foot it is
     * carried by an arched bridge over the Lethe moat.</li>
     * <li>The fight platform at the summit: a ritual circle, obelisks, a crenellated parapet, skulls on spikes.</li>
     * <li>The main tower from the north: from the foot of the cliff to the crown, buttresses, a balcony in the middle, an overhanging crenellated belt with spikes and
     * chains, a conical spire. Inside, a spiral staircase along the wall and floors: dungeon, storeroom, a hall with an exit to the platform,
     * library, laboratory, chambers, the crown hall with the main power focus.</li>
     * <li>Three turrets with lantern foci on top (extinguished with a phaser from the platform) and skull cages under the corbels,
     * a round bastion on the side.</li>
     * </ul>
     */
    static class Spire extends Building {

        /** The platform is LIFT higher than the floor of other arenas: the cliff is almost 30 blocks above the bottom of the world. */
        static final int LIFT = 12, TOP = WorldGenNests.MORTUM_TOP + LIFT;
        /** The spire ends below the Mortum bedrock ceiling (~123). */
        static final int COURT = 15, CRAG = 44, TX = 0, TZ = -21, TR = 9, TH = 44, ROOF = 18, STEP = 14, BASE = -28;
        /** Tower floors (from the platform floor): dungeon, storeroom, hall, library, laboratory, chambers, crown hall. */
        static final int[] FLOORS = { -21, -14, -7, 0, 7, 14, 21, 28, 35 };
        static final int CROWN = 35, BALCONY = 21;
        /** Turrets: angle around the tower, bottom and top (from the platform floor), radius. */
        static final double[][] TURRETS = { { 0.15, 6, 30, 2.8 }, { -1.15, 16, 38, 2.6 }, { -2.6, -2, 26, 2.9 } };
        /** The Lethe moat at the foot (from the south and sides), a bridge on the path. */
        static final int MOAT_IN = 39, MOAT_OUT = 44;
        static final int FOCUS = 90, BOOKS = 91;

        @Override
        int skin(int code) {
            switch (code) {
                case NETHER: return DDBRICK;
                case CHISEL: return NETHER;
                case OBSIDIAN: return DARK;
                default: return code;
            }
        }

        @Override
        protected Block block(int code, int meta) {
            if (code == FOCUS) return Blocks.glowstone;
            if (code == BOOKS) return Blocks.bookshelf;
            if (code == WorldGenNests.WATER) return com.divinerpgextended.registry.ModBlocks.lethe;
            return super.block(code, meta);
        }

        @Override
        boolean slopes() {
            return false;
        }

        @Override
        boolean floorLayer() {
            return false; // platform LIFT higher, solid rock under it; at the height of other arenas - a slope
        }

        @Override
        int foundationCode() {
            return WorldGenNests.STONE;
        }

        @Override
        boolean footprint(int dx, int dz) {
            return dx * dx + dz * dz <= MOAT_OUT * MOAT_OUT || tower(dx, dz) <= TR + 4;
        }

        @Override
        int reach() {
            return MOAT_OUT + 1;
        }

        /** Cleared high only around the tower; above the courtyard - for obelisks and parapet; slopes - with their own dome ({@link #dome}). */
        @Override
        int clear(int dx, int dz) {
            // height - from the floor of other arenas (Building.TOP), and the tower platform is LIFT higher
            if (tower(dx, dz) <= TR + 3) return LIFT + TH + ROOF + 8; // narrow: the tower with turrets and buttresses
            return dx * dx + dz * dz <= (COURT + 1) * (COURT + 1) ? LIFT + 12 : 0;
        }

        /** Top of the clearing above the cliff slope: a dome above the courtyard, descending outward. The tower passes through the upper layers by itself. */
        static int dome(double r, double td) {
            return TOP + 12 - (int) ((r - COURT - 1) * 1.0);
        }

        static double tower(int dx, int dz) {
            return Math.sqrt((dx - TX) * (dx - TX) + (dz - TZ) * (dz - TZ));
        }

        /** Smooth noise [0, 1) over the cell grid. */
        static double smooth(int x, int z, int cell, int salt) {
            int ix = Math.floorDiv(x, cell), iz = Math.floorDiv(z, cell);
            double tx = (x - ix * cell) / (double) cell, tz = (z - iz * cell) / (double) cell;
            double a = hash(ix * 7 + salt, iz) % 1000 / 1000.0, b = hash(ix * 7 + 7 + salt, iz) % 1000 / 1000.0;
            double c = hash(ix * 7 + salt, iz + 1) % 1000 / 1000.0, d = hash(ix * 7 + 7 + salt, iz + 1) % 1000 / 1000.0;
            return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
        }

        /**
         * Cliff rock height (0 - level with the platform): a plateau - the platform circle and the circle under the tower, then a precipice - steep at the top,
         * gentler toward the bottom, with ledges; occasionally stone peaks above the slope.
         */
        static int crag(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz), td = tower(dx, dz);
            double edge = Math.min(r - COURT - 1, td - TR - 2) - smooth(dx, dz, 7, 1) * 4;
            if (edge <= 0) return 0;
            double drop = edge * (3.0 - edge * 0.06) + smooth(dx, dz, 3, 5) * 3;
            double ledge = smooth(dx, dz, 5, 9) > 0.62 ? 3 : 0;
            double pin = smooth(dx, dz, 4, 13) > 0.8 && hash(dx, dz) % 3 != 0 ? 4 + smooth(dx, dz, 2, 17) * 8 : 0; // peaks
            return -(int) Math.max(0, drop - ledge - pin);
        }

        /** Cliff rock like the Mortum precipices around: dirt with wavy layers of stone, without foreign flecks. */
        static int rock(int dx, int y, int dz) {
            double band = (y + smooth(dx, dz, 9, 21) * 4) % 9;
            return band < 2 ? WorldGenNests.STONE : WorldGenNests.DIRT;
        }

        /** Height of the spiral staircase step in a ring column (from the platform floor), nearest to h. */
        static int stair(int dx, int dz, int k) {
            double ang = (Math.atan2(dz - TZ, dx - TX) + Math.PI * 2) % (Math.PI * 2);
            return BASE + (int) (ang / (Math.PI * 2) * STEP) + k * STEP;
        }

        @Override
        void build(int dx, int dz, int[] col) {
            double r = Math.sqrt(dx * dx + dz * dz), td = tower(dx, dz);
            int ax = Math.abs(dx), az = Math.abs(dz);
            boolean path = dz > 0 && ax <= 1 && r > COURT - 1; // path from the south
            int ground = TOP - LIFT - 16; // bottom of the world
            int top = TOP + crag(dx, dz);
            if (path) top = Math.max(top, TOP - (int) ((r - COURT) * 1.0));
            top = Math.max(top, ground);
            // Lethe moat at the foot (south and sides), a bridge on the path
            boolean moat = false; // the moat cut the terrain like a canyon; the river flows by the arena anyway
            if (moat) { // the moat is dug only at the bottom; above - as it was (we also remove the common TOP - 5 foundation and fill)
                for (int y = ground + 6; y <= TOP; y++) put(col, y, KEEP, 0);
                for (int y = ground - 4; y <= ground + 5; y++) put(col, y, AIR, 0);
                set(col, ground - 4, WorldGenNests.STONE);
                for (int y = ground - 3; y <= MortumWorld.SEA; y++) put(col, y, WorldGenNests.WATER, 0);
                if (path || (ax <= 2 && dz > 0)) { // bridge: deck, railings, an arch from below
                    int deck = MortumWorld.SEA + 4;
                    if (ax <= 1) set(col, deck, NETHER);
                    if (ax == 2) {
                        set(col, deck, NETHER);
                        set(col, deck + 1, FENCE);
                    }
                    double mid = (MOAT_IN + MOAT_OUT) / 2.0;
                    int arch = deck - 1 - (int) Math.round(2.5 * (1 - Math.abs(r - mid) / ((MOAT_OUT - MOAT_IN) / 2.0 + 0.5)));
                    for (int y = arch; y < deck; y++) if (ax <= 2) set(col, y, NETHER);
                }
                return;
            }
            if (r > MOAT_OUT) { // beyond the moat - the ground as it was
                for (int y = TOP - 5; y <= TOP + 10; y++) put(col, y, KEEP, 0);
                return;
            }
            if (r > COURT + 1) { // cliff slope: above the rock - natural terrain, except the clearing dome
                for (int y = top + 1; y <= TOP; y++) put(col, y, KEEP, 0); // remove the structure's common fill above the slope
                int dome = Math.max(dome(r, td), path ? top + 4 : 0);
                for (int y = top + 1; y <= dome; y++) put(col, y, AIR, 0);
                put(col, top - 4, FOUNDATION, 0);
                for (int y = top - 3; y < top; y++) set(col, y, rock(dx, y, dz));
                set(col, top, path ? GRAVEL : top - TOP > -3 || hash(dx, dz) % 3 != 0 ? WorldGenNests.GRASS : rock(dx, top, dz));
                if (path && (int) r % 3 == 0) set(col, top, SLAB_TOP);
            } else { // platform: a ritual circle, solid rock under it (no cavity at the height of other arenas)
                for (int y = TOP - LIFT - 4; y < TOP; y++) set(col, y, rock(dx, y, dz));
                int ri = (int) r;
                set(col, TOP, r < 2.5 ? WorldGenNests.GEM : ri % 4 == 0 ? OBSIDIAN : ri % 4 == 2 ? NETHER : worn(dx, dz));
                if (r < 0.7 || WorldGenNests.ringDist(dx, dz, 6, 12, 0) < 0.5) set(col, TOP, WorldGenNests.GLOW);
                double ob = WorldGenNests.ringDist(dx, dz, 11, 6, Math.PI / 6);
                if (ob < 1.2 && td > TR + 2) {
                    for (int y = TOP + 1; y <= TOP + (ob < 0.7 ? 6 : 4); y++) set(col, y, OBSIDIAN);
                    if (ob < 0.7) set(col, TOP + 7, WorldGenNests.GLOW);
                }
                if (r > COURT - 0.5 && !(dz > 0 && ax <= 1) && td > TR + 1) { // parapet with crenellations, a gate in the south
                    set(col, TOP + 1, NETHER);
                    set(col, TOP + 2, NETHER);
                    if ((ax + az) % 2 == 0) set(col, TOP + 3, NETHER);
                }
                if (r > COURT - 0.5 && dz > 0 && ax == 2) for (int y = TOP + 1; y <= TOP + 5; y++) set(col, y, CHISEL);
                if (r > COURT - 0.5 && dz > 0 && ax <= 1) set(col, TOP + 5, CHISEL);
            }
            // lower entrance: a passage from the door at the foot of the tower under the courtyard and slope to the end of the path (the door used to face the rock)
            if (dz > TZ && ax <= 2 && td > TR - 1.3) {
                int base = TOP + BASE;
                if (ax <= 1) {
                    set(col, base, NETHER);
                    for (int y = base + 1; y <= base + 4; y++) put(col, y, AIR, 0);
                    if (top > base + 5) set(col, base + 5, ax == 0 && Math.floorMod(dz, 6) == 0 ? WorldGenNests.GLOW : NETHER);
                } else if (top > base + 4) for (int y = base + 1; y <= base + 4; y++) set(col, y, NETHER);
            }
            tower(dx, dz, td, col);
            turrets(dx, dz, col);
        }

        /** Main tower: walls with windows, buttresses, floors and a spiral staircase, a balcony, a belt with spikes and chains, a spire. */
        private void tower(int dx, int dz, double td, int[] col) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            double ang = Math.atan2(dz - TZ, dx - TX);
            int base = TOP + BASE;
            // buttresses: four diagonal ribs, narrowing from the foot toward the middle of the tower
            double ribA = Math.abs(((ang % (Math.PI / 2)) + Math.PI / 2) % (Math.PI / 2) - Math.PI / 4); // zero on the diagonals, doors are free
            if (td > TR && td <= TR + 3.5 && ribA * td < 0.8) {
                int h = (int) ((TR + 3.5 - td) / 3.5 * (BALCONY - BASE)) + base;
                put(col, base - 1, FOUNDATION, 0);
                for (int y = base; y <= h; y++) set(col, y, NETHER);
                set(col, h + 1, SLAB_TOP);
            }
            // balcony: a ring of slabs outside with railings
            if (td > TR && td <= TR + 1.6) {
                set(col, TOP + BALCONY, SLAB_TOP);
                if (td > TR + 0.8) set(col, TOP + BALCONY + 1, FENCE);
            }
            if (td > TR + 1.6) return;
            if (td <= TR) {
                put(col, base - 1, FOUNDATION, 0);
                boolean wall = td > TR - 1.3;
                for (int y = base; y <= TOP + TH; y++) {
                    int h = y - TOP;
                    if (wall) {
                        boolean window = h % 7 == 3 && (int) ((ang + Math.PI) * 4) % 3 == 0 && h > -20;
                        boolean crownWin = h > CROWN && h < TH && (int) ((ang + Math.PI) * 4) % 2 == 0; // wide windows of the crown hall
                        put(col, y, AIR, 0);
                        if (!crownWin) set(col, y, window ? WorldGenNests.GLOW : h % 7 == 0 ? CHISEL : NETHER);
                        continue;
                    }
                    boolean ring = td > TR - 3.3; // staircase ring along the wall
                    boolean floor = false;
                    for (int f : FLOORS) if (h == f && (!ring || h == CROWN)) floor = true;
                    put(col, y, floor ? (h == CROWN ? OBSIDIAN : NETHER) : AIR, 0);
                    if (ring && h < CROWN) for (int k = 0; k < 6; k++) if (h == stair(dx, dz, k)) set(col, y, NETHER);
                }
                // doors: to the platform (south, level with it) and below to the path at the foot (south)
                if (dz > TZ && ax <= 1 && td > TR - 1.3) {
                    for (int y = TOP + 1; y <= TOP + 4; y++) put(col, y, AIR, 0);
                    for (int y = base + 1; y <= base + 4; y++) put(col, y, AIR, 0);
                }
                // main power focus in the crown hall on a pedestal
                if (td < 0.7) {
                    set(col, TOP + CROWN + 1, OBSIDIAN);
                    put(col, TOP + CROWN + 2, FOCUS, 0);
                }
            }
            // overhanging belt with crenellations and spikes, chains down
            if (td > TR - 1.3) {
                set(col, TOP + TH + 1, NETHER);
                set(col, TOP + TH + 2, NETHER);
                if ((ax + az) % 2 == 0) {
                    set(col, TOP + TH + 3, NETHER);
                    if ((ax * 3 + az) % 6 == 0) {
                        set(col, TOP + TH + 4, FENCE);
                        set(col, TOP + TH + 5, OBSIDIAN);
                    }
                }
                if (td > TR) {
                    set(col, TOP + TH, SLAB_TOP);
                    if ((ax * 5 + az * 3) % 11 == 0) for (int y = TOP + TH - 7; y < TOP + TH; y++) set(col, y, BARS); // chains
                }
            }
            // spire
            if (td <= TR - 1) {
                // roof: solid (thickness - the height difference between neighboring columns, without gaps), dark brick with
                // obsidian belts, on the top an obsidian spire with fire
                double k = ROOF / (double) TR;
                int cone = TOP + TH + 2 + (int) Math.round((TR - td + 0.3) * k), shell = (int) Math.ceil(k) + 1;
                for (int y = TOP + TH + 2; y < cone - shell; y++) put(col, y, AIR, 0);
                for (int y = Math.max(TOP + TH + 2, cone - shell); y <= cone; y++) set(col, y, (cone - TOP - TH) % 5 == 0 ? OBSIDIAN : NETHER);
                if (td < 0.7) for (int y = cone; y <= cone + 4; y++) set(col, y, y == cone + 4 ? WorldGenNests.GLOW : OBSIDIAN);
            }
        }

        /** Turret center (x, z). */
        static double[] turret(double[] t) {
            return new double[] { TX + Math.cos(t[0]) * (TR + 1.8), TZ + Math.sin(t[0]) * (TR + 1.8) };
        }

        /** Turrets: walls, a corbel from below, a lantern focus on top under a cone. */
        private void turrets(int dx, int dz, int[] col) {
            for (double[] t : TURRETS) {
                double[] c = turret(t);
                double d = Math.sqrt((dx - c[0]) * (dx - c[0]) + (dz - c[1]) * (dz - c[1])), tr = t[3];
                if (d > tr + 0.3) continue;
                int lo = TOP + (int) t[1], hi = TOP + (int) t[2];
                for (int y = lo; y <= hi; y++) set(col, y, NETHER);
                set(col, lo - 1, SLAB_TOP); // corbel
                // lantern: open windows near the top, the focus in the center
                if (d > tr - 1) {
                    boolean win = ((int) Math.round((Math.atan2(dz - c[1], dx - c[0]) + Math.PI) * 2)) % 2 == 0;
                    if (win) for (int y = hi - 3; y <= hi - 1; y++) put(col, y, AIR, 0);
                } else {
                    for (int y = hi - 3; y <= hi - 1; y++) put(col, y, AIR, 0);
                    if (d < 0.8) put(col, hi - 2, FOCUS, 0);
                }
                int cone = hi + 1 + (int) Math.round((tr + 0.3 - d) * 2.4);
                for (int y = Math.max(hi + 1, cone - 3); y <= cone; y++) set(col, y, OBSIDIAN); // solid cone, without gaps
                if (d < 0.6) {
                    set(col, cone + 1, OBSIDIAN);
                    set(col, cone + 2, FENCE);
                }
            }
        }

        /** The Demon's power foci (offsets from the center): three turret lanterns and the main one in the crown hall. */
        static java.util.List<int[]> foci() {
            java.util.List<int[]> l = new java.util.ArrayList<int[]>();
            for (double[] t : TURRETS) {
                double[] c = turret(t);
                l.add(new int[] { (int) Math.floor(c[0] + 0.5), TOP + (int) t[2] - 2, (int) Math.floor(c[1] + 0.5) });
            }
            l.add(new int[] { TX, TOP + CROWN + 2, TZ });
            return l;
        }

        @Override
        protected void decorate(World w, int cx, int cz, int x0, int z0) {
            altar(w, cx, cz, x0, z0, 0, TOP + 1, 0); // in the center of the ritual circle
            // skulls on spikes around the platform
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5 + 0.3;
                int px = (int) Math.round(Math.cos(a) * (COURT + 2)), pz = (int) Math.round(Math.sin(a) * (COURT + 2));
                if (tower(px, pz) < TR + 3 || (pz > 0 && Math.abs(px) <= 3)) continue;
                int y = TOP + crag(px, pz) + 1;
                place(w, cx, cz, x0, z0, px, y, pz, Blocks.nether_brick_fence, 0);
                place(w, cx, cz, x0, z0, px, y + 1, pz, Blocks.nether_brick_fence, 0);
                skull(w, cx, cz, x0, z0, px, y + 2, pz, i);
            }
            // skull cages under the turret corbels
            for (double[] t : TURRETS) {
                double[] c = turret(t);
                int tx = (int) Math.floor(c[0] + 0.5) + (int) Math.round(Math.cos(t[0]) * 4), tz = (int) Math.floor(c[1] + 0.5) + (int) Math.round(Math.sin(t[0]) * 4);
                int ty = TOP + (int) t[1] - 3;
                for (int y = ty; y <= ty + 2; y++) for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++)
                    if (Math.abs(i) == 1 || Math.abs(k) == 1 || y == ty) place(w, cx, cz, x0, z0, tx + i, y, tz + k, Blocks.iron_bars, 0);
                place(w, cx, cz, x0, z0, tx, ty - 1, tz, Blocks.iron_bars, 0);
                skull(w, cx, cz, x0, z0, tx, ty + 1, tz, 3);
                for (int y = ty + 3; y < TOP + (int) t[1] - 1; y++) place(w, cx, cz, x0, z0, tx, y, tz, Blocks.iron_bars, 0); // chain to the corbel
            }
            // floor furnishings
            int base = TOP + BASE;
            TwilightWorld tw = TwilightWorld.MORTUM;
            spawner(w, cx, cz, x0, z0, TX, base + 1, TZ, tw); // dungeon
            spawner(w, cx, cz, x0, z0, TX + 2, TOP - 21 + 1, TZ, tw);
            chest(w, cx, cz, x0, z0, TX + 3, TOP - 14 + 1, TZ, 5); // storeroom
            chest(w, cx, cz, x0, z0, TX - 3, TOP + 7 + 1, TZ + 1, 7); // library
            for (int i = -2; i <= 2; i++) {
                place(w, cx, cz, x0, z0, TX + i, TOP + 7 + 1, TZ - 4, Blocks.bookshelf, 0);
                place(w, cx, cz, x0, z0, TX + i, TOP + 7 + 2, TZ - 4, Blocks.bookshelf, 0);
            }
            place(w, cx, cz, x0, z0, TX + 1, TOP + 14 + 1, TZ, Blocks.brewing_stand, 0); // laboratory
            place(w, cx, cz, x0, z0, TX - 1, TOP + 14 + 1, TZ, Blocks.cauldron, 3);
            spawner(w, cx, cz, x0, z0, TX, TOP + 21 + 1, TZ + 2, tw); // chambers - guards
            chest(w, cx, cz, x0, z0, TX + 2, TOP + 28 + 1, TZ - 2, 6);
        }

        static void skull(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, int rot) {
            if (!place(w, cx, cz, x0, z0, dx, y, dz, Blocks.skull, 1)) return;
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(cx + dx, y, cz + dz);
            if (te instanceof net.minecraft.tileentity.TileEntitySkull) ((net.minecraft.tileentity.TileEntitySkull) te).func_145903_a(rot * 3 % 16);
        }

        static void chest(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, int count) {
            if (!place(w, cx, cz, x0, z0, dx, y, dz, Blocks.chest, 0)) return;
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(cx + dx, y, cz + dz);
            if (te instanceof net.minecraft.tileentity.TileEntityChest)
                net.minecraft.util.WeightedRandomChestContent.generateChestContents(new java.util.Random(cx * 31L + y * 7L + cz), TwilightLoot.dungeon(TwilightWorld.MORTUM),
                    (net.minecraft.tileentity.TileEntityChest) te, count);
        }

        static void spawner(World w, int cx, int cz, int x0, int z0, int dx, int y, int dz, TwilightWorld tw) {
            if (!com.divinerpgextended.Config.dungeonSpawners || !place(w, cx, cz, x0, z0, dx, y, dz, Blocks.mob_spawner, 0)) return;
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(cx + dx, y, cz + dz);
            Class<? extends net.minecraft.entity.EntityLiving>[] mobs = tw.mobs();
            String name = (String) net.minecraft.entity.EntityList.classToStringMapping.get(mobs[Math.abs(dx * 7 + y) % mobs.length]);
            if (te instanceof net.minecraft.tileentity.TileEntityMobSpawner && name != null)
                ((net.minecraft.tileentity.TileEntityMobSpawner) te).func_145881_a().setEntityName(name);
        }
    }
}
