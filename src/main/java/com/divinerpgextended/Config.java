package com.divinerpgextended;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** All balance numbers, so they can be changed without a rebuild. */
public class Config {

    /** Full set protection by tier R0-R7, in percent (index = tier). */
    // before 0.99.126: { 50, 65, 72, 74, 76, 78, 80, 83, 85 } - R7 and R8 protected worse than Terra armor and Ichor
    public static int[] tierFullReduction = { 50, 65, 72, 75, 78, 81, 84, 88, 91 };
    private static final int[] OLD_TIER_REDUCTION = { 50, 65, 72, 74, 76, 78, 80, 83, 85 };
    /** Small perk slots by tier R0-R7. */
    public static int[] tierSmallSlots = { 0, 2, 2, 3, 3, 3, 4, 4, 4 };
    /** Tempering R7 -> R8: how many Spirits of the Universe. */
    public static int upgradeR8Spirits = 1;
    /** Arcana Crown R8: multiplier of arcane damage (bonus for Arcana swords and staff projectiles). */
    public static float crownArcanaDamage = 2.0F;

    public static int upgradeR1FuelRealmiteIngots = 16;
    /** Cost in divine stones for forging R1 and changing the core: Shadow, Bedrock, End, Corruption (Divine armor pays with realmite ingots). */
    public static int[] coreForgeStones = { 8, 12, 8, 8 };
    /** Shadow core: speed, same as the set itself. */
    public static double coreShadowSpeed = 3.0;
    /** Bedrock indestructibility: after how many ticks standing still Resistance I and II apply. */
    public static int[] bedrockStanceTicks = { 40, 100 };
    /** Full Inferno or Netherite set: chance that the ore drops already smelted. */
    public static double netherSmeltChance = 0.25;
    /** How much dust one blitz shot uses (5 pellets - 5 dust). */
    public static int blitzDustPerShot = 5;
    /** The ammo reserve digit on the ranged weapon icon in the inventory (client). */
    public static boolean ammoCountOnIcon = true;
    /** Accessories from armor sockets are visible on the body. */
    public static boolean accessoriesOnBody = true;
    /** Antidote (jungle set, perk "Poison Resistance"): how many times faster bad effects pass. */
    public static double jungleDebuffSpeed = 2.0;
    /** Divine bedrock armor does not wear out. */
    public static boolean bedrockArmorUnbreakable = true;
    /** Protection of Jack's full sets in percent (Divine has 0 for all three): skeleton, Jack O Man, Withered Reaper. */
    public static int skelemanArmor = 35, jackOManArmor = 50, witherReaperArmor = 60;
    /** Tinkers' Construct slime islands do not spawn in the Divine worlds. */
    public static boolean noTinkersIslandsInDivine = true;
    /** Frostbite from ice weapons: slowness, stronger, on the third hit the mob freezes into ice. */
    public static boolean iceFreeze = true;
    /** Gust from a Skythern mob hit: a sideways push, like the Skythern Wind Lord. */
    public static double skythernGust = 1.6;
    /** Mortum afterlife: chance that a killed mob leaves a soul. */
    public static double mortumSoulChance = 0.25;
    /** Weather of the twilight dimensions: Eden overheating, Wildwood downpour, Apalachia rockfall. */
    public static boolean edenSun = true, wildwoodRain = true, apalachiaRockfall = true;
    /** World mini-bosses and how often they can appear for a single player. */
    public static boolean miniBosses = true;
    public static int miniBossMinutes = 20;
    public static int upgradeTwilightFuelCount = 16;
    public static int perkFuelArlemiteIngots = 8;
    /** Altar of corruption by the addon rules ({@link com.divinerpgextended.station.AltarLogic}). */
    public static boolean corruptAltar = true;
    /** Ceiling of the enchantment price of an item; a book costs from altarBookBase to altarBookMax levels. */
    public static int altarTopLevels = 120, altarBookBase = 30, altarBookMax = 150;
    /** Points per enchant at full level by tier (best, middle, weak): item and book. */
    public static int[] altarItemPoints = { 84, 29, 14 }, altarBookPoints = { 60, 20, 11 };
    /** Prices before 0.99.81 (item up to 50, book up to 120): such values in an old file are replaced with the new ones. */
    private static final int OLD_TOP_LEVELS = 50, OLD_BOOK_MAX = 120;
    private static final int[] OLD_ITEM_POINTS = { 35, 12, 6 }, OLD_BOOK_POINTS = { 45, 15, 8 };
    /** Price of "learn enchants" by slots: item (shards, shards, stone) and book (rupee ingots). */
    public static int[] altarRevealCost = { 1, 5, 1 }, altarBookRevealCost = { 1, 3, 5 };
    /** In 0.99.71 a book cost 1 / 5 / 10 arcane ore: such a price in an old file is replaced with the new one. */
    private static final int[] OLD_BOOK_REVEAL_COST = { 1, 5, 10 };
    /** In 0.99.71 books were learned for arcane ore: this line in an old file is replaced with rupees. */
    private static final String BOOK_REVEAL = "book=divinerpg:rupeeIngot,divinerpg:rupeeIngot", OLD_BOOK_REVEAL = "book=divinerpg:arcaniumOre,divinerpg:arcaniumOre";
    /** What to pay for "learn": item kind = for the first and second slots, for the last. */
    public static String[] altarRevealItems = { "weapon=divinerpg:moltenShards,divinerpg:moltenStone", "tool=divinerpg:terranShards,divinerpg:terranStone",
        "armor=divinerpg:jungleShards,divinerpg:jungleStone", "bow=divinerpg:enderShards,divinerpg:enderStone", BOOK_REVEAL,
        "other=divinerpg:corruptedShards,divinerpg:corruptedStone" };

    /** Great Cadillion health: Eden, Wildwood, Apalachia, Skythern. */
    public static int[] cadillionHealth = { 300, 400, 500, 600 };
    /**
     * How much health (in half-hearts) a Great Cadillion hit removes from a player in armor of the required tier. The raw
     * damage is computed from this and the tier's protection.
     */
    public static double[] cadillionHit = { 3.0, 3.5, 4.0, 4.5 };
    /** Required armor tier against the Great Cadillion: Eden, Wildwood, Apalachia, Skythern. */
    public static int[] cadillionRequiredTier = { 1, 2, 3, 4 };
    public static int twilightDemonRequiredTier = 5;
    public static int karotRequiredTier = 6;
    /** How many times the boss damage grows for each armor tier below the required one. */
    public static double underTierMultiplier = 3.0;

