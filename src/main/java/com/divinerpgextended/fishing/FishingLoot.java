package com.divinerpgextended.fishing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.registry.ModItems;

/**
 * Что ловится. Как в Террарии: сила рыбалки (удочка + приманка) сокращает ожидание и повышает шанс редкого улова и
 * ящиков; таблицы зависят от места (зоны). Без приманки - только обычная рыба и мусор.
 */
public final class FishingLoot {

    private FishingLoot() {}

    static final class Entry {

        final ItemStack stack;
        /** Виды одной строки (камни с аспектами Таумкрафта): выпадает один наугад; stack - первый, для показа. */
        final ItemStack[] alts;
        final int min, max, weight;

        Entry(Item item, int meta, int min, int max, int weight) {
            this(new ItemStack[] { new ItemStack(item, 1, meta) }, min, max, weight);
        }

        Entry(ItemStack[] alts, int min, int max, int weight) {
            this.alts = alts;
            this.stack = alts[0];
            this.min = min;
            this.max = max;
            this.weight = weight;
        }

        ItemStack make(Random r) {
            ItemStack s = (alts.length == 1 ? stack : alts[r.nextInt(alts.length)]).copy();
            s.stackSize = min + (max > min ? r.nextInt(max - min + 1) : 0);
            return s;
        }
    }

    static final class Table {

        final List<Entry> common = new ArrayList<Entry>(), uncommon = new ArrayList<Entry>(),
            rare = new ArrayList<Entry>(), veryRare = new ArrayList<Entry>();
    }

    private static Map<Zone, Table> tables;
    private static List<Entry> junk;

    private static Entry e(Item i, int min, int max, int w) {
        return new Entry(i, 0, min, max, w);
    }

    private static Entry e(Item i, int meta, int min, int max, int w) {
        return new Entry(i, meta, min, max, w);
    }

    private static Entry e(Block b, int min, int max, int w) {
        return new Entry(Item.getItemFromBlock(b), 0, min, max, w);
    }

    private static Entry fish(Fish f, int w) {
        return new Entry(ModItems.fish, f.ordinal(), 1, 1, w);
    }

    private static void add(List<Entry> l, Entry... es) {
        for (Entry x : es) l.add(x);
    }

