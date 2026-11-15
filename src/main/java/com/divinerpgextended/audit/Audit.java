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
 * Audit log for admins: valuable actions with items and progress that log plugins do not see (mod storages and
 * mechanics, not blocks). Always written to logs/dre-audit.log and, if the server has HawkEye, into it as well -
 * as an OTHER-type entry with the action "DRE ..." (search: /hawk search a:other p:Player).
 * <p>
 * Most actions end with a message to the player: {@link #say} is called from the say() helpers of the mechanics and
 * picks the important ones by message key. The rest (accessories, carrier, /dreinv) is written directly through {@link #log}.
 */
public final class Audit {

    private Audit() {}

    private static final Logger LOG = LogManager.getLogger("divinerpgextended");
    /** Message key (or its beginning, if it ends with a dot) -> action. */
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

    /** From the say() helper of a mechanic: record if the key is on the important list. */
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

    /** Record a player action: the audit file and HawkEye. */
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
            LOG.warn("dre-audit.log: could not write", t);
        }
    }

    /** Bridge to HawkEye (a Bukkit plugin on a hybrid server). All through reflection: without Bukkit and HawkEye it just stays silent. */
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
                LOG.info("Audit: HawkEye found, addon actions are written to it (actions \"DRE ...\", type OTHER)");
            } catch (ClassNotFoundException e) {
                // no Bukkit or HawkEye - an ordinary server or singleplayer
            } catch (Throwable t) {
                LOG.warn("Audit: HawkEye is present, but connecting to its API failed", t);
            }
        }

        /** Bukkit world for a Forge world: on Cauldron/Thermos World has getWorld(), otherwise we look through getHandle(). */
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
                LOG.warn("Audit: writing to HawkEye failed", t);
                ok = false;
            }
        }
    }
}