    public static double carcinusHealth = 300, carcinusDamage = 8, carcinusNonCrabDamageFactor = 0.25,
        carcinusWaterArmorFactor = 0.1, carcinusVortexDamage = 5;
    public static int carcinusSummonClaws = 4;
    public static double snowGlaconHealth = 400, snowGlaconDamage = 9;
    public static double colossusHealth = 1500, colossusDamage = 16, lichHealth = 600, lichDamage = 12;
    public static int throneSpacing = 2048, throneSummonSnowflakes = 12;
    /** Lairs of the twilight world bosses (cadillion nests, Demonic Circle): one per N x N blocks square. */
    public static int nestSpacing = 640;
    /** Lairs of the Watcher and the King of Scorchers in the Nether: one of each per N x N square (0 - none, together with the Ayeraco aerie). */
    public static int netherLairSpacing = 384;
    /** Bosses are summoned only in their lairs (altars, circle); turn off - summon anywhere, as in Divine. */
    public static boolean summonOnlyInLairs = true;
    public static boolean bossNoFly = true;
    /** Boss arena ({@link com.divinerpgextended.boss.BossArena}). */
    public static boolean bossArenaHold = true;
    public static int bossArenaLeaveSeconds = 60, bossArenaRadius = 24, bossSummonClearRadius = 8;
    /** Easter eggs: where to look for Half-Life and Half-Life 2 (Steam libraries separated by ";"); empty - search on your own. */
    public static String halfLifePath = "";
    /** Easter eggs (H.E.V. suit, crowbar, RPG-7, couch with an RPG); false - disabled for everyone on the server. */
    public static boolean easterEggs = true;
    public static boolean dungeonSpawners = false;
    public static int divineTorchRadius = 24;
    public static int mapWorldSize = 10000;
    public static boolean worldCrafts = true, gateStones = true, miningTiers = true, worldGates = true;
    /** Ores for which tool auto-smelt does not multiply ingots with Fortune ({@link com.divinerpgextended.perk.SmeltNoFortune}). */
    public static String[] smeltNoFortune = { "divinerpg:rupeeOre", "divinerpg:arlemiteOre" };
    /** Structures of the twilight worlds: 1 per N chunks (0 - none). */
    public static int twilightRuinsRarity = 30, twilightDungeonRarity = 40, twilightBridgeRarity = 3, twilightCampRarity = 45;
    public static int apalachiaStructureBoost = 2, twilightPondRarity = 20, vetheaLakeRarity = 10, vetheaHangingLakeRarity = 14;
    /** Carcinus lair: 1 per N ocean chunks; snow dungeon: 1 per N snowy chunks (0 - disable). */
    /** Overworld lairs on a grid from the seed: Carcinus and the ice cave - one per N x N region. Maps. */
    public static int carcinusSpacing = 320, snowDungeonSpacing = 640, mapSearchRadius = 5000, mapSkipNear = 128, mapCooldownSeconds = 5;

    public static boolean requireBossUnlocks = true;
    public static boolean replaceTwilightClock = true;
    public static boolean snowGlobeOnlyFromBoss = true, scatterInModBiomes = true;
    /** Minimum distance between overworld lairs of the same kind (blocks): 40 chunks. */
    public static int lairGap = 640;
    public static boolean snowflakesInSummons = true;
    public static boolean warnVetheaInventory = true;
    public static boolean progressSpawns = true, lowRankNoDrops = true;
    /** Mobs of other Divine worlds in the Overworld - by the player's access to their world. */
    public static boolean foreignMobsByStage = true;
    public static boolean arcanaNoDeadEnds = true;
    /** Fishing: relics (Nightmare Drill from Vethea), worm chance of the worm shovel. */
    public static boolean fishingRelics = true, drillArea = true;
    public static double relicChanceMultiplier = 1.0, wormChance = 0.08, wormRainChance = 0.2;
    /** Vethea: other people's items in reverse; floors (rifts, guardian, wings, flight ban); its own items from other mods. */
    public static boolean vetheaNightmare = true, vetheaFloors = true;
    public static String[] vetheaWhitelist = {};
    /** Weapons of other mods that the Vethea nightmare does not recognize by itself (it computes the damage itself, without a vanilla base). */
    public static String[] vetheaWeapons = {};
    /** Natural spawn in the Divine worlds - only Divine, addon and whitelist mobs. */
    public static boolean divineSpawnFilter = true;
    /** Arcana fountain hall: on average one per this many chunks (0 - none). */
    public static int arcanaFountainChance = 40;
    /** Locked Arcana chests: chance of a chamber (1 in N), guarantee per area (the Nth chamber of a kind without a chest), area size. */
    public static boolean arcanaChests = true;
    public static int arcanaChestChance = 20, arcanaChestPity = 12, arcanaChestRegion = 2048;
    /** World reliquaries: chance in ruins and crypts (1 in N) and guarantee (the Nth structure without a reliquary in the area), the same for Skythern ships, area size, ship rarity. */
    public static boolean reliquaries = true;
    public static int reliquaryChance = 12, reliquaryPity = 8, reliquaryShipChance = 3, reliquaryShipPity = 3, reliquaryRegion = 2048, skythernShipRarity = 200;
    /** World fishing: Skythern cloud islands (1 per N chunks) and schools of fish. */
    public static int cloudIslandRarity = 70;
    /** Liopleurodon is a fish boss summoned with Bloody Bait (normal spawn disabled), health. */
    public static boolean liopleurodonBoss = true;
    public static int liopleurodonHealth = 600;
    public static boolean fishSchools = true;
    /** Numbers of arcane potion effects (empty - free ones on their own, from 127 down). */
    public static int[] arcanePotionIds = {};
    public static String[] divineSpawnWhitelist = {};
    /** Vethea mob weaknesses: "mob class=kind[,kind]" (kinds: sword claw hammer bow disk staff cannon). */
    public static String[] vetheaWeakness = { "EntityLheiva=bow", "EntityLorgaFlight=sword,claw", "EntityDissiment=disk", "EntityHoverStinger=disk",
        "EntityZoragon=bow", "EntityGalroid=hammer", "EntityVhraak=hammer", "EntityShadahier=staff", "EntityCymesoid=staff", "EntityKazrotic=cannon" };
    /** Audit log for admins (logs/dre-audit.log) and a record in HawkEye if it is on the server. */
    public static boolean auditLog = true, auditHawkEye = true;
    /** Overworld melee weapon kinds: everything as a whole and combat between players; the kind numbers are in {@link com.divinerpgextended.perk.MeleeRoles.Kind}. */
    public static boolean meleeRoles = true, meleeRolesPvp = false;