    private static synchronized void init() {
        if (tables != null) return;
        tables = new EnumMap<Zone, Table>(Zone.class);
        for (Zone z : Zone.values()) tables.put(z, new Table());
        junk = new ArrayList<Entry>();
        add(junk, e(Items.leather_boots, 1, 1, 10), e(Items.leather, 1, 1, 10), e(Items.bone, 1, 2, 10),
            e(Items.glass_bottle, 1, 1, 10), e(Items.string, 1, 2, 5), e(Items.bowl, 1, 1, 10), e(Items.stick, 1, 2, 5),
            e(Items.rotten_flesh, 1, 1, 10), e(Blocks.tripwire_hook, 1, 1, 5));

        Table t = tables.get(Zone.OCEAN);
        add(t.common, fish(Fish.SEA_BASS, 30), e(Items.fish, 0, 1, 1, 30), e(Items.fish, 1, 1, 1, 15));
        add(t.uncommon, e(VanillaItemsOther.aquaticPellets, 1, 3, 10), e(VanillaItemsOther.crabClaw, 1, 2, 8),
            e(VanillaItemsOther.sharkFin, 1, 1, 6), e(Items.dye, 0, 1, 3, 8));
        add(t.rare, fish(Fish.CORAL_WRASSE, 10), fish(Fish.SHARK_PUP, 6), e(VanillaItemsOther.krakenScale, 1, 1, 6),
            e(VanillaItemsOther.aquaticIngot, 1, 2, 5));
        add(t.veryRare, e(VanillaItemsWeapons.sharkSword, 1, 1, 3), e(Items.diamond, 1, 2, 4));

        t = tables.get(Zone.FRESH);
        add(t.common, fish(Fish.RUFF, 30), e(Items.fish, 0, 1, 1, 30), e(Items.fish, 1, 1, 1, 20));
        add(t.uncommon, e(Items.iron_ingot, 1, 1, 8), e(VanillaItemsOther.realmiteIngot, 1, 2, 10), e(Items.clay_ball, 2, 4, 8));
        add(t.rare, fish(Fish.GOLD_CRUCIAN, 8), e(Items.gold_ingot, 1, 2, 6), e(Items.name_tag, 1, 1, 3));
        add(t.veryRare, e(Items.diamond, 1, 1, 4), e(Items.golden_apple, 1, 1, 2));

        t = tables.get(Zone.SNOW);
        add(t.common, fish(Fish.ICE_TROUT, 35), e(Items.fish, 0, 1, 1, 25));
        add(t.uncommon, e(Items.snowball, 4, 8, 8), e(Blocks.packed_ice, 2, 4, 6), e(IceikaItems.iceShards, 1, 2, 8));
        add(t.rare, e(IceikaItems.iceStone, 1, 2, 8), e(VanillaItemsWeapons.frostSword, 1, 1, 2));
        add(t.veryRare, e(Items.diamond, 1, 1, 4), e(VanillaItemsWeapons.frostCannon, 1, 1, 2));

        t = tables.get(Zone.ICEIKA);
        add(t.common, fish(Fish.SNOW_BURBOT, 35), fish(Fish.ICE_TROUT, 20));
        add(t.uncommon, e(IceikaItems.iceShards, 2, 4, 10), e(IceikaItems.iceStone, 1, 2, 8), e(ModItems.rimeIngot, 1, 1, 5));
        add(t.rare, fish(Fish.CRYSTAL_FISH, 8), e(IceikaItems.snowflake, 1, 1, 5));
        add(t.veryRare, e(IceikaItems.snowflake, 2, 3, 3), e(IceikaItems.frossivence, 1, 1, 1));

        t = tables.get(Zone.JUNGLE);
        add(t.common, fish(Fish.PIRANHA, 30), e(Items.fish, 0, 1, 1, 20));
        add(t.uncommon, e(VanillaItemsOther.jungleShards, 1, 2, 10), e(Items.dye, 3, 2, 4, 8), e(Blocks.vine, 1, 3, 6));
        add(t.rare, fish(Fish.EMERALD_CATFISH, 8), e(VanillaItemsOther.jungleStone, 1, 1, 6));
        add(t.veryRare, e(Items.emerald, 1, 2, 4));

        t = tables.get(Zone.DESERT);
        add(t.common, fish(Fish.SAND_CARP, 35), e(Items.fish, 0, 1, 1, 15));
        add(t.uncommon, e(Items.gold_nugget, 2, 6, 10), e(Items.bone, 1, 3, 8));
        add(t.rare, fish(Fish.MUMMY_FISH, 8), e(Items.gold_ingot, 1, 2, 6), e(VanillaItemsOther.shadowCoins, 1, 3, 4));
        add(t.veryRare, e(VanillaItemsWeapons.sandslash, 1, 1, 3));

        t = tables.get(Zone.CAVE);
        add(t.common, fish(Fish.CAVEFISH, 35), e(Items.fish, 0, 1, 1, 15));
        // необычный улов пещер - руда своего яруса (initMine), отдельной таблицы нет
        add(t.rare, e(Items.gold_ingot, 1, 2, 6), e(VanillaItemsOther.rupeeIngot, 1, 1, 4), e(VanillaItemsOther.arlemiteIngot, 1, 1, 4));
        add(t.veryRare, e(Items.diamond, 1, 2, 4), e(Items.emerald, 1, 1, 3));

        t = tables.get(Zone.HELL);
        add(t.common, fish(Fish.OBSIDIFISH, 40));
        add(t.uncommon, e(Items.blaze_powder, 1, 2, 8), e(Items.magma_cream, 1, 2, 6), e(Items.quartz, 2, 4, 8),
            e(VanillaItemsOther.hellstoneIngot, 1, 1, 6));
        add(t.rare, fish(Fish.FLAREFIN, 8), fish(Fish.EMBER_FRY, 6), e(VanillaItemsOther.bluefireStone, 1, 1, 5),
            e(VanillaItemsOther.netheriteIngot, 1, 2, 5));
        add(t.veryRare, e(VanillaItemsOther.furyFire, 1, 1, 2));

        t = tables.get(Zone.ARCANA);
        add(t.common, fish(Fish.ARCANE_EEL, 40));
        add(t.uncommon, e(ArcanaItems.arcanium, 1, 2, 8), e(ArcanaItems.collector, 1, 2, 8), e(ArcanaItems.hitchakSeeds, 1, 2, 4),
            e(ArcanaItems.veiloSeeds, 1, 2, 4));
        add(t.rare, fish(Fish.STARGAZER, 8), e(ArcanaItems.dungeonTokens, 1, 3, 6));
        add(t.veryRare, e(ArcanaItems.chargedCollector, 1, 1, 3));

        twilight(Zone.EDEN, Fish.EDEN_GOLDFISH, TwilightItemsOther.edenFragments, TwilightItemsOther.edenSoul,
            TwilightItemsOther.edenGem, TwilightItemsOther.edenChunk);
        twilight(Zone.WILDWOOD, Fish.WILDWOOD_GLOWFISH, TwilightItemsOther.wildwoodFragments,
            TwilightItemsOther.wildwoodSoul, TwilightItemsOther.wildwoodGem, TwilightItemsOther.wildwoodChunk);
        twilight(Zone.APALACHIA, Fish.APALACHIA_RAY, TwilightItemsOther.apalachiaFragments,
            TwilightItemsOther.apalachiaSoul, TwilightItemsOther.apalachiaGem, TwilightItemsOther.apalachiaChunk);
        twilight(Zone.SKYTHERN, Fish.SKYTHERN_FLYER, TwilightItemsOther.skythernFragments,
            TwilightItemsOther.skythernSoul, TwilightItemsOther.skythernGem, TwilightItemsOther.skythernChunk);
        twilight(Zone.MORTUM, Fish.MORTUM_EEL, TwilightItemsOther.mortumFragments, TwilightItemsOther.mortumSoul,
            TwilightItemsOther.mortumGem, TwilightItemsOther.mortumChunk);
        // редкие рыбы миров для заданий Леорны (Грозовой сом - только в ливень, см. storm); души Мортума заряжают Жатву душ
        add(tables.get(Zone.EDEN).rare, fish(Fish.SUN_PERCH, 6));
        add(tables.get(Zone.APALACHIA).rare, fish(Fish.AMETHYST_PIKE, 6));
        add(tables.get(Zone.MORTUM).rare, fish(Fish.BONE_FISH, 6));
        add(tables.get(Zone.MORTUM).uncommon, e(ModItems.wanderingSoul, 1, 1, 12));
        // пруды облачных островов Небесного терна
        t = tables.get(Zone.CLOUD);
        add(t.common, fish(Fish.CLOUDFIN, 40), fish(Fish.SKYTHERN_FLYER, 10));
        add(t.uncommon, e(TwilightItemsOther.skythernFragments, 1, 3, 10), e(TwilightItemsOther.skythernSoul, 1, 2, 8), e(Items.feather, 2, 5, 6));
        add(t.rare, fish(Fish.CLOUD_JELLY, 8), e(TwilightItemsOther.skythernGem, 1, 1, 6));
        add(t.veryRare, e(TwilightItemsOther.skythernChunk, 1, 1, 4));

        t = tables.get(Zone.DREAM);
        add(t.common, fish(Fish.DREAM_CARP, 40));
        add(t.uncommon, e(VetheaItems.dirtyPearls, 1, 3, 10), e(VetheaItems.teakerLump, 1, 1, 6));
        add(t.rare, e(VetheaItems.cleanPearls, 1, 2, 8), e(VetheaItems.amthirmisLump, 1, 1, 5));
        add(t.veryRare, e(VetheaItems.polishedPearls, 1, 1, 4), e(VetheaItems.darvenLump, 1, 1, 3));
        initMine();
    }

