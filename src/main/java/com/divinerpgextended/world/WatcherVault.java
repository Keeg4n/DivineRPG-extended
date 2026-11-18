package com.divinerpgextended.world;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.divinerpgextended.world.twilight.ColumnStructure;

/**
 * The Watcher's lair: the Eye observatory under the Nether lava lake. A hall of radius {@link #R} right at the bottom, an obsidian shell,
 * a layer of lava above the vault; at the center of the vault is a glass oculus through which the lava overhead is visible. On the floor is a mosaic of the eye
 * (white of quartz, iris of red clay, an obsidian pupil) and obsidian rays to the walls. Eight obsidian columns
 * with quartz bases, Nether brick belts and lamps are cover from the boss's gaze. In the walls are red glass windows with
 * lava behind them, on the vault are Glowstone stars and rays from the oculus.
 * <p>
 * The entrance is a sloping tunnel from the shore (+X): half-block steps, Nether brick ribs with Glowstone, lamps in the walls; at
 * the hall - a portal with quartz columns. Outside - a gate in the sheer wall of a rocky hill (pylons, columns, the lair's crest) and a
 * short porch: in rock it is simply an entrance in a cliff, over lava - a stony islet with piled walkways.
 * The hall is permanent: boss explosions do not break it ({@link com.divinerpgextended.boss.WatcherHunt}). A lair of the old kind
 * (with a rubble pile) is rebuilt into the new one when a player arrives and the Watcher is absent ({@link #rebuild}).
 */
public class WatcherVault extends ColumnStructure {

    /** Hall radius, floor (the floor block; they stand on FLOOR + 1), top of the hall at the center. */
    public static final int R = 22, FLOOR = 7, TOP = FLOOR + 19, LAVA_TOP = 31;
    /** Tunnel: a rise of 1 block per 2 horizontally to a height above the lava, then straight and a landing. */
    public static final int TUNNEL_Y = 33, TUNNEL_FLAT = 6;
    /** Cover columns: count, circle radius, thickness. */
    public static final int PILLARS = 8, PILLAR_RING = 15;
    public static final double PILLAR_R = 1.75;
    /** Porch in front of the gate (length); the rocky hill above the gate (depth, half-width). */
    static final int PIER = 5, MOUND_L = 12, MOUND_W = 7;

    protected static final int OBSIDIAN = 1, NETHERRACK = 2, BRICK = 4, GLOWSTONE = 5, QUARTZ = 6, LAMP = 7, LAVA = 8, SLAB = 9, CLAY = 10, GLASS = 11,
        FENCE = 12, CLEAR = 13, CHECKER = 14, FIRE = 15;

    public WatcherVault(int cx, int cz) {}

    @Override
    protected Block block(int code, int meta) {
        switch (code) {
            case OBSIDIAN: return Blocks.obsidian;
            case NETHERRACK: return Blocks.netherrack;
            case BRICK: return Blocks.nether_brick;
            case GLOWSTONE: return Blocks.glowstone;
            case QUARTZ: return WhiteStone.block(); // milk stone, not quartz (1.1.5)
            case LAMP: return net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
            case LAVA: return Blocks.lava;
            case SLAB: return Blocks.stone_slab;
            case CLAY: return Blocks.stained_hardened_clay;
            case GLASS: return Blocks.stained_glass;
            case FENCE: return Blocks.nether_brick_fence;
            case CLEAR: return Blocks.glass;
            case CHECKER: return net.divinerpg.utils.blocks.VanillaBlocks.checker; // Divine black-and-white checker
            case FIRE: return Blocks.fire;
            default: return Blocks.air;
        }
    }

    @Override
    protected int metaOf(int code, int meta) {
        if (code == QUARTZ) return 0; // the white has no carved and pillar variants
        return code == LAMP ? 6 : meta; // Divine purple lamp
    }

    @Override
    protected Block foundation() {
        return Blocks.nether_brick; // piles of the landing and towers
    }

    @Override
    protected boolean lit(Block b) {
        return b == Blocks.glowstone || b == Blocks.lava || b == Blocks.fire || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
    }

    /** Top of the hall at distance r from the center: a dome, lower at the walls. */
    public static int top(double r) {
        return r < R - 6 ? TOP : TOP - (int) Math.ceil((r - (R - 6)) * 1.1);
    }

    /** Inside the hall (the shell and tunnel - no). */
    public static boolean interior(int dx, int y, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        return r < R && y > FLOOR && y <= top(r);
    }

