package com.divinerpgextended.world.twilight;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.block.BlockLockedChest.Kind;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.ArcanaChests;
import com.divinerpgextended.world.RegionTally;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Reliquaries of the twilight dimensions: locked chests that need a world key ({@link com.divinerpgextended.item.ItemWorldKey}), inside - a world
 * item and materials. Where they stand:
 * <ul>
 * <li>Eden, Wildwood, Apalachia - in ruins on island tops, next to the ruins chest;</li>
 * <li>Mortum - in a crypt, by the sarcophagus;</li>
 * <li>Skythern - on thorn ships in the sky, in the cabin ({@link Ships}).</li>
 * </ul>
 * In a structure a reliquary appears with a chance ({@link Config#reliquaryChance}, on ships {@link Config#reliquaryShipChance});
 * guarantee: if there is no reliquary yet in the {@link Config#reliquaryRegion} area, the {@link Config#reliquaryPity}-th suitable structure
 * (on ships the {@link Config#reliquaryShipPity}-th) gets it without a roll. Tracking is in the world data ({@link RegionTally}).
 */
public final class Reliquaries {

    public static final String TALLY = "divinerpgextended_reliquaries";
    /** For selftest: how many were placed and how many by the guarantee. */
    public static int placed, forced;

    private Reliquaries() {}

    private static long hash(World w, int x, int y, int z) {
        long h = w.getSeed() ^ x * 341873128712L ^ z * 132897987541L ^ y * 42317861L ^ 0x5E1C7L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    public static int region(int c) {
        return RegionTally.region(c, Config.reliquaryRegion);
    }

    /** A suitable place in a structure: a chance roll or the area guarantee, placed - true. */
    public static boolean offer(World w, TwilightWorld tw, int x, int y, int z, int facing, int chance, int pity) {
        if (!Config.reliquaries || tw == null) return false;
        Kind k = Kind.of(tw);
        long key = RegionTally.key(region(x), region(z), k.ordinal());
        int how = RegionTally.get(w, TALLY).decide(key, new Random(hash(w, x, y, z)), chance, pity);
        if (how == 0) return false;
        ArcanaChests.place(w, x, y, z, k, facing);
        placed++;
        if (how == 2) forced++;
        return true;
    }

    /** A reliquary in ruins (Eden, Wildwood, Apalachia): ruins corner (x, z), floor base. */
    public static boolean inRuin(World w, TwilightWorld tw, int x, int base, int z) {
        if (tw == TwilightWorld.SKYTHERN || tw == TwilightWorld.MORTUM) return false; // for Skythern - on ships, for Mortum - in crypts
        return offer(w, tw, x + 4, base + 1, z + 6, 3, Config.reliquaryChance, Config.reliquaryPity);
    }

    /** A reliquary in a Mortum crypt: crypt corner (x, z), floor base. By the sarcophagus, away from the entrance. */
    public static boolean inCrypt(World w, int x, int base, int z) {
        return offer(w, TwilightWorld.MORTUM, x + 5, base + 1, z + 2, 3, Config.reliquaryChance, Config.reliquaryPity);
    }

    // ---------------- Loot ----------------

    private static int n(Random r, int a, int b) {
        return a + r.nextInt(b - a + 1);
    }

    /** The world item from a reliquary. */
    public static ItemStack relic(TwilightWorld w) {
        switch (w) {
            case EDEN: return new ItemStack(ModItems.trinket, 1, com.divinerpgextended.trinket.Trinket.SUN_HALO.ordinal());
            case WILDWOOD: return new ItemStack(ModItems.stormBow);
            case APALACHIA: return new ItemStack(ModItems.tomahawk);
            case SKYTHERN: return new ItemStack(ModItems.cloudStaff);
            default: return new ItemStack(ModItems.soulHarvest);
        }
    }

    /** Reliquary loot: a world item, stones, souls and world dust. */
    public static List<ItemStack> loot(Kind k, Random r) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (k.arcana()) return out;
        TwilightWorld w = TwilightWorld.values()[k.world()];
        out.add(relic(w));
        if (w.gem() != null) out.add(new ItemStack(w.gem(), n(r, 2, 4)));
        if (w.soul() != null) out.add(new ItemStack(w.soul(), n(r, 2, 4)));
        if (w.dust() != null) out.add(new ItemStack(w.dust(), n(r, 4, 8)));
        return out;
    }

    // ---------------- Thorn ships ----------------

    /**
     * A thorn ship in the Skythern sky: a hull of world planks {@link #L} x {@link #W} along the x axis (a keel of logs, bilges in
     * three rows, bow and stern tapering), a bulwark of slabs, a mainmast with a large white sail and a foremast with a small one, a pennant on the
     * mainmast, a cabin at the stern - in it the Skythern reliquary (with a chance and an area guarantee) and a ruins loot chest, world lamps at the bow
     * and in the cabin. It hangs in empty sky above the islands and between them (everything around is empty), built inside the population area.
     */
    public static class Ships implements IWorldGenerator {

        public static int built;
        public static final int L = 21, W = 7, TOP = 16;

        @Override
        public void generate(Random rand, int cx, int cz, World w, IChunkProvider gen, IChunkProvider prov) {
            if (Config.skythernShipRarity <= 0 || TwilightWorld.of(w.provider.dimensionId) != TwilightWorld.SKYTHERN) return;
            Random r = new Random(w.getSeed() ^ cx * 341873128712L ^ cz * 132897987541L ^ 0x5B1FL);
            if (r.nextInt(Config.skythernShipRarity) != 0) return;
            int x0 = cx * 16 + 8 + 1, z0 = cz * 16 + 8 + 4;
            for (int tries = 0; tries < 4; tries++) {
                int y0 = 120 + r.nextInt(45); // above the islands (their tops are up to 135) and between them
                if (free(w, x0, y0, z0) && !WorldGenTwilightFeatures.nearLair(w, x0 + L / 2, z0 + W / 2)) {
                    build(w, r, x0, y0, z0);
                    return;
                }
            }
        }

        /** The sky around the ship is empty (with a margin of a block on the sides and below, up to the pennant). */
        public static boolean free(World w, int x0, int y0, int z0) {
            for (int x = x0 - 1; x <= x0 + L; x++) for (int z = z0 - 1; z <= z0 + W; z++) for (int y = y0 - 1; y <= y0 + TOP; y++)
                if (!w.isAirBlock(x, y, z)) return false;
            return true;
        }

        /** Half the hull width at frame i (0 - stern, L-1 - bow). */
        static int half(int i) {
            return i == 0 ? 2 : i <= 15 ? 3 : i <= 17 ? 2 : i <= 19 ? 1 : 0;
        }

        /** Build the ship: corner (x0, z0), keel at y0, deck at y0+3. Returns the reliquary place {x, y, z}. */
        public static int[] build(World w, Random r, int x0, int y0, int z0) {
            TwilightWorld tw = TwilightWorld.SKYTHERN;
            Block planks = tw.planks(), log = tw.log(), slab = tw.slab();
            int c = W / 2, deck = y0 + 3; // ship axis along z, deck height
            for (int i = 0; i < L; i++) {
                int h = half(i);
                for (int k = c - h; k <= c + h; k++) {
                    int d = Math.abs(k - c);
                    if (d == 0 && i >= 2 && i <= L - 3) w.setBlock(x0 + i, y0, z0 + k, log, 0, 2); // keel
                    if (d <= Math.max(0, h - 2)) w.setBlock(x0 + i, y0 + 1, z0 + k, planks, 0, 2); // bottom
                    if (d <= h - 1 || h == 0) w.setBlock(x0 + i, y0 + 2, z0 + k, planks, 0, 2); // bilges
                    w.setBlock(x0 + i, deck, z0 + k, planks, 0, 2); // deck
                    if (d == h && h > 0 && i >= 6) w.setBlock(x0 + i, deck + 1, z0 + k, slab, 0, 2); // bulwark
                }
            }
            // stem at the bow, a lamp
            for (int y = deck; y <= deck + 2; y++) w.setBlock(x0 + L - 1, y, z0 + c, log, 0, 2);
            w.setBlock(x0 + L - 2, deck + 1, z0 + c, tw.lamp(), tw.lampMeta(), 2);
            // mainmast: yard, large sail, pennant
            int main = x0 + 11;
            for (int y = deck + 1; y <= deck + 12; y++) w.setBlock(main, y, z0 + c, log, 0, 2);
            for (int k = 0; k < W; k++) w.setBlock(main, deck + 10, z0 + k, log, 0, 2);
            for (int y = deck + 4; y <= deck + 9; y++) for (int k = 1; k < W - 1; k++) w.setBlock(main + 1, y, z0 + k, Blocks.wool, 0, 2);
            w.setBlock(main, deck + 13, z0 + c, Blocks.wool, 3, 2);
            w.setBlock(main, deck + 13, z0 + c + 1, Blocks.wool, 3, 2);
            // foremast: yard, small sail
            int fore = x0 + 16;
            for (int y = deck + 1; y <= deck + 9; y++) w.setBlock(fore, y, z0 + c, log, 0, 2);
            for (int k = 1; k < W - 1; k++) w.setBlock(fore, deck + 8, z0 + k, log, 0, 2);
            for (int y = deck + 4; y <= deck + 7; y++) for (int k = 2; k < W - 2; k++) w.setBlock(fore + 1, y, z0 + k, Blocks.wool, 0, 2);
            // cabin at the stern: walls, a roof of slabs, entrance toward the bow, windows on the sides
            int cab = 5;
            for (int i = 0; i <= cab; i++) for (int k = c - half(i); k <= c + half(i); k++) {
                boolean wall = i == 0 || i == cab || Math.abs(k - c) == half(i);
                for (int y = deck + 1; y <= deck + 3; y++) w.setBlock(x0 + i, y, z0 + k, wall ? planks : Blocks.air, 0, 2);
                w.setBlock(x0 + i, deck + 4, z0 + k, slab, 0, 2);
            }
            w.setBlock(x0 + cab, deck + 1, z0 + c, Blocks.air, 0, 2);
            w.setBlock(x0 + cab, deck + 2, z0 + c, Blocks.air, 0, 2);
            for (int i : new int[] { 2, 3 }) {
                w.setBlock(x0 + i, deck + 2, z0 + c - half(i), Blocks.glass_pane, 0, 2);
                w.setBlock(x0 + i, deck + 2, z0 + c + half(i), Blocks.glass_pane, 0, 2);
            }
            w.setBlock(x0 + 1, deck + 3, z0 + c, tw.lamp(), tw.lampMeta(), 2);
            WorldGenTwilightFeatures.chest(w, r, x0 + 1, deck + 1, z0 + c + 1, TwilightLoot.ruins(tw), 4 + r.nextInt(3));
            int[] rel = { x0 + 1, deck + 1, z0 + c - 1 };
            offer(w, tw, rel[0], rel[1], rel[2], 5, Config.reliquaryShipChance, Config.reliquaryShipPity);
            built++;
            return rel;
        }
    }
}
