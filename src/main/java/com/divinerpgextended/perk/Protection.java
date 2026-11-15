package com.divinerpgextended.perk;

import java.lang.reflect.Method;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

/**
 * Protected regions with PvP disabled: addon weapon and armor effects (ignite, poison, slowness, wave, damage reflection) do not affect
 * players or peaceful or neutral creatures there (zombie pigmen and spiders do not count, they can be hit).
 * <p>
 * Effects are applied in LivingHurtEvent, while a region (WorldGuard on a hybrid server) cancels the damage later - so
 * we ask ourselves: PvP is off on the whole server, the WorldGuard pvp flag at the target's position, or a plugin cancelled a trial hit
 * from player to player. Without Bukkit and WorldGuard only the server setting remains.
 */
public final class Protection {

    /** For the selftest: true - assume the target is in a region without PvP. */
    public static boolean testNoPvp;
    /** For the selftest: true - assume building here is not allowed (someone else's region). */
    public static boolean testNoBuild;
    private static boolean placeChecked;
    private static java.lang.reflect.Constructor<?> placeEvent;
    private static Method getBlockAt, getState, getRelative, itemInHand;
    private static Object faceDown;

    /**
     * Whether the player may place a block at the point, when an item places it itself, bypassing normal placement: spawn protection and adventure
     * mode ({@link EntityPlayer#canPlayerEdit}), and on a hybrid server - a trial Bukkit block place event, which region
     * plugins (WorldGuard, GriefPrevention...) cancel. The block itself is not placed.
     */
    public static boolean canBuild(EntityPlayer p, int x, int y, int z) {
        if (p == null || !p.canPlayerEdit(x, y, z, 1, p.getHeldItem())) return false;
        if (testNoBuild) return false;
        if (p.worldObj.isRemote || !bukkit()) return true;
        try {
            if (!placeChecked) {
                placeChecked = true;
                Class<?> bb = Class.forName("org.bukkit.block.Block"), face = Class.forName("org.bukkit.block.BlockFace");
                getBlockAt = Class.forName("org.bukkit.World").getMethod("getBlockAt", int.class, int.class, int.class);
                getState = bb.getMethod("getState");
                getRelative = bb.getMethod("getRelative", face);
                faceDown = face.getField("DOWN").get(null);
                itemInHand = Class.forName("org.bukkit.entity.HumanEntity").getMethod("getItemInHand");
                placeEvent = Class.forName("org.bukkit.event.block.BlockPlaceEvent").getConstructor(bb, Class.forName("org.bukkit.block.BlockState"), bb,
                    Class.forName("org.bukkit.inventory.ItemStack"), Class.forName("org.bukkit.entity.Player"), boolean.class);
            }
            if (placeEvent == null) return true;
            Object world = bukkitWorld.invoke(p.worldObj), block = getBlockAt.invoke(world, x, y, z), player = bukkitEntity.invoke(p);
            Object ev = placeEvent.newInstance(block, getState.invoke(block), getRelative.invoke(block, faceDown), itemInHand.invoke(player), player, true);
            callEvent.invoke(pluginManager, ev);
            return !(Boolean) ev.getClass().getMethod("isCancelled").invoke(ev);
        } catch (Throwable t) {
            placeEvent = null;
            return true;
        }
    }

    private static boolean bukkitChecked;
    private static Method bukkitWorld, bukkitEntity, callEvent, wgQuery, wgManager, wgRegions, wgAllows;
    private static Object pluginManager, pvpFlag, damageCause, deny, wgQuerySource;
    private static Class<?> locationClass, damageEventClass, entityClass;

    private Protection() {}

    /** Whom the region protects: players and non-hostile creatures, except zombie pigmen and spiders. */
    public static boolean shielded(Entity victim) {
        if (victim instanceof EntityPlayer) return true;
        if (!(victim instanceof EntityLivingBase) || victim instanceof EntityPigZombie || victim instanceof EntitySpider) return false;
        return !(victim instanceof IMob);
    }

    /** true - the effect from attacker on victim is forbidden by the region (attacker - a player, an armor wearer or null). */
    public static boolean spare(Entity attacker, Entity victim) {
        if (victim == null || victim.worldObj.isRemote || victim == attacker || !shielded(victim)) return false;
        if (testNoPvp) return true;
        MinecraftServer s = MinecraftServer.getServer();
        if (victim instanceof EntityPlayer && s != null && !s.isPVPEnabled()) return true;
        if (!bukkit()) return false;
        try {
            if (noPvpAt(victim)) return true;
            return attacker instanceof EntityPlayer && victim instanceof EntityPlayer && cancelled(attacker, victim);
        } catch (Throwable t) {
            return false;
        }
    }

