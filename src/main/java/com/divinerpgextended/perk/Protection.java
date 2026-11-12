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
 * Приваты с выключенным PvP: эффекты оружия и брони аддона (поджог, яд, замедление, волна, отражение урона) не трогают
 * там игроков и мирных или нейтральных существ (свинозомби и пауки - не в счёт, их бить можно).
 * <p>
 * Эффекты накладываются в LivingHurtEvent, а приват (WorldGuard на гибридном сервере) отменяет урон позже - поэтому
 * спрашиваем сами: PvP выключен на всём сервере, флаг pvp WorldGuard в точке цели или плагин отменил пробный удар
 * игрока по игроку. Без Bukkit и WorldGuard остаётся только настройка сервера.
 */
public final class Protection {

    /** Для автотеста: true - считать, что цель в привате без PvP. */
    public static boolean testNoPvp;
    /** Для автотеста: true - считать, что строить здесь нельзя (чужой приват). */
    public static boolean testNoBuild;
    private static boolean placeChecked;
    private static java.lang.reflect.Constructor<?> placeEvent;
    private static Method getBlockAt, getState, getRelative, itemInHand;
    private static Object faceDown;

    /**
     * Можно ли игроку поставить блок в точке, если вещь ставит его сама, мимо обычной установки: спавн-защита и режим
     * приключения ({@link EntityPlayer#canPlayerEdit}), а на гибридном сервере - пробное событие установки блока Bukkit, его
     * отменяют плагины привата (WorldGuard, GriefPrevention...). Сам блок не ставится.
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

    /** Кого бережёт приват: игроков и не враждебных существ, кроме свинозомби и пауков. */
    public static boolean shielded(Entity victim) {
        if (victim instanceof EntityPlayer) return true;
        if (!(victim instanceof EntityLivingBase) || victim instanceof EntityPigZombie || victim instanceof EntitySpider) return false;
        return !(victim instanceof IMob);
    }

    /** true - эффект от attacker по victim запрещён приватом (attacker - игрок, носитель брони или null). */
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

    /** Флаг pvp WorldGuard в точке существа: deny. */
    private static boolean noPvpAt(Entity e) throws Exception {
        if (pvpFlag == null) return false;
        Object world = bukkitWorld.invoke(e.worldObj);
        Object loc = locationClass.getConstructor(Class.forName("org.bukkit.World"), double.class, double.class, double.class)
            .newInstance(world, e.posX, e.posY, e.posZ);
        if (wgQuery != null) { // WorldGuard 6: RegionQuery.queryState(место, null, PVP)
            Object arr = java.lang.reflect.Array.newInstance(wgQuery.getParameterTypes()[2].getComponentType(), 1);
            java.lang.reflect.Array.set(arr, 0, pvpFlag);
            return wgQuery.invoke(wgQuerySource, loc, null, arr) == deny;
        }
        Object manager = wgManager.invoke(null, world); // WorldGuard 5
        return manager != null && !(Boolean) wgAllows.invoke(wgRegions.invoke(manager, loc), pvpFlag);
    }

    /** Пробный удар без урона: плагины привата (WorldGuard, GriefPrevention...) его отменяют. */
    private static boolean cancelled(Entity attacker, Entity victim) throws Exception {
        Object ev = damageEventClass.getConstructor(entityClass, entityClass, damageCause.getClass(), double.class)
            .newInstance(bukkitEntity.invoke(attacker), bukkitEntity.invoke(victim), damageCause, 0.0D);
        callEvent.invoke(pluginManager, ev);
        return (Boolean) ev.getClass().getMethod("isCancelled").invoke(ev);
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
        try {
            Class<?> wg = Class.forName("com.sk89q.worldguard.bukkit.WGBukkit");
            pvpFlag = Class.forName("com.sk89q.worldguard.protection.flags.DefaultFlag").getField("PVP").get(null);
            Class<?> sf = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            try {
                Object plugin = wg.getMethod("getPlugin").invoke(null);
                Object container = plugin.getClass().getMethod("getRegionContainer").invoke(plugin);
                wgQuerySource = container.getClass().getMethod("createQuery").invoke(container);
                wgQuery = wgQuerySource.getClass().getMethod("queryState", locationClass,
                    Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable"), java.lang.reflect.Array.newInstance(sf, 0).getClass());
                deny = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State").getField("DENY").get(null);
            } catch (Throwable old) {
                wgQuery = null;
                wgManager = wg.getMethod("getRegionManager", Class.forName("org.bukkit.World"));
                wgRegions = wgManager.getReturnType().getMethod("getApplicableRegions", locationClass);
                wgAllows = wgRegions.getReturnType().getMethod("allows", sf);
            }
            com.divinerpgextended.DivineRPGExtended.LOG.info("Приваты: эффекты оружия сверяются с флагом pvp WorldGuard");
        } catch (Throwable t) {
            pvpFlag = null;
        }
        return true;
    }
}