    // ------------------------------------------------------------------ шахтёрская рыбалка

    /** Ярусы пещер по высоте поплавка (зона {@link Zone#CAVE}). */
    public static final int UPPER = 0, MIDDLE = 1, DEEP = 2, TIERS = 3;
    /** Верхние пещеры - с этой высоты (и до 47), средние - с {@link #MIDDLE_FROM}, ниже - глубины. */
    public static final int UPPER_FROM = 33, MIDDLE_FROM = 17;
    /** Глубины целиком - с этой силы рыбалки; слабее там ловится как в средних пещерах. */
    public static final int DEEP_POWER = 40;
    /** Мета шахтёрского ящика: CRATE_MINE + ярус (у остальных ящиков мета - номер зоны). */
    public static final int CRATE_MINE = 32;

    /** Руда ярусов пещер, руда лавы, редкая добавка шахтёрского ящика. */
    private static List<Entry>[] mine;
    private static List<Entry> lava, mineRare;

    /** Ярус по высоте поплавка и силе рыбалки. */
    public static int caveTier(double y, int power) {
        int by = net.minecraft.util.MathHelper.floor_double(y);
        if (by >= UPPER_FROM) return UPPER;
        if (by >= MIDDLE_FROM) return MIDDLE;
        return power >= DEEP_POWER ? DEEP : MIDDLE;
    }

    private static int tier(int t) {
        return t >= 0 && t < TIERS ? t : MIDDLE;
    }

