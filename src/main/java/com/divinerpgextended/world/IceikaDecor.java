package com.divinerpgextended.world;

import java.util.Random;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.divinerpg.utils.items.IceikaItems;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenMinable;

import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Iceika refinement after Divine populates a chunk.
 * <ul>
 * <li>Rime Ore in frozen stone.</li>
 * <li>Underground ice caves and ice lakes: winding cavities with ice veins, snow and icicles, domes
 * with water under ice, columns and blue lamps. Divine's dungeons stay hidden in the mass: you have to
 * dig to them, and a cave or lake sometimes accidentally leads to a dungeon.</li>
 * <li>Chests: empty frozen dungeon chests are filled with dungeon loot, and villager chests (Divine has only
 * mint candies there) get food, fuel, flint and steel and materials.</li>
 * </ul>
 * Coziness in Iceika workshops (the merchant's and master's house). Divine builds them when populating a chunk, and right after we
 * find the workshop by the bookshelf layout and update the interior: a spruce floor, a hearth at the back wall with a
 * stone platform, a rug in front of it, two tables with chairs. The tree, lamps, chests and merchants stay.
 */
public class IceikaDecor implements IWorldGenerator {

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != ConfigurationHelper.iceika) return;
        // Rime Ore: veins of 5 in frozen stone below y=46
        for (int i = 0; i < 6; i++) {
            new WorldGenMinable(ModBlocks.rimeOre, 0, 5, IceikaBlocks.frozenStone).generate(world, rand,
                chunkX * 16 + 8 + rand.nextInt(16), 6 + rand.nextInt(40), chunkZ * 16 + 8 + rand.nextInt(16));
        }
        // caves and lakes - not inside the Frozen Throne (they would cut the dome and the arena)
        // a cave spreads from the center up to ~30 blocks: check with a margin, not a single point (otherwise it gnawed the dome wall)
        boolean throne = WorldGenThrone.near(world, chunkX * 16 + 16, chunkZ * 16 + 16, 36);
        if (rand.nextInt(10) == 0 && !throne) cavern(world, rand, chunkX * 16 + 16, chunkZ * 16 + 16);
        if (rand.nextInt(18) == 0 && !throne) lake(world, rand, chunkX * 16 + 16, chunkZ * 16 + 16);
        chests(world, rand, chunkX, chunkZ);
        // Divine places the workshop's corner within its own chunk
        for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
            for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                for (int y = 120; y > 10; y--) {
                    if (isWorkshop(world, x, y, z)) {
                        renovate(world, x, y, z);
                        return;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- underground caves and lakes

    private static boolean protectedBlock(Block b) {
        return b == IceikaBlocks.icyBricks || b == IceikaBlocks.icyStone || b == IceikaBlocks.rollumSpawner
            || b == IceikaBlocks.frostArcherSpawner || b == IceikaBlocks.frostedChest || b == Blocks.bedrock
            || b.getMaterial()
                .isLiquid()
            || b instanceof net.minecraft.block.BlockContainer;
    }

    /** Cut a cavity without touching dungeons, water and bedrock. */
    private static void carve(World w, WorldGenLairs.Carver c) {
        for (int[] p : c.cells) {
            if (p[1] < 6 || WorldGenThrone.near(w, p[0], p[2], 6)) continue; // the Throne's walls and vault are not touched
            if (!protectedBlock(w.getBlock(p[0], p[1], p[2]))) w.setBlock(p[0], p[1], p[2], Blocks.air, 0, 2);
        }
    }

    /** An icicle of packed ice, if there is enough room under it. */
    private static void icicle(World w, WorldGenLairs.Carver c, int x, int y, int z, int len) {
        for (int k = 0; k <= len + 1; k++) if (!c.has(x, y - k, z) || !w.isAirBlock(x, y - k, z)) return;
        for (int k = 0; k < len; k++) w.setBlock(x, y - k, z, Blocks.packed_ice, 0, 2);
    }

    /** An ice cave: 3-4 wide curved passages, ice veins, snow on the floor, icicles. */
    private static void cavern(World w, Random rand, int x, int z) {
        double y = 14 + rand.nextInt(30);
        WorldGenLairs.Carver c = new WorldGenLairs.Carver(x - 30, (int) y - 14, z - 30, 61, 30, 61, Integer.MAX_VALUE);
        double[] p = { x + rand.nextInt(9) - 4, y, z + rand.nextInt(9) - 4 };
        int segs = 3 + rand.nextInt(2);
        for (int i = 0; i < segs; i++) {
            double a = rand.nextDouble() * Math.PI * 2, len = 8 + rand.nextInt(6);
            double[] q = { Math.max(x - 22, Math.min(x + 22, p[0] + Math.cos(a) * len)),
                Math.max(y - 6, Math.min(y + 6, p[1] + rand.nextInt(5) - 2)),
                Math.max(z - 22, Math.min(z + 22, p[2] + Math.sin(a) * len)) };
            c.line(p, q, rand, false, 2.2, 3.8, 1.8, 3.0);
            p = q;
        }
        carve(w, c);
        decorate(w, rand, c, 12, 3);
        if (rand.nextInt(3) == 0 && !c.cells.isEmpty()) {
            int[] q = c.cells.get(rand.nextInt(c.cells.size()));
            int ly = q[1];
            while (c.has(q[0], ly + 1, q[2])) ly++;
            if (!protectedBlock(w.getBlock(q[0], ly + 1, q[2]))) w.setBlock(q[0], ly + 1, q[2], VanillaBlocks.lamp2, 0, 2);
        }
    }

    /** Snow on the floor, packed ice veins in the walls, icicles from the ceiling (every icicleRate-th vault point). */
    private static void decorate(World w, Random rand, WorldGenLairs.Carver c, int icicleRate, int maxLen) {
        int[][] six = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] p : c.cells) {
            int x = p[0], y = p[1], z = p[2];
            if (!w.isAirBlock(x, y, z)) continue;
            for (int[] d : six) {
                Block b = w.getBlock(x + d[0], y + d[1], z + d[2]);
                if (b == IceikaBlocks.frozenStone && rand.nextInt(3) == 0) {
                    w.setBlock(x + d[0], y + d[1], z + d[2], rand.nextInt(4) == 0 ? Blocks.ice : Blocks.packed_ice, 0, 2);
                }
            }
            if (!c.has(x, y - 1, z) && w.getBlock(x, y - 1, z)
                .isOpaqueCube() && rand.nextInt(10) < 4) w.setBlock(x, y, z, Blocks.snow_layer, rand.nextInt(2), 2);
            if (!c.has(x, y + 1, z) && rand.nextInt(icicleRate) == 0) icicle(w, c, x, y, z, 1 + rand.nextInt(maxLen));
        }
    }

    /** An underground ice lake: a dome, below it water under ice with ice holes, columns, dense icicles, blue lamps. */
    private static void lake(World w, Random rand, int x, int z) {
        int y = 12 + rand.nextInt(25);
        double rh = 8 + rand.nextInt(5), rv = 5 + rand.nextInt(2);
        int ri = (int) Math.ceil(rh);
        WorldGenLairs.Carver c = new WorldGenLairs.Carver(x - ri - 2, y - 4, z - ri - 2, 2 * ri + 5, (int) rv + 8, 2 * ri + 5,
            Integer.MAX_VALUE);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                for (int dy = -3; dy <= rv; dy++) {
                    double e = (dx * dx + dz * dz) / (rh * rh) + (dy > 0 ? dy * dy / (rv * rv) : dy * dy / 9.0);
                    if (e < 1.0) c.add(x + dx, y + dy, z + dz);
                }
            }
        }
        carve(w, c);
        // water and an ice crust with holes
        for (int[] p : c.cells) {
            if (!w.isAirBlock(p[0], p[1], p[2])) continue;
            int dy = p[1] - y;
            if (dy < 0) w.setBlock(p[0], p[1], p[2], Blocks.water, 0, 2);
            else if (dy == 0) w.setBlock(p[0], p[1], p[2], rand.nextInt(10) < 8 ? Blocks.ice : Blocks.water, 0, 2);
        }
        // ice columns from the ice to the vault
        for (int k = 0; k < 2 + rand.nextInt(2); k++) {
            double a = rand.nextDouble() * Math.PI * 2, r = rh * (0.3 + rand.nextDouble() * 0.4);
            int px = x + (int) Math.round(Math.cos(a) * r), pz = z + (int) Math.round(Math.sin(a) * r);
            for (int py = y - 3; py <= y + (int) rv + 1; py++) {
                if (c.has(px, py, pz)) w.setBlock(px, py, pz, Blocks.packed_ice, 0, 2);
            }
        }
        decorate(w, rand, c, 6, 4);
        // blue lamps in the vault
        for (int k = 0; k < 2; k++) {
            int lx = x + rand.nextInt(ri) - ri / 2, lz = z + rand.nextInt(ri) - ri / 2, ly = y;
            while (c.has(lx, ly + 1, lz)) ly++;
            if (ly > y && !protectedBlock(w.getBlock(lx, ly + 1, lz))) w.setBlock(lx, ly + 1, lz, VanillaBlocks.lamp2, 0, 2);
        }
        // sometimes a frozen chest on the shore
        if (rand.nextInt(3) == 0) {
            for (int t = 0; t < 30; t++) {
                double a = rand.nextDouble() * Math.PI * 2;
                int sx = x + (int) Math.round(Math.cos(a) * (rh - 1.5)), sz = z + (int) Math.round(Math.sin(a) * (rh - 1.5));
                int sy = y + 1;
                while (c.has(sx, sy - 1, sz) && w.isAirBlock(sx, sy - 1, sz)) sy--;
                if (!w.isAirBlock(sx, sy, sz) || !w.getBlock(sx, sy - 1, sz)
                    .isOpaqueCube()) continue;
                LairLoot.chest(w, rand, sx, sy, sz, IceikaBlocks.decorativeFrostedChest, LairLoot.iceikaDungeon());
                break;
            }
        }
    }

    // ---------------------------------------------------------------- chests

    private static boolean onlyPeppermints(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && s.getItem() != IceikaItems.peppermints) return false;
        }
        return true;
    }

    private static boolean empty(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i) != null) return false;
        return true;
    }

    /**
     * Fill the chests Divine has just placed in this and neighboring chunks (its structures reach into the neighbors). Each chest is filled
     * once: the positions of filled chests and of chests placed by players are kept in the world ({@link Seen}). Before, every population
     * of a neighbor refilled an emptied dungeon chest again, and an empty decorative chest carried to the edge of the explored land was filled
     * with village loot.
     */
    private static void chests(World w, Random rand, int chunkX, int chunkZ) {
        Seen seen = Seen.get(w);
        for (int i = 0; i <= 1; i++) {
            for (int k = 0; k <= 1; k++) {
                if (!w.getChunkProvider()
                    .chunkExists(chunkX + i, chunkZ + k)) continue;
                Chunk ch = w.getChunkFromChunkCoords(chunkX + i, chunkZ + k);
                for (Object o : ch.chunkTileEntityMap.values()) {
                    if (!(o instanceof TileEntityChest)) continue;
                    TileEntityChest te = (TileEntityChest) o;
                    Block b = w.getBlock(te.xCoord, te.yCoord, te.zCoord);
                    if (b != IceikaBlocks.frostedChest && b != IceikaBlocks.decorativeFrostedChest) continue;
                    if (!seen.add(te.xCoord, te.yCoord, te.zCoord)) continue;
                    if (b == IceikaBlocks.frostedChest && empty(te)) {
                        LairLoot.fill(te, rand, LairLoot.iceikaDungeon(), 3 + rand.nextInt(3));
                    } else if (b == IceikaBlocks.decorativeFrostedChest && onlyPeppermints(te)) {
                        LairLoot.fill(te, rand, LairLoot.village(), 3 + rand.nextInt(3));
                    }
                }
            }
        }
    }

    /** Frosted chests already handled in Iceika: filled at generation or placed by a player - they are never filled again. */
    public static class Seen extends net.minecraft.world.WorldSavedData {

        static final String NAME = "dre_iceika_chests";
        private final java.util.Set<Long> at = new java.util.HashSet<Long>();

        public Seen(String name) {
            super(name);
        }

        public static Seen get(World w) {
            Seen s = (Seen) w.perWorldStorage.loadData(Seen.class, NAME);
            if (s == null) {
                s = new Seen(NAME);
                w.perWorldStorage.setData(NAME, s);
            }
            return s;
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }

        /** @return true if the chest was not seen before (now it is) */
        public boolean add(int x, int y, int z) {
            if (!at.add(key(x, y, z))) return false;
            markDirty();
            return true;
        }

        public boolean has(int x, int y, int z) {
            return at.contains(key(x, y, z));
        }

        /** For the selftest: forget the place (the test world is reused between runs). */
        public void forget(int x, int y, int z) {
            if (at.remove(key(x, y, z))) markDirty();
        }

        @Override
        public void readFromNBT(net.minecraft.nbt.NBTTagCompound tag) {
            int[] a = tag.getIntArray("at");
            for (int i = 0; i + 1 < a.length; i += 2) at.add((long) a[i] << 32 | a[i + 1] & 0xFFFFFFFFL);
        }

        @Override
        public void writeToNBT(net.minecraft.nbt.NBTTagCompound tag) {
            int[] a = new int[at.size() * 2];
            int i = 0;
            for (long k : at) {
                a[i++] = (int) (k >>> 32);
                a[i++] = (int) k;
            }
            tag.setIntArray("at", a);
        }
    }

    /** A frosted chest placed by a player is his own: generation does not fill it. */
    public static class Events {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onPlace(net.minecraftforge.event.world.BlockEvent.PlaceEvent e) {
            if (e.world.isRemote || e.world.provider.dimensionId != ConfigurationHelper.iceika) return;
            if (e.placedBlock == IceikaBlocks.frostedChest || e.placedBlock == IceikaBlocks.decorativeFrostedChest) Seen.get(e.world).add(e.x, e.y, e.z);
        }
    }

    /** The workshop's corner (x, y, z): 2x2 bookshelves in the corners, coal stone in the floor corner, a lamp next to it. */
    private static boolean isWorkshop(World w, int x, int y, int z) {
        return w.getBlock(x + 1, y + 1, z + 1) == IceikaBlocks.workshopBookcase
            && w.getBlock(x + 12, y + 1, z + 12) == IceikaBlocks.workshopBookcase
            && w.getBlock(x + 12, y + 1, z + 1) == IceikaBlocks.workshopBookcase
            && w.getBlock(x, y, z) == IceikaBlocks.coalstone && w.getBlock(x + 2, y, z + 2) == IceikaBlocks.workshopLamp;
    }

    private static void set(World w, int x, int y, int z, Block b, int meta) {
        w.setBlock(x, y, z, b, meta, 2);
    }

    private static void renovate(World w, int x, int y, int z) {
        // a spruce floor instead of the motley rings of carpet and coal stone
        for (int i = 1; i <= 12; i++) {
            for (int k = 1; k <= 12; k++) {
                Block b = w.getBlock(x + i, y, z + k);
                if (b == IceikaBlocks.workshopCarpet || b == IceikaBlocks.coalstone) set(w, x + i, y, z + k, Blocks.planks, 1);
            }
        }
        // a hearth at the back wall (the door is on the z side), a stone platform around
        for (int i = 4; i <= 9; i++) {
            for (int k = 10; k <= 12; k++) set(w, x + i, y, z + k, IceikaBlocks.coalstone, 0);
        }
        for (int i = 5; i <= 8; i++) {
            boolean fire = i == 6 || i == 7;
            set(w, x + i, y + 1, z + 12, fire ? ModBlocks.hearth : IceikaBlocks.coalstone, 0);
            set(w, x + i, y + 2, z + 12, IceikaBlocks.coalstone, 0);
        }
        set(w, x + 5, y + 3, z + 12, Blocks.stone_slab, 3); // a shelf above the hearth
        set(w, x + 8, y + 3, z + 12, Blocks.stone_slab, 3);
        // a red rug in front of the hearth
        for (int i = 5; i <= 8; i++) for (int k = 8; k <= 9; k++) set(w, x + i, y + 1, z + k, Blocks.carpet, 14);
        // two tables with chairs (a chair with its back to the door)
        for (int t : new int[] { 3, 10 }) {
            set(w, x + t, y + 1, z + 6, Blocks.fence, 0);
            set(w, x + t, y + 2, z + 6, Blocks.wooden_pressure_plate, 0);
            set(w, x + t, y + 1, z + 7, Blocks.spruce_stairs, 2);
        }
    }
}
