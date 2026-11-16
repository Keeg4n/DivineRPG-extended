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

    /** Our own trial block events are being posted: addon listeners with side effects (furnace modules dropping) ignore them. */
    public static boolean probing;
    private static java.lang.reflect.Constructor<?> breakEvent;
    private static boolean warnedBuild, warnedBreak, warnedHit;

    private static void warn(String what, Throwable t) {
        com.divinerpgextended.DivineRPGExtended.LOG.warn("Claims: " + what + " cannot be checked - it is refused everywhere (" + t + ")");
    }

    /** Spawn protection and adventure mode. */
    private static boolean vanillaEdit(EntityPlayer p, int x, int y, int z) {
        return p.canPlayerEdit(x, y, z, 1, p.getHeldItem()) && p.worldObj.canMineBlock(p, x, y, z);
    }

    /**
     * Whether the player may place a block at the point, when an item places it itself, bypassing normal placement: spawn protection and adventure
     * mode, and on a hybrid server - a trial Bukkit block place event, which region plugins (WorldGuard, GriefPrevention...) cancel; on plain
     * Forge - a trial Forge place event for claim mods. The block itself is not placed. If the check breaks (another core), placing is refused.
     */
    public static boolean canBuild(EntityPlayer p, int x, int y, int z) {
        if (p == null || !vanillaEdit(p, x, y, z)) return false;
        if (testNoBuild) return false;
        if (p.worldObj.isRemote) return true;
        if (!bukkit()) {
            net.minecraftforge.event.world.BlockEvent.PlaceEvent ev = new net.minecraftforge.event.world.BlockEvent.PlaceEvent(
                net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(p.worldObj, x, y, z), p.worldObj.getBlock(x, y - 1, z), p);
            return !post(ev);
        }
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
            if (placeEvent == null) return false;
            Object world = bukkitWorld.invoke(p.worldObj), block = getBlockAt.invoke(world, x, y, z), player = bukkitEntity.invoke(p);
            Object ev = placeEvent.newInstance(block, getState.invoke(block), getRelative.invoke(block, faceDown), itemInHand.invoke(player), player, true);
            callEvent.invoke(pluginManager, ev);
            if ((Boolean) ev.getClass().getMethod("isCancelled").invoke(ev)) return false;
            return (Boolean) ev.getClass().getMethod("canBuild").invoke(ev); // some plugins forbid through setBuild(false)
        } catch (Throwable t) {
            placeEvent = null;
            if (!warnedBuild) warn("placing blocks by addon items", t);
            warnedBuild = true;
            return false;
        }
    }

    /**
     * Whether the player may remove or replace the block at the point, when the addon does it itself (not by mining): spawn protection and adventure
     * mode, and on a hybrid server - a trial Bukkit block break event, on plain Forge - a trial Forge break event. Air - as placing.
     */
    public static boolean canBreak(EntityPlayer p, int x, int y, int z) {
        if (p == null || !vanillaEdit(p, x, y, z)) return false;
        if (testNoBuild) return false;
        if (p.worldObj.isRemote) return true;
        if (p.worldObj.isAirBlock(x, y, z)) return canBuild(p, x, y, z);
        if (!bukkit()) {
            net.minecraftforge.event.world.BlockEvent.BreakEvent ev = new net.minecraftforge.event.world.BlockEvent.BreakEvent(x, y, z, p.worldObj,
                p.worldObj.getBlock(x, y, z), p.worldObj.getBlockMetadata(x, y, z), p);
            return !post(ev);
        }
        try {
            if (breakEvent == null) {
                breakEvent = Class.forName("org.bukkit.event.block.BlockBreakEvent").getConstructor(Class.forName("org.bukkit.block.Block"),
                    Class.forName("org.bukkit.entity.Player"));
                if (getBlockAt == null) getBlockAt = Class.forName("org.bukkit.World").getMethod("getBlockAt", int.class, int.class, int.class);
            }
            Object world = bukkitWorld.invoke(p.worldObj), block = getBlockAt.invoke(world, x, y, z), player = bukkitEntity.invoke(p);
            Object ev = breakEvent.newInstance(block, player);
            callEvent.invoke(pluginManager, ev);
            return !(Boolean) ev.getClass().getMethod("isCancelled").invoke(ev);
        } catch (Throwable t) {
            if (!warnedBreak) warn("removing blocks by addon items", t);
            warnedBreak = true;
            return false;
        }
    }

    private static final java.util.Map<String, long[]> BUILD = new java.util.HashMap<String, long[]>();

    /** {@link #canBuild} remembered for 2 seconds: for checks every tick (a magnet, a pet collecting items). */
    public static boolean canBuildCached(EntityPlayer p, int x, int y, int z) {
        if (testNoBuild) return false; // the selftest's imitation is not remembered
        MinecraftServer s = MinecraftServer.getServer();
        int now = s == null ? 0 : s.getTickCounter();
        String key = p.getEntityId() + ":" + p.dimension + ":" + x + "," + y + "," + z;
        long[] c = BUILD.get(key);
        if (c != null && now - c[0] >= 0 && now - c[0] < 40) return c[1] != 0;
        boolean r = canBuild(p, x, y, z);
        if (BUILD.size() > 4096) BUILD.clear();
        BUILD.put(key, new long[] { now, r ? 1 : 0 });
        return r;
    }

    /** An item on the ground may be pulled or brought to the player: not someone else's boss loot share, not out of someone else's region. */
    public static boolean mayFetch(EntityPlayer p, net.minecraft.entity.item.EntityItem it) {
        String own = com.divinerpgextended.halite.Halite.owner(it.getEntityItem());
        if (own != null && !own.equals(p.getUniqueID().toString())) return false;
        String vanillaOwner = it.func_145798_i();
        if (vanillaOwner != null && !vanillaOwner.isEmpty() && !vanillaOwner.equals(p.getCommandSenderName()) && it.lifespan - it.age > 200) return false;
        return canBuildCached(p, net.minecraft.util.MathHelper.floor_double(it.posX), net.minecraft.util.MathHelper.floor_double(it.posY),
            net.minecraft.util.MathHelper.floor_double(it.posZ));
    }

    /** Placing a block in place of another one: both. */
    public static boolean canReplace(EntityPlayer p, int x, int y, int z) {
        return canBreak(p, x, y, z) && (p.worldObj.isAirBlock(x, y, z) || canBuild(p, x, y, z));
    }

    /** A trial Forge event: true - cancelled. Addon listeners know it is a probe ({@link #probing}). */
    private static boolean post(cpw.mods.fml.common.eventhandler.Event ev) {
        boolean was = probing;
        probing = true;
        try {
            return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev);
        } finally {
            probing = was;
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

    /** The player responsible for the attacker: the player himself, the owner of a pet or summon, the shooter of a projectile; otherwise null. */
    public static EntityPlayer playerOf(Entity a) {
        for (int i = 0; i < 3 && a != null; i++) {
            if (a instanceof EntityPlayer) return (EntityPlayer) a;
            EntityPlayer pet = com.divinerpgextended.item.ItemPetWhistle.owner(a); // tamed Divine pets (owner by name), ghosts, arcana summons
            if (pet != null) return pet;
            Entity next = null;
            if (a instanceof net.minecraft.entity.IEntityOwnable) {
                Entity o = ((net.minecraft.entity.IEntityOwnable) a).getOwner();
                next = o;
            }
            if (next == null && a instanceof net.minecraft.entity.projectile.EntityThrowable) next = ((net.minecraft.entity.projectile.EntityThrowable) a).getThrower();
            if (next == null && a instanceof net.minecraft.entity.projectile.EntityArrow) next = ((net.minecraft.entity.projectile.EntityArrow) a).shootingEntity;
            if (next == null && a instanceof net.minecraft.entity.projectile.EntityFireball) next = ((net.minecraft.entity.projectile.EntityFireball) a).shootingEntity;
            if (next == null && a instanceof com.divinerpgextended.entity.EntityTomahawk) next = ((com.divinerpgextended.entity.EntityTomahawk) a).owner();
            a = next;
        }
        return null;
    }

    /** Trial hits for one attacker - victim pair are remembered for half a second: one hit asks several times, summons ask every tick. */
    private static final java.util.Map<Long, long[]> HITS = new java.util.HashMap<Long, long[]>();

    /**
     * true - the effect from attacker on victim is forbidden by the region (attacker - a player, his pet, summon or projectile, an armor wearer or null).
     * The region plugin itself decides by a trial hit from the responsible player: it knows the pvp flag for players, the build rights and
     * damage-animals for creatures, the claim trust. Before, creatures were checked only by the pvp flag, which is not set in an ordinary region -
     * the bleeding, fire and poison of addon weapons reached villagers and animals in someone else's region. If the check breaks, the effect
     * is refused.
     */
    public static boolean spare(Entity attacker, Entity victim) {
        if (victim == null || victim.worldObj.isRemote || victim == attacker || !shielded(victim)) return false;
        if (testNoPvp) return true;
        MinecraftServer s = MinecraftServer.getServer();
        if (victim instanceof EntityPlayer && s != null && !s.isPVPEnabled()) return true;
        if (!bukkit()) return false;
        EntityPlayer p = playerOf(attacker);
        if (p == victim) return false;
        long key = (long) (p != null ? p.getEntityId() : attacker != null ? attacker.getEntityId() : -1) << 32 | victim.getEntityId() & 0xFFFFFFFFL;
        int now = s == null ? 0 : s.getTickCounter();
        long[] c = HITS.get(key);
        if (c != null && now - c[0] >= 0 && now - c[0] < 10) return c[1] != 0;
        boolean r;
        try {
            r = noPvpAt(victim) || p != null && cancelled(p, victim);
        } catch (Throwable t) {
            if (!warnedHit) warn("addon weapon effects on players and peaceful creatures", t);
            warnedHit = true;
            r = true;
        }
        if (HITS.size() > 4096) HITS.clear();
        HITS.put(key, new long[] { now, r ? 1 : 0 });
        return r;
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