    /** Height where the boss hovers (the bottom of its frame is lower by half its height). */
    public static int bossY() {
        return FLOOR + 9;
    }

    /** Center {x, z} of the nearest column relative to the hall center. */
    public static double[] pillar(double dx, double dz) {
        double step = Math.PI * 2 / PILLARS, a = Math.atan2(dz, dx) - step / 2;
        double ca = Math.round(a / step) * step + step / 2;
        return new double[] { Math.cos(ca) * PILLAR_RING, Math.sin(ca) * PILLAR_RING };
    }

    // ---------------------------------------------------------------- hall

    /** The eye on the floor: inside an "almond" along the X axis (grow - widen by that many blocks). */
    static boolean lens(double dx, double dz, double grow) {
        double w = 15.5 + grow, half = 8.5 * (1 - (dx / w) * (dx / w)) + grow;
        return Math.abs(dx) < w && Math.abs(dz) <= half;
    }

    /** Hall floor: eye mosaic, rays, Glowstone by the walls. */
    static void floor(int dx, int dz, double r, int[] col) {
        put(col, FLOOR - 1, OBSIDIAN, 0);
        int code = BRICK, meta = 0;
        if (lens(dx, dz, 0)) {
            double a = Math.atan2(dz, dx);
            if (!lens(dx, dz, -1)) code = OBSIDIAN; // eyelid outline
            else if (r < 2.3) code = OBSIDIAN; // pupil
            else if (r < 5.5) {
                code = CLAY;
                meta = Math.abs(Math.sin(a * 6)) > 0.85 ? 1 : 14; // iris with streaks
            } else if (r < 6.3) {
                code = CLAY;
                meta = 15;
            } else {
                code = QUARTZ;
                if (hash(dx * 17 + 3, dz * 29 - 5) % 19 == 0) {
                    code = CLAY; // blood vessels on the white
                    meta = 14;
                }
            }
        } else {
            double[] p = pillar(dx, dz);
            double ray = Math.abs(Math.sin(Math.atan2(dz, dx) * 4)) * r; // 8 rays along the axes and diagonals
            if (Math.hypot(dx - p[0], dz - p[1]) < PILLAR_R + 0.8) code = OBSIDIAN;
            else if (r > 16.4 && r < 17.4) {
                code = QUARTZ;
                meta = 1;
            } else if (ray < 0.7 && r > 17) code = Math.abs(r - 20) < 0.7 ? GLOWSTONE : OBSIDIAN;
            else if (r >= R - 1) code = OBSIDIAN;
        }
        put(col, FLOOR, code, meta);
    }

    /** A window in the wall: 11 of them every 30 degrees (the entrance is in the east), 3 blocks wide. */
    protected static boolean window(int dx, int dz, double r) {
        double step = Math.PI / 6, a = Math.atan2(dz, dx);
        long k = Math.round(a / step);
        return k != 0 && Math.abs(a - k * step) * r <= 1.5;
    }

    protected static final int WIN_LO = FLOOR + 2, WIN_HI = FLOOR + 7;

    /** A hall cell - window glass. */
    static boolean glassCell(int dx, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        return r >= R - 1 && r < R && window(dx, dz, r);
    }

