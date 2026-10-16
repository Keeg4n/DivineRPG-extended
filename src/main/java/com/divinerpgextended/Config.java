package com.divinerpgextended;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Все числа баланса, чтобы менять без пересборки. */
public class Config {

    /** Защита полного сета по ступеням R0-R7, в процентах (индекс = ступень). */
    public static int[] tierFullReduction = { 50, 65, 72, 74, 76, 78, 80, 83 };
    /** Малых слотов под перки по ступеням R0-R7. */
    public static int[] tierSmallSlots = { 0, 2, 2, 3, 3, 3, 4, 4 };

    public static int upgradeR1FuelRealmiteIngots = 16;
    public static int upgradeTwilightFuelCount = 16;
    public static int perkFuelArlemiteIngots = 8;

    /** Здоровье великого кадиллиона: Eden, Wildwood, Apalachia, Skythern. */
    public static int[] cadillionHealth = { 300, 400, 500, 600 };
    /**
     * Сколько здоровья (в полусердцах) снимает удар великого кадиллиона игроку в броне требуемой ступени. Сырой
     * урон считается из этого и защиты ступени.
     */
    public static double[] cadillionHit = { 3.0, 3.5, 4.0, 4.5 };
    /** Требуемая ступень брони против великого кадиллиона: Eden, Wildwood, Apalachia, Skythern. */
    public static int[] cadillionRequiredTier = { 1, 2, 3, 4 };
    public static int twilightDemonRequiredTier = 5;
    public static int karotRequiredTier = 6;
    /** Во сколько раз растёт урон босса за каждую ступень брони ниже требуемой. */
    public static double underTierMultiplier = 3.0;

    public static double carcinusHealth = 300, carcinusDamage = 8, carcinusNonCrabDamageFactor = 0.25,
        carcinusWaterArmorFactor = 0.1, carcinusVortexDamage = 5;
    public static int carcinusSummonClaws = 4;
    public static double snowGlaconHealth = 400, snowGlaconDamage = 9;
    public static double colossusHealth = 1500, colossusDamage = 16, lichHealth = 600, lichDamage = 12;
    public static int throneSpacing = 2048, throneSummonSnowflakes = 12;
    /** Логова боссов сумеречных миров (гнёзда кадиллионов, Демонический круг): одно на квадрат N x N блоков. */
    public static int nestSpacing = 640;
    /** Логова Смотрителя и Короля скорчеров в Аду: одно каждого на квадрат N x N (0 - нет, вместе с гнездовьем Айрако). */
    public static int netherLairSpacing = 384;
    /** Боссов вызывают только в их логовах (алтари, круг); выключить - вызов где угодно, как в Divine. */
    public static boolean summonOnlyInLairs = true;
    /** Постройки сумеречных миров: 1 на N чанков (0 - нет). */
    public static int twilightRuinsRarity = 30, twilightDungeonRarity = 40, twilightBridgeRarity = 3, twilightCampRarity = 45;
    /** Логово Карцинуса: 1 на N океанских чанков; снежное подземелье: 1 на N заснеженных чанков (0 - выключить). */
    public static int carcinusShoreRarity = 40, snowDungeonRarity = 200;

    public static boolean requireBossUnlocks = true;
    public static boolean replaceTwilightClock = true;
    public static boolean snowGlobeOnlyFromBoss = true;
    public static boolean snowflakesInSummons = true;
    public static boolean warnVetheaInventory = true;
    public static boolean progressSpawns = true, lowRankNoDrops = true;
    public static boolean arcanaNoDeadEnds = true;
    /** Рыбалка: реликвии (кошмарный бур из Ветеи), шанс червей у червекопалки. */
    public static boolean fishingRelics = true, drillArea = true;
    public static double relicChanceMultiplier = 1.0, wormChance = 0.08, wormRainChance = 0.2;
    /** Ветея: чужие вещи наоборот; этажи (разломы, страж, крылья, запрет полёта); свои вещи из других модов. */
    public static boolean vetheaNightmare = true, vetheaFloors = true;
    public static String[] vetheaWhitelist = {};
    /** Оружие других модов, которое кошмар Ветеи сам не узнаёт (урон считает само, без ванильной основы). */
    public static String[] vetheaWeapons = {};
    /** Журнал аудита для админов (logs/dre-audit.log) и запись в HawkEye, если он есть на сервере. */
    public static boolean auditLog = true, auditHawkEye = true;