    /** Ярус шахтёрского ящика по мете; старый ящик пещер - средние; не шахтёрский - -1. */
    public static int crateTier(int meta) {
        if (meta >= CRATE_MINE && meta < CRATE_MINE + TIERS) return meta - CRATE_MINE;
        return meta == Zone.CAVE.ordinal() ? MIDDLE : -1;
    }

    /** Ящик зоны (в пещерах - шахтёрский своего яруса). */
    public static ItemStack crateItem(Zone zone, int tier) {
        return new ItemStack(ModItems.crate, 1, zone == Zone.CAVE ? CRATE_MINE + tier(tier) : zone.ordinal());
    }

    /** Все ящики для показа: по одному на зону, вместо ящика пещер - три шахтёрских. */
    public static List<ItemStack> crateKinds() {
        List<ItemStack> l = new ArrayList<ItemStack>();
        for (Zone z : Zone.values()) {
            if (z != Zone.CAVE) l.add(new ItemStack(ModItems.crate, 1, z.ordinal()));
            else for (int t = 0; t < TIERS; t++) l.add(new ItemStack(ModItems.crate, 1, CRATE_MINE + t));
        }
        return l;
    }

    /** Руда модов сборки по рудному словарю: по одному блоку на имя; нет такой руды в сборке - null. */
    private static Entry dict(int min, int max, int w, String... names) {
        List<ItemStack> l = new ArrayList<ItemStack>();
        for (String n : names) {
            for (ItemStack s : net.minecraftforge.oredict.OreDictionary.getOres(n)) {
                if (s == null || !(s.getItem() instanceof net.minecraft.item.ItemBlock)) continue;
                ItemStack c = s.copy();
                c.stackSize = 1;
                if (c.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) c.setItemDamage(0);
                l.add(c);
                break;
            }
        }
        return l.isEmpty() ? null : new Entry(l.toArray(new ItemStack[l.size()]), min, max, w);
    }

    private static void addOres(List<Entry> l, Entry... es) {
        for (Entry x : es) if (x != null) l.add(x);
    }

    @SuppressWarnings("unchecked")
    private static void initMine() {
        Block realmite = net.divinerpg.utils.blocks.VanillaBlocks.realmiteOre, arlemite = net.divinerpg.utils.blocks.VanillaBlocks.arlemiteOre,
            rupee = net.divinerpg.utils.blocks.VanillaBlocks.rupeeOre, netherite = net.divinerpg.utils.blocks.VanillaBlocks.netheriteOre,
            bloodgem = net.divinerpg.utils.blocks.VanillaBlocks.bloodgemOre;
        // Таумкрафт: камни с аспектами - одна строка на шесть видов
        Entry infused = dict(1, 2, 3, "oreInfusedAir", "oreInfusedFire", "oreInfusedWater", "oreInfusedEarth", "oreInfusedOrder", "oreInfusedEntropy");
        mine = new List[TIERS];
        for (int i = 0; i < TIERS; i++) mine[i] = new ArrayList<Entry>();
        // веса - по предложению из листа добычи (12.11.2026); медь, олово, алюминий (в RedServer Magic - Tinkers), серебро, свинец - если
        // они есть в сборке, с небольшим весом, чтобы не забивали ящик
        Entry aluminum = dict(1, 2, 2, "oreAluminum");
        if (aluminum == null) aluminum = dict(1, 2, 2, "oreAluminium");
        addOres(mine[UPPER], e(Blocks.coal_ore, 2, 4, 10), e(Blocks.iron_ore, 1, 3, 8), dict(1, 2, 3, "oreAmber"), infused, e(Blocks.redstone_ore, 1, 2, 2),
            dict(1, 3, 3, "oreCopper"), dict(1, 3, 3, "oreTin"), aluminum);
        addOres(mine[MIDDLE], e(Blocks.iron_ore, 1, 3, 8), e(Blocks.coal_ore, 2, 3, 5), e(Blocks.gold_ore, 1, 2, 5), e(Blocks.lapis_ore, 1, 3, 5),
            e(Blocks.redstone_ore, 1, 3, 4), e(realmite, 1, 2, 4), dict(1, 1, 2, "oreCinnabar"), infused, dict(1, 2, 2, "oreCopper"), dict(1, 2, 2, "oreTin"),
            dict(1, 2, 2, "oreSilver"), dict(1, 2, 2, "oreLead"));
        addOres(mine[DEEP], e(Blocks.redstone_ore, 1, 3, 6), e(Blocks.gold_ore, 1, 2, 5), e(realmite, 1, 3, 6), e(Blocks.lapis_ore, 1, 2, 3),
            e(arlemite, 1, 1, 3), e(rupee, 1, 1, 3), e(Blocks.diamond_ore, 1, 1, 2), e(Blocks.emerald_ore, 1, 1, 1), dict(1, 2, 2, "oreSilver"),
            dict(1, 2, 2, "oreLead"));
        lava = new ArrayList<Entry>();
        addOres(lava, e(Blocks.quartz_ore, 2, 4, 10), e(netherite, 1, 2, 4), e(bloodgem, 1, 1, 3));
        mineRare = new ArrayList<Entry>();
        addOres(mineRare, e(Blocks.diamond_ore, 1, 1, 2), e(rupee, 1, 1, 2), e(Blocks.emerald_ore, 1, 1, 1));
    }

