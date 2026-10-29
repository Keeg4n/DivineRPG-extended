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
    public static boolean bossNoFly = true;
    /** Арена босса ({@link com.divinerpgextended.boss.BossArena}). */
    public static boolean bossArenaHold = true;
    public static int bossArenaLeaveSeconds = 60, bossArenaRadius = 24, bossSummonClearRadius = 8;
    public static boolean dungeonSpawners = false;
    public static int divineTorchRadius = 24;
    public static int mapWorldSize = 10000;
    public static boolean worldCrafts = true, gateStones = true, miningTiers = true;
    /** Постройки сумеречных миров: 1 на N чанков (0 - нет). */
    public static int twilightRuinsRarity = 30, twilightDungeonRarity = 40, twilightBridgeRarity = 3, twilightCampRarity = 45;
    public static int apalachiaStructureBoost = 2, twilightPondRarity = 20, vetheaLakeRarity = 10, vetheaHangingLakeRarity = 14;
    /** Логово Карцинуса: 1 на N океанских чанков; снежное подземелье: 1 на N заснеженных чанков (0 - выключить). */
    /** Логова обычного мира по сетке из сида: Карцинус и ледяная пещера - по одному на регион N x N. Карты. */
    public static int carcinusSpacing = 320, snowDungeonSpacing = 640, mapSearchRadius = 5000, mapSkipNear = 128, mapCooldownSeconds = 5;

    public static boolean requireBossUnlocks = true;
    public static boolean replaceTwilightClock = true;
    public static boolean snowGlobeOnlyFromBoss = true, scatterInModBiomes = true;
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
    /** Естественный спавн в мирах Divine - только мобы Divine, аддона и белого списка. */
    public static boolean divineSpawnFilter = true;
    /** Фонтанный зал Арканы: в среднем один на столько чанков (0 - нет). */
    public static int arcanaFountainChance = 40;
    /** Номера эффектов арканных зелий (пусто - свободные сами, от 127 вниз). */
    public static int[] arcanePotionIds = {};
    public static String[] divineSpawnWhitelist = {};
    /** Слабости мобов Ветеи: "класс моба=вид[,вид]" (виды: sword claw hammer bow disk staff cannon). */
    public static String[] vetheaWeakness = { "EntityLheiva=bow", "EntityLorgaFlight=sword,claw", "EntityDissiment=disk", "EntityHoverStinger=disk",
        "EntityZoragon=bow", "EntityGalroid=hammer", "EntityVhraak=hammer", "EntityShadahier=staff", "EntityCymesoid=staff", "EntityKazrotic=cannon" };
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
            "Не используется с 0.61.0: Карот - зеркало, его сила зависит от оружия и брони игрока, а не от ступени");
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
        apalachiaStructureBoost = cfg.getInt("apalachiaStructureBoost", "worldgen", apalachiaStructureBoost, 1, 10,
            "Во сколько раз в Апалачии чаще руины, лагеря и подземелья на островах (острова там реже и тоньше)");
        twilightPondRarity = cfg.getInt("twilightPondRarity", "worldgen", twilightPondRarity, 0, 100000,
            "Пруды для рыбалки на островах сумеречных миров (причал, ива, кувшинки): в среднем 1 на N чанков (0 - не генерировать)");
        vetheaLakeRarity = cfg.getInt("vetheaLakeRarity", "worldgen", vetheaLakeRarity, 0, 100000,
            "Озёра на этажах Ветеи (рыбалка мечтаний): в среднем 1 на N чанков на каждом этаже (0 - не генерировать)");
        vetheaHangingLakeRarity = cfg.getInt("vetheaHangingLakeRarity", "worldgen", vetheaHangingLakeRarity, 0, 100000,
            "Озёра в потолке этажей Ветеи (вода мечтаний течёт вверх, гладь вровень с потолком, у неё висят вверх ногами и рыбачат): в среднем 1 на N чанков на этаж (0 - не генерировать)");
        twilightDungeonRarity = cfg.getInt("twilightDungeonRarity", "worldgen", twilightDungeonRarity, 0, 100000,
            "Подземелья в толще крупных островов сумеречных миров (спавнер, сундук): в среднем 1 на N чанков (0 - не генерировать)");
        twilightBridgeRarity = cfg.getInt("twilightBridgeRarity", "worldgen", twilightBridgeRarity, 0, 100000,
            "Мосты между близкими островами сумеречных миров (пролёт 4-14 блоков): пробовать в 1 из N чанков, ставится только если рядом есть пропасть подходящей ширины (0 - не генерировать)");
        twilightCampRarity = cfg.getInt("twilightCampRarity", "worldgen", twilightCampRarity, 0, 100000,
            "Лагеря странников в сумеречных мирах (торговец, костёр - точка возрождения): в среднем 1 на N чанков (0 - не генерировать)");
        netherLairSpacing = cfg.getInt("netherLairSpacing", "worldgen", netherLairSpacing, 0, 100000,
            "Логова Ада (Обсерватория Смотрителя, Огненная палата Короля скорчеров): одно каждого на квадрат N x N блоков Ада; 0 - не генерировать их и гнездовье Айрако в Энде (тогда их предметы вызова работают где угодно)");
        mapWorldSize = cfg.getInt("mapWorldSize", b, mapWorldSize, 0, 1000000,
            "Карты логов показывают логова только в квадрате такого размера с центром в нуле мира: 10000 - от -5000 до +5000 по X и Z; 0 - без границ");
        divineTorchRadius = cfg.getInt("divineTorchRadius", b, divineTorchRadius, 0, 128,
            "Факелы Divine (водный, скелетный, Эдема, арканиевый) и факелы сумеречных миров в обычном мире: в этом радиусе мобы Divine сами не появляются; 0 - выключено");
        dungeonSpawners = cfg.getBoolean("dungeonSpawners", b, dungeonSpawners,
            "Спавнеры мобов в постройках аддона (Древняя гробница, ледяная пещера, логово под храмом джунглей, подземелья сумеречных миров, склепы Мортума, башня Сумеречного демона). false - без спавнеров: мобы приходят сами, как везде");
        bossNoFly = cfg.getBoolean("bossNoFly", b, bossNoFly,
            "Рядом с живым боссом (48 блоков) полёт выключается - кроме Скайтерна и творческого режима. false - летать можно");
        bossArenaLeaveSeconds = cfg.getInt("bossArenaLeaveSeconds", b, bossArenaLeaveSeconds, -1, 3600,
            "Арена босса: если на ней не осталось бойцов (ушли, телепортировались, умерли, вышли из игры), босс ждёт столько секунд и исчезает без трофеев. Вернулся хоть один - ожидание сначала. 0 - исчезает сразу, как Страж Гайи в Botania; -1 - не исчезает никогда");
        bossArenaHold = cfg.getBoolean("bossArenaHold", b, bossArenaHold,
            "Пока босс жив, бойца у края арены отталкивает обратно к центру (выйти можно только телепортом). false - выходить можно, остаётся только правило bossArenaLeaveSeconds");
        bossArenaRadius = cfg.getInt("bossArenaRadius", b, bossArenaRadius, 8, 128,
            "Радиус арены босса, вызванного не в логове аддона (Аркана, Ветея, вызов где угодно); у логов арена - их зал, у Ледяного Трона - купол до кольца пилонов (80)");
        bossSummonClearRadius = cfg.getInt("bossSummonClearRadius", b, bossSummonClearRadius, 0, 32,
            "Вызов босса вне логова (summonOnlyInLairs=false, логова выключены): в этом радиусе вокруг точки вызова на 4 блока вверх должно быть пусто, как у Стража Гайи (до 10% колонн можно занять). 0 - без проверки. В логове проверяется только, что место появления босса не застроено");
        summonOnlyInLairs = cfg.getBoolean("summonOnlyInLairs", b, summonOnlyInLairs,
            "Боссов вызывают только в их логовах: зов кадиллиона - на алтаре гнезда, кристаллы Мортума - в Демоническом круге, Таинственные часы - на алтаре гробницы. false - где угодно, как в Divine");
        worldCrafts = cfg.getBoolean("worldCrafts", b, worldCrafts,
            "Крафты сумеречных миров цепочкой: вещь мира = вещь прошлого мира + куски + материал мобов, рукояти из палок мира; палки, факелы и стрелы миров. false - рецепты Divine");
        miningTiers = cfg.getBoolean("miningTiers", b, miningTiers,
            "Уровни кирок: руду сумеречного мира ломает только кирка этого мира или выше, сумеречный камень - кирка прошлого мира или выше (в Эдеме - божественная)");
        gateStones = cfg.getBoolean("gateStones", b, gateStones,
            "Рамки порталов Дикого леса..Мортума - камни врат из сердца стража (трофей Великого кадиллиона) вместо блоков камней прошлого мира. Старые рамки из блоков камней перестанут работать");
        throneSpacing = cfg.getInt("throneSpacing", "worldgen", throneSpacing, 0, 100000,
            "Ледяной Трон в Iceika: один на квадрат N x N блоков, место выбирается из сида мира (0 - не генерировать). Меньше 1024 не ставить: арена 300 блоков");
        carcinusSpacing = cfg.getInt("carcinusSpacing", "worldgen", carcinusSpacing, 0, 100000,
            "Логово Карцинуса: одно на квадрат N x N блоков, если в нём есть песчаный пляж у моря; место из сида мира, карта знает его заранее (0 - не генерировать)");
        snowDungeonSpacing = cfg.getInt("snowDungeonSpacing", "worldgen", snowDungeonSpacing, 0, 100000,
            "Ледяная пещера Снежного Глакона: одна на квадрат N x N блоков, если в нём есть ровная заснеженная суша; место из сида мира (0 - не генерировать)");
        mapSearchRadius = cfg.getInt("mapSearchRadius", "worldgen", mapSearchRadius, 500, 30000,
            "Карты логов обычного мира: радиус поиска в блоках (5000 - квадрат 10000 x 10000)");
        mapSkipNear = cfg.getInt("mapSkipNear", "worldgen", mapSkipNear, 0, 1000,
            "Карты логов: место ближе N блоков считается найденным - карта ведёт к следующему");
        mapCooldownSeconds = cfg.getInt("mapCooldownSeconds", "worldgen", mapCooldownSeconds, 0, 600,
            "Карты логов: перезарядка в секундах (поиск по карте биомов нагружает сервер)");
        scatterInModBiomes = cfg.getBoolean("scatterInModBiomes", "worldgen", scatterInModBiomes,
            "Пирамиды и храмы джунглей обычного мира - и в биомах других модов (Biomes O' Plenty): пирамида в любом жарком песке, храм в любых джунглях, а если джунглей у спавна нет - в тёплых зелёных землях. Под ними гробница и логово Древнего драмкрикса");
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
        arcanaFountainChance = cfg.getInt("arcanaFountainChance", "arcana", arcanaFountainChance, 0, 10000,
            "Фонтанный зал Арканы (чаша арканного концентрата: восстанавливает аркану, только там рыба Арканы и только на арканную удочку): "
                + "в среднем один на столько новых чанков; 0 - не строить");
        arcanePotionIds = cfg.get("arcana", "arcanePotionIds", arcanePotionIds,
            "Номера эффектов арканных зелий (1-127, по порядку: ясность, чародейство, собранность, оберег от чар, каменная кожа, отвод стрел, стойкость, "
                + "колючая шкура, внутренний жар, боевой раж, твёрдая рука, жажда арканы, рыбацкий фарт). Пусто - берутся свободные от 127 вниз; "
                + "занятый номер тоже заменяется свободным. Задавать, только если эффекты конфликтуют с другим модом").getIntList();
        divineSpawnFilter = cfg.getBoolean("divineSpawnFilter", v, divineSpawnFilter,
            "Миры Divine (Iceika, сумеречные, Аркана, Ветея): сами по себе появляются только мобы Divine и аддона (и из divineSpawnWhitelist); "
                + "мобы других модов (слизни Tinkers, Grimoire of Gaia...) - нет. Спавнеры и призыв не затронуты");
        divineSpawnWhitelist = cfg.getStringList("divineSpawnWhitelist", v, divineSpawnWhitelist,
            "Кому ещё можно появляться в мирах Divine: класс моба, имя сущности (как в /summon) или начало имени (modid - для всех мобов мода), по одному в строке");
        vetheaWeakness = cfg.getStringList("vetheaWeakness", v, vetheaWeakness,
            "Слабости мобов Ветеи (класс моба Divine=вид[,вид]): своим видом x2 урона и двойная добыча за добивание, остальным x0.25. "
                + "Виды: sword claw hammer bow disk staff cannon. Пустой список - слабостей нет");
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
