package com.divinerpgextended.world;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * The Fire Chamber of the King of Scorchers - a race track under the Nether lava lake (the king is a Scorcher). The shell, vault with an oculus,
 * lava windows and the entrance (tunnel, gate in a rocky hill, porch) are as in the Watcher's lair ({@link WatcherVault}); its own
 * hall: in the center a Divine black-and-white checkered dais, around it a lava moat with four checkered bridges; further the track of Nether
 * brick - white dashed markings, a checkered finish line in the west, a curb along the edge of red and white blocks. Along the
 * inner edge of the track are eight checkered fire pillars (not on the axes: on the axes the king cuts through the bridges), on them
 * eternal fire. Above the gate instead of the eye is a checkered flag. The fight is {@link com.divinerpgextended.boss.KingRace}.
 */
public class ScorcherPit extends WatcherVault {

    /** Dais, moat (from and to), pillars (circle, thickness), track markings. */
    public static final double DAIS = 6, MOAT_IN = 6, MOAT_OUT = 9.5, POSTS = 12, POST_R = 1.3, LANE = 15.3;
    public static final int POST_COUNT = 8, POST_H = 8;

    public ScorcherPit(int cx, int cz) {
        super(cx, cz);
    }

    @Override
    protected boolean lit(Block b) {
        return super.lit(b) || b == net.divinerpg.utils.blocks.VanillaBlocks.lamp1;
    }

    @Override
    protected void hall(int dx, int dz, double r, int[] col) {
        int t = top(r);
        put(col, FLOOR - 1, OBSIDIAN, 0);
        boolean bridge = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        double a = Math.atan2(dz, dx);
        int code = BRICK, meta = 0;
        if (r < DAIS - 0.8) code = CHECKER; // checkered dais
        else if (r < MOAT_IN) code = OBSIDIAN; // dais rim
        else if (r < MOAT_OUT) code = bridge ? CHECKER : LAVA; // moat and bridges
        else if (r >= R - 1) code = OBSIDIAN;
        else if (r >= R - 2.2) { // curb: red and white every two blocks
            boolean red = ((int) Math.floor(a * r / 2)) % 2 == 0;
            code = red ? CLAY : QUARTZ;
            meta = red ? 14 : 0;
        } else if (dx < 0 && Math.abs(dz) <= 1 && r > MOAT_OUT + 0.5) code = CHECKER; // finish line
        else if (Math.abs(r - LANE) < 0.5 && ((int) Math.floor(a * r / 2)) % 2 == 0) {
            code = QUARTZ; // dashes
            meta = 0;
        }
        put(col, FLOOR, code, meta);
        if (code == LAVA) put(col, FLOOR - 1, BRICK, 0);
        double pd = postDist(dx, dz);
        for (int y = FLOOR + 1; y <= t; y++) {
            int h = y - FLOOR;
            if (pd < POST_R && h <= POST_H) {
                put(col, y, h == POST_H ? NETHERRACK : (h / 2) % 2 == 0 ? CHECKER : BRICK, 0); // checkered pillar, netherrack on top
                continue;
            }
            if (pd < 0.7 && h == POST_H + 1) {
                put(col, y, FIRE, 0); // eternal fire on the netherrack
                continue;
            }
            if (r < 1 && h == 1) {
                put(col, y, AIR, 0);
                continue;
            }
            if (wallCell(dx, dz, r, y, col, 1)) continue; // windows - orange glass
            put(col, y, AIR, 0);
        }
        vault(dx, dz, r, t, col);
    }

    /** Distance to the nearest fire pillar. */
    static double postDist(double dx, double dz) {
        return com.divinerpgextended.world.LairDomes.ringDist(dx, dz, POSTS, POST_COUNT, Math.PI / POST_COUNT); // not on the axes: the king cuts through them
    }

    /** Above the gate is a checkered flag in a Nether brick frame. */
    @Override
    protected int[] emblem(int row, int dz) {
        return Math.abs(dz) == 2 && row != 1 ? new int[] { BRICK, 0 } : new int[] { CHECKER, 0 };
    }

    /** The chamber is built the new way (a checker in the floor center), not the old sphere in the sky. */
    public static boolean isNew(World w, int cx, int cz) {
        return w.getBlock(cx, FLOOR, cz) == net.divinerpg.utils.blocks.VanillaBlocks.checker;
    }

    /** The old chamber (the sphere in the sky before 0.99.51): its floor is at height 40 with a lamp in the center. */
    public static final int OLD_FLOOR = 40;

    public static boolean isOld(World w, int cx, int cz) {
        Block b = w.getBlock(cx, OLD_FLOOR, cz);
        return !isNew(w, cx, cz) && (b == net.divinerpg.utils.blocks.VanillaBlocks.lamp1 || b == Blocks.nether_brick);
    }
}