    /** WorldGuard pvp flag at the creature's position: deny. */
    private static boolean noPvpAt(Entity e) throws Exception {
        if (!wgChecked) worldGuard();
        if (pvpFlag == null) return false;
        Object world = bukkitWorld.invoke(e.worldObj);
        Object loc = locationClass.getConstructor(Class.forName("org.bukkit.World"), double.class, double.class, double.class)
            .newInstance(world, e.posX, e.posY, e.posZ);
        if (wgQuery != null) { // WorldGuard 6: RegionQuery.queryState(location, null, PVP)
            Object arr = java.lang.reflect.Array.newInstance(wgQuery.getParameterTypes()[2].getComponentType(), 1);
            java.lang.reflect.Array.set(arr, 0, pvpFlag);
            return wgQuery.invoke(wgQuerySource, loc, null, arr) == deny;
        }
        Object manager = wgManager.invoke(null, world); // WorldGuard 5
        return manager != null && !(Boolean) wgAllows.invoke(wgRegions.invoke(manager, loc), pvpFlag);
    }

    /** A trial hit with no damage: region plugins (WorldGuard, GriefPrevention...) cancel it. */
    private static boolean cancelled(Entity attacker, Entity victim) throws Exception {
        Object ev = damageEventClass.getConstructor(entityClass, entityClass, damageCause.getClass(), double.class)
            .newInstance(bukkitEntity.invoke(attacker), bukkitEntity.invoke(victim), damageCause, 0.0D);
        callEvent.invoke(pluginManager, ev);
        return (Boolean) ev.getClass().getMethod("isCancelled").invoke(ev);
    }

    private static boolean wgChecked;

    /** WorldGuard pvp flag: we take the plugin classes with its class loader. */
    private static synchronized void worldGuard() {
        if (wgChecked) return;
        try {
            // plugin classes are visible only to its class loader: Class.forName from the mod does not find them
            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, "WorldGuard");
            if (plugin == null) { // without WorldGuard - only Bukkit trial events; at server start the plugin may not be loaded yet
                MinecraftServer srv = MinecraftServer.getServer();
                if (srv == null || srv.getTickCounter() > 600) wgChecked = true;
                return;
            }
            wgChecked = true;
            ClassLoader cl = plugin.getClass().getClassLoader();
            Class<?> wg = Class.forName("com.sk89q.worldguard.bukkit.WGBukkit", true, cl);
            pvpFlag = Class.forName("com.sk89q.worldguard.protection.flags.DefaultFlag", true, cl).getField("PVP").get(null);
            Class<?> sf = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, cl);
            try {
                Object container = plugin.getClass().getMethod("getRegionContainer").invoke(plugin);
                wgQuerySource = container.getClass().getMethod("createQuery").invoke(container);
                wgQuery = wgQuerySource.getClass().getMethod("queryState", locationClass,
                    Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable", true, cl), java.lang.reflect.Array.newInstance(sf, 0).getClass());
                deny = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State", true, cl).getField("DENY").get(null);
            } catch (Throwable old) {
                wgQuery = null;
                wgManager = wg.getMethod("getRegionManager", Class.forName("org.bukkit.World"));
                wgRegions = wgManager.getReturnType().getMethod("getApplicableRegions", locationClass);
                wgAllows = wgRegions.getReturnType().getMethod("allows", sf);
            }
            com.divinerpgextended.DivineRPGExtended.LOG.info("Claims: weapon effects are checked against the WorldGuard pvp flag");
        } catch (Throwable t) {
            wgChecked = true;
            pvpFlag = null;
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Claims: WorldGuard pvp flag cannot be read, Bukkit probe events remain", t);
        }
    }

    private static synchronized boolean bukkit() {
        if (bukkitChecked) return pluginManager != null;
        bukkitChecked = true;
        try {
            Class<?> b = Class.forName("org.bukkit.Bukkit");
            bukkitWorld = net.minecraft.world.World.class.getMethod("getWorld");
            bukkitEntity = Entity.class.getMethod("getBukkitEntity");
            locationClass = Class.forName("org.bukkit.Location");
            entityClass = Class.forName("org.bukkit.entity.Entity");
            damageEventClass = Class.forName("org.bukkit.event.entity.EntityDamageByEntityEvent");
            Class<?> cause = Class.forName("org.bukkit.event.entity.EntityDamageEvent$DamageCause");
            damageCause = cause.getField("ENTITY_ATTACK").get(null);
            Object pm = b.getMethod("getPluginManager").invoke(null);
            callEvent = Class.forName("org.bukkit.plugin.PluginManager").getMethod("callEvent", Class.forName("org.bukkit.event.Event"));
            pluginManager = pm;
        } catch (Throwable t) {
            pluginManager = null;
            return false;
        }
        return true;
    }
}
