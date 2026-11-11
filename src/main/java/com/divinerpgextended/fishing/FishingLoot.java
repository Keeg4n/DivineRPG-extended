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
        final int min, max, weight;

        Entry(Item item, int meta, int min, int max, int weight) {
            this.stack = new ItemStack(item, 1, meta);
            this.min = min;
            this.max = max;
            this.weight = weight;
        }

        ItemStack make(Random r) {
            ItemStack s = stack.copy();
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
        add(t.uncommon, e(Items.coal, 1, 3, 10), e(Items.redstone, 2, 4, 8), e(Items.iron_ingot, 1, 1, 8));
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
        init();
        Table t = tables.get(zone);
        if (!baited) {
            if (zone != Zone.HELL && r.nextFloat() < 0.25F) return pick(junk, r);
            return pick(t.common, r);
        }
        if (zone != Zone.HELL && r.nextFloat() < Math.max(0.02F, 0.12F - power / 800F)) return pick(junk, r);
        if (r.nextFloat() < 0.06F + power / 1200F + crateBonus) return new ItemStack(ModItems.crate, 1, zone.ordinal());
        float x = r.nextFloat();
        if (x < power / 2500F * rareMult && !t.veryRare.isEmpty()) return pick(t.veryRare, r);
        if (x < power / 600F * rareMult && !t.rare.isEmpty()) return pick(t.rare, r);
        if (x < 0.15F + power / 500F && !t.uncommon.isEmpty()) return pick(t.uncommon, r);
        return pick(t.common, r);
    }

    /** Содержимое ящика зоны: 4-7 предметов из необычного и редкого, плюс рыба зоны и иногда приманки. */
    public static List<ItemStack> crate(Random r, Zone zone) {
        init();
        Table t = tables.get(zone);
        List<ItemStack> out = new ArrayList<ItemStack>();
        int n = 4 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            ItemStack s = r.nextInt(4) == 0 && !t.rare.isEmpty() ? pick(t.rare, r) : pick(t.uncommon, r);
            if (s != null) out.add(s);
        }
        if (r.nextInt(8) == 0 && !t.veryRare.isEmpty()) out.add(pick(t.veryRare, r));
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
        view(out, t.rare, View.RARE);
        view(out, t.veryRare, View.VERY_RARE);
        if (zone == Zone.WILDWOOD) out.add(new View(new ItemStack(ModItems.fish, 1, Fish.STORM_CATFISH.ordinal()), View.RARE, 1, 1)); // только в ливень
        if (zone.twilight()) out.add(new View(new ItemStack(ModItems.fish, 1, Fish.GOLDEN_FISH.ordinal()), View.VERY_RARE, 1, 1));
        ItemStack relic = relicOf(zone);
        if (relic != null && com.divinerpgextended.Config.fishingRelics) out.add(new View(relic, View.RELIC, 1, 1));
        out.add(new View(new ItemStack(ModItems.crate, 1, zone.ordinal()), View.CRATE, 1, 1));
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

    /** Что может лежать в ящике зоны. */
    public static List<View> crateContents(Zone zone) {
        init();
        Table t = tables.get(zone);
        List<View> out = new ArrayList<View>();
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