    /** Руда яруса для показа (NEI, автотесты). */
    public static List<ItemStack> ores(int tier) {
        init();
        List<ItemStack> l = new ArrayList<ItemStack>();
        for (Entry x : tier < 0 ? lava : mine[tier(tier)]) for (ItemStack a : x.alts) l.add(a.copy());
        return l;
    }

    private static void twilight(Zone z, Fish f, Item fragments, Item soul, Item gem, Item chunk) {
        Table t = tables.get(z);
        add(t.common, fish(f, 40));
        add(t.uncommon, e(fragments, 1, 3, 10), e(soul, 1, 2, 8));
        add(t.rare, e(gem, 1, 1, 8));
        add(t.veryRare, e(chunk, 1, 1, 4));
    }

    private static ItemStack pick(List<Entry> l, Random r) {
        int total = 0;
        for (Entry x : l) total += x.weight;
        if (total <= 0) return null;
        int n = r.nextInt(total);
        for (Entry x : l) {
            n -= x.weight;
            if (n < 0) return x.make(r);
        }
        return null;
    }

    /**
     * Улов. power - сила рыбалки, baited - была ли приманка, crateBonus и rareMult - от удочки и приманки.
     */
    public static ItemStack roll(Random r, Zone zone, int power, boolean baited, float crateBonus, float rareMult) {
        return roll(r, zone, power, baited, crateBonus, rareMult, MIDDLE);
    }

    /** Улов; tier - ярус пещер ({@link #caveTier}), вне пещер не важен. */
    public static ItemStack roll(Random r, Zone zone, int power, boolean baited, float crateBonus, float rareMult, int tier) {
        init();
        Table t = tables.get(zone);
        if (!baited) {
            if (zone != Zone.HELL && r.nextFloat() < 0.25F) return pick(junk, r);
            return pick(t.common, r);
        }
        if (zone != Zone.HELL && r.nextFloat() < Math.max(0.02F, 0.12F - power / 800F)) return pick(junk, r);
        if (r.nextFloat() < 0.06F + power / 1200F + crateBonus) return crateItem(zone, tier);
        float x = r.nextFloat();
        if (x < power / 2500F * rareMult && !t.veryRare.isEmpty()) return pick(t.veryRare, r);
        if (x < power / 600F * rareMult && !t.rare.isEmpty()) return pick(t.rare, r);
        if (x < 0.15F + power / 500F) {
            if (zone == Zone.CAVE) { // в пещерах необычный улов - 1-2 руды своего яруса
                ItemStack o = pick(mine[tier(tier)], r);
                if (o != null) o.stackSize = 1 + r.nextInt(2);
                return o;
            }
            if (!t.uncommon.isEmpty()) return pick(t.uncommon, r);
        }
        return pick(t.common, r);
    }

    public static List<ItemStack> crate(Random r, Zone zone) {
        return crate(r, zone, MIDDLE);
    }

