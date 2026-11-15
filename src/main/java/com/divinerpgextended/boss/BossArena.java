package com.divinerpgextended.boss;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Boss arena:
 * <ul>
 * <li>A summon outside a lair - only on a flat free area ({@link #clearField}); in a lair the place is set by the structure,
 * we only check that the boss spawn point has not been built over ({@link #roomFor}).</li>
 * <li>Fighters are players (not in creative mode) who have been in the arena while the boss is alive. At the arena edge a fighter is pushed
 * back to the center ({@link Config#bossArenaHold}); teleport, death, leaving the game - leaving the arena.</li>
 * <li>No fighters left in the arena - the boss waits {@link Config#bossArenaLeaveSeconds} seconds and vanishes without trophies
 * (0 - at once; -1 - does not vanish). Someone came back - the wait resets. The leave time is stored in the boss,
 * so leaving until the chunk unloads and coming back later does not work either.</li>
 * </ul>
 * The arena is the lair leash ({@link LairSummons#home}) with a margin {@link #MARGIN}, otherwise a circle of {@link Config#bossArenaRadius}
 * around the spawn point; a boss that appeared in someone else's arena (the Lich King from the Colossus) stays in it. Flight near
 * the boss is disabled by {@link BossNoFly}. The Hive Queen does not count: she has her own fight reset ({@link com.divinerpgextended.vethea.HiveTimer}).
 */
public class BossArena {

    /** Arena margin beyond the lair leash: the boss stays in the leash, fighters on the whole area. */
    public static final int MARGIN = 6;
    /** Width of the ring beyond the arena edge where a fighter is pulled back; further - he has left (teleport, pearl). */
    public static final int PULL = 8;
    /** Arena height from the floor: below (floors of the Demon tower) and above (Throne pylons). */
    public static final int BELOW = 16, ABOVE = 40;
    /** Summon outside a lair: height of the area above the floor and the allowed share of built-over columns. */
    public static final int FIELD_H = 4;
    public static final double FIELD_CLUTTER = 0.1;

    public static final String ARENA = "dreArena", ON = "dreArenaOn", EMPTY = "dreArenaEmpty", TOLD = "dreArenaTold";

    /** Arena of a living boss: where, who is fighting. */
    public static final class Arena {
        public final int dim;
        public int cx, floor, cz, r;
        public final Set<UUID> fighters = new HashSet<UUID>();
        long seen;
        /** Fight started: a fighter has been in the arena. */
        boolean on;
        final net.minecraft.util.IChatComponent name;

        Arena(int dim, net.minecraft.util.IChatComponent name) {
            this.dim = dim;
            this.name = name;
        }

        public boolean inside(Entity e) {
            return e.dimension == dim && e.posY >= floor - BELOW && e.posY <= floor + ABOVE && dist(e) <= r;
        }

        double dist(Entity e) {
            double dx = e.posX - cx - 0.5, dz = e.posZ - cz - 0.5;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    private static final Map<Integer, Arena> ACTIVE = new ConcurrentHashMap<Integer, Arena>();

    /** A boss that has an arena: Divine and addon bosses, except the Hive Queen. */
    public static boolean applies(Entity e) {
        if (!(e instanceof EntityLiving) || e instanceof net.divinerpg.entities.vethea.EntityHiveQueen) return false;
        if (!(e instanceof IBossDisplayData || e instanceof net.divinerpg.entities.base.EntityDivineRPGBoss)) return false;
        String cls = e.getClass().getName();
        return cls.startsWith("net.divinerpg.") || cls.startsWith("com.divinerpgextended.");
    }

    /** Set the arena explicitly (summon at an altar, when the boss stands not in the hall center). */
    public static void set(Entity boss, int cx, int floor, int cz, int r) {
        boss.getEntityData().setIntArray(ARENA, new int[] { cx, floor, cz, r });
    }

    /** Boss arena {x, floor, z, radius}: lair leash with a margin, set, inherited, or around the spawn point. */
    public static int[] bounds(Entity boss) {
        int[] h = LairSummons.home(boss);
        if (h != null) return new int[] { h[0], h[1], h[2], h[3] + MARGIN };
        NBTTagCompound d = boss.getEntityData();
        int[] a = d.getIntArray(ARENA);
        if (a.length == 4) return a;
        Arena host = at(boss);
        if (host != null) {
            a = new int[] { host.cx, host.floor, host.cz, host.r };
            if (host.on) d.setBoolean(ON, true);
        } else {
            a = new int[] { (int) Math.floor(boss.posX), (int) Math.floor(boss.posY), (int) Math.floor(boss.posZ), Config.bossArenaRadius };
        }
        d.setIntArray(ARENA, a);
        return a;
    }

    /** The living arena the entity stands in, or null. */
    public static Arena at(Entity e) {
        long now = e.worldObj.getTotalWorldTime();
        for (Arena a : ACTIVE.values()) {
            if (now - a.seen <= 40 && now >= a.seen && a.inside(e)) return a;
        }
        return null;
    }

    /** Fighters in the arena now: live players on the server, not in creative mode. */
    private static int countInside(Arena a, long now) {
        int n = 0;
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.capabilities.isCreativeMode || !p.isEntityAlive() || !a.inside(p)) continue;
            a.fighters.add(p.getUniqueID());
            n++;
        }
        return n;
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        EntityLiving boss = e.entityLiving instanceof EntityLiving ? (EntityLiving) e.entityLiving : null;
        if (boss == null || boss.worldObj.isRemote || boss.ticksExisted % 10 != 0 || !boss.isEntityAlive() || !applies(boss)) return;
        tick(boss);
    }

    /** Arena check (every half second): who is in the arena, whether it is time for the boss to leave. */
    public static void tick(EntityLiving boss) {
        long now = boss.worldObj.getTotalWorldTime();
        int[] b = bounds(boss);
        Arena a = ACTIVE.get(boss.getEntityId());
        if (a == null || a.dim != boss.dimension) ACTIVE.put(boss.getEntityId(), a = new Arena(boss.dimension, name(boss)));
        a.cx = b[0];
        a.floor = b[1];
        a.cz = b[2];
        a.r = b[3];
        a.seen = now;
        sweep(now);

        NBTTagCompound d = boss.getEntityData();
        a.on = d.getBoolean(ON);
        if (countInside(a, now) > 0) {
            d.setBoolean(ON, a.on = true);
            d.removeTag(EMPTY);
            return;
        }
        if (!d.getBoolean(ON) || Config.bossArenaLeaveSeconds < 0) return;
        if (!d.hasKey(EMPTY)) {
            d.setLong(EMPTY, now);
            long told = d.getLong(TOLD); // pacing at the border - no more than once per 10 seconds
            if (Config.bossArenaLeaveSeconds > 0 && (!d.hasKey(TOLD) || now - told > 200 || now < told)) {
                d.setLong(TOLD, now);
                tell(a, boss, new ChatComponentTranslation("dre.chat.arena_empty", a.name, Config.bossArenaLeaveSeconds));
            }
        }
        long since = d.getLong(EMPTY);
        if (now < since) d.setLong(EMPTY, since = now); // world time was rolled back
        if (now - since >= Config.bossArenaLeaveSeconds * 20L) despawn(boss, a);
    }

    private static void sweep(long now) {
        for (Iterator<Arena> it = ACTIVE.values().iterator(); it.hasNext();) {
            Arena a = it.next();
            if (now - a.seen > 200 || now < a.seen - 200) it.remove();
        }
    }

    /** The boss leaves: smoke, sound, a message to the fighters. Without trophies and without a counted victory. */
    public static void despawn(EntityLiving boss, Arena a) {
        if (boss.worldObj instanceof WorldServer) {
            ((WorldServer) boss.worldObj).func_147487_a("largesmoke", boss.posX, boss.posY + boss.height / 2, boss.posZ, 60, boss.width / 2,
                boss.height / 2, boss.width / 2, 0.02);
        }
        boss.worldObj.playSoundEffect(boss.posX, boss.posY, boss.posZ, "mob.endermen.portal", 2.0F, 0.5F);
        if (a != null) tell(a, boss, new ChatComponentTranslation("dre.chat.arena_gone", a.name));
        com.divinerpgextended.debug.Debug.note(boss, "No fighters left on the arena - the boss left");
        boss.setDead();
        ACTIVE.remove(boss.getEntityId());
    }

    /** Boss name for chat - as a translation key, so the client shows it in its own language. */
    public static net.minecraft.util.IChatComponent name(Entity boss) {
        Boss b = Boss.of(boss);
        String key = b != null ? b.langKey() : "entity." + net.minecraft.entity.EntityList.getEntityString(boss) + ".name";
        return new ChatComponentTranslation(key);
    }

    /** A message to this arena's fighters, wherever they are. */
    private static void tell(Arena a, Entity boss, ChatComponentTranslation msg) {
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (a.fighters.contains(p.getUniqueID())) p.addChatMessage(msg);
        }
    }

    // ---------------------------------------------------------------- holding in the arena

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote || !Config.bossArenaHold || ACTIVE.isEmpty()) return;
        hold(e.player);
    }

    /** A fighter at the arena edge - a push toward the center. true - pushed. */
    public static boolean hold(EntityPlayer p) {
        if (p.capabilities.isCreativeMode || !p.isEntityAlive()) return false;
        long now = p.worldObj.getTotalWorldTime();
        for (Arena a : ACTIVE.values()) {
            if (a.dim != p.dimension || now - a.seen > 40 || !a.fighters.contains(p.getUniqueID())) continue;
            if (p.posY < a.floor - BELOW || p.posY > a.floor + ABOVE) continue;
            double d = a.dist(p);
            if (d <= a.r - 0.5 || d > a.r + PULL) continue;
            double dx = a.cx + 0.5 - p.posX, dz = a.cz + 0.5 - p.posZ;
            p.motionX = dx / d * 0.7;
            p.motionZ = dz / d * 0.7;
            if (p.onGround) p.motionY = 0.3;
            p.fallDistance = 0;
            p.velocityChanged = true;
            long told = p.getEntityData().getLong(TOLD);
            if (!p.getEntityData().hasKey(TOLD) || now - told > 100 || now < told) {
                p.getEntityData().setLong(TOLD, now);
                p.addChatMessage(new ChatComponentTranslation("dre.chat.arena_hold", a.name));
            }
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- place for the summon

    /** The boss will fit: there are no blocks in its frame (lair halls are tight by design - without margin). */
    public static boolean roomFor(EntityLiving boss) {
        return boss.worldObj.getCollidingBoundingBoxes(boss, boss.boundingBox).isEmpty();
    }

    /** Share of built-over columns in circle r around (x, z) at height y..y+FIELD_H-1 (0..1). */
    public static double clutter(World w, int x, int y, int z, int r) {
        int all = 0, busy = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                all++;
                for (int dy = 0; dy < FIELD_H; dy++) {
                    Block b = w.getBlock(x + dx, y + dy, z + dz);
                    if (b.getCollisionBoundingBoxFromPool(w, x + dx, y + dy, z + dz) != null) {
                        busy++;
                        break;
                    }
                }
            }
        }
        return all == 0 ? 0 : (double) busy / all;
    }

    /** Summon outside a lair: around the point (floor - y) there is a flat free area. */
    public static boolean clearField(World w, int x, int y, int z) {
        return Config.bossSummonClearRadius <= 0 || clutter(w, x, y, z, Config.bossSummonClearRadius) <= FIELD_CLUTTER;
    }

    /**
     * Divine summon items outside a lair (lairs disabled or summon anywhere): a free area is needed. In a lair
     * the item is checked by {@link LairSummons}, Vethea and Arcana rooms are Divine structures, we do not touch them.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote) return;
        ItemStack held = e.entityPlayer.getHeldItem();
        if (held == null || !summonItem(held.getItem()) || LairSummons.inLair(e.entityPlayer, held.getItem(), e.x, e.y, e.z)) return;
        if (e.world.getBlock(e.x, e.y, e.z) instanceof com.divinerpgextended.block.BlockBossAltar) return;
        if (clearField(e.world, e.x, e.y + 1, e.z)) return;
        e.useItem = Event.Result.DENY;
        e.setCanceled(true);
        e.entityPlayer.addChatMessage(new ChatComponentTranslation("dre.chat.summon_space", Config.bossSummonClearRadius));
    }

    /** Divine boss summon items that place the boss at the click point. */
    public static boolean summonItem(Item i) {
        return i instanceof net.divinerpg.items.twilight.ItemTwilightSpawner || i instanceof net.divinerpg.items.vanilla.ItemCallOfTheWatcher
            || i instanceof net.divinerpg.items.vanilla.ItemInfernalFlame || i instanceof net.divinerpg.items.vanilla.ItemHordeHorn;
    }
}
