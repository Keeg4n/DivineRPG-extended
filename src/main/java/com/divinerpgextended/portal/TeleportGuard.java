package com.divinerpgextended.portal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import org.apache.logging.log4j.LogManager;

/**
 * Быстрые телепорты игрока (пустотный шаг и спасение брони Энда, скипетр Энда, кристалл телепортации) спрашивают разрешения у
 * сервера: на гибридном ядре (Cauldron, Thermos, KCauldron) - обычным событием телепорта Bukkit с причиной ENDER_PEARL. Его
 * проверяют плагины приватов (WorldGuard и др.): запрет телепортов в регионе отменяет событие - телепорта нет. На чистом
 * Forge (без Bukkit) - всегда можно. Ошибка рефлексии один раз пишется в лог и проверка выключается.
 */
public final class TeleportGuard {

    private static boolean tried, ok;
    private static Method getBukkitEntity, getWorld, getLocation, getPluginManager, callEvent, isCancelled;
    private static Constructor<?> location, event;
    private static Object cause;

    private TeleportGuard() {}

    private static synchronized void init() {
        if (tried) return;
        tried = true;
        try {
            ClassLoader cl = TeleportGuard.class.getClassLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, cl);
            Class<?> world = Class.forName("org.bukkit.World", false, cl);
            Class<?> player = Class.forName("org.bukkit.entity.Player", false, cl);
            Class<?> loc = Class.forName("org.bukkit.Location", false, cl);
            Class<?> causeCls = Class.forName("org.bukkit.event.player.PlayerTeleportEvent$TeleportCause", false, cl);
            Class<?> ev = Class.forName("org.bukkit.event.player.PlayerTeleportEvent", false, cl);
            Class<?> evBase = Class.forName("org.bukkit.event.Event", false, cl);
            getBukkitEntity = net.minecraft.entity.Entity.class.getMethod("getBukkitEntity");
            getWorld = player.getMethod("getWorld");
            getLocation = player.getMethod("getLocation");
            getPluginManager = bukkit.getMethod("getPluginManager");
            callEvent = getPluginManager.getReturnType().getMethod("callEvent", evBase);
            isCancelled = ev.getMethod("isCancelled");
            location = loc.getConstructor(world, double.class, double.class, double.class, float.class, float.class);
            event = ev.getConstructor(player, loc, loc, causeCls);
            for (Object c : causeCls.getEnumConstants()) if ("ENDER_PEARL".equals(c.toString())) cause = c;
            ok = cause != null;
        } catch (Throwable notHybrid) {
            ok = false; // чистый Forge: приватов-плагинов нет
        }
    }

    /** Разрешает ли сервер быстрый телепорт игрока из текущей точки в (x, y, z). */
    public static boolean allowed(EntityPlayer p, double x, double y, double z) {
        if (!(p instanceof EntityPlayerMP) || p instanceof net.minecraftforge.common.util.FakePlayer) return true;
        init();
        if (!ok) return true;
        try {
            Object bp = getBukkitEntity.invoke(p);
            Object to = location.newInstance(getWorld.invoke(bp), x, y, z, p.rotationYaw, p.rotationPitch);
            Object e = event.newInstance(bp, getLocation.invoke(bp), to, cause);
            callEvent.invoke(getPluginManager.invoke(null), e);
            return !(Boolean) isCancelled.invoke(e);
        } catch (Throwable t) {
            ok = false;
            LogManager.getLogger("divinerpgextended").warn("Проверка телепорта в приватах выключена: {}", t.toString());
            return true;
        }
    }

    /** То же, с сообщением игроку при запрете. */
    public static boolean check(EntityPlayer p, double x, double y, double z) {
        if (allowed(p, x, y, z)) return true;
        p.addChatMessage(new ChatComponentTranslation("dre.chat.teleport_denied"));
        return false;
    }
}