    /**
     * Содержимое ящика зоны: 4-7 предметов из необычного и редкого, плюс рыба зоны и иногда приманки. Шахтёрские ящики
     * (пещеры по ярусам и лава): 5-8 стопок руды блоками, в пещерах в 1 из 8 - ещё алмаз, изумруд или рупий.
     */
    public static List<ItemStack> crate(Random r, Zone zone, int tier) {
        init();
        Table t = tables.get(zone);
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (zone == Zone.CAVE || zone == Zone.HELL) {
            List<Entry> ores = zone == Zone.HELL ? lava : mine[tier(tier)];
            int n = 5 + r.nextInt(4);
            for (int i = 0; i < n; i++) {
                ItemStack s = pick(ores, r);
                if (s != null) out.add(s);
            }
            if (r.nextInt(8) == 0) {
                ItemStack s = pick(zone == Zone.HELL ? t.veryRare : mineRare, r);
                if (s != null) out.add(s);
            }
        } else {
            int n = 4 + r.nextInt(4);
            for (int i = 0; i < n; i++) {
                ItemStack s = r.nextInt(4) == 0 && !t.rare.isEmpty() ? pick(t.rare, r) : pick(t.uncommon, r);
                if (s != null) out.add(s);
            }
            if (r.nextInt(8) == 0 && !t.veryRare.isEmpty()) out.add(pick(t.veryRare, r));
        }
        ItemStack f = pick(t.common, r);
        if (f != null) out.add(f);
        if (r.nextInt(2) == 0) {
            Bait b = Bait.randomArcana(r);
            out.add(new ItemStack(b.item(), 2 + r.nextInt(3)));
        }
        // шмот мира: оружие или инструмент ~2%, часть брони ~0.5%
        com.divinerpgextended.world.twilight.TwilightWorld tw = zone.world();
        if (tw != null) {
            float x = r.nextFloat();
            Item[] tools = gear(tw), armor = tw.armor();
            if (x < GEAR_CHANCE) {
                Item it = tools[r.nextInt(tools.length)];
                if (it != null) out.add(new ItemStack(it));
            } else if (x < GEAR_CHANCE + ARMOR_CHANCE) {
                Item it = armor[r.nextInt(armor.length)];
                if (it != null) out.add(new ItemStack(it));
            }
        }
        return out;
    }

    /** Шанс оружия или инструмента мира в ящике сумеречного мира и шанс части брони. */
    public static final float GEAR_CHANCE = 0.02F, ARMOR_CHANCE = 0.005F;

    /** Оружие и инструменты мира Divine: клинок, слайсер, блиц, фазер, лук, кирка, топор, лопата. */
    public static Item[] gear(com.divinerpgextended.world.twilight.TwilightWorld w) {
        switch (w) {
            case EDEN: return new Item[] { net.divinerpg.utils.items.TwilightItemsWeapons.edenBlade, net.divinerpg.utils.items.TwilightItemsWeapons.edenSlicer,
                net.divinerpg.utils.items.TwilightItemsWeapons.edenBlitz, net.divinerpg.utils.items.TwilightItemsWeapons.edenPhaser, net.divinerpg.utils.items.TwilightItemsWeapons.edenBow,
                net.divinerpg.utils.items.TwilightItemsTools.edenPickaxe, net.divinerpg.utils.items.TwilightItemsTools.edenAxe, net.divinerpg.utils.items.TwilightItemsTools.edenShovel };
            case WILDWOOD: return new Item[] { net.divinerpg.utils.items.TwilightItemsWeapons.wildwoodBlade, net.divinerpg.utils.items.TwilightItemsWeapons.wildwoodSlicer,
                net.divinerpg.utils.items.TwilightItemsWeapons.wildwoodBlitz, net.divinerpg.utils.items.TwilightItemsWeapons.wildwoodPhaser, net.divinerpg.utils.items.TwilightItemsWeapons.wildwoodBow,
                net.divinerpg.utils.items.TwilightItemsTools.wildwoodPickaxe, net.divinerpg.utils.items.TwilightItemsTools.wildwoodAxe, net.divinerpg.utils.items.TwilightItemsTools.wildwoodShovel };
            case APALACHIA: return new Item[] { net.divinerpg.utils.items.TwilightItemsWeapons.apalachiaBlade, net.divinerpg.utils.items.TwilightItemsWeapons.apalachiaSlicer,
                net.divinerpg.utils.items.TwilightItemsWeapons.apalachiaBlitz, net.divinerpg.utils.items.TwilightItemsWeapons.apalachiaPhaser, net.divinerpg.utils.items.TwilightItemsWeapons.apalachiaBow,
                net.divinerpg.utils.items.TwilightItemsTools.apalachiaPickaxe, net.divinerpg.utils.items.TwilightItemsTools.apalachiaAxe, net.divinerpg.utils.items.TwilightItemsTools.apalachiaShovel };
            case SKYTHERN: return new Item[] { net.divinerpg.utils.items.TwilightItemsWeapons.skythernBlade, net.divinerpg.utils.items.TwilightItemsWeapons.skythernSlicer,
                net.divinerpg.utils.items.TwilightItemsWeapons.skythernBlitz, net.divinerpg.utils.items.TwilightItemsWeapons.skythernPhaser, net.divinerpg.utils.items.TwilightItemsWeapons.skythernBow,
                net.divinerpg.utils.items.TwilightItemsTools.skythernPickaxe, net.divinerpg.utils.items.TwilightItemsTools.skythernAxe, net.divinerpg.utils.items.TwilightItemsTools.skythernShovel };
            default: return new Item[] { net.divinerpg.utils.items.TwilightItemsWeapons.mortumBlade, net.divinerpg.utils.items.TwilightItemsWeapons.mortumSlicer,
                net.divinerpg.utils.items.TwilightItemsWeapons.mortumBlitz, net.divinerpg.utils.items.TwilightItemsWeapons.mortumPhaser, net.divinerpg.utils.items.TwilightItemsWeapons.mortumBow,
                net.divinerpg.utils.items.TwilightItemsTools.mortumPickaxe, net.divinerpg.utils.items.TwilightItemsTools.mortumAxe, net.divinerpg.utils.items.TwilightItemsTools.mortumShovel };
        }
    }

