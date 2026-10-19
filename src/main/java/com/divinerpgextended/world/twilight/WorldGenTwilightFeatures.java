package com.divinerpgextended.world.twilight;

import java.util.Random;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Мелкие постройки сумеречных миров - ставятся при заселении чанка внутри его области (+8 от угла и в пределах
 * загруженных при заселении чанков), чтобы не тянуть генерацию соседей.
 * <ul>
 * <li>Руины на вершине острова: площадка из камня и досок мира, обломки колонн и стен, сундук с добычей, стража из
 * мобов мира.</li>
 * <li>Подземелье в толще крупного острова: каменная комната со спавнером моба мира и сундуком, лестница наверх и
 * коридор к краю острова, если край рядом.</li>
 * <li>Мортум - мир мёртвых ({@link MortumWorld}): вместо руин склепы, вместо мостов - река Лета с пристанями Харона,
 * по дну пещеры черепа и надгробия.</li>
 * </ul>
 */
public class WorldGenTwilightFeatures implements IWorldGenerator {

    /** Для замеров и автотеста: сколько построено с запуска. */
    public static int ruins, dungeons, bridges, camps;

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        TwilightWorld tw = TwilightWorld.of(world.provider.dimensionId);
        if (tw == null) return;
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8;
        // своё зерно на чанк: постройки не зависят от порядка генерации
        Random r = new Random(world.getSeed() ^ chunkX * 341873128712L ^ chunkZ * 132897987541L ^ 0x7A1L);
        boolean mortum = tw == TwilightWorld.MORTUM;
        if (mortum) { // река течёт и мимо арен (на самих аренах её нет)
            int river = MortumWorld.lethe(world, tw, x0, z0);
            if (river > 4) MortumWorld.dock(world, r, tw, x0, z0);
            MortumWorld.bridges(world, tw, x0, z0);
            MortumWorld.arenaDock(world, tw, x0, z0);
        }
        if (nearLair(world, x0 + 8, z0 + 8)) return;
        boolean built = false;
        // острова неровные и дырявые: каждой постройке - четыре места в области заселения
        int[][] spots = { { 0, 0 }, { 7, 0 }, { 0, 7 }, { 7, 7 } };
        if (mortum) MortumWorld.bones(world, r, tw, x0, z0);
        if (Config.twilightRuinsRarity > 0 && r.nextInt(Config.twilightRuinsRarity) == 0) {
            if (mortum) for (int[] o : spots) built = built || MortumWorld.crypt(world, r, tw, x0 + o[0], z0 + o[1]);
            else for (int[] o : spots) if (!built && (built = ruin(world, r, tw, x0 + o[0], z0 + o[1]))) ruins++;
        } else if (Config.twilightCampRarity > 0 && r.nextInt(Config.twilightCampRarity) == 0) {
            for (int[] o : spots) if (!built && (built = camp(world, r, tw, x0 + o[0], z0 + o[1]))) camps++;
        } else if (Config.twilightDungeonRarity > 0 && r.nextInt(Config.twilightDungeonRarity) == 0) {
            for (int[] o : spots) if (!built && (built = dungeon(world, r, tw, x0 + o[0], z0 + o[1], x0 + 23))) dungeons++;
        }
        // мосты: у построек - всегда пробуем (мост издалека подсказывает, что рядом что-то есть), в остальных местах редко
        // в Мортуме мостов нет: пещера мёртвых, деревянный мост там чужой
        if (tw != TwilightWorld.MORTUM && Config.twilightBridgeRarity > 0 && (built || r.nextInt(Config.twilightBridgeRarity) == 0)) {
            if (bridge(world, r, tw, x0, z0)) bridges++;
        }
    }

    /** Рядом с гнездом или кругом не строим (они висят выше, но тень и проходы под ними оставим чистыми). */
    static boolean nearLair(World w, int x, int z) {
        int[] c = WorldGenNests.nearest(w.getSeed(), w.provider.dimensionId, x, z);
        if (c == null) return false;
        int d = WorldGenNests.radius(w.provider.dimensionId) + 24;
        return Math.abs(c[0] - x) < d && Math.abs(c[2] - z) < d;
    }

    // ---------------------------------------------------------------- поверхность

    /** Блок земли острова (не листва, не бревно, не растения). */
    static boolean ground(TwilightWorld tw, Block b) {
        return b == tw.grass() || b == tw.dirt() || b == TwilightBlocks.twilightStone;
    }

    /** Верхний блок земли острова в столбце или -1. */
    public static int surface(World w, TwilightWorld tw, int x, int z) {
        // Мортум - пещера под бедроковым потолком (как Ад): земля - только та, над которой воздух, начиная ниже потолка
        boolean cave = tw == TwilightWorld.MORTUM;
        for (int y = cave ? 118 : 135; y > 4; y--) {
            Block b = w.getBlock(x, y, z);
            if (ground(tw, b) && (!cave || w.isAirBlock(x, y + 1, z) && w.isAirBlock(x, y + 2, z))) return y;
        }
        return -1;
    }

    // ---------------------------------------------------------------- руины

    /** Руины 9 x 9 с углом в (x, z); false - место не подошло (неровно или не остров). */
    public static boolean ruin(World w, Random r, TwilightWorld tw, int x, int z) {
        int lo = 999, hi = -1;
        for (int i = 0; i < 9; i++) for (int k = 0; k < 9; k++) {
            int y = surface(w, tw, x + i, z + k);
            if (y < 0) return false;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        if (hi - lo > 5) return false;
        int base = hi;
        Block stone = TwilightBlocks.twilightStone, planks = tw.planks(), gem = tw.gemBlock();
        for (int i = 0; i < 9; i++) for (int k = 0; k < 9; k++) {
            int s = surface(w, tw, x + i, z + k);
            for (int y = s + 1; y < base; y++) w.setBlock(x + i, y, z + k, tw.dirt(), 0, 2);
            for (int y = base + 1; y <= base + 7; y++) if (!w.isAirBlock(x + i, y, z + k)) w.setBlock(x + i, y, z + k, Blocks.air, 0, 2);
            int roll = r.nextInt(10);
            if (roll < 6) w.setBlock(x + i, base, z + k, stone, 0, 2);
            else if (roll < 9) w.setBlock(x + i, base, z + k, planks, 0, 2);
            // остальное - трава мира: пол разрушен
        }
        // колонны по углам: часть обломана
        int[][] corners = { { 1, 1 }, { 7, 1 }, { 1, 7 }, { 7, 7 } };
        for (int[] c : corners) {
            int h = 2 + r.nextInt(5);
            for (int y = 1; y <= h; y++) w.setBlock(x + c[0], base + y, z + c[1], stone, 0, 2);
            if (h >= 5) w.setBlock(x + c[0], base + h + 1, z + c[1], gem, 0, 2);
        }
        // обломки стен по краям
        for (int i = 0; i < 9; i++) {
            for (int side = 0; side < 4; side++) {
                int px = side < 2 ? i : side == 2 ? 0 : 8, pz = side < 2 ? (side == 0 ? 0 : 8) : i;
                if (r.nextInt(3) != 0) continue;
                int h = 1 + r.nextInt(2);
                for (int y = 1; y <= h; y++) w.setBlock(x + px, base + y, z + pz, stone, 0, 2);
            }
        }
        w.setBlock(x + 4, base, z + 2, tw.lamp(), tw.lampMeta(), 2);
        chest(w, r, x + 4, base + 1, z + 4, TwilightLoot.ruins(tw), 5 + r.nextInt(3));
        // стража
        int guards = 2 + r.nextInt(2);
        for (int i = 0; i < guards; i++) spawn(w, r, tw, x + 2 + r.nextInt(5), base + 1, z + 2 + r.nextInt(5));
        return true;
    }

    // ---------------------------------------------------------------- подземелье

    /** Комната 9 x 9 внутри острова с углом в (x, z); false - остров тонкий. */
    public static boolean dungeon(World w, Random r, TwilightWorld tw, int x, int z, int maxX) {
        int top = surface(w, tw, x + 4, z + 4);
        if (top < 20) return false;
        int floor = top - 10;
        // толщина: под всей комнатой должен быть камень (не висеть в пустоте)
        for (int i = 0; i < 9; i += 4) for (int k = 0; k < 9; k += 4) {
            for (int y = floor - 1; y <= top - 4; y++) if (w.isAirBlock(x + i, y, z + k)) return false;
        }
        Block stone = TwilightBlocks.twilightStone, gem = tw.gemBlock();
        for (int i = 0; i < 9; i++) for (int k = 0; k < 9; k++) {
            for (int y = floor - 1; y <= floor + 5; y++) {
                boolean shell = i == 0 || i == 8 || k == 0 || k == 8 || y == floor - 1 || y == floor + 5;
                if (shell) w.setBlock(x + i, y, z + k, (i + k + y) % 7 == 0 ? gem : stone, 0, 2);
                else w.setBlock(x + i, y, z + k, Blocks.air, 0, 2);
            }
        }
        w.setBlock(x + 1, floor + 4, z + 1, tw.lamp(), tw.lampMeta(), 2);
        w.setBlock(x + 7, floor + 4, z + 7, tw.lamp(), tw.lampMeta(), 2);
        // спавнер моба мира
        w.setBlock(x + 4, floor, z + 4, Blocks.mob_spawner, 0, 2);
        if (w.getTileEntity(x + 4, floor, z + 4) instanceof TileEntityMobSpawner) {
            Class<? extends EntityLiving>[] mobs = tw.mobs();
            String name = (String) EntityList.classToStringMapping.get(mobs[r.nextInt(mobs.length)]);
            if (name != null) ((TileEntityMobSpawner) w.getTileEntity(x + 4, floor, z + 4)).func_145881_a().setEntityName(name);
        }
        chest(w, r, x + 7, floor, z + 1, TwilightLoot.dungeon(tw), 6 + r.nextInt(3));
        sealIn(w, x + 7, floor, z + 1, tw);
        // лестница наверх в углу: шахта до поверхности
        for (int y = floor; y <= top + 1; y++) {
            w.setBlock(x + 1, y, z + 7, Blocks.air, 0, 2);
            if (y > floor + 4) {
                w.setBlock(x, y, z + 7, stone, 0, 2);
                w.setBlock(x + 2, y, z + 7, stone, 0, 2);
                w.setBlock(x + 1, y, z + 8, stone, 0, 2);
            }
            w.setBlock(x + 1, y, z + 6, stone, 0, 2);
            w.setBlock(x + 1, y, z + 7, Blocks.ladder, 3, 2); // лестница на северной стенке шахты
        }
        w.setBlock(x + 1, top + 1, z + 7, Blocks.air, 0, 2);
        // коридор к краю острова, если край в пределах области заселения
        corridor(w, x, z, floor, maxX);
        return true;
    }

    /** Коридор 2 блока высотой из восточной стены комнаты, пока не выйдет в пустоту (не дальше maxX и 11 блоков). */
    private static void corridor(World w, int x, int z, int floor, int maxX) {
        int cz = z + 4;
        for (int i = 9; i <= 19 && x + i <= maxX; i++) {
            boolean open = w.isAirBlock(x + i, floor, cz) && w.isAirBlock(x + i, floor + 1, cz);
            if (open && i > 9) return; // вышли к краю
            w.setBlock(x + i - 1, floor, cz, Blocks.air, 0, 2);
            w.setBlock(x + i - 1, floor + 1, cz, Blocks.air, 0, 2);
        }
    }

    // ---------------------------------------------------------------- лагерь

    /** Цвет шерсти палатки: у каждого мира свой. */
    private static int wool(TwilightWorld tw) {
        switch (tw) {
            case EDEN: return 4;
            case WILDWOOD: return 11;
            case APALACHIA: return 10;
            case SKYTHERN: return 0;
            default: return 14;
        }
    }

    /**
     * Лагерь странника 8 x 8 с углом в (x, z): палатка-шалаш из шерсти цвета мира с сундуком, горящий костёр (жаровня)
     * с брёвнами-сиденьями, у костра Странник-торговец. Присесть и ПКМ по костру - точка возрождения в этом мире.
     */
    public static boolean camp(World w, Random r, TwilightWorld tw, int x, int z) {
        int lo = 999, hi = -1;
        for (int i = 0; i < 8; i++) for (int k = 0; k < 8; k++) {
            int y = surface(w, tw, x + i, z + k);
            if (y < 0) return false;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        if (hi - lo > 3) return false;
        int base = hi;
        for (int i = 0; i < 8; i++) for (int k = 0; k < 8; k++) {
            int s = surface(w, tw, x + i, z + k);
            for (int y = s + 1; y <= base; y++) w.setBlock(x + i, y, z + k, y == base ? tw.grass() : tw.dirt(), 0, 2);
            for (int y = base + 1; y <= base + 5; y++) if (!w.isAirBlock(x + i, y, z + k)) w.setBlock(x + i, y, z + k, Blocks.air, 0, 2);
        }
        // шалаш вдоль X: x..x+3, поперёк z+1..z+5 (высоты 1-2-3-2-1), задняя стенка закрыта
        int c = wool(tw);
        int[] roof = { 1, 2, 3, 2, 1 };
        for (int i = 0; i < 4; i++) for (int k = 0; k < 5; k++) {
            w.setBlock(x + i, base + roof[k], z + 1 + k, Blocks.wool, c, 2);
            if (i == 0) for (int y = 1; y < roof[k]; y++) w.setBlock(x, base + y, z + 1 + k, Blocks.wool, c, 2);
        }
        chest(w, r, x + 1, base + 1, z + 3, TwilightLoot.ruins(tw), 3 + r.nextInt(3));
        // костёр с сиденьями
        w.setBlock(x + 6, base, z + 3, com.divinerpgextended.registry.ModBlocks.brazier, 1, 2);
        w.setBlock(x + 6, base + 1, z + 1, tw.log(), 8, 2);
        w.setBlock(x + 6, base + 1, z + 5, tw.log(), 8, 2);
        // Странник
        com.divinerpgextended.entity.EntityWanderer t = new com.divinerpgextended.entity.EntityWanderer(w);
        t.setHome(tw);
        t.setLocationAndAngles(x + 5.5, base + 1, z + 3.5, 90, 0);
        w.spawnEntityInWorld(t);
        return true;
    }

    // ---------------------------------------------------------------- мост

    /** Пролёт между краями островов: от 4 до 14 блоков пустоты, края на высоте с разницей не больше 4. */
    private static final int MIN_GAP = 4, MAX_GAP = 14;

    /**
     * Мост поперёк пропасти по линии через середину области заселения (вдоль X или Z - что найдётся). Всё, что
     * ставим, лежит в [x0 - 8, x0 + 24) - в чанках, загруженных для заселения.
     */
    public static boolean bridge(World w, Random r, TwilightWorld tw, int x0, int z0) {
        boolean alongX = r.nextBoolean();
        for (int attempt = 0; attempt < 2; attempt++, alongX = !alongX) {
            int p = (alongX ? z0 : x0) + 8; // поперечная координата линии
            int from = (alongX ? x0 : z0) - 6, to = (alongX ? x0 : z0) + 21;
            int[] s = new int[to - from + 1];
            for (int i = 0; i < s.length; i++) s[i] = alongX ? surface(w, tw, from + i, p) : surface(w, tw, p, from + i);
            for (int a = 0; a < s.length; a++) {
                if (s[a] < 0) continue;
                int gap = 0, b = a + 1;
                while (b < s.length && (s[b] < 0 || s[b] < s[a] - 15)) {
                    gap++;
                    b++;
                }
                if (b >= s.length || gap < MIN_GAP || gap > MAX_GAP || Math.abs(s[b] - s[a]) > 4) continue;
                span(w, r, tw, alongX, p, from + a, s[a], from + b, s[b]);
                return true;
            }
        }
        return false;
    }

    /** Провисающий мост шириной 3 с перилами от (a, ya) до (b, yb); каждый четвёртый с провалом посередине. */
    private static void span(World w, Random r, TwilightWorld tw, boolean alongX, int p, int a, int ya, int b, int yb) {
        int len = b - a;
        double sag = len / 6.0;
        boolean broken = r.nextInt(4) == 0;
        int hole = a + len / 2;
        Block deck = tw.planks(), log = tw.log();
        for (int i = a; i <= b; i++) {
            double t = (i - a) / (double) len;
            int y = (int) Math.round(ya + (yb - ya) * t - sag * Math.sin(Math.PI * t));
            boolean gone = broken && Math.abs(i - hole) <= 1;
            for (int q = -1; q <= 1; q++) {
                int x = alongX ? i : p + q, z = alongX ? p + q : i;
                if (!gone) w.setBlock(x, y, z, deck, 0, 2);
                for (int h = 1; h <= 3; h++) if (!w.isAirBlock(x, y + h, z) && i > a && i < b) w.setBlock(x, y + h, z, Blocks.air, 0, 2);
                if (q != 0 && !gone) {
                    boolean post = (i - a) % 4 == 0;
                    w.setBlock(x, y + 1, z, post ? log : Blocks.fence, 0, 2);
                    if (post) w.setBlock(x, y + 2, z, Blocks.fence, 0, 2);
                }
            }
        }
    }

    // ---------------------------------------------------------------- общее

    /** В сундуке подземелья всегда печать святилища мира (кроме Мортума - там вызывают кристаллами). */
    static void sealIn(World w, int x, int y, int z, TwilightWorld tw) {
        if (tw == TwilightWorld.MORTUM || !(w.getTileEntity(x, y, z) instanceof TileEntityChest)) return;
        TileEntityChest c = (TileEntityChest) w.getTileEntity(x, y, z);
        net.minecraft.item.ItemStack seal = new net.minecraft.item.ItemStack(com.divinerpgextended.registry.ModItems.sanctumSeal, 1, tw.ordinal());
        for (int i = 0; i < c.getSizeInventory(); i++) {
            if (c.getStackInSlot(i) == null) {
                c.setInventorySlotContents(i, seal);
                return;
            }
        }
        c.setInventorySlotContents(13, seal);
    }

    static void chest(World w, Random r, int x, int y, int z, WeightedRandomChestContent[] table, int count) {
        w.setBlock(x, y, z, Blocks.chest, 0, 2);
        if (w.getTileEntity(x, y, z) instanceof TileEntityChest) {
            WeightedRandomChestContent.generateChestContents(r, table, (TileEntityChest) w.getTileEntity(x, y, z), count);
        }
    }

    static void spawn(World w, Random r, TwilightWorld tw, int x, int y, int z) {
        Class<? extends EntityLiving>[] mobs = tw.surfaceMobs(); // стража стоит под открытым небом
        try {
            EntityLiving m = mobs[r.nextInt(mobs.length)].getConstructor(World.class).newInstance(w);
            m.setLocationAndAngles(x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
            m.func_110163_bv(); // стража не исчезает
            w.spawnEntityInWorld(m);
        } catch (Exception ignored) {
            // у моба нет конструктора (World) - без стражи
        }
    }
}
