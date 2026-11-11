package com.divinerpgextended.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.divinerpg.utils.blocks.ArcanaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.block.BlockLockedChest.Kind;
import com.divinerpgextended.block.TileLockedChest;
import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.Trinket;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Запертые сундуки Арканы. Комнаты Арканы - шаблоны Divine (чанк - четыре этажа комнат 16 x 16, пол на y = 8, 16, 24, 32), и
 * у части шаблонов за дверью под ключ - каморка с арканитовой рудой: за дверью слизи (две комнаты), древнего кирпича, камня
 * душ. С шансом {@link Config#arcanaChestChance} руда в такой каморке заменяется сундуком под ключ этой двери: каморка
 * тупиковая, руда и так занимала место, проход не сужается (у западного и северного края чанка сундук встаёт рядом с
 * рудой, см. {@link #EDGE}). У разрушенных дверей каморок с рудой нет: это комната-крест,
 * в восьмиугольный центр которой ведут четыре разрушенные двери, - там сундук встаёт в угол восьмиугольника, в стороне от
 * прохода.
 * <p>
 * Гарантия: мир Арканы делится на участки {@link Config#arcanaChestRegion} x {@link Config#arcanaChestRegion} (по умолчанию
 * 2048 - в любом квадрате 10000 x 10000 их не меньше девяти целых). Если на участке ещё нет сундука какого-то вида, то
 * {@link Config#arcanaChestPity}-я подходящая каморка этого вида на участке получает сундук без броска. Учёт - в данных
 * мира Арканы ({@link RegionTally}, {@link #TALLY}).
 */
public class ArcanaChests implements IWorldGenerator {

    private static final int LEVELS = 4, H = 8;
    /** Углы восьмиугольника в центре комнаты разрушенных дверей (смещения в комнате). */
    private static final int[][] DEGRADED_SPOTS = { { 5, 5 }, { 10, 5 }, { 5, 10 }, { 10, 10 } };
    /** Для самопроверки: сколько сундуков поставлено и сколько по гарантии. */
    public static int placed, forced;
    /** Для самопроверки: последние поставленные (x, y, z, вид). */
    public static final java.util.ArrayDeque<int[]> recent = new java.util.ArrayDeque<int[]>();

    @Override
    public void generate(Random rand, int cx, int cz, World w, IChunkProvider gen, IChunkProvider prov) {
        if (!Config.arcanaChests || w.provider.dimensionId != ConfigurationHelper.arcana) return;
        for (int lv = 0; lv < LEVELS; lv++) room(w, cx * 16, (lv + 1) * H, cz * 16);
    }

    /** Каморка комнаты (x0, y0 - пол, z0): {x, y, z, вид, сторона} или null. */
    public static int[] vault(World w, int x0, int y0, int z0) {
        Block d = ArcanaBlocks.degradedBrickDoor;
        if (w.getBlock(x0 + 7, y0 + 1, z0 + 3) == d && w.getBlock(x0 + 3, y0 + 1, z0 + 7) == d && w.getBlock(x0 + 12, y0 + 1, z0 + 8) == d
            && w.getBlock(x0 + 8, y0 + 1, z0 + 12) == d) {
            int start = (int) (hash(w, x0, y0, z0) >>> 3 & 3);
            for (int i = 0; i < 4; i++) {
                int[] s = DEGRADED_SPOTS[(start + i) & 3];
                int x = x0 + s[0], z = z0 + s[1];
                if (w.isAirBlock(x, y0 + 1, z) && w.isAirBlock(x, y0 + 2, z) && w.getBlock(x, y0, z).getMaterial().isSolid())
                    return new int[] { x, y0 + 1, z, Kind.DEGRADED.ordinal(), s[1] < 8 ? 3 : 2 };
            }
            return null;
        }
        List<int[]> ores = new ArrayList<int[]>();
        for (int y = y0 + 1; y < y0 + H - 1; y++) for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
            if (w.getBlock(x, y, z) != ArcanaBlocks.arcaniumOre) continue;
            int face = open(w, x, y, z, x0, z0);
            if (face < 0) continue; // в толще стены - не каморка
            Kind k = door(w, x, y, z, x0, z0);
            if (k == null) continue;
            if (x - x0 < EDGE || z - z0 < EDGE) { // у западного или северного края чанка - рядом в той же каморке
                int[] b = beside(w, x, y, z, x0, z0);
                if (b != null) ores.add(new int[] { b[0], y, b[1], k.ordinal(), b[2] });
            } else ores.add(new int[] { x, y, z, k.ordinal(), face });
        }
        if (ores.isEmpty()) return null;
        return ores.get((int) ((hash(w, x0, y0, z0) >>> 5) % ores.size()));
    }

    /**
     * Блок с тайлом при установке оглядывает соседей на 2 блока по сторонам (обновление компараторов, World.func_147453_f):
     * у западного и северного края чанка это читает соседний чанк, а тот при заселении этого чанка может быть ещё не
     * создан - игра сгенерировала бы его посреди генерации (каскад). Восточный и южный соседи при заселении уже есть.
     * Поэтому сундук ставится не ближе {@link #EDGE} к западному и северному краю: если руда ближе - в соседнюю свободную
     * клетку той же каморки, а руда остаётся.
     */
    public static final int EDGE = 2;

    /** Свободная клетка рядом с рудой не ближе {@link #EDGE} к западному и северному краю, на твёрдом полу: {x, z, сторона} или null. */
    static int[] beside(World w, int x, int y, int z, int x0, int z0) {
        int[][] dirs = { { 1, 0 }, { 0, 1 }, { 0, -1 }, { -1, 0 } };
        for (int[] d : dirs) {
            int bx = x + d[0], bz = z + d[1];
            if (!in(bx, bz, x0, z0) || bx - x0 < EDGE || bz - z0 < EDGE || !w.isAirBlock(bx, y, bz) || !w.getBlock(bx, y - 1, bz).getMaterial().isSolid()) continue;
            int face = open(w, bx, y, bz, x0, z0);
            if (face >= 0) return new int[] { bx, bz, face };
        }
        return null;
    }

    /** Внутри комнаты (за её край не смотрим: соседний чанк может быть ещё не сгенерирован). */
    private static boolean in(int x, int z, int x0, int z0) {
        return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
    }

    /** В какую сторону от блока свободно (сторона сундука, 2-5 как у ванильного) или -1. */
    static int open(World w, int x, int y, int z, int x0, int z0) {
        if (in(x, z + 1, x0, z0) && w.isAirBlock(x, y, z + 1)) return 3;
        if (in(x, z - 1, x0, z0) && w.isAirBlock(x, y, z - 1)) return 2;
        if (in(x + 1, z, x0, z0) && w.isAirBlock(x + 1, y, z)) return 5;
        if (in(x - 1, z, x0, z0) && w.isAirBlock(x - 1, y, z)) return 4;
        return -1;
    }

    /** Вид ближайшей двери под ключ (не дальше 3 блоков по горизонтали, этаж тот же, в своей комнате) или null. */
    static Kind door(World w, int x, int y, int z, int x0, int z0) {
        Kind best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (!in(x + dx, z + dz, x0, z0)) continue;
            Kind k = doorKind(w.getBlock(x + dx, y + dy, z + dz));
            if (k == null) continue;
            int dist = dx * dx + dz * dz + dy * dy;
            if (dist < bestD) {
                bestD = dist;
                best = k;
            }
        }
        return best;
    }

    public static Kind doorKind(Block b) {
        if (b == ArcanaBlocks.soulSludgeDoor) return Kind.SLUDGE;
        if (b == ArcanaBlocks.ancientBrickDoor) return Kind.ANCIENT;
        if (b == ArcanaBlocks.soulStoneDoor) return Kind.SOUL;
        if (b == ArcanaBlocks.degradedBrickDoor) return Kind.DEGRADED;
        return null;
    }

    private static long hash(World w, int x, int y, int z) {
        long h = w.getSeed() ^ x * 341873128712L ^ z * 132897987541L ^ y * 42317861L ^ 0xC4E57L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    /** Участок по координате. */
    public static int region(int c) {
        return RegionTally.region(c, Config.arcanaChestRegion);
    }

    /** Имя учёта участков в данных мира Арканы ({@link RegionTally}). */
    public static final String TALLY = "divinerpgextended_arcana_chests";

    /** Комната: есть каморка - бросок шанса или гарантия участка; поставлен - true. */
    public static boolean room(World w, int x0, int y0, int z0) {
        int[] v = vault(w, x0, y0, z0);
        if (v == null) return false;
        Kind k = Kind.of(v[3]);
        long key = RegionTally.key(region(v[0]), region(v[2]), k.ordinal());
        int how = RegionTally.get(w, TALLY).decide(key, new Random(hash(w, v[0], v[1], v[2])), Config.arcanaChestChance, Config.arcanaChestPity);
        if (how == 0) return false;
        boolean pity = how == 2;
        place(w, v[0], v[1], v[2], k, v[4]);
        placed++;
        recent.addLast(new int[] { v[0], v[1], v[2], k.ordinal() });
        if (recent.size() > 32) recent.removeFirst();
        if (pity) forced++;
        return true;
    }

    /** Поставить запертый сундук. */
    public static void place(World w, int x, int y, int z, Kind k, int facing) {
        w.setBlock(x, y, z, ModBlocks.lockedChest, k.ordinal(), 2);
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileLockedChest) ((TileLockedChest) te).facing = facing;
    }

    // ---------------- Добыча ----------------

    private static ItemStack st(Item i, int n) {
        return new ItemStack(i, n);
    }

    private static int n(Random r, int a, int b) {
        return a + r.nextInt(b - a + 1);
    }

    private static <T> T pick(Random r, T[] a) {
        return a[r.nextInt(a.length)];
    }

    /** Оружие Арканы для древнего сундука. */
    public static Item[] weapons() {
        return new Item[] { ArcanaItems.stormSword, ArcanaItems.captainsSparkler, ArcanaItems.laVekor, ArcanaItems.enderScepter, ArcanaItems.arcaniumSaber,
            ArcanaItems.reflector, ArcanaItems.shadowSword, ArcanaItems.liviciaSword, ArcanaItems.meteorMash,
            ArcanaItems.divineAccumulator, ArcanaItems.arcaniteBlaster, ArcanaItems.arcaniteBlade, ArcanaItems.generalsStaff, ArcanaItems.meriksMissile,
            ArcanaItems.staffStarlight, ArcanaItems.starlight, ArcanaItems.firefly, ArcanaItems.wizardBook };
    }

    /** Аксессуары для сундука душ. */
    public static final Trinket[] ACCESSORIES = { Trinket.DUNGEON_LAMP, Trinket.ARCANE_WARD, Trinket.CLARITY_CRYSTAL };

    /** Добыча сундука при открытии. */
    public static List<ItemStack> loot(Kind k, Random r) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        switch (k) {
            case SLUDGE: // расходники
                out.add(st(ArcanaItems.weakArcanaPotion, n(r, 2, 4)));
                if (r.nextInt(5) < 3) out.add(st(ArcanaItems.strongArcanaPotion, n(r, 1, 2)));
                for (int i = n(r, 2, 3); i > 0; i--) out.add(arcanePotion(r));
                out.add(st(ArcanaItems.collector, n(r, 2, 5)));
                if (r.nextInt(10) < 3) out.add(st(ArcanaItems.chargedCollector, 1));
                if (r.nextBoolean()) out.add(st(ArcanaItems.grenade, n(r, 4, 8)));
                out.add(st(ArcanaItems.arcanium, n(r, 2, 5)));
                out.add(st(ArcanaItems.dungeonTokens, n(r, 3, 8)));
                break;
            case ANCIENT: // оружие Арканы
                out.add(st(pick(r, weapons()), 1));
                out.add(st(ArcanaItems.arcanium, n(r, 2, 4)));
                out.add(st(ArcanaItems.dungeonTokens, n(r, 3, 6)));
                break;
            case SOUL: // аксессуары
                out.add(new ItemStack(ModItems.trinket, 1, pick(r, ACCESSORIES).ordinal()));
                out.add(st(ArcanaItems.weakArcanaPotion, n(r, 1, 2)));
                out.add(st(ArcanaItems.arcanium, n(r, 2, 4)));
                out.add(st(ArcanaItems.dungeonTokens, n(r, 4, 8)));
                break;
            case DEGRADED: // инструменты и редкости
                out.add(st(pick(r, new Item[] { ArcanaItems.attractor, ArcanaItems.staffOfEnrichment, ArcanaItems.orbOfLight }), 1));
                out.add(st(ArcanaItems.chargedCollector, n(r, 1, 2)));
                if (r.nextBoolean())
                    out.add(st(pick(r, new Item[] { ArcanaItems.golemSpawner, ArcanaItems.seimerSpawner, ArcanaItems.paratikuSpawner, ArcanaItems.fyracryxSpawner }), 1));
                if (r.nextInt(5) < 3) out.add(st(pick(r, new Item[] { ArcanaItems.hitchakSeeds, ArcanaItems.veiloSeeds, ArcanaItems.marsineSeeds,
                    ArcanaItems.lamonaSeeds, ArcanaItems.firestockSeeds, ArcanaItems.pinflySeeds, ArcanaItems.aquamarineSeeds, ArcanaItems.eucalyptusSeeds }), n(r, 2, 4)));
                out.add(st(ArcanaItems.arcanium, n(r, 4, 8)));
                out.add(st(ArcanaItems.dungeonTokens, n(r, 5, 12)));
                break;
            default: // реликварии миров
                out.addAll(com.divinerpgextended.world.twilight.Reliquaries.loot(k, r));
                break;
        }
        for (java.util.Iterator<ItemStack> it = out.iterator(); it.hasNext();) if (it.next().getItem() == null) it.remove();
        return out;
    }

    private static ItemStack arcanePotion(Random r) {
        List<com.divinerpgextended.magic.ArcaneEffect> ok = new ArrayList<com.divinerpgextended.magic.ArcaneEffect>();
        for (com.divinerpgextended.magic.ArcaneEffect e : com.divinerpgextended.magic.ArcaneEffect.values()) if (!e.removed()) ok.add(e);
        com.divinerpgextended.magic.ArcaneEffect e = ok.get(r.nextInt(ok.size()));
        return com.divinerpgextended.magic.ItemArcanePotion.of(e, e.leveled() && r.nextInt(10) < 3, false, false);
    }
}