    /** Золотая рыбка: в сумеречных мирах 1 из {@link #GOLDEN_ODDS} поклёвок, ни сила, ни приманка, ни удочка не влияют. */
    public static final int GOLDEN_ODDS = 1000;

    public static ItemStack golden(Random r, Zone zone) {
        return zone.twilight() && r.nextInt(GOLDEN_ODDS) == 0 ? new ItemStack(ModItems.fish, 1, Fish.GOLDEN_FISH.ordinal()) : null;
    }

    /** Грозовой сом: только в Диком лесу в ливень (и в дождь), с приманкой, с шансом редкого улова (не меньше 5%). */
    public static ItemStack storm(Random r, Zone zone, boolean raining, boolean baited, int power, float rareMult) {
        if (zone != Zone.WILDWOOD || !raining || !baited) return null;
        return r.nextFloat() < Math.max(0.05F, power / 600F * rareMult) ? new ItemStack(ModItems.fish, 1, Fish.STORM_CATFISH.ordinal()) : null;
    }

    /** Пещерный удильщик: в пещере на Глазок циклопа - с этим шансом на поклёвку (растёт с редким уловом удочки). */
    public static final float ANGLER_CHANCE = 0.12F;

    public static ItemStack cave(Random r, Zone zone, Bait bait, float rareMult) {
        if (zone != Zone.CAVE || bait != Bait.CYCLOPS_EYE) return null;
        return r.nextFloat() < ANGLER_CHANCE * rareMult ? new ItemStack(ModItems.fish, 1, Fish.CAVE_ANGLER.ordinal()) : null;
    }

    /** Золотой улов Эдема: рыба приходит позолоченной (8%, с Солнечной блесной - 16%). */
    public static final float GILD_CHANCE = 0.08F;
    public static final String GILDED = "dreGilded";

    public static void gild(Random r, Zone zone, Bait bait, ItemStack loot) {
        if (zone != Zone.EDEN || loot == null || loot.getItem() != ModItems.fish) return;
        if (r.nextFloat() >= GILD_CHANCE * (bait == Bait.SUN_LURE ? 2 : 1)) return;
        if (!loot.hasTagCompound()) loot.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        loot.getTagCompound().setBoolean(GILDED, true);
    }

    public static boolean gilded(ItemStack s) {
        return s != null && s.hasTagCompound() && s.getTagCompound().getBoolean(GILDED);
    }

    /** Квестовые рыбы (задания Леорны). */
    public static List<Fish> questFish() {
        List<Fish> l = new ArrayList<Fish>();
        for (Fish f : Fish.values()) if (f.quest) l.add(f);
        return l;
    }

    /** Строка таблицы для показа (NEI): предмет, редкость и сколько штук. */
    public static final class View {

        public static final int COMMON = 0, UNCOMMON = 1, RARE = 2, VERY_RARE = 3, CRATE = 4, BAIT = 5, JUNK = 6, RELIC = 7;

        public final ItemStack stack;
        public final int tier, min, max;

        View(ItemStack stack, int tier, int min, int max) {
            this.stack = stack;
            this.tier = tier;
            this.min = min;
            this.max = max;
        }
    }

    private static void view(List<View> out, List<Entry> l, int tier) {
        for (Entry x : l) out.add(new View(x.stack.copy(), tier, x.min, x.max));
    }