    /** Массив из конфига; если в старом файле он короче нового - дополняется значениями по умолчанию. */
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
        tierFullReduction = intList(cfg, bal, "tierFullReduction", tierFullReduction,
            "Защита полного сета по ступеням R0, R1, ... R7 в процентах");
        tierSmallSlots = intList(cfg, bal, "tierSmallSlots", tierSmallSlots,
            "Число малых слотов под перки по ступеням R0, R1, ... R7");
        upgradeR1FuelRealmiteIngots = cfg.getInt("upgradeR1FuelRealmiteIngots", bal, upgradeR1FuelRealmiteIngots,
            0, 64, "Сколько реалмитовых слитков стоит улучшение R0 -> R1");
        upgradeTwilightFuelCount = cfg.getInt("upgradeTwilightFuelCount", bal, upgradeTwilightFuelCount, 0, 64,
            "Сколько камней мира стоит закалка R2-R7");
        perkFuelArlemiteIngots = cfg.getInt("perkFuelArlemiteIngots", bal, perkFuelArlemiteIngots,
            0, 64, "Сколько арлемитовых слитков стоит установка малого перка");
        cadillionHealth = intList(cfg, "bosses", "cadillionHealth", cadillionHealth,
            "Здоровье великого кадиллиона: Eden, Wildwood, Apalachia, Skythern");
        Property hit = cfg.get("bosses", "cadillionHit", cadillionHit,
            "Сколько здоровья (в полусердцах, у игрока 20) снимает удар великого кадиллиона, если на игроке броня "
                + "требуемой ступени: Eden, Wildwood, Apalachia, Skythern");
        double[] h = hit.getDoubleList();
        if (h.length >= cadillionHit.length) cadillionHit = h;
        else hit.set(cadillionHit);
        cadillionRequiredTier = intList(cfg, "bosses", "cadillionRequiredTier", cadillionRequiredTier,
            "Требуемая ступень брони против великого кадиллиона: Eden, Wildwood, Apalachia, Skythern");
        twilightDemonRequiredTier = cfg.getInt("twilightDemonRequiredTier", "bosses", twilightDemonRequiredTier, 0, 9,
            "Требуемая ступень брони против Сумеречного демона");
        karotRequiredTier = cfg.getInt("karotRequiredTier", "bosses", karotRequiredTier, 0, 9,
            "Требуемая ступень брони против Карота");
        underTierMultiplier = cfg.get("bosses", "underTierMultiplier", underTierMultiplier,
            "Во сколько раз растёт урон босса за каждую ступень брони ниже требуемой (1 - выключить)").getDouble();
        String b = "bosses";
        carcinusHealth = cfg.get(b, "carcinusHealth", carcinusHealth, "Здоровье Карцинуса").getDouble();
        carcinusDamage = cfg.get(b, "carcinusDamage", carcinusDamage, "Урон Карцинуса за удар").getDouble();
        carcinusNonCrabDamageFactor = cfg.get(b, "carcinusNonCrabDamageFactor", carcinusNonCrabDamageFactor,
            "Какую долю урона Карцинус получает от не-крабового оружия").getDouble();
        carcinusWaterArmorFactor = cfg.get(b, "carcinusWaterArmorFactor", carcinusWaterArmorFactor,
            "Какую долю урона Карцинус наносит игроку в водной броне (сет Kraken, Aquastrive или Странник с водным перком)").getDouble();
        carcinusVortexDamage = cfg.get(b, "carcinusVortexDamage", carcinusVortexDamage,
            "Урон песчаного вихря Карцинуса (сквозь любую броню, в полусердцах; от вихря можно отбежать)").getDouble();
        carcinusSummonClaws = cfg.getInt("carcinusSummonClaws", b, carcinusSummonClaws, 0, 64,
            "Сколько крабовых клешней нужно для призыва Карцинуса на алтаре (клешни роняют норные крабы в лабиринте)");
        snowGlaconHealth = cfg.get(b, "snowGlaconHealth", snowGlaconHealth, "Здоровье Снежного Глакона").getDouble();
        snowGlaconDamage = cfg.get(b, "snowGlaconDamage", snowGlaconDamage, "Урон Снежного Глакона за удар").getDouble();
        colossusHealth = cfg.get(b, "colossusHealth", colossusHealth, "Здоровье Ледяного Колосса").getDouble();
        colossusDamage = cfg.get(b, "colossusDamage", colossusDamage, "Урон удара Ледяного Колосса").getDouble();
        lichHealth = cfg.get(b, "lichHealth", lichHealth, "Здоровье Короля-лича").getDouble();
        lichDamage = cfg.get(b, "lichDamage", lichDamage, "Урон Короля-лича в ближнем бою").getDouble();
        throneSummonSnowflakes = cfg.getInt("throneSummonSnowflakes", b, throneSummonSnowflakes, 0, 64,
            "Сколько снежинок нужно для призыва Ледяного Колосса на алтаре Ледяного Трона");
        nestSpacing = cfg.getInt("nestSpacing", "worldgen", nestSpacing, 0, 100000,
            "Логова боссов сумеречных миров (гнёзда Великих кадиллионов, Демонический круг Мортума): одно на квадрат N x N блоков в каждом мире, над островами на высоте 140 (0 - не генерировать). Меньше 256 не ставить");
        twilightRuinsRarity = cfg.getInt("twilightRuinsRarity", "worldgen", twilightRuinsRarity, 0, 100000,
            "Руины с наградой на островах сумеречных миров: в среднем 1 на N чанков (0 - не генерировать)");
        twilightDungeonRarity = cfg.getInt("twilightDungeonRarity", "worldgen", twilightDungeonRarity, 0, 100000,
            "Подземелья в толще крупных островов сумеречных миров (спавнер, сундук): в среднем 1 на N чанков (0 - не генерировать)");
        twilightBridgeRarity = cfg.getInt("twilightBridgeRarity", "worldgen", twilightBridgeRarity, 0, 100000,
            "Мосты между близкими островами сумеречных миров (пролёт 4-14 блоков): пробовать в 1 из N чанков, ставится только если рядом есть пропасть подходящей ширины (0 - не генерировать)");
        twilightCampRarity = cfg.getInt("twilightCampRarity", "worldgen", twilightCampRarity, 0, 100000,
            "Лагеря странников в сумеречных мирах (торговец, костёр - точка возрождения): в среднем 1 на N чанков (0 - не генерировать)");
        netherLairSpacing = cfg.getInt("netherLairSpacing", "worldgen", netherLairSpacing, 0, 100000,
            "Логова Ада (Обсерватория Смотрителя, Огненная палата Короля скорчеров): одно каждого на квадрат N x N блоков Ада; 0 - не генерировать их и гнездовье Айрако в Энде (тогда их предметы вызова работают где угодно)");
        summonOnlyInLairs = cfg.getBoolean("summonOnlyInLairs", b, summonOnlyInLairs,
            "Боссов вызывают только в их логовах: зов кадиллиона - на алтаре гнезда, кристаллы Мортума - в Демоническом круге, Таинственные часы - на алтаре гробницы. false - где угодно, как в Divine");
        throneSpacing = cfg.getInt("throneSpacing", "worldgen", throneSpacing, 0, 100000,
            "Ледяной Трон в Iceika: один на квадрат N x N блоков, место выбирается из сида мира (0 - не генерировать). Меньше 1024 не ставить: арена 300 блоков");
        carcinusShoreRarity = cfg.getInt("carcinusShoreRarity", "worldgen", carcinusShoreRarity, 0, 100000,
            "Логово Карцинуса: в среднем 1 на N чанков песчаного пляжа (материк или остров; 0 - не генерировать)");
        snowDungeonRarity = cfg.getInt("snowDungeonRarity", "worldgen", snowDungeonRarity, 0, 100000,
            "Снежное подземелье: в среднем 1 на N заснеженных чанков (0 - не генерировать)");
        snowGlobeOnlyFromBoss = cfg.getBoolean("snowGlobeOnlyFromBoss", Configuration.CATEGORY_GENERAL,
            snowGlobeOnlyFromBoss, "Убрать рецепт Снежного шара Divine: шар падает только со Снежного Глакона");
        snowflakesInSummons = cfg.getBoolean("snowflakesInSummons", Configuration.CATEGORY_GENERAL,
            snowflakesInSummons, "Добавить снежинки Iceika в рецепты Таинственных часов и Зова Смотрителя");
        requireBossUnlocks = cfg.getBoolean("requireBossUnlocks", Configuration.CATEGORY_GENERAL, requireBossUnlocks,
            "Требовать убийство связанного босса для улучшений и перков");
        replaceTwilightClock = cfg.getBoolean("replaceTwilightClock", Configuration.CATEGORY_GENERAL,
            replaceTwilightClock, "Убрать рецепт сумеречных часов Divine: порталы открываются Часами Странника по уровням");
        arcanaNoDeadEnds = cfg.getBoolean("arcanaNoDeadEnds", "worldgen", arcanaNoDeadEnds,
            "Аркана без тупиков: на стыках комнат, где проёмы не совпадают, прорубается проход 2x2 (только новые чанки)");
        progressSpawns = cfg.getBoolean("progressSpawns", Configuration.CATEGORY_GENERAL, progressSpawns,
            "Мобы Divine в обычном мире, аду и Энде открываются по рангу: 0 - крабы, 1 (Карцинус) - снег, океан, Wildfire и адские пауки, 2 (Король-лич) - все остальные, кроме Энда, 3 (Древняя сущность) - мобы Энда");
        lowRankNoDrops = cfg.getBoolean("lowRankNoDrops", Configuration.CATEGORY_GENERAL, lowRankNoDrops,
            "С моба Divine, которого убил игрок ниже нужного ранга, не падают дроп и опыт (убить можно, нафармить - нет)");
        warnVetheaInventory = cfg.getBoolean("warnVetheaInventory", Configuration.CATEGORY_GENERAL,
            warnVetheaInventory, "Предупреждать в логе, если в Divine включён отдельный инвентарь Vethea");
        String fish = "fishing";
        fishingRelics = cfg.getBoolean("fishingRelics", fish, fishingRelics,
            "Реликвии рыбалки: редкий улов вроде Кошмарного бура в Ветее. false - не ловятся совсем");
        relicChanceMultiplier = cfg.get(fish, "relicChanceMultiplier", relicChanceMultiplier,
            "Множитель шанса реликвий (1.0 - примерно раз в 250 поклёвок с лучшей снастью)").getDouble();
        drillArea = cfg.getBoolean("drillArea", fish, drillArea,
            "Кошмарный бур копает 3x3 (присев - один блок). false - всегда один блок");
        wormChance = cfg.get(fish, "wormChance", wormChance, "Шанс червя с земли/травы при копании червекопалкой").getDouble();
        wormRainChance = cfg.get(fish, "wormRainChance", wormRainChance, "То же в дождь").getDouble();
        String v = "vethea";
        vetheaNightmare = cfg.getBoolean("vetheaNightmare", v, vetheaNightmare,
            "Кошмар наоборот: в Ветее чужое оружие лечит, чужая броня усиливает урон, чужая еда отнимает сытость, чужие зелья "
                + "дают обратный эффект, чужие инструменты копают медленно, не ветейские аксессуары молчат");
        vetheaFloors = cfg.getBoolean("vetheaFloors", v, vetheaFloors,
            "Этажи Ветеи: наверх только через разломы на Крыльях сна, низ плит не ломается, выше дозволенного этажа "
                + "сбрасывает, обычный полёт выключен (кроме творческого режима)");
        vetheaWhitelist = cfg.getStringList("vetheaWhitelist", v, vetheaWhitelist,
            "Предметы других модов, которые в Ветее считаются своими (modid:name, по одному в строке)");
        vetheaWeapons = cfg.getStringList("vetheaWeapons", v, vetheaWeapons,
            "Оружие других модов, которое в Ветее не распознаётся само (modid:name): чужое оружие лечит мобов. Мечи, инструменты, луки, Tinkers' Construct, катана и разборщик Tainted Magic, мульти-инструмент Blood Magic узнаются без настройки");
        auditLog = cfg.getBoolean("auditLog", "audit", auditLog,
            "Журнал действий для админов: logs/dre-audit.log (аксессуары, переноска питомцев, /dreinv, сделки и услуги торговцев, дары и возврат вещей Ветеи)");
        auditHawkEye = cfg.getBoolean("auditHawkEye", "audit", auditHawkEye,
            "Те же действия - в HawkEye, если он стоит на сервере (тип OTHER, действия DRE ...)");
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
