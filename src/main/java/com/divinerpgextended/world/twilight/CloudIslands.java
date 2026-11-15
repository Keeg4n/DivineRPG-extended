package com.divinerpgextended.world.twilight;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Cloud islands of Skythern: a flat elliptical cloud island in the sky (thicker in the middle), in the middle a pond with world water in a
 * cloud bowl. Fishing in such a pond is its own zone ({@link com.divinerpgextended.fishing.Zone#CLOUD}): Cloudfin and
 * Cloud Jelly for Leorna's quests. It hangs in empty sky, is built inside the population area; on average one per
 * {@link Config#cloudIslandRarity} chunks.
 */
public class CloudIslands implements IWorldGenerator {

    public static int built;

    @Override
    public void generate(Random rand, int cx, int cz, World w, IChunkProvider gen, IChunkProvider prov) {
        if (Config.cloudIslandRarity <= 0 || TwilightWorld.of(w.provider.dimensionId) != TwilightWorld.SKYTHERN) return;
        Random r = new Random(w.getSeed() ^ cx * 341873128712L ^ cz * 132897987541L ^ 0xC10DL);
        if (r.nextInt(Config.cloudIslandRarity) != 0) return;
        int x = cx * 16 + 16, z = cz * 16 + 16, rx = 5 + r.nextInt(3), rz = 4 + r.nextInt(3);
        for (int tries = 0; tries < 4; tries++) {
            int y = 110 + r.nextInt(50);
            if (free(w, x, y, z, rx, rz) && !WorldGenTwilightFeatures.nearLair(w, x, z)) {
                build(w, x, y, z, rx, rz);
                return;
            }
        }
    }

    /** The sky around the island is empty. */
    public static boolean free(World w, int x, int y, int z, int rx, int rz) {
        for (int dx = -rx - 1; dx <= rx + 1; dx++) for (int dz = -rz - 1; dz <= rz + 1; dz++) for (int dy = -4; dy <= 3; dy++)
            if (!w.isAirBlock(x + dx, y + dy, z + dz)) return false;
        return true;
    }

    /** Build: middle (x, z), top of the cloud at y, semi-axes rx, rz. How many blocks of water are in the pond. */
    public static int build(World w, int x, int y, int z, int rx, int rz) {
        int water = 0;
        for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) {
            double d = (double) dx * dx / (rx * rx) + (double) dz * dz / (rz * rz);
            if (d > 1.0) continue;
            int thick = 1 + (int) Math.round(2.4 * (1 - d)); // thicker in the middle
            for (int dy = 0; dy < thick; dy++) w.setBlock(x + dx, y - dy, z + dz, ModBlocks.cloud, 0, 2);
            double p = (double) dx * dx / ((rx - 2) * (rx - 2)) + (double) dz * dz / ((rz - 2) * (rz - 2));
            if (p <= 1.0) { // pond: water in the cloud bowl, deeper in the middle
                int depth = p < 0.35 && thick >= 3 ? 2 : 1;
                for (int dy = 0; dy < depth; dy++) {
                    w.setBlock(x + dx, y - dy, z + dz, Blocks.water, 0, 2);
                    water++;
                }
            }
        }
        built++;
        return water;
    }
}