    /** Array from the config; if it is shorter in an old file than in the new one, it is padded with default values. */
    private static int[] intList(Configuration cfg, String cat, String key, int[] def, String comment) {
        Property p = cfg.get(cat, key, def, comment);
        int[] v = p.getIntList();
        if (v.length >= def.length) return v;
        int[] out = def.clone();
        System.arraycopy(v, 0, out, 0, v.length);
        p.set(out);
        return out;
    }

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        String bal = "balance";
        int[] newReduction = tierFullReduction.clone();
        tierFullReduction = intList(cfg, bal, "tierFullReduction", tierFullReduction,
            "Full set protection by tier R0, R1, ... R8, in percent");
        if (java.util.Arrays.equals(tierFullReduction, OLD_TIER_REDUCTION)) { // old default numbers - to the new ones
            tierFullReduction = newReduction;
            cfg.get(bal, "tierFullReduction", newReduction).set(newReduction);
        }
        tierSmallSlots = intList(cfg, bal, "tierSmallSlots", tierSmallSlots,
            "Number of small perk slots by tier R0, R1, ... R8");
        upgradeR8Spirits = cfg.getInt("upgradeR8Spirits", bal, upgradeR8Spirits, 0, 16,
            "Number of Spirits of the Universe that tempering Wanderer armor from R7 to R8 (Crown) costs");
        crownArcanaDamage = cfg.getFloat("crownArcanaDamage", bal, crownArcanaDamage, 1F, 10F,
            "Arcana R8 Crown: arcane damage multiplier - bonuses of Arcana swords and staff projectiles (the regular part of a sword hit does not grow)");
        upgradeR1FuelRealmiteIngots = cfg.getInt("upgradeR1FuelRealmiteIngots", bal, upgradeR1FuelRealmiteIngots,
            0, 64, "Number of realmite ingots that converting Divine armor into Wanderer armor R1 costs");
        coreForgeStones = intList(cfg, bal, "coreForgeStones", coreForgeStones,
            "Number of divine stones that forging Wanderer armor R1 and switching the core to this set costs: Shadow, Bedrock, Ender, Damaged. The sets themselves cost very differently, this payment evens it out and keeps the bosses on the way to Eden");
        bedrockStanceTicks = intList(cfg, bal, "bedrockStanceTicks", bedrockStanceTicks,
            "Bedrock indestructibility (full set and core): after how many ticks Resistance I and Resistance II apply, no knockback from the first tier. 20 ticks = 1 second");
        blitzDustPerShot = cfg.getInt("blitzDustPerShot", bal, blitzDustPerShot, 1, 64,
            "How much of its own world's dust one blitz shot uses (it fires 5 pellets). With less than this in the charge and inventory, the blitz does not fire");
        accessoriesOnBody = cfg.getBoolean("accessoriesOnBody", "client", accessoriesOnBody,
            "Accessories built into worn armor are visible on the player's body (rings, pendants, belts, wings, etc.), on yourself and on other players");
        ammoCountOnIcon = cfg.getBoolean("ammoCountOnIcon", "client", ammoCountOnIcon,
            "Ammo count (charge + inventory) on the icon of the bow, blitz, phaser and other ranged weapons in your own inventory; infinite ammo shows the infinity sign, less than one shot's worth is shown in red");
        jungleDebuffSpeed = cfg.get(bal, "jungleDebuffSpeed", jungleDebuffSpeed,
            "Antidote: the full Divine jungle set and the \"Poison Resistance\" perk on Wanderer armor - poison has no effect, and other bad effects (including foreign ones that milk does not remove, e.g. Thaumcraft warp) are not removed but pass this many times faster. 1 - as usual").getDouble();
        bedrockArmorUnbreakable = cfg.getBoolean("bedrockArmorUnbreakable", bal, bedrockArmorUnbreakable,
            "Divine bedrock armor does not wear out (like bedrock itself). Requires a restart");
        skelemanArmor = cfg.getInt("skelemanArmor", bal, skelemanArmor, 0, 90,
            "Full skeleton set protection (Jack sells it from the start), in percent, like Divine armor. 0 - as in Divine (no protection). Requires a restart");
        jackOManArmor = cfg.getInt("jackOManArmor", bal, jackOManArmor, 0, 90,
            "Full Jack O Man set protection (after Carcinus), in percent. 0 - as in Divine (no protection). Requires a restart");
        witherReaperArmor = cfg.getInt("witherReaperArmor", bal, witherReaperArmor, 0, 90,
            "Full Withered Reaper set protection (after the Lich King), in percent. 0 - as in Divine (no protection). Requires a restart");
        noTinkersIslandsInDivine = cfg.getBoolean("noTinkersIslandsInDivine", bal, noTinkersIslandsInDivine,
            "Tinkers' Construct slime islands do not spawn in Divine worlds (in new chunks; existing ones stay). Requires a restart");
        iceFreeze = cfg.getBoolean("iceFreeze", bal, iceFreeze,
            "Frostbite from ice weapons (Iceika swords and maul, Frossivence, Frost Sword, ice bows, cannons, snow shuriken): a hit slows the mob, the second hit slows it more, the third freezes the mob in ice for 2.5 s (bosses are only slowed)");
        netherSmeltChance = cfg.get(bal, "netherSmeltChance", netherSmeltChance, "Full Inferno or Netherite set: chance that mined ore drops already smelted (0 - off, 1 - always). Does not work with Silk Touch").getDouble();
        skythernGust = cfg.get(bal, "skythernGust", skythernGust, "Skythern: how far a mob hit pushes the player sideways (1.6 - like a gust from the Wind Lord, 0 - no sideways push). One mob pushes at most once every 4 seconds").getDouble();
        mortumSoulChance = cfg.get(bal, "mortumSoulChance", mortumSoulChance, "Mortum: chance that a mob killed by a player leaves a soul for 3 seconds; if not dispersed, the mob rises again with no loot (0 - off)").getDouble();
        edenSun = cfg.getBoolean("edenSun", bal, edenSun, "Eden: the sun overheats players under the open sky (weakness, nausea, burning)");
        wildwoodRain = cfg.getBoolean("wildwoodRain", bal, wildwoodRain, "Wildwood: downpour every 15-25 minutes (slowness, mobs heal, marine weapons are stronger)");
        apalachiaRockfall = cfg.getBoolean("apalachiaRockfall", bal, apalachiaRockfall, "Apalachia: rockfall every 10-15 minutes (boulders fall from the sky, blocks are not broken)");
        miniBosses = cfg.getBoolean("miniBosses", bal, miniBosses, "Mini-bosses in the twilight dimensions and on Vethea floors 1-3");
        miniBossMinutes = cfg.getInt("miniBossMinutes", bal, miniBossMinutes, 1, 600, "A mini-boss spawns for a given player at most once per this many minutes");
        coreShadowSpeed = cfg.get(bal, "coreShadowSpeed", coreShadowSpeed, "Shadow core in Wanderer armor: how many times faster walking is (3 - like the Shadow set itself, 1 - no bonus)").getDouble();
        upgradeTwilightFuelCount = cfg.getInt("upgradeTwilightFuelCount", bal, upgradeTwilightFuelCount, 0, 64,
            "Number of world stones that tempering R2-R7 costs");
        perkFuelArlemiteIngots = cfg.getInt("perkFuelArlemiteIngots", bal, perkFuelArlemiteIngots,
            0, 64, "Number of arlemite ingots that installing a small perk costs");
        corruptAltar = cfg.getBoolean("corruptAltar", bal, corruptAltar,
            "Divine corrupt altar by the addon rules: enchantments are rolled when you place an item, and revealed for a payment depending on the item kind, the price depends on the rolled enchantments, a book gets enchantments for a single item. false - altar as in Divine");
        int itemTop = altarTopLevels, bookMax = altarBookMax;
        int[] itemPoints = altarItemPoints, bookPoints = altarBookPoints;
        altarTopLevels = cfg.getInt("altarTopLevels", bal, altarTopLevels, 1, 1000,
            "Corrupt altar: cap on the enchanting price of an item, in levels");
        altarBookBase = cfg.getInt("altarBookBase", bal, altarBookBase, 0, 1000,
            "Corrupt altar: the price in levels at which a book starts (enchantment points are added to it)");
        altarBookMax = cfg.getInt("altarBookMax", bal, altarBookMax, 1, 1000,
            "Corrupt altar: cap on the book price, in levels");
        altarItemPoints = intList(cfg, bal, "altarItemPoints", altarItemPoints,
            "Corrupt altar, item: how many levels an enchantment at full level costs - best (Protection, Sharpness, Power, Efficiency, Fortune, Looting, Silk Touch, Infinity), "
                + "medium (Unbreaking, Feather Falling and others, enchantments from other mods), weak (Smite, Bane of Arthropods, Knockback, Punch, fire, blast and projectile protection). An incomplete level costs proportionally");
        altarBookPoints = intList(cfg, bal, "altarBookPoints", altarBookPoints,
            "Corrupt altar, book: same as altarItemPoints, on top of altarBookBase");
        if (altarTopLevels == OLD_TOP_LEVELS) {
            altarTopLevels = itemTop;
            cfg.get(bal, "altarTopLevels", itemTop).set(itemTop);
        }
        if (altarBookMax == OLD_BOOK_MAX) {
            altarBookMax = bookMax;
            cfg.get(bal, "altarBookMax", bookMax).set(bookMax);
        }
        if (java.util.Arrays.equals(altarItemPoints, OLD_ITEM_POINTS)) {
            altarItemPoints = itemPoints;
            cfg.get(bal, "altarItemPoints", itemPoints).set(itemPoints);
        }
        if (java.util.Arrays.equals(altarBookPoints, OLD_BOOK_POINTS)) {
            altarBookPoints = bookPoints;
            cfg.get(bal, "altarBookPoints", bookPoints).set(bookPoints);
        }
        altarRevealCost = intList(cfg, bal, "altarRevealCost", altarRevealCost,
            "Corrupt altar, item: cost to reveal the enchantments of the first, second and last slot (shards, shards, stone; 0 - free)");
        int[] bookCost = altarBookRevealCost;
        altarBookRevealCost = intList(cfg, bal, "altarBookRevealCost", altarBookRevealCost,
            "Corrupt altar, book: cost to reveal the enchantments of the first, second and last slot (0 - free)");
        if (java.util.Arrays.equals(altarBookRevealCost, OLD_BOOK_REVEAL_COST)) {
            altarBookRevealCost = bookCost;
            cfg.get(bal, "altarBookRevealCost", bookCost).set(bookCost);
        }
        Property reveal = cfg.get(bal, "altarRevealItems", altarRevealItems,
            "Corrupt altar: what to pay for \"reveal\" (kind=for the first and second slots,for the last; modid:name). Kinds: weapon - swords, tool - pickaxes, axes, shovels, fishing rods, "
                + "armor - armor, bow - bows, book - books, other - everything else");
        altarRevealItems = reveal.getStringList();
        for (int i = 0; i < altarRevealItems.length; i++) {
            if (!OLD_BOOK_REVEAL.equals(altarRevealItems[i].trim())) continue;
            altarRevealItems[i] = BOOK_REVEAL;
            reveal.set(altarRevealItems);
        }
        cadillionHealth = intList(cfg, "bosses", "cadillionHealth", cadillionHealth,
            "Great Cadillion health: Eden, Wildwood, Apalachia, Skythern");
        Property hit = cfg.get("bosses", "cadillionHit", cadillionHit,
            "How much health (in half-hearts, a player has 20) a Great Cadillion hit removes if the player wears armor "
                + "of the required tier: Eden, Wildwood, Apalachia, Skythern");
        double[] h = hit.getDoubleList();
        if (h.length >= cadillionHit.length) cadillionHit = h;
        else hit.set(cadillionHit);
        cadillionRequiredTier = intList(cfg, "bosses", "cadillionRequiredTier", cadillionRequiredTier,
            "Required armor tier against the Great Cadillion: Eden, Wildwood, Apalachia, Skythern");
        twilightDemonRequiredTier = cfg.getInt("twilightDemonRequiredTier", "bosses", twilightDemonRequiredTier, 0, 9,
            "Required armor tier against the Twilight Demon");
        karotRequiredTier = cfg.getInt("karotRequiredTier", "bosses", karotRequiredTier, 0, 9,
            "Unused since 0.61.0: Karot is a mirror, its strength depends on the player's weapon and armor, not on the tier");
        underTierMultiplier = cfg.get("bosses", "underTierMultiplier", underTierMultiplier,
            "How many times boss damage grows for each armor tier below the required one (1 - off)").getDouble();
        String b = "bosses";
        carcinusHealth = cfg.get(b, "carcinusHealth", carcinusHealth, "Carcinus health").getDouble();
        carcinusDamage = cfg.get(b, "carcinusDamage", carcinusDamage, "Carcinus damage per hit").getDouble();
        carcinusNonCrabDamageFactor = cfg.get(b, "carcinusNonCrabDamageFactor", carcinusNonCrabDamageFactor,
            "What share of damage Carcinus takes from non-crab weapons").getDouble();
        carcinusWaterArmorFactor = cfg.get(b, "carcinusWaterArmorFactor", carcinusWaterArmorFactor,
            "What share of damage Carcinus deals to a player in water armor (Kraken armor set, Aquastrive armor, or Wanderer with a water perk)").getDouble();
        carcinusVortexDamage = cfg.get(b, "carcinusVortexDamage", carcinusVortexDamage,
            "Carcinus sand whirlwind damage (through any armor, in half-hearts; you can run away from the whirlwind)").getDouble();
        carcinusSummonClaws = cfg.getInt("carcinusSummonClaws", b, carcinusSummonClaws, 0, 64,
            "How many crab claws are needed to summon Carcinus at the altar (burrow crabs in the maze drop claws)");
        snowGlaconHealth = cfg.get(b, "snowGlaconHealth", snowGlaconHealth, "Snow Glacon health").getDouble();
        snowGlaconDamage = cfg.get(b, "snowGlaconDamage", snowGlaconDamage, "Snow Glacon damage per hit").getDouble();
        colossusHealth = cfg.get(b, "colossusHealth", colossusHealth, "Frost Colossus health").getDouble();
        colossusDamage = cfg.get(b, "colossusDamage", colossusDamage, "Frost Colossus hit damage").getDouble();
        lichHealth = cfg.get(b, "lichHealth", lichHealth, "Lich King health").getDouble();
        lichDamage = cfg.get(b, "lichDamage", lichDamage, "Lich King melee damage").getDouble();
        throneSummonSnowflakes = cfg.getInt("throneSummonSnowflakes", b, throneSummonSnowflakes, 0, 64,
            "How many snowflakes are needed to summon the Frost Colossus at the Frozen Throne altar");
        nestSpacing = cfg.getInt("nestSpacing", "worldgen", nestSpacing, 0, 100000,
            "Twilight dimension boss lairs (Great Cadillion nests, Mortum Demonic Circle): one per N x N block square in each world, above the islands at height 140 (0 - do not generate). Do not set below 256");
        twilightRuinsRarity = cfg.getInt("twilightRuinsRarity", "worldgen", twilightRuinsRarity, 0, 100000,
            "Ruins with a reward on twilight dimension islands: on average 1 per N chunks (0 - do not generate)");
        apalachiaStructureBoost = cfg.getInt("apalachiaStructureBoost", "worldgen", apalachiaStructureBoost, 1, 10,
            "How many times more frequent ruins, camps and dungeons on islands are in Apalachia (islands there are rarer and thinner)");
        twilightPondRarity = cfg.getInt("twilightPondRarity", "worldgen", twilightPondRarity, 0, 100000,
            "Fishing ponds on twilight dimension islands (pier, willow, lily pads): on average 1 per N chunks (0 - do not generate)");
        vetheaLakeRarity = cfg.getInt("vetheaLakeRarity", "worldgen", vetheaLakeRarity, 0, 100000,
            "Lakes on Vethea floors (dream fishing): on average 1 per N chunks on each floor (0 - do not generate)");
        vetheaHangingLakeRarity = cfg.getInt("vetheaHangingLakeRarity", "worldgen", vetheaHangingLakeRarity, 0, 100000,
            "Lakes in the ceiling of Vethea floors (dream water flows upward, the surface is level with the ceiling, you hang upside down at it and fish): on average 1 per N chunks per floor (0 - do not generate)");
        twilightDungeonRarity = cfg.getInt("twilightDungeonRarity", "worldgen", twilightDungeonRarity, 0, 100000,
            "Dungeons inside large twilight dimension islands (spawner, chest): on average 1 per N chunks (0 - do not generate)");
        twilightBridgeRarity = cfg.getInt("twilightBridgeRarity", "worldgen", twilightBridgeRarity, 0, 100000,
            "Bridges between nearby twilight dimension islands (span of 4-14 blocks): try in 1 of N chunks, placed only if there is a chasm of suitable width nearby (0 - do not generate)");
        twilightCampRarity = cfg.getInt("twilightCampRarity", "worldgen", twilightCampRarity, 0, 100000,
            "Wanderer camps in twilight dimensions (trader, campfire - respawn point): on average 1 per N chunks (0 - do not generate)");
        netherLairSpacing = cfg.getInt("netherLairSpacing", "worldgen", netherLairSpacing, 0, 100000,
            "Nether lairs (Watcher's Observatory, King of Scorchers' Fire Chamber): one of each per N x N block square of the Nether; 0 - do not generate them or the Ayeraco aerie in the End (then their summon items work anywhere)");
        mapWorldSize = cfg.getInt("mapWorldSize", b, mapWorldSize, 0, 1000000,
            "Lair maps show lairs only within a square of this size centered on the world origin: 10000 - from -5000 to +5000 on X and Z; 0 - no limit");
        divineTorchRadius = cfg.getInt("divineTorchRadius", b, divineTorchRadius, 0, 128,
            "Divine torches (water, skeleton, Eden, arcanium) and twilight dimension torches in the overworld: within this radius Divine mobs do not spawn on their own; 0 - off");
        dungeonSpawners = cfg.getBoolean("dungeonSpawners", b, dungeonSpawners,
            "Mob spawners in addon structures (Ancient Tomb, ice cave, lair under the jungle temple, twilight dimension dungeons, Mortum crypts, Twilight Demon tower). false - no spawners: mobs come on their own, as everywhere");
        bossNoFly = cfg.getBoolean("bossNoFly", b, bossNoFly,
            "Near a living boss (48 blocks) flying is disabled - except for Skythern and creative mode. false - flying is allowed");
        bossArenaLeaveSeconds = cfg.getInt("bossArenaLeaveSeconds", b, bossArenaLeaveSeconds, -1, 3600,
            "Boss arena: if no fighters are left on it (left, teleported, died, logged out), the boss waits this many seconds and disappears without trophies. If even one returns, the wait starts over. 0 - disappears immediately; -1 - never disappears");
        easterEggs = cfg.getBoolean("easterEggs", b, easterEggs,
            "Easter eggs: a few renamed items get their own look, and a special couch appears in new chunks. false - disabled: items are ordinary, the couch is not placed. The server's setting applies to all players");
        halfLifePath = cfg.getString("halfLifePath", b, halfLifePath,
            "Easter egg sounds: the mod reads them only from the Steam library folders listed here (the folder that contains steamapps; several - separated by ;), e.g. D:/SteamLibrary. Nothing is searched on its own. Empty - no such sounds");
        bossArenaHold = cfg.getBoolean("bossArenaHold", b, bossArenaHold,
            "While the boss is alive, a fighter at the arena edge is pushed back to the center (the only way out is teleport). false - leaving is allowed, only the bossArenaLeaveSeconds rule remains");
        bossArenaRadius = cfg.getInt("bossArenaRadius", b, bossArenaRadius, 8, 128,
            "Radius of a boss arena for a boss summoned outside an addon lair (Arcana, Vethea, summoned anywhere); for lairs the arena is their hall, for the Frozen Throne - the dome up to the ring of pylons (80)");
        bossSummonClearRadius = cfg.getInt("bossSummonClearRadius", b, bossSummonClearRadius, 0, 32,
            "Boss summon outside a lair (summonOnlyInLairs=false, lairs disabled): within this radius around the summon point, 4 blocks up must be empty (up to 10% of columns may be occupied). 0 - no check. In a lair only the boss spawn spot is checked to not be built over");
        summonOnlyInLairs = cfg.getBoolean("summonOnlyInLairs", b, summonOnlyInLairs,
            "Bosses can be summoned only in their lairs: Cadillion call - at the nest altar, Mortum crystals - in the Demonic Circle, Mysterious Clock - at the tomb altar. false - anywhere, as in Divine");
        worldCrafts = cfg.getBoolean("worldCrafts", b, worldCrafts,
            "Twilight dimension crafting as a chain: world item = previous world item + pieces + mob material, handles from world sticks; world sticks, torches and arrows. false - Divine recipes");
        miningTiers = cfg.getBoolean("miningTiers", b, miningTiers,
            "Pickaxe levels: twilight dimension ore can be mined only with a pickaxe of that world or higher, twilight stone - with a pickaxe of the previous world or higher (in Eden - divine)");
        smeltNoFortune = cfg.getStringList("smeltNoFortune", b, smeltNoFortune,
            "Ores (modid:name) for which auto-smelting while digging (Tinkers' Construct \"Lava\" modifier) does not multiply ingots with Fortune: one smelt per block. Empty list - Fortune works everywhere");
        gateStones = cfg.getBoolean("gateStones", b, gateStones,
            "Wildwood..Mortum portal frames - gate stones from the guardian heart (Great Cadillion trophy) instead of previous world stone blocks. Old frames made of stone blocks will stop working");
        worldGates = cfg.getBoolean("worldGates", b, worldGates,
            "Personal passes: twilight portals and the Nightmare Bed let in only players who reached the world themselves (Eden - after the Watcher, the next twilight world - after the guardian of the previous one, Vethea - after the Spirit of the Universe bosses), even if someone else lit the portal. false - anyone walks through a lit portal. Arcana is always gated");
        throneSpacing = cfg.getInt("throneSpacing", "worldgen", throneSpacing, 0, 100000,
            "Frozen Throne in Iceika: one per N x N block square, the place is chosen from the world seed (0 - do not generate). Do not set below 1024: the arena is 300 blocks");
        carcinusSpacing = cfg.getInt("carcinusSpacing", "worldgen", carcinusSpacing, 0, 100000,
            "Carcinus lair: one per N x N block square, if it has a sandy beach by the sea; the place is chosen from the world seed, the map knows it in advance (0 - do not generate)");
        snowDungeonSpacing = cfg.getInt("snowDungeonSpacing", "worldgen", snowDungeonSpacing, 0, 100000,
            "Snow Glacon ice cave: one per N x N block square, if it has flat snowy land; the place is chosen from the world seed (0 - do not generate)");
        lairGap = cfg.getInt("lairGap", "worldgen", lairGap, 0, 10000,
            "Overworld lairs of the same kind (Carcinus, Glacon ice cave, tomb under the pyramid, city under the jungle temple) - no closer than N blocks to each other; of two close ones only one remains (640 = 40 chunks)");
        mapSearchRadius = cfg.getInt("mapSearchRadius", "worldgen", mapSearchRadius, 500, 30000,
            "Overworld lair maps: search radius in blocks (5000 - a 10000 x 10000 square)");
        mapSkipNear = cfg.getInt("mapSkipNear", "worldgen", mapSkipNear, 0, 1000,
            "Lair maps: a place closer than N blocks counts as found - the map leads to the next one");
        mapCooldownSeconds = cfg.getInt("mapCooldownSeconds", "worldgen", mapCooldownSeconds, 0, 600,
            "Lair maps: cooldown in seconds (searching the biome map loads the server)");
        scatterInModBiomes = cfg.getBoolean("scatterInModBiomes", "worldgen", scatterInModBiomes,
            "Overworld jungle pyramids and temples - also in biomes of other mods (Biomes O' Plenty): a pyramid in any hot sand, a temple in any jungle, and if there is no jungle near spawn - in warm green lands. Under them are the tomb and the Ancient Dramcryx lair");
        snowGlobeOnlyFromBoss = cfg.getBoolean("snowGlobeOnlyFromBoss", Configuration.CATEGORY_GENERAL,
            snowGlobeOnlyFromBoss, "Remove the Divine Snowball recipe: the ball drops only from the Snow Glacon");
        snowflakesInSummons = cfg.getBoolean("snowflakesInSummons", Configuration.CATEGORY_GENERAL,
            snowflakesInSummons, "Add Iceika snowflakes to the Mysterious Clock and Watcher's Call recipes");
        requireBossUnlocks = cfg.getBoolean("requireBossUnlocks", Configuration.CATEGORY_GENERAL, requireBossUnlocks,
            "Require killing the linked boss for upgrades and perks");
        replaceTwilightClock = cfg.getBoolean("replaceTwilightClock", Configuration.CATEGORY_GENERAL,
            replaceTwilightClock, "Remove the Divine twilight clock recipe: portals are opened by Wanderer's Clocks by level");
        arcanaNoDeadEnds = cfg.getBoolean("arcanaNoDeadEnds", "worldgen", arcanaNoDeadEnds,
            "Arcana without dead ends: where room openings do not match, a 2x2 passage is cut (new chunks only)");
        progressSpawns = cfg.getBoolean("progressSpawns", Configuration.CATEGORY_GENERAL, progressSpawns,
            "Divine mobs in the overworld, Nether and End unlock by rank: 0 - crabs, 1 (Carcinus) - snow, ocean, Wildfire and Hell Spiders, 2 (Lich King) - all others except the End, 3 (Ancient Entity) - End mobs");
        foreignMobsByStage = cfg.getBoolean("foreignMobsByStage", Configuration.CATEGORY_GENERAL, foreignMobsByStage,
            "Mobs of Iceika, twilight dimensions, Arcana and Vethea that end up in the overworld (spawner, egg) do not touch a player who has not reached their world, and drop nothing if that player kills them. Iceika - after Carcinus, Eden and Arcana - after the Watcher, the next twilight dimension - after the previous guardian, Vethea - after the Spirit of the Universe bosses");
        lowRankNoDrops = cfg.getBoolean("lowRankNoDrops", Configuration.CATEGORY_GENERAL, lowRankNoDrops,
            "A Divine mob killed by a player below the required rank drops no loot or experience (it can be killed, but not farmed)");
        warnVetheaInventory = cfg.getBoolean("warnVetheaInventory", Configuration.CATEGORY_GENERAL,
            warnVetheaInventory, "Warn in the log if the separate Vethea inventory is enabled in Divine");
        String fish = "fishing";
        fishingRelics = cfg.getBoolean("fishingRelics", fish, fishingRelics,
            "Fishing relics: a rare catch like the Nightmare Drill in Vethea. false - not caught at all");
        relicChanceMultiplier = cfg.get(fish, "relicChanceMultiplier", relicChanceMultiplier,
            "Relic chance multiplier (1.0 - about once per 250 bites with the best gear)").getDouble();
        drillArea = cfg.getBoolean("drillArea", fish, drillArea,
            "The Nightmare Drill digs 3x3 (sneaking - one block). false - always one block");
        wormChance = cfg.get(fish, "wormChance", wormChance, "Chance of a worm from dirt/grass when digging with the worm digger").getDouble();
        wormRainChance = cfg.get(fish, "wormRainChance", wormRainChance, "Same in rain").getDouble();
        String v = "vethea";
        vetheaNightmare = cfg.getBoolean("vetheaNightmare", v, vetheaNightmare,
            "Nightmare reversed: in Vethea foreign weapons heal, foreign armor boosts damage, foreign food reduces hunger, foreign potions "
                + "give the opposite effect, foreign tools dig slowly, non-Vethean accessories stay silent");
        vetheaFloors = cfg.getBoolean("vetheaFloors", v, vetheaFloors,
            "Vethea floors: going up only through rifts on Dream Wings, the underside of slabs cannot be broken, above the allowed floor "
                + "you are thrown back, normal flying is disabled (except in creative mode)");
        vetheaWhitelist = cfg.getStringList("vetheaWhitelist", v, vetheaWhitelist,
            "Items from other mods that count as native in Vethea (modid:name, one per line)");
        vetheaWeapons = cfg.getStringList("vetheaWeapons", v, vetheaWeapons,
            "Weapons from other mods that are not recognized automatically in Vethea (modid:name): foreign weapons heal mobs. Swords, tools, bows, Tinkers' Construct, the Tainted Magic katana and disassembler, the Blood Magic multi-tool are recognized without configuration");
        arcanaFountainChance = cfg.getInt("arcanaFountainChance", "arcana", arcanaFountainChance, 0, 10000,
            "Arcana fountain hall (a bowl of arcane concentrate: restores arcana, Arcana fish only there and only with the Arcana fishing rod): "
                + "on average one per this many new chunks; 0 - do not build");
        arcanaChests = cfg.getBoolean("arcanaChests", "arcana", arcanaChests,
            "Locked Arcana chests (for keys of slime, ancient brick, soul stone and broken brick): placed in new chunks in chambers behind doors "
                + "instead of arcanite ore, at broken doors - in a corner of the central hall");
        arcanaChestChance = cfg.getInt("arcanaChestChance", "arcana", arcanaChestChance, 1, 10000,
            "Chance of a chest in a suitable chamber: one in this many");
        arcanaChestPity = cfg.getInt("arcanaChestPity", "arcana", arcanaChestPity, 1, 10000,
            "Guarantee: if the area has no chest of this kind yet, every Nth suitable chamber gets one without a roll");
        arcanaChestRegion = cfg.getInt("arcanaChestRegion", "arcana", arcanaChestRegion, 256, 100000,
            "Guarantee area size, in blocks (2048 - any 10000 x 10000 square has at least nine whole areas)");
        reliquaries = cfg.getBoolean("reliquaries", "worldgen", reliquaries,
            "Twilight dimension reliquaries (for the world key: Arcana key + 4 world stones): in the ruins of Eden, Wildwood and Apalachia, in Mortum crypts, on Skythern thorn ships");
        reliquaryChance = cfg.getInt("reliquaryChance", "worldgen", reliquaryChance, 1, 10000, "Chance of a reliquary in ruins and crypts: one in this many");
        reliquaryPity = cfg.getInt("reliquaryPity", "worldgen", reliquaryPity, 1, 10000,
            "Guarantee: if the area has no reliquary yet, every Nth suitable place (ruins, crypt) gets one without a roll");
        reliquaryShipChance = cfg.getInt("reliquaryShipChance", "worldgen", reliquaryShipChance, 1, 10000, "Chance of a reliquary on a thorn ship: one in this many");
        reliquaryShipPity = cfg.getInt("reliquaryShipPity", "worldgen", reliquaryShipPity, 1, 10000, "Guarantee on ships: every Nth ship in an area without a reliquary gets one");
        reliquaryRegion = cfg.getInt("reliquaryRegion", "worldgen", reliquaryRegion, 256, 100000, "Reliquary guarantee area size, in blocks");
        liopleurodonBoss = cfg.getBoolean("liopleurodonBoss", "bosses", liopleurodonBoss,
            "Liopleurodon - a fish boss for Bloody Bait in the ocean (between Apalachia and Skythern); regular Liopleurodons do not spawn");
        liopleurodonHealth = cfg.getInt("liopleurodonHealth", "bosses", liopleurodonHealth, 50, 100000, "Liopleurodon health");
        cloudIslandRarity = cfg.getInt("cloudIslandRarity", "worldgen", cloudIslandRarity, 0, 100000,
            "Skythern cloud islands with a pond (cloud fishing): on average one attempt per this many chunks; 0 - do not build");
        fishSchools = cfg.getBoolean("fishSchools", "fishing", fishSchools,
            "Fish schools: sometimes a bubbling spot appears on the water near the player for a minute - bites come three times faster and rare ones more often");
        skythernShipRarity = cfg.getInt("skythernShipRarity", "worldgen", skythernShipRarity, 0, 100000,
            "Thorn ships in the Skythern sky: on average one attempt per this many chunks (a ship is placed if the sky around is empty); 0 - do not build");
        arcanePotionIds = cfg.get("arcana", "arcanePotionIds", arcanePotionIds,
            "Arcane potion effect IDs (1-127, in order: clarity, sorcery, composure, charm ward, stone skin, deflection, fortitude, "
                + "bramble hide, inner heat, battle fervor, steady hand, arcane hunger, angler's luck). Empty - free IDs are taken from 127 downward; "
                + "an occupied ID is also replaced with a free one. Set only if the effects conflict with another mod").getIntList();
        divineSpawnFilter = cfg.getBoolean("divineSpawnFilter", v, divineSpawnFilter,
            "Divine worlds (Iceika, twilight, Arcana, Vethea): only Divine and addon mobs (and those from divineSpawnWhitelist) spawn on their own; "
                + "mobs from other mods (Tinkers slimes, Grimoire of Gaia...) do not. Spawners and summoning are unaffected");
        divineSpawnWhitelist = cfg.getStringList("divineSpawnWhitelist", v, divineSpawnWhitelist,
            "Who else may spawn in Divine worlds: mob class, entity name (as in /summon) or name prefix (modid - for all mobs of a mod), one per line");
        vetheaWeakness = cfg.getStringList("vetheaWeakness", v, vetheaWeakness,
            "Vethea mob weaknesses (Divine mob class=kind[,kind]): its own kind deals x2 damage and double loot on a finishing blow, others x0.25. "
                + "Kinds: sword claw hammer bow disk staff cannon. Empty list - no weaknesses");
        auditLog = cfg.getBoolean("auditLog", "audit", auditLog,
            "Admin action log: logs/dre-audit.log (accessories, pet carrying, /dreinv, trader deals and services, Vethea gifts and item returns)");
        auditHawkEye = cfg.getBoolean("auditHawkEye", "audit", auditHawkEye,
            "The same actions go to HawkEye, if it is installed on the server (type OTHER, actions DRE ...)");
        meleeRoles = cfg.getBoolean("enabled", "melee", meleeRoles,
            "Overworld and Nether melee weapon subtypes (knife, rapier, sword, sledgehammer): each has its own target invulnerability window after a hit and its own damage. false - all weapons as in vanilla Minecraft, both against mobs and between players");
        meleeRolesPvp = cfg.getBoolean("pvp", "melee", meleeRolesPvp,
            "The same rules in player-versus-player combat (own numbers: windowTicksPvp, damagePvp). false - hits on players as in vanilla Minecraft. Claims that forbid PvP are not bypassed. TiC handles its own rapiers and cleavers itself, this switch does not affect them. The grounded crit of the full Terran set also applies to players only with this setting");
        for (com.divinerpgextended.perk.MeleeRoles.Kind k : com.divinerpgextended.perk.MeleeRoles.Kind.values()) {
            String c = "melee." + k.key();
            k.window = cfg.getInt("windowTicks", c, k.window, 0, 200,
                "How many ticks the target is invulnerable after a hit, against mobs (20 ticks = 1 second; 10 in vanilla Minecraft). A hit stronger than the previous one goes through even inside the window - by the difference");
            k.damage = cfg.getFloat("damage", c, k.damage, 0F, 100F, "Damage multiplier against mobs");
            k.windowPvp = cfg.getInt("windowTicksPvp", c, k.windowPvp, 0, 200, "The same window in player-versus-player combat (if melee.pvp is enabled)");
            k.damagePvp = cfg.getFloat("damagePvp", c, k.damagePvp, 0F, 100F, "Damage multiplier in player-versus-player combat (if melee.pvp is enabled)");
            if (k == com.divinerpgextended.perk.MeleeRoles.Kind.MAUL) { // before 0.99.122 the defaults were x2 and x1.5: old values - to the new ones
                if (Math.abs(k.damage - 2F) < 1e-4F) {
                    k.damage = 1.2F;
                    cfg.getCategory(c).get("damage").set("1.2");
                }
                if (Math.abs(k.damagePvp - 1.5F) < 1e-4F) {
                    k.damagePvp = 1.2F;
                    cfg.getCategory(c).get("damagePvp").set("1.2");
                }
            }
        }
        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    public static int fullReduction(int tier) {
        if (tier < 0) return 0;
        return tierFullReduction[Math.min(tier, tierFullReduction.length - 1)];
    }

    public static int smallSlots(int tier) {
        if (tier < 0) return 0;
        return tierSmallSlots[Math.min(tier, tierSmallSlots.length - 1)];
    }
}