    /** Улов зоны с приманкой, от обычного к очень редкому, плюс ящик зоны. */
    public static List<View> catches(Zone zone) {
        init();
        Table t = tables.get(zone);
        List<View> out = new ArrayList<View>();
        view(out, t.common, View.COMMON);
        view(out, t.uncommon, View.UNCOMMON);
        if (zone == Zone.CAVE) { // руда всех ярусов, 1-2 штуки
            List<ItemStack> seen = new ArrayList<ItemStack>();
            for (List<Entry> l : mine) for (Entry x : l) for (ItemStack a : x.alts) {
                boolean dup = false;
                for (ItemStack s : seen) dup |= s.isItemEqual(a);
                if (dup) continue;
                seen.add(a);
                out.add(new View(a.copy(), View.UNCOMMON, 1, 2));
            }
        }
        view(out, t.rare, View.RARE);
        view(out, t.veryRare, View.VERY_RARE);
        if (zone == Zone.WILDWOOD) out.add(new View(new ItemStack(ModItems.fish, 1, Fish.STORM_CATFISH.ordinal()), View.RARE, 1, 1)); // только в ливень
        if (zone.twilight()) out.add(new View(new ItemStack(ModItems.fish, 1, Fish.GOLDEN_FISH.ordinal()), View.VERY_RARE, 1, 1));
        if (zone == Zone.CAVE) out.add(new View(new ItemStack(ModItems.fish, 1, Fish.CAVE_ANGLER.ordinal()), View.RARE, 1, 1)); // только на Глазок циклопа
        ItemStack relic = relicOf(zone);
        if (relic != null && com.divinerpgextended.Config.fishingRelics) out.add(new View(relic, View.RELIC, 1, 1));
        if (zone == Zone.CAVE) for (int k = 0; k < TIERS; k++) out.add(new View(crateItem(zone, k), View.CRATE, 1, 1));
        else out.add(new View(crateItem(zone, MIDDLE), View.CRATE, 1, 1));
        return out;
    }

    /** Реликвия зоны (ловится очень редко и только с приманкой) или null. */
    public static ItemStack relicOf(Zone zone) {
        return zone == Zone.DREAM ? new ItemStack(ModItems.nightmareDrill) : null;
    }

    /** Шанс реликвии растёт с силой: при силе 130 (лучшая снасть) - около 1 из 250 поклёвок. */
    public static ItemStack relic(Random r, Zone zone, int power) {
        if (!com.divinerpgextended.Config.fishingRelics) return null;
        ItemStack s = relicOf(zone);
        if (s == null) return null;
        double chance = 0.004 * Math.min(1.5, power / 130.0) * com.divinerpgextended.Config.relicChanceMultiplier;
        return r.nextDouble() < chance ? s : null;
    }

    /** Мусор (любая вода, кроме лавы; без приманки - каждый четвёртый улов). */
    public static List<View> junk() {
        init();
        List<View> out = new ArrayList<View>();
        view(out, junk, View.JUNK);
        return out;
    }

    public static List<View> crateContents(Zone zone) {
        return crateContents(zone, MIDDLE);
    }

    /** Что может лежать в ящике зоны (в пещерах - в шахтёрском ящике этого яруса). */
    public static List<View> crateContents(Zone zone, int tier) {
        init();
        Table t = tables.get(zone);
        List<View> out = new ArrayList<View>();
        if (zone == Zone.CAVE || zone == Zone.HELL) {
            for (Entry x : zone == Zone.HELL ? lava : mine[tier(tier)]) for (ItemStack a : x.alts) out.add(new View(a.copy(), View.UNCOMMON, x.min, x.max));
            view(out, zone == Zone.HELL ? t.veryRare : mineRare, View.VERY_RARE);
            view(out, t.common, View.COMMON);
            for (Bait b : Bait.values()) if (b.arcana) out.add(new View(new ItemStack(b.item()), View.BAIT, 2, 4));
            return out;
        }
        view(out, t.uncommon, View.UNCOMMON);
        view(out, t.rare, View.RARE);
        view(out, t.veryRare, View.VERY_RARE);
        view(out, t.common, View.COMMON);
        for (Bait b : Bait.values()) if (b.arcana) out.add(new View(new ItemStack(b.item()), View.BAIT, 2, 4));
        com.divinerpgextended.world.twilight.TwilightWorld tw = zone.world();
        if (tw != null) { // шмот мира
            for (Item it : gear(tw)) if (it != null) out.add(new View(new ItemStack(it), View.VERY_RARE, 1, 1));
            for (Item it : tw.armor()) if (it != null) out.add(new View(new ItemStack(it), View.VERY_RARE, 1, 1));
        }
        return out;
    }
}
