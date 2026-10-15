package com.divinerpgextended.audit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.divinerpgextended.Config;

/**
 * Журнал аудита для админов: ценные действия с вещами и прогрессом, которых не видят плагины журналов (хранилища и
 * механики мода, а не блоки). Пишется всегда в logs/dre-audit.log и, если на сервере есть HawkEye, ещё и в него -
 * записью типа OTHER с действием "DRE ..." (поиск: /hawk search a:other p:Игрок).
 * <p>
 * Большинство действий заканчиваются сообщением игроку: {@link #say} вызывается из помощников say() механик и
 * отбирает важное по ключу сообщения. Остальное (аксессуары, переноска, /dreinv) пишется напрямую через {@link #log}.
 */
public final class Audit {

    private Audit() {}

    private static final Logger LOG = LogManager.getLogger("divinerpgextended");
    /** Ключ сообщения (или его начало, если кончается точкой) -> действие. */
    private static final Map<String, String> KEYS = new LinkedHashMap<String, String>();

    static {
        KEYS.put("dre.jack.done", "Jack order");
        KEYS.put("dre.jack.junk_done", "Jack junk");
        KEYS.put("dre.tinker.done", "Tinker combine");
        KEYS.put("dre.merik.result", "Merik reforge");
        KEYS.put("dre.merik.result_bad", "Merik reforge");
        KEYS.put("dre.general.statue_taken", "General statue");
        KEYS.put("dre.general.order_done", "General order");
        KEYS.put("dre.general.promoted", "General rank");
        KEYS.put("dre.vatticus.task_done", "Vatticus research");
        KEYS.put("dre.zelus.skill.", "Pet training");
        KEYS.put("dre.furnace.installed", "Furnace module");
        KEYS.put("dre.quest.thanks", "Fishing quest");
        KEYS.put("dre.quest.reward", "Fishing reward");
        KEYS.put("dre.chat.accessory_drop", "Boss accessory");
        KEYS.put("dre.chat.crystal_given", "Memory crystal");
        KEYS.put("dre.chat.merchant_voice", "Merchant summon");
        KEYS.put("dre.vethea.gift", "Dream gift");
        KEYS.put("dre.vethea.gift_marked", "Dream gift marked");
        KEYS.put("dre.vethea.stash_found", "Vethea stash return");
        KEYS.put("dre.vethea.stash_done", "Vethea stash return");
        KEYS.put("dre.vethea.guard", "Vethea guard");
    }

    private static BufferedWriter out;

    /** Из помощника say() механики: записать, если ключ из списка важных. */
    public static void say(EntityPlayer p, String key, Object... args) {
        if (p == null || p.worldObj.isRemote) return;
        String action = KEYS.get(key);
        if (action == null) {
            for (Map.Entry<String, String> en : KEYS.entrySet()) {
                if (en.getKey().endsWith(".") && key.startsWith(en.getKey())) action = en.getValue();
            }
        }
        if (action == null) return;
        String text;
        try {
            IChatComponent c = new ChatComponentTranslation(key, args);
            text = EnumChatFormatting.getTextWithoutFormattingCodes(c.getUnformattedText());
        } catch (Throwable t) {
            text = key;
        }
        log(p, action, text);
    }

    /** Записать действие игрока: файл аудита и HawkEye. */
    public static void log(EntityPlayer p, String action, String data) {
        if (p == null || p.worldObj.isRemote) return;
        if (Config.auditLog) file(p, action, data);
        if (Config.auditHawkEye) Hawk.add(p, action, data);
    }

    private static synchronized void file(EntityPlayer p, String action, String data) {
        try {
            if (out == null) {
                File dir = MinecraftServer.getServer().getFile("logs");
                dir.mkdirs();
                out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, "dre-audit.log"), true), StandardCharsets.UTF_8));
            }
            out.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " [" + p.getCommandSenderName() + "] dim " + p.dimension
                + " " + (int) Math.floor(p.posX) + " " + (int) Math.floor(p.posY) + " " + (int) Math.floor(p.posZ) + " " + action + ": " + data);
            out.newLine();
            out.flush();
        } catch (Throwable t) {
            LOG.warn("dre-audit.log: не удалось записать", t);
        }
    }

    /** Мост в HawkEye (Bukkit-плагин на гибридном сервере). Всё через рефлексию: без Bukkit и HawkEye просто молчит. */
    static final class Hawk {

        private static boolean tried, ok;
        private static Object plugin;
        private static Method add, getWorlds;
        private static Constructor<?> location;

        private static void init() {
            tried = true;
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                Object pm = bukkit.getMethod("getPluginManager").invoke(null);
                Object pl = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "HawkEye");
                if (pl == null || !(Boolean) pl.getClass().getMethod("isEnabled").invoke(pl)) return;
                ClassLoader cl = pl.getClass().getClassLoader();
                Class<?> api = Class.forName("uk.co.oliwali.HawkEye.util.HawkEyeAPI", true, cl);
                Class<?> loc = Class.forName("org.bukkit.Location", true, cl);
                add = api.getMethod("addCustomEntry", Class.forName("org.bukkit.plugin.java.JavaPlugin", true, cl), String.class,
                    String.class, loc, String.class);
                location = loc.getConstructor(Class.forName("org.bukkit.World", true, cl), double.class, double.class, double.class);
                getWorlds = bukkit.getMethod("getWorlds");
                plugin = pl;
                ok = true;
                LOG.info("Аудит: HawkEye найден, действия аддона пишутся в него (действия \"DRE ...\", тип OTHER)");
            } catch (ClassNotFoundException e) {
                // нет Bukkit или HawkEye - обычный сервер или одиночная игра
            } catch (Throwable t) {
                LOG.warn("Аудит: HawkEye есть, но подключиться к его API не удалось", t);
            }
        }

        /** Мир Bukkit для мира Forge: на Cauldron/Thermos у World есть getWorld(), иначе ищем по getHandle(). */
        private static Object world(World w) throws Exception {
            try {
                Object o = w.getClass().getMethod("getWorld").invoke(w);
                if (o != null) return o;
            } catch (NoSuchMethodException ignored) {}
            for (Object bw : (List<?>) getWorlds.invoke(null)) {
                if (bw.getClass().getMethod("getHandle").invoke(bw) == w) return bw;
            }
            return null;
        }

        static void add(EntityPlayer p, String action, String data) {
            if (!tried) init();
            if (!ok) return;
            try {
                Object bw = world(p.worldObj);
                if (bw == null) return;
                Object loc = location.newInstance(bw, p.posX, p.posY, p.posZ);
                String d = data.length() > 200 ? data.substring(0, 200) : data;
                add.invoke(null, plugin, "DRE " + action, p.getCommandSenderName(), loc, d);
            } catch (Throwable t) {
                LOG.warn("Аудит: запись в HawkEye не удалась", t);
                ok = false;
            }
        }
    }
}