    /**
     * Lava behind the window glass - only if from the hall side it is covered by glass on all four sides: on the circle's diagonals
     * the neighboring hall cell is sometimes no longer a window but air - lava poured into the hall.
     */
    static boolean behindGlass(int dx, int dz, double r) {
        if (!window(dx, dz, r)) return false;
        int[][] n = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] d : n) {
            int x = dx + d[0], z = dz + d[1];
            if (x * x + z * z < R * R && !glassCell(x, z)) return false;
        }
        return true;
    }

    /**
     * Patch the windows of a finished lair (built before 0.99.56): lava without glass on the hall side - to obsidian, flowing lava
     * that spilled into the hall - remove. @return how many blocks were changed
     */
    public static int seal(World w, int cx, int cz) {
        int changed = 0;
        for (int dx = -R - 1; dx <= R + 1; dx++) {
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx, z = cz + dz;
                if (r >= R && r < R + 1 && window(dx, dz, r) && !behindGlass(dx, dz, r)) {
                    for (int y = WIN_LO; y <= WIN_HI; y++)
                        if (w.getBlock(x, y, z).getMaterial() == net.minecraft.block.material.Material.lava) {
                            w.setBlock(x, y, z, Blocks.obsidian, 0, 2);
                            changed++;
                        }
                } else if (r < R) {
                    for (int y = FLOOR + 1; y <= WIN_HI + 1; y++) {
                        Block b = w.getBlock(x, y, z);
                        if ((b == Blocks.flowing_lava || b == Blocks.lava) && w.getBlockMetadata(x, y, z) != 0) {
                            w.setBlock(x, y, z, Blocks.air, 0, 2);
                            changed++;
                        }
                    }
                }
            }
        }
        return changed;
    }

    /** A cell at the hall wall: a window (sill, glass, lintel); true - placed. glass - the glass meta. */
    protected static boolean wallCell(int dx, int dz, double r, int y, int[] col, int glass) {
        if (r < R - 1 || !window(dx, dz, r)) return false;
        if (y == WIN_LO - 1 || y == WIN_HI + 1) put(col, y, QUARTZ, 1); // sill and lintel
        else if (y >= WIN_LO && y <= WIN_HI) put(col, y, GLASS, glass);
        else return false;
        return true;
    }

    /** The vault above the hall: an oculus into the lava, a ring and rays around it, stars; above - the lava lake. */
    protected static void vault(int dx, int dz, double r, int t, int[] col) {
        if (r < 3.5) {
            put(col, t + 1, CLEAR, 0);
            put(col, t + 2, CLEAR, 0);
        } else {
            int code = OBSIDIAN, meta = 0;
            if (r < 4.6) {
                code = QUARTZ;
                meta = 1;
            } else if (r < 11 && Math.abs(Math.sin(Math.atan2(dz, dx) * 6)) * r < 0.55) code = BRICK;
            else if (hash(dx * 7 + 1, dz * 13 + 2) % 31 == 0) code = GLOWSTONE;
            put(col, t + 1, code, meta);
            put(col, t + 2, OBSIDIAN, 0);
        }
        for (int y = t + 3; y <= LAVA_TOP; y++) put(col, y, LAVA, 0);
    }

    /** A hall column (r < R): floor, pillar or air, vault with an oculus, lava above it. */
    protected void hall(int dx, int dz, double r, int[] col) {
        int t = top(r);
        floor(dx, dz, r, col);
        double[] p = pillar(dx, dz);
        double pd = Math.hypot(dx - p[0], dz - p[1]);
        double inward = -((dx - p[0]) * p[0] + (dz - p[1]) * p[1]) / Math.max(0.01, pd * PILLAR_RING); // the face toward the hall center
        for (int y = FLOOR + 1; y <= t; y++) {
            int code = AIR, meta = 0, h = y - FLOOR;
            if (pd < PILLAR_R + (y >= t - 2 ? (y - (t - 3)) * 0.8 : 0)) {
                if (y >= t - 2 && pd >= PILLAR_R - 0.3) code = BRICK; // the capital goes into the vault
                else if (h % 6 == 0) code = BRICK; // belts
                else if (h % 6 == 3 && pd > 0.9 && inward > 0.75) code = LAMP; // lamps toward the center
                else code = OBSIDIAN;
            } else if (h <= 2 && pd < PILLAR_R + 0.6) {
                code = QUARTZ; // base
                meta = h == 1 ? 0 : 1;
            } else if (wallCell(dx, dz, r, y, col, 14)) continue;
            put(col, y, code, meta);
        }
        vault(dx, dz, r, t, col);
    }

    @Override
    protected boolean column(int dx, int dz, int[] col) {
        double r = Math.sqrt(dx * dx + dz * dz);
        boolean tunnel = dx >= R - 4 && Math.abs(dz) <= MOUND_W && dx <= tunnelEnd() + PIER;
        if (r > R + 4 && !tunnel) return false;
        if (r < R) hall(dx, dz, r, col);
        else if (r < R + 2) {
            int t = top(R - 0.5);
            for (int y = FLOOR - 1; y <= t + 2; y++) put(col, y, OBSIDIAN, 0); // wall
            if (r < R + 1 && behindGlass(dx, dz, r)) for (int y = WIN_LO; y <= WIN_HI; y++) put(col, y, LAVA, 0); // lava behind glass
            for (int y = t + 3; y <= LAVA_TOP; y++) put(col, y, LAVA, 0);
        } else if (r <= R + 4) {
            // the lake shore is above the lava: we leave the top of the ring as is, otherwise a circle above the lair is visible on the lava from above
            for (int y = FLOOR - 1; y <= LAVA_TOP - 3; y++) put(col, y, NETHERRACK, 0);
        }
        if (tunnel) tunnel(dx, dz, col);
        return true;
    }

    // ---------------------------------------------------------------- entrance

    /** The last tunnel column is the gate facade (the porch is beyond). */
    public static int tunnelEnd() {
        return R - 1 + (TUNNEL_Y - (FLOOR + 1)) * 2 + TUNNEL_FLAT;
    }

    /**
     * The hill above the gate: a rocky mound of netherrack, cut by a sheer wall at the facade. In rock - just a wall with
     * a gate; over open lava - a stony islet with a gate and walkways.
     */
    static int moundTop(int dx, int dz) {
        int end = tunnelEnd();
        if (dx > end || dx < end - MOUND_L) return -1;
        double ex = (dx - (end - 2)) / (double) (MOUND_L - 2), ez = dz / (double) MOUND_W, e = ex * ex + ez * ez;
        if (e >= 1) return -1;
        return TUNNEL_Y + (int) Math.round((1 - e) * 9 + (hash(dx * 5, dz * 7) % 3) - 1);
    }

    /** A 5 x 5 sloping tunnel in obsidian, a portal at the hall; at the top - a gate in the rocky hill and a porch. */
    private void tunnel(int dx, int dz, int[] col) {
        int t = dx - (R - 1), az = Math.abs(dz), end = tunnelEnd();
        if (t < 0) return; // inside the hall
        int mt = moundTop(dx, dz);
        if (mt > 0) for (int y = LAVA_TOP - 6; y <= mt; y++) put(col, y, NETHERRACK, 0);
        if (dx > end) {
            porch(dx - end, az, col);
            return;
        }
        if (dx > end - 6) {
            gate(end - dx, dz, az, col);
            return;
        }
        if (az > 3) return;
        int rise = Math.min(t, (TUNNEL_Y - (FLOOR + 1)) * 2);
        int yb = FLOOR + 1 + rise / 2;
        boolean half = rise % 2 == 1, rib = t % 6 == 0 && t > 0;
        if (t == 0) { // portal at the hall: quartz columns, an arch, Glowstone in the keystone
            for (int y = yb - 1; y <= yb + 5; y++) {
                if (az == 3) put(col, y, y >= yb && y <= yb + 4 ? QUARTZ : BRICK, 2);
                else put(col, y, y == yb - 1 || y == yb + 5 ? BRICK : AIR, 0);
            }
            if (dz == 0) put(col, yb + 5, GLOWSTONE, 0);
            return;
        }
        if (az == 3) { // wall: obsidian, brick ribs, lamps between the ribs
            for (int y = yb - 1; y <= yb + 5; y++) put(col, y, rib ? BRICK : OBSIDIAN, 0);
            if (t % 6 == 3) put(col, yb + 2, LAMP, 0);
            return;
        }
        put(col, yb - 1, BRICK, 0);
        put(col, yb, half ? SLAB : AIR, 6);
        for (int y = yb + 1; y <= yb + 4; y++) put(col, y, AIR, 0);
        if (az == 2 && rib) put(col, yb + 4, BRICK, 0); // rib: brackets under the vault
        put(col, yb + 5, rib ? (dz == 0 ? GLOWSTONE : BRICK) : OBSIDIAN, 0);
        put(col, yb + 6, OBSIDIAN, 0);
    }

    /** The gate (k - columns from the facade inward): a passage in the rock; on the facade - pylons, columns, the lair's crest, a cornice. */
    private void gate(int k, int dz, int az, int[] col) {
        int y0 = TUNNEL_Y;
        if (az > 3) return; // hill rock
        put(col, y0 - 1, BRICK, 0);
        if (k > 0) {
            if (az == 3) {
                for (int y = y0; y <= y0 + 5; y++) put(col, y, OBSIDIAN, 0);
                if (k == 3) put(col, y0 + 2, LAMP, 0);
            } else {
                for (int y = y0; y <= y0 + 4; y++) put(col, y, AIR, 0);
                put(col, y0 + 5, OBSIDIAN, 0);
            }
            return;
        }
        // facade: obsidian pylons with Glowstone, quartz columns, a lintel, the crest, a cornice
        if (az == 3) {
            for (int y = y0; y <= y0 + 8; y++) put(col, y, OBSIDIAN, 0);
            put(col, y0 + 9, GLOWSTONE, 0);
            return;
        }
        if (az <= 1) for (int y = y0; y <= y0 + 3; y++) put(col, y, AIR, 0);
        else for (int y = y0; y <= y0 + 3; y++) put(col, y, QUARTZ, 2);
        put(col, y0 + 4, BRICK, 0);
        for (int row = 0; row < 3; row++) {
            int[] e = emblem(row, dz);
            put(col, y0 + 7 - row, e[0], e[1]);
        }
        put(col, y0 + 8, BRICK, 0);
    }

    /** The crest above the gate: {code, meta} in row row (0 - top) and column dz (-2..2). For the Watcher - the eye. */
    protected int[] emblem(int row, int dz) {
        int[][] eye = { { 0, 6, 6, 6, 0 }, { 6, 14, 1, 14, 6 }, { 0, 6, 6, 6, 0 } }; // 6 - quartz, 14 - iris, 1 - pupil
        int v = eye[row][dz + 2];
        return v == 0 ? new int[] { BRICK, 0 } : v == 6 ? new int[] { QUARTZ, 1 } : v == 1 ? new int[] { OBSIDIAN, 0 } : new int[] { CLAY, 14 };
    }

    /** The porch in front of the gate (k - columns from the facade): decking, railings at the sides, lamps at the corners; the passage is cut low. */
    private void porch(int k, int az, int[] col) {
        if (az > 3 || k > PIER) return;
        int y0 = TUNNEL_Y;
        put(col, y0 - 1, BRICK, 0);
        for (int y = y0; y <= y0 + 3; y++) put(col, y, AIR, 0);
        if (az == 3) put(col, y0, FENCE, 0);
        if (az == 3 && k == PIER) put(col, y0 + 1, LAMP, 0);
        if ((k == 2 || k == PIER) && (az == 3 || az == 0)) put(col, y0 - 2, FOUNDATION, 0);
    }

    // ---------------------------------------------------------------- old lairs

    /** A lair of the old kind (with a rubble pile): where the new one has the eye white of quartz, the old has the obsidian of the floor. */
    public static boolean old(World w, int cx, int cz) {
        int n = 0;
        for (int dx : new int[] { -9, 9, 11 }) if (w.getBlock(cx + dx, FLOOR, cz) == Blocks.obsidian) n++;
        return n >= 2;
    }

    /** Blocks the rebuild may replace: air, fire, materials of the old and new hall. */
    static boolean ours(Block b) {
        return b == Blocks.air || b == Blocks.fire || b == Blocks.netherrack || b == Blocks.soul_sand || b == Blocks.nether_brick || b == Blocks.glowstone
            || WhiteStone.is(b) || b == Blocks.obsidian || b == Blocks.stone_slab || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp2;
    }

    /**
     * Rebuild the hall of an old lair by the new blueprint: floor, everything inside and the vault. Only air, fire and the structure's
     * blocks are replaced - anything else players placed stays; players in the hall - as before.
     */
    public int rebuild(World w, int cx, int cz, List<EntityPlayer> keepFree) {
        int[] col = new int[256 * 2];
        int changed = 0;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r >= R) continue;
                for (int i = 0; i < col.length; i += 2) col[i] = KEEP;
                column(dx, dz, col);
                int x = cx + dx, z = cz + dz, t = top(r);
                for (int y = FLOOR; y <= t + 2; y++) {
                    if (col[y * 2] == KEEP || near(keepFree, x, y, z)) continue;
                    Block b = blockAt(col[y * 2], col[y * 2 + 1]);
                    int meta = metaAt(col[y * 2], col[y * 2 + 1]);
                    Block was = w.getBlock(x, y, z);
                    if (was == b && w.getBlockMetadata(x, y, z) == meta) continue;
                    if (was.hasTileEntity(w.getBlockMetadata(x, y, z)) || !ours(was)) continue;
                    w.setBlock(x, y, z, b, meta, 2);
                    changed++;
                }
            }
        }
        return changed;
    }

    static boolean near(List<EntityPlayer> ps, int x, int y, int z) {
        for (EntityPlayer p : ps) {
            double dx = x + 0.5 - p.posX, dz = z + 0.5 - p.posZ;
            if (dx * dx + dz * dz < 2.3 * 2.3 && y >= p.boundingBox.minY - 1 && y <= p.boundingBox.maxY + 1) return true;
        }
        return false;
    }
}
