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
 * Арена босса - как у Стража Гайи в Botania:
 * <ul>
 * <li>Вызов не в логове - только на ровной свободной площадке ({@link #clearField}); в логове место задаёт постройка,
 * проверяем лишь, что точку появления босса не застроили ({@link #roomFor}).</li>
 * <li>Бойцы - игроки (не в творческом режиме), побывавшие на арене при живом боссе. У края арены бойца отталкивает
 * обратно к центру ({@link Config#bossArenaHold}); телепорт, смерть, выход из игры - уход с арены.</li>
 * <li>На арене не осталось бойцов - босс ждёт {@link Config#bossArenaLeaveSeconds} секунд и исчезает без трофеев
 * (0 - сразу, как Гайа; -1 - не исчезает). Кто-то вернулся - ожидание сбрасывается. Время ухода хранится в боссе,
 * так что уйти, пока чанк не выгрузится, и вернуться потом тоже не выйдет.</li>
 * </ul>
 * Арена - привязь логова ({@link LairSummons#home}) с запасом {@link #MARGIN}, иначе круг {@link Config#bossArenaRadius}
 * вокруг места появления; босс, появившийся на чужой арене (Король-лич из Колосса), остаётся на ней. Полёт рядом с
 * боссом выключает {@link BossNoFly}. Королева улья не в счёт: у неё свой сброс боя ({@link com.divinerpgextended.vethea.HiveTimer}).
 */
public class BossArena {

    /** Запас арены за привязью логова: босс держится в привязи, бойцы - на всей площадке. */
    public static final int MARGIN = 6;
    /** Ширина кольца за краем арены, где бойца тянет обратно; дальше - он ушёл (телепорт, жемчуг). */
    public static final int PULL = 8;
    /** Высота арены от пола: ниже (этажи башни Демона) и выше (пилоны Трона). */
    public static final int BELOW = 16, ABOVE = 40;
    /** Вызов вне логова: высота площадки над полом и допустимая доля застроенных колонн. */
    public static final int FIELD_H = 4;
    public static final double FIELD_CLUTTER = 0.1;

    public static final String ARENA = "dreArena", ON = "dreArenaOn", EMPTY = "dreArenaEmpty", TOLD = "dreArenaTold";

    /** Арена живого босса: где, кто бьётся. */
    public static final class Arena {
        public final int dim;
        public int cx, floor, cz, r;
        public final Set<UUID> fighters = new HashSet<UUID>();
        long seen;
        /** Бой начат: на арене побывал боец. */
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

    /** Босс, у которого есть арена: боссы Divine и аддона, кроме Королевы улья. */
    public static boolean applies(Entity e) {
        if (!(e instanceof EntityLiving) || e instanceof net.divinerpg.entities.vethea.EntityHiveQueen) return false;
        if (!(e instanceof IBossDisplayData || e instanceof net.divinerpg.entities.base.EntityDivineRPGBoss)) return false;
        String cls = e.getClass().getName();
        return cls.startsWith("net.divinerpg.") || cls.startsWith("com.divinerpgextended.");
    }

    /** Задать арену явно (вызов на алтаре, когда босс встаёт не в центре зала). */
    public static void set(Entity boss, int cx, int floor, int cz, int r) {
        boss.getEntityData().setIntArray(ARENA, new int[] { cx, floor, cz, r });
    }

    /** Арена босса {x, пол, z, радиус}: привязь логова с запасом, заданная, унаследованная или вокруг места появления. */
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

    /** Живая арена, на которой стоит сущность, или null. */
    public static Arena at(Entity e) {
        long now = e.worldObj.getTotalWorldTime();
        for (Arena a : ACTIVE.values()) {
            if (now - a.seen <= 40 && now >= a.seen && a.inside(e)) return a;
        }
        return null;
    }

    /** Бойцы на арене сейчас: живые игроки на сервере, не в творческом режиме. */
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

    /** Проверка арены (раз в полсекунды): кто на арене, не пора ли боссу уйти. */
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
            long told = d.getLong(TOLD); // топчется на границе - не чаще раза в 10 секунд
            if (Config.bossArenaLeaveSeconds > 0 && (!d.hasKey(TOLD) || now - told > 200 || now < told)) {
                d.setLong(TOLD, now);
                tell(a, boss, new ChatComponentTranslation("dre.chat.arena_empty", a.name, Config.bossArenaLeaveSeconds));
            }
        }
        long since = d.getLong(EMPTY);
        if (now < since) d.setLong(EMPTY, since = now); // время мира откатили
        if (now - since >= Config.bossArenaLeaveSeconds * 20L) despawn(boss, a);
    }

    private static void sweep(long now) {
        for (Iterator<Arena> it = ACTIVE.values().iterator(); it.hasNext();) {
            Arena a = it.next();
            if (now - a.seen > 200 || now < a.seen - 200) it.remove();
        }
    }

    /** Босс уходит: дым, звук, весть бойцам. Без трофеев и без засчитанной победы. */
    public static void despawn(EntityLiving boss, Arena a) {
        if (boss.worldObj instanceof WorldServer) {
            ((WorldServer) boss.worldObj).func_147487_a("largesmoke", boss.posX, boss.posY + boss.height / 2, boss.posZ, 60, boss.width / 2,
                boss.height / 2, boss.width / 2, 0.02);
        }
        boss.worldObj.playSoundEffect(boss.posX, boss.posY, boss.posZ, "mob.endermen.portal", 2.0F, 0.5F);
        if (a != null) tell(a, boss, new ChatComponentTranslation("dre.chat.arena_gone", a.name));
        com.divinerpgextended.debug.Debug.note(boss, "На арене не осталось бойцов - босс ушёл");
        boss.setDead();
        ACTIVE.remove(boss.getEntityId());
    }

    /** Имя босса для чата - ключом перевода, чтобы клиент показал его на своём языке. */
    public static net.minecraft.util.IChatComponent name(Entity boss) {
        Boss b = Boss.of(boss);
        String key = b != null ? b.langKey() : "entity." + net.minecraft.entity.EntityList.getEntityString(boss) + ".name";
        return new ChatComponentTranslation(key);
    }

    /** Весть бойцам этой арены, где бы они ни были. */
    private static void tell(Arena a, Entity boss, ChatComponentTranslation msg) {
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (a.fighters.contains(p.getUniqueID())) p.addChatMessage(msg);
        }
    }

    // ---------------------------------------------------------------- удержание на арене

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote || !Config.bossArenaHold || ACTIVE.isEmpty()) return;
        hold(e.player);
    }

    /** Боец у края арены - толчок к центру. true - толкнули. */
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

    // ---------------------------------------------------------------- место для вызова

    /** Босс поместится: в его рамке нет блоков (залы логов тесные по задумке - без запаса). */
    public static boolean roomFor(EntityLiving boss) {
        return boss.worldObj.getCollidingBoundingBoxes(boss, boss.boundingBox).isEmpty();
    }

    /** Доля застроенных колонн в круге r вокруг (x, z) на высоте y..y+FIELD_H-1 (0..1). */
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

    /** Вызов вне логова: вокруг точки (пол - y) ровная свободная площадка, как у Гайи. */
    public static boolean clearField(World w, int x, int y, int z) {
        return Config.bossSummonClearRadius <= 0 || clutter(w, x, y, z, Config.bossSummonClearRadius) <= FIELD_CLUTTER;
    }

    /**
     * Предметы вызова Divine вне логова (логова выключены или вызов где угодно): нужна свободная площадка. В логове
     * предмет проверяет {@link LairSummons}, комнаты Ветеи и Арканы - постройки Divine, их не трогаем.
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

    /** Предметы вызова боссов Divine, которые ставят босса у точки клика. */
    public static boolean summonItem(Item i) {
        return i instanceof net.divinerpg.items.twilight.ItemTwilightSpawner || i instanceof net.divinerpg.items.vanilla.ItemCallOfTheWatcher
            || i instanceof net.divinerpg.items.vanilla.ItemInfernalFlame || i instanceof net.divinerpg.items.vanilla.ItemHordeHorn;
    }
}
