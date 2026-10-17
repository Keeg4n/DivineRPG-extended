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
 * Вамачерон) - только внутри Демонического круга Мортума.</li>
 * <li>Таинственные часы (Древняя сущность) - только на алтаре гробницы.</li>
 * <li>Зов Смотрителя, Инфернальное пламя, Рог орды Айрако - только в своих логовах в Аду и Энде
 * ({@link WorldGenOuterLairs}).</li>
 * </ul>
 * Творческий режим не ограничен (админам для проверок).
 */
public class LairSummons {

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote || !Config.summonOnlyInLairs) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getHeldItem();
        if (held == null || p.capabilities.isCreativeMode) return;
        if (e.world.getBlock(e.x, e.y, e.z) instanceof BlockBossAltar) return; // алтарь решает сам
        String refuse = refusal(p, held.getItem(), e.x, e.y, e.z);
        if (refuse == null) return;
        e.useItem = Event.Result.DENY;
        p.addChatMessage(new ChatComponentTranslation(refuse));
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

    /** Боссы, вызванные кристаллом внутри Демонического круга, - на привязь к кругу. */
    @SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.minecraft.entity.EntityLiving) || e.entity.getEntityData().hasKey(HOME)) return;
        if (!(e.entity instanceof net.minecraft.entity.boss.IBossDisplayData || e.entity instanceof net.divinerpg.entities.base.EntityDivineRPGBoss
            || e.entity instanceof net.divinerpg.entities.vanilla.EntityAyeraco)) return;
        if (WorldGenNests.inCircle(e.world, e.entity.posX, e.entity.posY, e.entity.posZ)) {
            int[] c = WorldGenNests.nearest(e.world.getSeed(), e.world.provider.dimensionId, e.entity.posX, e.entity.posZ);
            leash(e.entity, c[0], WorldGenNests.TOP + 1, c[2], WorldGenNests.CIRCLE_R - 2);
            return;
        }
        for (int type = 0; type < 3; type++) {
            if (!WorldGenOuterLairs.inside(e.world, type, e.entity.posX, e.entity.posY, e.entity.posZ)) continue;
            int[] c = WorldGenOuterLairs.nearest(e.world.getSeed(), type, e.entity.posX, e.entity.posZ);
            leash(e.entity, c[0], WorldGenOuterLairs.FLOOR[type] + 1, c[2], WorldGenOuterLairs.RADIUS[type] - 3);
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

    /** Ключ отказа или null, если здесь вызывать можно. */
    public static String refusal(EntityPlayer p, Item item, int x, int y, int z) {
        if (item instanceof ItemTwilightSpawner) {
            return WorldGenNests.inCircle(p.worldObj, x + 0.5, y + 1, z + 0.5) ? null : "dre.chat.summon_circle";
        }
        if (item == VanillaItemsOther.mysteriousClock) return "dre.chat.summon_tomb";
        int type = item instanceof net.divinerpg.items.vanilla.ItemCallOfTheWatcher ? WorldGenOuterLairs.WATCHER
            : item instanceof net.divinerpg.items.vanilla.ItemInfernalFlame ? WorldGenOuterLairs.SCORCHERS
                : item instanceof net.divinerpg.items.vanilla.ItemHordeHorn ? WorldGenOuterLairs.AYERACO : -1;
        if (type < 0 || !WorldGenOuterLairs.enabled(type)) return null;
        return WorldGenOuterLairs.inside(p.worldObj, type, x + 0.5, y + 1, z + 0.5) ? null : "dre.chat.summon_lair_" + type;
    }
}
