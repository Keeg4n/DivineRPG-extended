package com.divinerpgextended.world;

import java.util.Random;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Arcana fountain hall: in Arcana every chunk is four floors of 16 x 16 rooms of height 8 (floor at heights 8, 16, 24, 32; empty below, ceiling at 40). Occasionally ({@link Config#arcanaFountainChance})
 * one ordinary room (without chests and spawners - not a stash or a boss lair) in a new chunk is rebuilt: walls and passages
 * stay, inside there is a clean floor and a fountain with a bowl of Arcane Concentrate ({@link com.divinerpgextended.block.BlockArcaneConcentrate}):
 * in it arcana is restored and Arcana fish are caught.
 */
public class ArcanaFountain implements IWorldGenerator {

    public static final int LEVELS = 4, H = 8, IN0 = 2, IN1 = 13;
    /** For the selftest: how many halls were built. */
    public static int built;

    @Override
    public void generate(Random rand, int cx, int cz, World w, IChunkProvider gen, IChunkProvider prov) {
        if (w.provider.dimensionId != ConfigurationHelper.arcana || Config.arcanaFountainChance <= 0) return;
        Random r = new Random(w.getSeed() ^ cx * 341873128712L ^ cz * 132897987541L ^ 0xA3CA4AL);
        if (r.nextInt(Config.arcanaFountainChance) != 0) return;
        build(w, cx * 16, floorY(r.nextInt(LEVELS)), cz * 16);
    }

    /** Floor height of a level (0 - the lowest). */
    public static int floorY(int level) {
        return (level + 1) * H;
    }

    /** A room without chests and spawners (stashes and boss lairs are not touched). */
    static boolean plain(World w, int x0, int y0, int z0) {
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) for (int y = y0; y < y0 + H; y++)
            if (w.getTileEntity(x, y, z) != null || w.getBlock(x, y, z) == net.divinerpg.utils.blocks.ArcanaBlocks.dramixAltar
                || w.getBlock(x, y, z) == net.divinerpg.utils.blocks.ArcanaBlocks.parasectaAltar) return false;
        return true;
    }

    /** Rebuild the room (x0, y0 - floor, z0) into a fountain hall. false - the room did not fit. */
    public static boolean build(World w, int x0, int y0, int z0) {
        if (y0 < floorY(0) || !plain(w, x0, y0, z0)) return false; // below the first floor is a void, the hall would hang under the map
        Block floor = w.getBlock(x0 + 8, y0, z0 + 8);
        if (floor.isAir(w, x0 + 8, y0, z0 + 8)) floor = net.divinerpg.utils.blocks.ArcanaBlocks.ancientTile;
        Block rim = net.divinerpg.utils.blocks.ArcanaBlocks.ancientBrick, lamp = net.divinerpg.utils.blocks.ArcanaBlocks.dungenLamp;
        // inside - empty, an even floor (passages in the walls are not touched)
        for (int x = x0 + IN0; x <= x0 + IN1; x++) for (int z = z0 + IN0; z <= z0 + IN1; z++) {
            w.setBlock(x, y0, z, floor, 0, 2);
            for (int y = y0 + 1; y < y0 + H - 1; y++) w.setBlock(x, y, z, Blocks.air, 0, 2);
        }
        // an 8 x 8 bowl: a rim, concentrate inside; in the center - a pillar with arcana fire
        int c0 = x0 + 4, c1 = x0 + 11, d0 = z0 + 4, d1 = z0 + 11;
        for (int x = c0; x <= c1; x++) for (int z = d0; z <= d1; z++) {
            boolean edge = x == c0 || x == c1 || z == d0 || z == d1;
            boolean pillar = (x == x0 + 7 || x == x0 + 8) && (z == z0 + 7 || z == z0 + 8);
            if (edge) w.setBlock(x, y0 + 1, z, rim, 0, 2);
            else if (pillar) {
                for (int y = y0 + 1; y <= y0 + 3; y++) w.setBlock(x, y, z, rim, 0, 2);
                w.setBlock(x, y0 + 4, z, lamp, 0, 2);
            } else w.setBlock(x, y0 + 1, z, ModBlocks.arcaneConcentrate, 0, 2);
        }
        // fires in the corners of the hall
        for (int[] c : new int[][] { { IN0, IN0 }, { IN0, IN1 }, { IN1, IN0 }, { IN1, IN1 } }) w.setBlock(x0 + c[0], y0 + 1, z0 + c[1], lamp, 0, 2);
        built++;
        return true;
    }
}
