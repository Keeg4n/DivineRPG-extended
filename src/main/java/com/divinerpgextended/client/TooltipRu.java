package com.divinerpgextended.client;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Перевод описаний предметов Divine, вписанных прямо в код по-английски ("Shoots Lightning", "Fills 3.0 Hunger Bars"): языковым
 * файлом их не перевести. Строка подсказки сравнивается с образцами и заменяется русской из ru_RU.lang; цвет начала строки
 * сохраняется. Ключи есть только в русском файле - на других языках ничего не меняется. Подписан только на клиенте; сам класс
 * общий, чтобы автотест сервера проверил образцы.
 */
public class TooltipRu {

    /** {образец, ключ}: образец - вся строка без цветов; группы в скобках подставляются в перевод. */
    private static final Object[][] RULES = {
        { "Fills ([0-9.]+) Hunger Bars", "dre.tip.fills" }, { "([0-9.]+) Saturation", "dre.tip.saturation" },
        { "(?i)Pet Food:\\s*true", "dre.tip.pet_food_yes" }, { "(?i)Pet Food:\\s*false", "dre.tip.pet_food_no" },
        { "Replenishes ([0-9]+) Hunger", "dre.tip.replenish_hunger" }, { "Replenishes ([0-9]+) Health", "dre.tip.replenish_health" },
        { "Heals ([0-9.]+) hearts on use", "dre.tip.heals" }, { "([0-9.]+) Times Faster", "dre.tip.faster" }, { "([0-9.]+) Times Slower", "dre.tip.slower" },
        { "Spawns a pet Brown Grizzle", "dre.tip.pet_grizzle_brown" }, { "Spawns a pet White Grizzle", "dre.tip.pet_grizzle_white" },
        { "Spawns a pet (Fyracryx|Golem Of Rejuvenation|Paratiku|Seimer)", "dre.tip.pet_named" }, { "Spawns a pet", "dre.tip.pet" },
        { "Bouncing Projectile", "dre.tip.bouncing" }, { "Casts light when thrown", "dre.tip.light_thrown" }, { "Deals 20 Damage to Player", "dre.tip.hurts_player" },
        { "Does not prevent fall damage", "dre.tip.no_fall_guard" }, { "Drops an explosive meteor from the sky", "dre.tip.meteor" },
        { "Explosive homing projectile", "dre.tip.homing" }, { "Gives Speed and Water Breathing for 30 seconds\\.", "dre.tip.sky_flower" },
        { "Knocks mobs away", "dre.tip.knocks_away" }, { "Lauches you into the air", "dre.tip.launches" }, { "On use: Teleports the player", "dre.tip.teleports" },
        { "Poisons Enemies", "dre.tip.poisons" }, { "Poisons enemies for 2 seconds", "dre.tip.poisons_2" }, { "Projectile splits into 5 on impact", "dre.tip.splits" },
        { "Pulls mobs toward you", "dre.tip.pulls" }, { "Returns to sender", "dre.tip.returns" }, { "Shoots Lightning", "dre.tip.lightning" },
        { "Slows mobs for 5 seconds", "dre.tip.slows" }, { "Spawns a friendly Wraith", "dre.tip.wraith" }, { "13 Damage", "dre.tip.damage_13" },
        { "20 Health", "dre.tip.health_20" }, { "Despawns after a short time", "dre.tip.short_life" }, { "Speeds up player when used", "dre.tip.speeds_up" },
        { "Teleport to spawn point", "dre.tip.to_spawn" }, { "Use on arcanite dirt to purify\\.", "dre.tip.purify" },
    };
    private static final Pattern[] PATTERNS = new Pattern[RULES.length];
    static {
        for (int i = 0; i < RULES.length; i++) PATTERNS[i] = Pattern.compile((String) RULES[i][0]);
    }

    /** Какой образец подошёл: {ключ, подстановки...}; null - строка не из этих. */
    public static String[] rule(String plain) {
        for (int i = 0; i < RULES.length; i++) {
            Matcher m = PATTERNS[i].matcher(plain);
            if (!m.matches()) continue;
            String[] r = new String[m.groupCount() + 1];
            r[0] = (String) RULES[i][1];
            for (int g = 1; g < r.length; g++) r[g] = m.group(g);
            return r;
        }
        return null;
    }

    /** Все ключи перевода (для автотеста: каждый должен быть в ru_RU.lang). */
    public static String[] keys() {
        String[] k = new String[RULES.length];
        for (int i = 0; i < RULES.length; i++) k[i] = (String) RULES[i][1];
        return k;
    }

    /** Перевод одной строки подсказки (null - не из Divine или перевода на этом языке нет). */
    public static String translate(String line) {
        String[] r = rule(EnumChatFormatting.getTextWithoutFormattingCodes(line).trim());
        if (r == null || !StatCollector.canTranslate(r[0])) return null;
        Object[] args = new Object[r.length - 1];
        for (int g = 0; g < args.length; g++) args[g] = pet(r[g + 1]);
        return lead(line) + StatCollector.translateToLocalFormatted(r[0], args);
    }

    /** Имя питомца по-русски из файла Divine (иначе как есть). */
    private static String pet(String s) {
        String key = "entity.divinerpg." + s.replace(" ", "") + ".name";
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : s;
    }

    /** Коды цвета в начале строки. */
    private static String lead(String line) {
        int i = 0;
        while (i + 1 < line.length() && line.charAt(i) == '§') i += 2;
        return line.substring(0, i);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTooltip(ItemTooltipEvent e) {
        List<String> tip = e.toolTip;
        for (int i = 1; i < tip.size(); i++) { // первая строка - имя предмета
            String t = translate(tip.get(i));
            if (t != null) tip.set(i, t);
        }
    }
}
