package com.divinerpgextended.boss;

import net.divinerpg.items.twilight.ItemTwilightSpawner;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.block.BlockBossAltar;
import com.divinerpgextended.world.WorldGenOuterLairs;
import com.divinerpgextended.world.twilight.WorldGenNests;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Боссов вызывают в их логовах, а не где угодно (summonOnlyInLairs). Предметы вызова Divine не трогаем - отменяем их
 * применение вне логова, клик по алтарю логова при этом работает (алтарь сам принимает предмет).
 * <ul>
 * <li>Кристаллы сумеречных боссов (Сумеречный демон, Карот, Вечный лучник, Денсос и Рейвор, Пожиратель душ,
 * Вамачерон) - только на своей арене Мортума ({@link com.divinerpgextended.world.twilight.MortumArenas}).</li>
 * <li>Таинственные часы (Древняя сущность) - только на алтаре гробницы.</li>
 * <li>Зов Смотрителя, Инфернальное пламя, Рог орды Айрако - только в своих логовах в Аду и Энде
 * ({@link WorldGenOuterLairs}).</li>
 * </ul>
 * Правила одни для всех, и в творческом режиме тоже (иначе при проверках боссы оказывались где попало).
 */
public class LairSummons {

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote || !Config.summonOnlyInLairs) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getHeldItem();
        if (held == null) return;
        if (e.world.getBlock(e.x, e.y, e.z) instanceof BlockBossAltar) return; // алтарь решает сам
        String refuse = refusal(p, held.getItem(), e.x, e.y, e.z);
        if (refuse == null) {
            // большие боссы арен: кристалл ставит их на свободное место арены, а не впритык к алтарю
            com.divinerpgextended.world.twilight.MortumArenas.Type t = held.getItem() instanceof ItemTwilightSpawner
                ? com.divinerpgextended.world.twilight.MortumArenas.ofCrystal(held.getItem()) : null;
            int[] c = t == null ? null : com.divinerpgextended.world.twilight.MortumArenas.nearest(e.world, e.x, e.z);
            int[] s = c == null ? null : com.divinerpgextended.world.twilight.MortumArenas.summonBlock(e.world, t, c);
            if (s != null && (s[0] != e.x || s[1] != e.y || s[2] != e.z)) {
                e.useItem = Event.Result.DENY;
                held.getItem().onItemUse(held, p, e.world, s[0], s[1], s[2], 1, 0.5F, 1.0F, 0.5F);
            }
            return;
        }
        e.useItem = Event.Result.DENY;
        p.addChatMessage(new ChatComponentTranslation(refuse));
        ((net.minecraft.world.WorldServer) e.world).func_147487_a("smoke", e.x + 0.5, e.y + 1.2, e.z + 0.5, 12, 0.2, 0.2, 0.2, 0.02);
        e.world.playSoundEffect(e.x + 0.5, e.y + 1, e.z + 0.5, "random.fizz", 0.6F, 1.4F); // кристалл гаснет
    }

    // ---------------------------------------------------------------- привязь к логову

    private static final String HOME = "dreLairHome";

    /** Дом логова босса [x, пол, z, радиус] или null. */
    public static int[] home(net.minecraft.entity.Entity boss) {
        int[] h = boss.getEntityData().getIntArray(HOME);
        return h.length == 4 ? h : null;
    }

    /** Босс вызван в логове над пропастью: не уходит за край (center, радиус, пол). */
    public static void leash(net.minecraft.entity.Entity boss, int cx, int floorY, int cz, int radius) {
        if (!boss.worldObj.isRemote) ArenaCalm.clear(boss.worldObj, cx, floorY, cz, radius);
        boss.getEntityData().setIntArray(HOME, new int[] { cx, floorY, cz, radius });
    }

    /** Боссы, вызванные кристаллом на арене Мортума, - на привязь к арене (лучник - сразу на свой помост). */
    @SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.minecraft.entity.EntityLiving) || e.entity.getEntityData().hasKey(HOME)) return;
        if (!(e.entity instanceof net.minecraft.entity.boss.IBossDisplayData || e.entity instanceof net.divinerpg.entities.base.EntityDivineRPGBoss
            || e.entity instanceof net.divinerpg.entities.vanilla.EntityAyeraco)) return;
        if (com.divinerpgextended.world.twilight.TwilightWorld.of(e.world.provider.dimensionId) == com.divinerpgextended.world.twilight.TwilightWorld.MORTUM) {
            int[] c = WorldGenNests.nearest(e.world.getSeed(), e.world.provider.dimensionId, e.entity.posX, e.entity.posZ);
            if (c != null) {
                com.divinerpgextended.world.twilight.MortumArenas.Type t = com.divinerpgextended.world.twilight.MortumArenas.typeAt(e.world, c);
                if (com.divinerpgextended.world.twilight.MortumArenas.inArena(e.world, t, e.entity.posX, e.entity.posY, e.entity.posZ)) {
                    int floor = com.divinerpgextended.world.twilight.MortumArenas.floorY(t) + 1;
                    leash(e.entity, c[0], floor, c[2], t.leash);
                    int[] spot = com.divinerpgextended.world.twilight.MortumArenas.bossSpot(t);
                    if (spot != null) e.entity.setPosition(c[0] + 0.5, floor + spot[1], c[2] + spot[2] + 0.5);
                    return;
                }
            }
        }
        for (int type = 0; type < 3; type++) {
            if (!WorldGenOuterLairs.inside(e.world, type, e.entity.posX, e.entity.posY, e.entity.posZ)) continue;
            int[] c = WorldGenOuterLairs.nearest(e.world.getSeed(), type, e.entity.posX, e.entity.posZ);
            if (type == WorldGenOuterLairs.WATCHER && e.entity instanceof net.divinerpg.entities.vanilla.EntityTheWatcher) {
                // логово старого вида (с завалом) - сначала в новое; сам Смотритель - над оком в центре
                com.divinerpgextended.boss.WatcherHunt.upgrade(e.world, c);
                e.entity.setPosition(c[0] + 0.5, com.divinerpgextended.world.WatcherVault.bossY() - e.entity.height / 2, c[2] + 0.5);
            }
            // орда кружит над всем островом (круг шире острова не бывает) - привязь шире зала
            leash(e.entity, c[0], WorldGenOuterLairs.FLOOR[type] + 1, c[2], WorldGenOuterLairs.RADIUS[type] + (type == WorldGenOuterLairs.AYERACO ? 6 : -3));
            return;
        }
    }

    @SubscribeEvent
    public void onUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent e) {
        net.minecraft.entity.EntityLivingBase m = e.entityLiving;
        if (m.worldObj.isRemote || m.ticksExisted % 2 != 0) return;
        int[] h = m.getEntityData().getIntArray(HOME);
        if (h.length != 4) return;
        if (m.ticksExisted % 20 == 0 && m.isEntityAlive()) ArenaCalm.mark(m, h);
        double dx = m.posX - h[0] - 0.5, dz = m.posZ - h[2] - 0.5, d = Math.sqrt(dx * dx + dz * dz);
        if (d <= h[3] && m.posY >= h[1] - 2) return;
        if (d <= h[3] + 6 && m.posY >= h[1] - 2) {
            // чуть вышел за край - разворачиваем к центру, без телепорта
            if (m instanceof net.minecraft.entity.EntityLiving) ((net.minecraft.entity.EntityLiving) m).getNavigator().clearPathEntity();
            m.motionX = -dx / d * 0.45;
            m.motionZ = -dz / d * 0.45;
            m.velocityChanged = true;
            return;
        }
        m.fallDistance = 0;
        m.motionX = m.motionY = m.motionZ = 0;
        m.setPositionAndUpdate(h[0] + 0.5, h[1] + 0.5, h[2] + 0.5);
        com.divinerpgextended.debug.Debug.note(m, "Босс ушёл за край логова - вернулся в центр");
    }

    /** Вызов предметом идёт в его логове (Демонический круг и арены Мортума, логова Ада и Энда). */
    public static boolean inLair(EntityPlayer p, Item item, int x, int y, int z) {
        if (item instanceof ItemTwilightSpawner) {
            com.divinerpgextended.world.twilight.MortumArenas.Type t = com.divinerpgextended.world.twilight.MortumArenas.ofCrystal(item);
            return t == null ? WorldGenNests.inCircle(p.worldObj, x + 0.5, y + 1, z + 0.5)
                : com.divinerpgextended.world.twilight.MortumArenas.inArena(p.worldObj, t, x + 0.5, y + 1, z + 0.5);
        }
        int type = item instanceof net.divinerpg.items.vanilla.ItemCallOfTheWatcher ? WorldGenOuterLairs.WATCHER
            : item instanceof net.divinerpg.items.vanilla.ItemInfernalFlame ? WorldGenOuterLairs.SCORCHERS
                : item instanceof net.divinerpg.items.vanilla.ItemHordeHorn ? WorldGenOuterLairs.AYERACO : -1;
        return type >= 0 && WorldGenOuterLairs.enabled(type) && WorldGenOuterLairs.inside(p.worldObj, type, x + 0.5, y + 1, z + 0.5);
    }

    /** Ключ отказа или null, если здесь вызывать можно. */
    public static String refusal(EntityPlayer p, Item item, int x, int y, int z) {
        if (item instanceof ItemTwilightSpawner) {
            com.divinerpgextended.world.twilight.MortumArenas.Type t = com.divinerpgextended.world.twilight.MortumArenas.ofCrystal(item);
            if (t == null) return WorldGenNests.inCircle(p.worldObj, x + 0.5, y + 1, z + 0.5) ? null : "dre.chat.summon_circle";
            if (!com.divinerpgextended.world.twilight.MortumArenas.inArena(p.worldObj, t, x + 0.5, y + 1, z + 0.5)) return "dre.chat.summon_arena_" + t.key();
            return p.worldObj.getBlock(x, y, z) == com.divinerpgextended.registry.ModBlocks.arenaAltar ? null : "dre.chat.summon_altar";
        }
        if (item == VanillaItemsOther.mysteriousClock) return "dre.chat.summon_tomb";
        int type = item instanceof net.divinerpg.items.vanilla.ItemCallOfTheWatcher ? WorldGenOuterLairs.WATCHER
            : item instanceof net.divinerpg.items.vanilla.ItemInfernalFlame ? WorldGenOuterLairs.SCORCHERS
                : item instanceof net.divinerpg.items.vanilla.ItemHordeHorn ? WorldGenOuterLairs.AYERACO : -1;
        if (type < 0 || !WorldGenOuterLairs.enabled(type)) return null;
        return WorldGenOuterLairs.inside(p.worldObj, type, x + 0.5, y + 1, z + 0.5) ? null : "dre.chat.summon_lair_" + type;
    }
}
