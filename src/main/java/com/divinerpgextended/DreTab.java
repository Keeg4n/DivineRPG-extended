package com.divinerpgextended;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.divinerpgextended.fishing.Rod;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Вкладки творческого режима аддона. Все предметы и блоки ставят себе {@link #TAB} при создании, а {@link #sort()}
 * после регистрации раскладывает их по вкладкам по имени регистрации: основное, снаряжение, аксессуары, рыбалка,
 * аркана и зелья, боссы и прогресс. Предметы без вкладки (горящий факел, посадки) так и остаются без неё.
 */
public final class DreTab extends CreativeTabs {

    /** Основное: блоки, факелы, лампы, транспорт и прочее. */
    public static final DreTab TAB = new DreTab("divinerpgextended", "main");
    public static final DreTab GEAR = new DreTab("dre_gear", "gear");
    public static final DreTab TRINKETS = new DreTab("dre_trinkets", "trinkets");
    public static final DreTab FISHING = new DreTab("dre_fishing", "fishing");
    public static final DreTab MAGIC = new DreTab("dre_magic", "magic");
    public static final DreTab BOSSES = new DreTab("dre_bosses", "bosses");

    private final String icon;

    private DreTab(String label, String icon) {
        super(label);
        this.icon = icon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getTabIconItem() {
        Item i = null;
        if ("gear".equals(icon)) i = ModItems.halitePickaxe;
        else if ("trinkets".equals(icon)) i = ModItems.trinket;
        else if ("fishing".equals(icon)) i = ModItems.rods[Rod.HUNGER.ordinal()];
        else if ("magic".equals(icon)) i = ModItems.arcanePotion;
        else if ("bosses".equals(icon)) i = ModItems.bossEgg;
        return i != null ? i : ModItems.wandererClock;
    }

    private static Set<String> set(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    static final Set<String> GEAR_NAMES = set("icePick", "rimePick", "halitePickaxe", "haliteAxe", "haliteShovel", "haliteMultitool", "arcanePick",
        "mageStaff");
    static final Set<String> TRINKET_NAMES = set("trinket", "deepShell");
    static final Set<String> FISHING_NAMES = set("fish", "crate", "worm", "wormShovel", "fishGuide", "baitBag", "nightmareDrill");
    static final Set<String> MAGIC_NAMES = set("arcanePotion", "arcaneBrewer", "arcaneConcentrate", "lureTorch", "memoryCrystal", "furnaceModule",
        "petCarrier");
    static final Set<String> BOSS_NAMES = set("carcinusAltar", "glaconAltar", "throneAltar", "tombAltar", "nestAltar", "arenaAltar", "jungleAltar",
        "windTotem", "lairSpawner", "burrow", "carcinusHeart", "sharkNest", "cadillionCall", "lairMap", "twilightMaterial", "sanctumSeal", "rimeOre",
        "rimeIngot", "watcherEye", "frozenHeart", "throneShard", "bestiary", "jungleOre", "jungleGem", "jungleTotem", "haliteMaterial", "mortumTrophy",
        "spiritSpark", "worldSpirit", "guardianHeart", "dreamItem", "bossEgg");

    /** Вкладка по имени регистрации (без "divinerpgextended:"). */
    public static DreTab of(String name) {
        if (GEAR_NAMES.contains(name) || name.startsWith("wanderer") && !name.equals("wandererClock") && !name.equals("wandererAnvil")
            && !name.equals("wandererEgg")) return GEAR;
        if (TRINKET_NAMES.contains(name)) return TRINKETS;
        if (FISHING_NAMES.contains(name) || name.startsWith("rod_") || name.startsWith("angler")) return FISHING;
        if (MAGIC_NAMES.contains(name)) return MAGIC;
        if (BOSS_NAMES.contains(name)) return BOSSES;
        return TAB;
    }

    private static boolean ours(CreativeTabs t) {
        return t instanceof DreTab;
    }

    /** Разложить зарегистрированные предметы и блоки аддона по вкладкам (после регистрации; только клиент - вкладки есть лишь там). */
    @SideOnly(Side.CLIENT)
    public static void sort() {
        String prefix = DivineRPGExtended.MODID + ":";
        for (Object k : Item.itemRegistry.getKeys()) {
            String key = (String) k;
            if (!key.startsWith(prefix)) continue;
            Item i = (Item) Item.itemRegistry.getObject(key);
            if (i != null && ours(i.getCreativeTab())) i.setCreativeTab(of(key.substring(prefix.length())));
        }
        for (Object k : Block.blockRegistry.getKeys()) {
            String key = (String) k;
            if (!key.startsWith(prefix)) continue;
            Block b = (Block) Block.blockRegistry.getObject(key);
            if (b != null && ours(b.getCreativeTabToDisplayOn())) b.setCreativeTab(of(key.substring(prefix.length())));
        }
    }
}
