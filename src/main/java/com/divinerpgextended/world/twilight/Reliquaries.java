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
 * Реликварии сумеречных миров: запертые сундуки под ключ мира ({@link com.divinerpgextended.item.ItemWorldKey}), внутри - вещь
 * мира и материалы. Где стоят:
 * <ul>
 * <li>Эдем, Дикий лес, Апалачия - в руинах на вершинах островов, рядом с сундуком руин;</li>
 * <li>Мортум - в склепе, у саркофага;</li>
 * <li>Небесный терн - на терновых кораблях в небе, в каюте ({@link Ships}).</li>
 * </ul>
 * В постройке реликварий появляется с шансом ({@link Config#reliquaryChance}, на кораблях {@link Config#reliquaryShipChance});
 * гарантия: если на участке {@link Config#reliquaryRegion} ещё нет реликвария, {@link Config#reliquaryPity}-я подходящая постройка
 * (на кораблях {@link Config#reliquaryShipPity}-я) получает его без броска. Учёт - в данных мира ({@link RegionTally}).
 */
public final class Reliquaries {

    public static final String TALLY = "divinerpgextended_reliquaries";
    /** Для самопроверки: сколько поставлено и сколько по гарантии. */
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

    /** Подходящее место в постройке: бросок шанса или гарантия участка, поставлен - true. */
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

    /** Реликварий в руинах (Эдем, Дикий лес, Апалачия): угол руин (x, z), пол base. */
    public static boolean inRuin(World w, TwilightWorld tw, int x, int base, int z) {
        if (tw == TwilightWorld.SKYTHERN || tw == TwilightWorld.MORTUM) return false; // у Терна - на кораблях, у Мортума - в склепах
        return offer(w, tw, x + 4, base + 1, z + 6, 3, Config.reliquaryChance, Config.reliquaryPity);
    }

    /** Реликварий в склепе Мортума: угол склепа (x, z), пол base. Рядом с саркофагом, в стороне от входа. */
    public static boolean inCrypt(World w, int x, int base, int z) {
        return offer(w, TwilightWorld.MORTUM, x + 5, base + 1, z + 2, 3, Config.reliquaryChance, Config.reliquaryPity);
    }

    // ---------------- Добыча ----------------

    private static int n(Random r, int a, int b) {
        return a + r.nextInt(b - a + 1);
    }

    /** Вещь мира из реликвария. */
    public static ItemStack relic(TwilightWorld w) {
        switch (w) {
            case EDEN: return new ItemStack(ModItems.trinket, 1, com.divinerpgextended.trinket.Trinket.SUN_HALO.ordinal());
            case WILDWOOD: return new ItemStack(ModItems.stormBow);
            case APALACHIA: return new ItemStack(ModItems.tomahawk);
            case SKYTHERN: return new ItemStack(ModItems.cloudStaff);
            default: return new ItemStack(ModItems.soulHarvest);
        }
    }

    /** Добыча реликвария: вещь мира, камни, души и пыль мира. */
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

    // ---------------- Терновые корабли ----------------

    /**
     * Терновый корабль в небе Небесного терна: корпус из досок мира {@link #L} x {@link #W} вдоль оси x (киль из брёвен, скулы в
     * три ряда, нос и корма сужаются), фальшборт из плит, грот-мачта с большим белым парусом и фок-мачта с малым, флажок на
     * гроте, каюта на корме - в ней реликварий Терна (с шансом и гарантией участка) и сундук с добычей руин, лампы мира на носу
     * и в каюте. Висит в пустом небе над островами и между ними (вокруг всё пусто), строится внутри области заселения.
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
                int y0 = 120 + r.nextInt(45); // над островами (их верх - до 135) и между ними
                if (free(w, x0, y0, z0) && !WorldGenTwilightFeatures.nearLair(w, x0 + L / 2, z0 + W / 2)) {
                    build(w, r, x0, y0, z0);
                    return;
                }
            }
        }

        /** Небо вокруг корабля пусто (с запасом в блок по бокам и снизу, до флажка). */
        public static boolean free(World w, int x0, int y0, int z0) {
            for (int x = x0 - 1; x <= x0 + L; x++) for (int z = z0 - 1; z <= z0 + W; z++) for (int y = y0 - 1; y <= y0 + TOP; y++)
                if (!w.isAirBlock(x, y, z)) return false;
            return true;
        }

        /** Половина ширины корпуса на шпангоуте i (0 - корма, L-1 - нос). */
        static int half(int i) {
            return i == 0 ? 2 : i <= 15 ? 3 : i <= 17 ? 2 : i <= 19 ? 1 : 0;
        }

        /** Построить корабль: угол (x0, z0), киль на y0, палуба на y0+3. Возвращает место реликвария {x, y, z}. */
        public static int[] build(World w, Random r, int x0, int y0, int z0) {
            TwilightWorld tw = TwilightWorld.SKYTHERN;
            Block planks = tw.planks(), log = tw.log(), slab = tw.slab();
            int c = W / 2, deck = y0 + 3; // ось корабля по z, высота палубы
            for (int i = 0; i < L; i++) {
                int h = half(i);
                for (int k = c - h; k <= c + h; k++) {
                    int d = Math.abs(k - c);
                    if (d == 0 && i >= 2 && i <= L - 3) w.setBlock(x0 + i, y0, z0 + k, log, 0, 2); // киль
                    if (d <= Math.max(0, h - 2)) w.setBlock(x0 + i, y0 + 1, z0 + k, planks, 0, 2); // днище
                    if (d <= h - 1 || h == 0) w.setBlock(x0 + i, y0 + 2, z0 + k, planks, 0, 2); // скулы
                    w.setBlock(x0 + i, deck, z0 + k, planks, 0, 2); // палуба
                    if (d == h && h > 0 && i >= 6) w.setBlock(x0 + i, deck + 1, z0 + k, slab, 0, 2); // фальшборт
                }
            }
            // форштевень на носу, лампа
            for (int y = deck; y <= deck + 2; y++) w.setBlock(x0 + L - 1, y, z0 + c, log, 0, 2);
            w.setBlock(x0 + L - 2, deck + 1, z0 + c, tw.lamp(), tw.lampMeta(), 2);
            // грот-мачта: рея, большой парус, флажок
            int main = x0 + 11;
            for (int y = deck + 1; y <= deck + 12; y++) w.setBlock(main, y, z0 + c, log, 0, 2);
            for (int k = 0; k < W; k++) w.setBlock(main, deck + 10, z0 + k, log, 0, 2);
            for (int y = deck + 4; y <= deck + 9; y++) for (int k = 1; k < W - 1; k++) w.setBlock(main + 1, y, z0 + k, Blocks.wool, 0, 2);
            w.setBlock(main, deck + 13, z0 + c, Blocks.wool, 3, 2);
            w.setBlock(main, deck + 13, z0 + c + 1, Blocks.wool, 3, 2);
            // фок-мачта: рея, малый парус
            int fore = x0 + 16;
            for (int y = deck + 1; y <= deck + 9; y++) w.setBlock(fore, y, z0 + c, log, 0, 2);
            for (int k = 1; k < W - 1; k++) w.setBlock(fore, deck + 8, z0 + k, log, 0, 2);
            for (int y = deck + 4; y <= deck + 7; y++) for (int k = 2; k < W - 2; k++) w.setBlock(fore + 1, y, z0 + k, Blocks.wool, 0, 2);
            // каюта на корме: стены, крыша из плит, вход к носу, окна по бортам
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
