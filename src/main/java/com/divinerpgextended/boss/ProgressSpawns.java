package com.divinerpgextended.boss;

import net.minecraft.util.MathHelper;

import java.util.HashMap;
import java.util.Map;

import net.divinerpg.entities.vanilla.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Мобы Divine в обычном мире, аду и Энде открываются по рангу игрока.
 * <ul>
 * <li>Ранг 0 (начало): только крабы.</li>
 * <li>Ранг 1 (убит Карцинус): в аду Wildfire и адские пауки, в снегах Глаконы и Фросты, в океане акулы, киты и
 * лиоплевродоны.</li>
 * <li>Ранг 2 (убит Король-лич): остальные мобы Divine этих миров (пустыня, джунгли, равнины, лес, подземелья,
 * ад).</li>
 * <li>Ранг 3 (убита Древняя сущность): мобы Энда - эндер-пауки, тройняшки и эндер-смотрители (с них глаз для
 * призыва Смотрителя, а Смотритель открывает Аркану).</li>
 * </ul>
 * Моб появляется, только если у всех игроков, вокруг которых игра вообще спавнит мобов (8 чанков от чанка игрока -
 * до ~180 блоков по диагонали; раньше было 128 блоков, и мобы рождались за этим кругом и приходили), ранг не ниже нужного - решает самый слабый, чтобы тот,
 * кто не качает Divine, не страдал из-за соседа. Если моб уже есть, а рядом игрок ниже рангом, моб его не трогает.
 * Игрок может такого моба убить (чтобы не мешал, если забрёл к нему домой), но дроп и опыт с него не падают
 * (lowRankNoDrops в конфиге) - бесплатного фарма не до своей ступени нет.
 * Мирные мобы и торговцы Divine, боссы с призыва и Iceika это не затрагивает.
 */
public class ProgressSpawns {

    private static final Map<Class<?>, Integer> TIER = new HashMap<Class<?>, Integer>();

    static {
        // ранг 1: Карцинус повержен
        for (Class<?> c : new Class<?>[] { EntityWildfire.class, EntityHellSpider.class, EntityGlacon.class,
            EntityFrost.class, EntityShark.class, EntityWhale.class, EntityLiopleurodon.class }) {
            TIER.put(c, 1);
        }
        // ранг 2: Король-лич повержен
        for (Class<?> c : new Class<?>[] { EntityDesertCrawler.class, EntityAridWarrior.class, EntitySaguaroWorm.class,
            EntityJungleBat.class, EntityJungleSpider.class, EntityJungleDramcryx.class, EntityKobblin.class,
            EntityCyclops.class, EntityPumpkinSpider.class, EntityMiner.class, EntityCaveCrawler.class,
            EntityRotatick.class, EntityEnthralledDramcryx.class, EntityTheEye.class, EntityTheGrue.class,
            EntityCaveclops.class, EntityHellPig.class, EntityScorcher.class }) {
            TIER.put(c, 2);
        }
        // ранг 3: Древняя сущность повержена
        for (Class<?> c : new Class<?>[] { EntityEnderSpider.class, EntityEnderTriplets.class, EntityEnderWatcher.class }) {
            TIER.put(c, 3);
        }
    }

    /** Ранг игрока по убитым боссам. */
    public static int rank(EntityPlayer p) {
        if (BossProgress.has(p, Boss.LICH_KING)) return BossProgress.has(p, Boss.ANCIENT_ENTITY) ? 3 : 2;
        if (BossProgress.has(p, Boss.CARCINUS)) return 1;
        return 0;
    }

    /** Нужный рангу моба или 0, если моб не ограничен. */
    public static int tier(Entity e) {
        if (e == null) return 0;
        Integer t = TIER.get(e.getClass());
        return t == null ? 0 : t;
    }

    /** Радиус в чанках, в котором игра спавнит мобов вокруг игрока (SpawnerAnimals: 8). */
    public static final int SPAWN_CHUNKS = 8;

    private static boolean gated(World w) {
        int d = w.provider.dimensionId;
        return Config.progressSpawns && (d == 0 || d == -1 || d == 1);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.world.isRemote || !gated(event.world)) return;
        int need = tier(event.entityLiving);
        if (need == 0) return;
        int cx = MathHelper.floor_double(event.x) >> 4, cz = MathHelper.floor_double(event.z) >> 4;
        for (Object o : event.world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (Math.abs(cx - (MathHelper.floor_double(p.posX) >> 4)) <= SPAWN_CHUNKS && Math.abs(cz - (MathHelper.floor_double(p.posZ) >> 4)) <= SPAWN_CHUNKS
                && rank(p) < need) {
                event.setResult(Event.Result.DENY);
                return;
            }
        }
    }

    /** Моб не выбирает целью игрока ниже своего ранга. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent event) {
        if (!(event.target instanceof EntityPlayer) || !(event.entityLiving instanceof EntityLiving)
            || event.entityLiving.worldObj.isRemote || !gated(event.entityLiving.worldObj)) return;
        if (rank((EntityPlayer) event.target) >= tier(event.entityLiving)) return;
        EntityLiving m = (EntityLiving) event.entityLiving;
        m.setAttackTarget(null);
        if (m instanceof EntityCreature) ((EntityCreature) m).setTarget(null);
    }

    /** Моб не наносит урона игроку ниже своего ранга. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer) || event.entityLiving.worldObj.isRemote
            || !gated(event.entityLiving.worldObj)) return;
        if (tier(event.source.getEntity()) > rank((EntityPlayer) event.entityLiving)) {
            event.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(event.entityLiving, "Моб выше твоего ранга: урон отменён");
        }
    }

    private static final String NO_DROPS = "dreNoDrops";

    /** Убийца - игрок (напрямую, стрелой) или тот, кто последним ранил моба (если добило пламя и т.п.). */
    private static EntityPlayer killer(EntityLivingBase dead, Entity source) {
        if (source instanceof EntityPlayer) return (EntityPlayer) source;
        EntityLivingBase last = dead.getAITarget();
        return last instanceof EntityPlayer ? (EntityPlayer) last : null;
    }

    /** Убит игроком ниже ранга: без опыта и (в onDrops) без дропа. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!Config.lowRankNoDrops || event.entityLiving.worldObj.isRemote || !gated(event.entityLiving.worldObj)
            || !(event.entityLiving instanceof EntityLiving)) return;
        int need = tier(event.entityLiving);
        if (need == 0) return;
        EntityPlayer k = killer(event.entityLiving, event.source.getEntity());
        if (k == null || rank(k) >= need) return;
        event.entityLiving.getEntityData()
            .setBoolean(NO_DROPS, true);
        ObfuscationReflectionHelper.setPrivateValue(EntityLiving.class, (EntityLiving) event.entityLiving, 0,
            "experienceValue", "field_70728_aV");
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (event.entityLiving.getEntityData()
            .getBoolean(NO_DROPS)) {
            event.drops.clear();
            com.divinerpgextended.debug.Debug.note(event.source.getEntity(), "Моб убит ниже нужного ранга: дроп и опыт не выпали");
        }
    }
}
