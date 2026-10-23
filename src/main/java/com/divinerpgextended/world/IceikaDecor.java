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
 * Доработка Iceika после того, как Divine заселил чанк.
 * <ul>
 * <li>Руда инея в мёрзлом камне.</li>
 * <li>Подземные ледяные пещеры и ледяные озёра: петляющие полости с прожилками льда, снегом и сосульками, купола
 * с водой подо льдом, колоннами и голубыми лампами. Данжи Divine остаются спрятанными в толще: к ним надо
 * прокапываться, а пещера или озеро иногда случайно выводит к данжу.</li>
 * <li>Сундуки: пустые морозные сундуки данжей наполняем лутом подземелий, а в сундуки жителей (у Divine там только
 * мятные леденцы) добавляем еду, топливо, огниво и материалы.</li>
 * </ul>
 * Уют в мастерских Iceika (дом торговца и мастера). Divine строит их при заселении чанка, мы сразу после этого
 * находим мастерскую по расстановке книжных шкафов и обновляем интерьер: еловый пол, очаг у задней стены с
 * каменной площадкой, коврик перед ним, два стола со стульями. Ёлка, лампы, сундуки и торговцы остаются.
 */
public class IceikaDecor implements IWorldGenerator {

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != ConfigurationHelper.iceika) return;
        // руда инея: жилы по 5 в мёрзлом камне ниже y=46
        for (int i = 0; i < 6; i++) {
            new WorldGenMinable(ModBlocks.rimeOre, 0, 5, IceikaBlocks.frozenStone).generate(world, rand,
                chunkX * 16 + 8 + rand.nextInt(16), 6 + rand.nextInt(40), chunkZ * 16 + 8 + rand.nextInt(16));
        }
        // пещеры и озёра - не внутри Ледяного Трона (прорезали бы купол и арену)
        boolean throne = WorldGenThrone.shields(world, chunkX * 16 + 16, 30, chunkZ * 16 + 16);
        if (rand.nextInt(10) == 0 && !throne) cavern(world, rand, chunkX * 16 + 16, chunkZ * 16 + 16);
        if (rand.nextInt(18) == 0 && !throne) lake(world, rand, chunkX * 16 + 16, chunkZ * 16 + 16);
        chests(world, rand, chunkX, chunkZ);
        // угол мастерской Divine ставит в пределах своего чанка
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

    // ---------------------------------------------------------------- подземные пещеры и озёра

    private static boolean protectedBlock(Block b) {
        return b == IceikaBlocks.icyBricks || b == IceikaBlocks.icyStone || b == IceikaBlocks.rollumSpawner
            || b == IceikaBlocks.frostArcherSpawner || b == IceikaBlocks.frostedChest || b == Blocks.bedrock
            || b.getMaterial()
                .isLiquid()
            || b instanceof net.minecraft.block.BlockContainer;
    }

    /** Вырезать полость, не задевая данжи, воду и бедрок. */
    private static void carve(World w, WorldGenLairs.Carver c) {
        for (int[] p : c.cells) {
            if (p[1] < 6) continue;
            if (!protectedBlock(w.getBlock(p[0], p[1], p[2]))) w.setBlock(p[0], p[1], p[2], Blocks.air, 0, 2);
        }
    }

    /** Сосулька из пакового льда, если под ней хватает места. */
    private static void icicle(World w, WorldGenLairs.Carver c, int x, int y, int z, int len) {
        for (int k = 0; k <= len + 1; k++) if (!c.has(x, y - k, z) || !w.isAirBlock(x, y - k, z)) return;
        for (int k = 0; k < len; k++) w.setBlock(x, y - k, z, Blocks.packed_ice, 0, 2);
    }

    /** Ледяная пещера: 3-4 широких кривых хода, прожилки льда, снег на полу, сосульки. */
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

    /** Снег на полу, прожилки пакового льда в стенах, сосульки с потолка (каждая icicleRate-я точка свода). */
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

    /** Подземное ледяное озеро: купол, внизу вода подо льдом с полыньями, колонны, густые сосульки, голубые лампы. */
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
        // вода и ледяная корка с полыньями
        for (int[] p : c.cells) {
            if (!w.isAirBlock(p[0], p[1], p[2])) continue;
            int dy = p[1] - y;
            if (dy < 0) w.setBlock(p[0], p[1], p[2], Blocks.water, 0, 2);
            else if (dy == 0) w.setBlock(p[0], p[1], p[2], rand.nextInt(10) < 8 ? Blocks.ice : Blocks.water, 0, 2);
        }
        // ледяные колонны от льда до свода
        for (int k = 0; k < 2 + rand.nextInt(2); k++) {
            double a = rand.nextDouble() * Math.PI * 2, r = rh * (0.3 + rand.nextDouble() * 0.4);
            int px = x + (int) Math.round(Math.cos(a) * r), pz = z + (int) Math.round(Math.sin(a) * r);
            for (int py = y - 3; py <= y + (int) rv + 1; py++) {
                if (c.has(px, py, pz)) w.setBlock(px, py, pz, Blocks.packed_ice, 0, 2);
            }
        }
        decorate(w, rand, c, 6, 4);
        // голубые лампы в своде
        for (int k = 0; k < 2; k++) {
            int lx = x + rand.nextInt(ri) - ri / 2, lz = z + rand.nextInt(ri) - ri / 2, ly = y;
            while (c.has(lx, ly + 1, lz)) ly++;
            if (ly > y && !protectedBlock(w.getBlock(lx, ly + 1, lz))) w.setBlock(lx, ly + 1, lz, VanillaBlocks.lamp2, 0, 2);
        }
        // иногда морозный сундук на берегу
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

    // ---------------------------------------------------------------- сундуки

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

    /** Наполнить сундуки, которые Divine только что поставил в этом и соседних чанках. */
    private static void chests(World w, Random rand, int chunkX, int chunkZ) {
        for (int i = 0; i <= 1; i++) {
            for (int k = 0; k <= 1; k++) {
                if (!w.getChunkProvider()
                    .chunkExists(chunkX + i, chunkZ + k)) continue;
                Chunk ch = w.getChunkFromChunkCoords(chunkX + i, chunkZ + k);
                for (Object o : ch.chunkTileEntityMap.values()) {
                    if (!(o instanceof TileEntityChest)) continue;
                    TileEntityChest te = (TileEntityChest) o;
                    Block b = w.getBlock(te.xCoord, te.yCoord, te.zCoord);
                    if (b == IceikaBlocks.frostedChest && empty(te)) {
                        LairLoot.fill(te, rand, LairLoot.iceikaDungeon(), 3 + rand.nextInt(3));
                    } else if (b == IceikaBlocks.decorativeFrostedChest && onlyPeppermints(te)) {
                        LairLoot.fill(te, rand, LairLoot.village(), 3 + rand.nextInt(3));
                    }
                }
            }
        }
    }

    /** Угол (x, y, z) мастерской: по углам 2x2 книжные шкафы, в углу пола угольный камень, рядом лампа. */
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
        // еловый пол вместо пёстрых колец ковра и угольного камня
        for (int i = 1; i <= 12; i++) {
            for (int k = 1; k <= 12; k++) {
                Block b = w.getBlock(x + i, y, z + k);
                if (b == IceikaBlocks.workshopCarpet || b == IceikaBlocks.coalstone) set(w, x + i, y, z + k, Blocks.planks, 1);
            }
        }
        // очаг у задней стены (дверь на стороне z), каменная площадка вокруг
        for (int i = 4; i <= 9; i++) {
            for (int k = 10; k <= 12; k++) set(w, x + i, y, z + k, IceikaBlocks.coalstone, 0);
        }
        for (int i = 5; i <= 8; i++) {
            boolean fire = i == 6 || i == 7;
            set(w, x + i, y + 1, z + 12, fire ? ModBlocks.hearth : IceikaBlocks.coalstone, 0);
            set(w, x + i, y + 2, z + 12, IceikaBlocks.coalstone, 0);
        }
        set(w, x + 5, y + 3, z + 12, Blocks.stone_slab, 3); // полка над очагом
        set(w, x + 8, y + 3, z + 12, Blocks.stone_slab, 3);
        // красный коврик перед очагом
        for (int i = 5; i <= 8; i++) for (int k = 8; k <= 9; k++) set(w, x + i, y + 1, z + k, Blocks.carpet, 14);
        // два стола со стульями (стул спиной к двери)
        for (int t : new int[] { 3, 10 }) {
            set(w, x + t, y + 1, z + 6, Blocks.fence, 0);
            set(w, x + t, y + 2, z + 6, Blocks.wooden_pressure_plate, 0);
            set(w, x + t, y + 1, z + 7, Blocks.spruce_stairs, 2);
        }
    }
}
