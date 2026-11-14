package com.divinerpgextended.boss;

import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.divinerpg.entities.vanilla.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
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
 * В Энде наоборот - решает самый сильный: моб появляется, если рядом есть хоть один игрок нужного ранга (игрок ниже
 * рангом не может нарочно мешать фарму; его самого такие мобы не трогают).
 * Моб появляется, только если у всех игроков, вокруг которых игра вообще спавнит мобов (8 чанков от чанка игрока -
 * до ~180 блоков по диагонали; раньше было 128 блоков, и мобы рождались за этим кругом и приходили), ранг не ниже нужного - решает самый слабый, чтобы тот,
 * кто не качает Divine, не страдал из-за соседа. Если моб уже есть, а рядом игрок ниже рангом, моб его не трогает.
 * Игрок может такого моба убить (чтобы не мешал, если забрёл к нему домой), но дроп и опыт с него не падают
 * (lowRankNoDrops в конфиге) - бесплатного фарма не до своей ступени нет.
 * Мирные мобы и торговцы Divine, боссы с призыва и Iceika это не затрагивает.
 * <p>
 * Отсекается на уровне выбора: игра берёт вид моба для стаи из списка биома ({@code WorldServer.spawnRandomCreature}), и
 * виды выше допустимого ранга из этого списка убираются ({@link #candidates}, через {@link com.divinerpgextended.asm.SpawnTransformer}) -
 * стая выбирается из разрешённых, плотность спавна не проседает. Так же отбираются существа, которых игра селит при
 * генерации чанка (SpawnerAnimals.performWorldGenSpawning) - там проверки при появлении нет вовсе, и раньше так у
 * игрока ранга 0 появлялись Глаконы (Divine прописывает их ещё и как существ). Раньше моб выбирался из всего списка и отклонялся уже
 * при появлении - вся попытка стаи пропадала. Отказ при появлении ({@link #onCheckSpawn}) остался страховкой: спавнеры,
 * зов факелом, чужие спавнеры модов, моб стаи у границы зоны другого игрока.
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

    /** Убирать запрещённых из выбора вида (выключается только самопроверкой - для сравнения со старым способом). */
    public static boolean filterCandidates = true;

    private static boolean gated(World w) {
        int d = w.provider.dimensionId;
        return Config.progressSpawns && (d == 0 || d == -1 || d == 1);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (denied(event.world, event.entityLiving, event.x, event.z)) event.setResult(Event.Result.DENY);
    }

    /** Нельзя ли сейчас появиться этому мобу в этой точке по рангам игроков рядом (и для естественного спавна, и для зова факелом). */
    public static boolean denied(World w, Entity mob, double x, double z) {
        if (w.isRemote) return false;
        if (Config.liopleurodonBoss && mob.getClass() == EntityLiopleurodon.class) return true; // лиоплевродон - только босс на наживку
        if (!gated(w)) return false;
        int need = tier(mob);
        return need > 0 && need > allowedRank(w, MathHelper.floor_double(x) >> 4, MathHelper.floor_double(z) >> 4);
    }

    /**
     * До какого ранга мобы могут появиться в чанке: у игроков, вокруг которых игра спавнит мобов в этом чанке, - у самого
     * слабого; в Энде - у самого сильного (игрок ниже рангом не мешает спавну тому, кому эти мобы нужны, его самого они не
     * трогают). Игроков рядом нет - без ограничений.
     */
    public static int allowedRank(World w, int cx, int cz) {
        boolean end = w.provider.dimensionId == 1, any = false;
        int r = end ? 0 : MAX_RANK;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (Math.abs(cx - (MathHelper.floor_double(p.posX) >> 4)) > SPAWN_CHUNKS || Math.abs(cz - (MathHelper.floor_double(p.posZ) >> 4)) > SPAWN_CHUNKS) continue;
            any = true;
            r = end ? Math.max(r, rank(p)) : Math.min(r, rank(p));
        }
        return any ? r : MAX_RANK;
    }

    public static final int MAX_RANK = 3;

    /**
     * Из каких видов игра выбирает моба для стаи в точке (x, z): список биома без видов выше допустимого ранга. Список
     * биома не меняется - если убирать нечего, он и возвращается, иначе копия.
     */
    public static List<BiomeGenBase.SpawnListEntry> candidates(World w, int x, int z, List<BiomeGenBase.SpawnListEntry> list) {
        if (list == null || list.isEmpty() || !filterCandidates || w.isRemote || !gated(w)) return list;
        int top = 0;
        for (BiomeGenBase.SpawnListEntry e : list) top = Math.max(top, tierOf(e.entityClass));
        if (top == 0) return list;
        int allowed = allowedRank(w, x >> 4, z >> 4);
        if (top <= allowed) return list;
        List<BiomeGenBase.SpawnListEntry> out = new ArrayList<BiomeGenBase.SpawnListEntry>(list.size());
        for (BiomeGenBase.SpawnListEntry e : list) if (tierOf(e.entityClass) <= allowed) out.add(e);
        return out;
    }

    /** Нужный ранг для вида моба (0 - не ограничен). */
    public static int tierOf(Class<?> c) {
        Integer t = TIER.get(c);
        return t == null ? 0 : t;
    }

    /** Моб не выбирает целью игрока ниже своего ранга. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent event) {
        if (!(event.target instanceof EntityPlayer) || !(event.entityLiving instanceof EntityLiving)
            || event.entityLiving.worldObj.isRemote || !gated(event.entityLiving.worldObj)) return;
        if (ready((EntityPlayer) event.target, event.entityLiving)) return;
        EntityLiving m = (EntityLiving) event.entityLiving;
        m.setAttackTarget(null);
        if (m instanceof EntityCreature) ((EntityCreature) m).setTarget(null);
    }

    /** Моб не наносит урона игроку ниже своего ранга (и не дошедшему до его мира). */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer) || event.entityLiving.worldObj.isRemote
            || !gated(event.entityLiving.worldObj)) return;
        Entity src = event.source.getEntity();
        if (src != null && !(src instanceof EntityPlayer) && !ready((EntityPlayer) event.entityLiving, src)) {
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
        if (tier(event.entityLiving) == 0 && foreign(event.entityLiving) == null) return; // боссов решает BossTracker
        EntityPlayer k = killer(event.entityLiving, event.source.getEntity());
        if (k == null || ready(k, event.entityLiving)) return;
        markNoDrops(event.entityLiving);
    }

    /** Без дропа и опыта (дроп убирает {@link #onDrops} последним). */
    public static void markNoDrops(EntityLivingBase e) {
        e.getEntityData().setBoolean(NO_DROPS, true);
        if (e instanceof EntityLiving) ObfuscationReflectionHelper.setPrivateValue(EntityLiving.class, (EntityLiving) e, 0, "experienceValue", "field_70728_aV");
    }

    public static boolean noDrops(Entity e) {
        return e != null && e.getEntityData().getBoolean(NO_DROPS);
    }

    // ---------------------------------------------------------------- мобы других миров Divine в обычном мире и боссы

    /** Мир Divine, откуда моб, по пакету класса (сумеречный - по главе бестиария); null - обычный мир, Ад, Край или не Divine. */
    public static com.divinerpgextended.bestiary.Bestiary.Chapter home(Entity e) {
        String c = e.getClass().getName();
        if (!c.startsWith("net.divinerpg.entities.")) return null;
        if (c.contains(".iceika.")) return com.divinerpgextended.bestiary.Bestiary.Chapter.ICEIKA;
        if (c.contains(".arcana.")) return com.divinerpgextended.bestiary.Bestiary.Chapter.ARCANA;
        if (c.contains(".vethea.")) return com.divinerpgextended.bestiary.Bestiary.Chapter.VETHEA;
        if (!c.contains(".twilight.")) return null;
        String name = net.minecraft.entity.EntityList.getEntityString(e);
        com.divinerpgextended.bestiary.Bestiary.Chapter ch = name == null ? null : com.divinerpgextended.bestiary.Bestiary.chapterOf(name);
        return ch != null ? ch : com.divinerpgextended.bestiary.Bestiary.Chapter.EDEN;
    }

    static boolean bossLike(Entity e) {
        return Boss.of(e) != null || e instanceof net.minecraft.entity.boss.IBossDisplayData || e instanceof net.divinerpg.entities.base.EntityDivineRPGBoss;
    }

    /**
     * Моб другого мира Divine, попавший в обычный мир (спавнер, яйцо, портал): мир, до которого должен дойти игрок, чтобы
     * этот моб его замечал и что-то с него падало. Боссы (их решает {@link BossTracker}), торговцы и приручённые - null.
     */
    public static com.divinerpgextended.bestiary.Bestiary.Chapter foreign(Entity e) {
        if (e == null || e.worldObj == null || e.worldObj.provider.dimensionId != 0 || !Config.foreignMobsByStage || !(e instanceof EntityLiving)) return null;
        if (bossLike(e) || e instanceof net.minecraft.entity.IMerchant || e instanceof net.minecraft.entity.INpc) return null;
        if (e instanceof net.minecraft.entity.passive.EntityTameable && ((net.minecraft.entity.passive.EntityTameable) e).isTamed()) return null;
        return home(e);
    }

    /** Шесть боссов Мортума: частички трёх разных - Дух сумерек. */
    static final Boss[] MORTUM_SIX = { Boss.TWILIGHT_DEMON, Boss.KAROT, Boss.ETERNAL_ARCHER, Boss.TWINS, Boss.SOUL_FIEND, Boss.VAMACHERON };

    /**
     * Дошёл ли игрок до мира: Айсика - после Карцинуса (как снежные мобы обычного мира), Эдем и Аркана - после Смотрителя,
     * следующий сумеречный мир - после стража предыдущего (его шестерня открывает портал), Ветея - когда побеждены боссы
     * Духа вселенной (три босса Мортума, Драмикс, Парасекта, Древняя сущность, Король Скорчеров; Айрако победой не пишется).
     */
    public static boolean reached(EntityPlayer p, com.divinerpgextended.bestiary.Bestiary.Chapter ch) {
        switch (ch) {
            case ICEIKA: return rank(p) >= 1;
            case EDEN: case ARCANA: return BossProgress.has(p, Boss.WATCHER);
            case WILDWOOD: return BossProgress.has(p, Boss.CADILLION_EDEN);
            case APALACHIA: return BossProgress.has(p, Boss.CADILLION_WILDWOOD);
            case SKYTHERN: return BossProgress.has(p, Boss.CADILLION_APALACHIA);
            case MORTUM: return BossProgress.has(p, Boss.CADILLION_SKYTHERN);
            case VETHEA: {
                if (!BossProgress.has(p, Boss.DRAMIX) || !BossProgress.has(p, Boss.PARASECTA) || !BossProgress.has(p, Boss.ANCIENT_ENTITY)
                    || !BossProgress.has(p, Boss.KING_OF_SCORCHERS)) return false;
                int m = 0;
                for (Boss b : MORTUM_SIX) if (BossProgress.has(p, b)) m++;
                return m >= 3;
            }
            default: return true;
        }
    }

    /** Кого победить раньше, чтобы дойти до мира (ключ перевода). */
    public static String needKey(com.divinerpgextended.bestiary.Bestiary.Chapter ch) {
        switch (ch) {
            case ICEIKA: return Boss.CARCINUS.langKey();
            case EDEN: case ARCANA: return Boss.WATCHER.langKey();
            case WILDWOOD: return Boss.CADILLION_EDEN.langKey();
            case APALACHIA: return Boss.CADILLION_WILDWOOD.langKey();
            case SKYTHERN: return Boss.CADILLION_APALACHIA.langKey();
            case MORTUM: return Boss.CADILLION_SKYTHERN.langKey();
            default: return "dre.stage.vethea";
        }
    }

    /** Замечает ли моб игрока и падает ли с него что-то: ранг для мобов обычного мира, Ада и Края, мир - для чужих в обычном мире. */
    public static boolean ready(EntityPlayer p, Entity mob) {
        int t = tier(mob);
        if (t > 0) return rank(p) >= t;
        com.divinerpgextended.bestiary.Bestiary.Chapter ch = foreign(mob);
        return ch == null || reached(p, ch);
    }

    /** Этап босса: мир, до которого надо дойти (null - ранг по {@link #bossRank} или без условий). */
    public static com.divinerpgextended.bestiary.Bestiary.Chapter bossWorld(Boss b) {
        switch (b) {
            case CADILLION_EDEN: return com.divinerpgextended.bestiary.Bestiary.Chapter.EDEN;
            case CADILLION_WILDWOOD: return com.divinerpgextended.bestiary.Bestiary.Chapter.WILDWOOD;
            case CADILLION_APALACHIA: return com.divinerpgextended.bestiary.Bestiary.Chapter.APALACHIA;
            case CADILLION_SKYTHERN: return com.divinerpgextended.bestiary.Bestiary.Chapter.SKYTHERN;
            case TWILIGHT_DEMON: case KAROT: case ETERNAL_ARCHER: case TWINS: case SOUL_FIEND: case VAMACHERON:
                return com.divinerpgextended.bestiary.Bestiary.Chapter.MORTUM;
            case DRAMIX: case PARASECTA: return com.divinerpgextended.bestiary.Bestiary.Chapter.ARCANA;
            case WRECK: case RAGLOK: case LADY_LUNA: return com.divinerpgextended.bestiary.Bestiary.Chapter.VETHEA;
            case LIOPLEURODON: return com.divinerpgextended.bestiary.Bestiary.Chapter.SKYTHERN; // после стража Апалачии
            default: return null;
        }
    }

    /** Ранг, нужный боссу обычного мира, Ада и Края: цепочка Карцинус - Король-лич - Древняя сущность - Смотритель. */
    static int bossRank(Boss b) {
        switch (b) {
            case SNOW_GLACON: case LICH_KING: return 1;
            case ANCIENT_ENTITY: case KING_OF_SCORCHERS: case JUNGLE_DRAMCRYX: case JUNGLE_KING: return 2;
            case WATCHER: return 3;
            default: return 0;
        }
    }

    /**
     * Босс в обычном мире, Аду или Крае: дошёл ли игрок до его этапа. Не дошёл - ни доли добычи, ни победы
     * ({@link BossTracker}, {@link com.divinerpgextended.halite.HaliteDrops}). В остальных мирах - всегда да.
     */
    public static boolean bossReady(EntityPlayer p, Entity boss) {
        if (boss == null || boss.worldObj == null || !gated(boss.worldObj)) return true;
        Boss b = Boss.of(boss);
        if (b == null) return !(boss instanceof EntityAyeraco) || rank(p) >= 3; // орда Айрако в Крае - после Древней сущности
        com.divinerpgextended.bestiary.Bestiary.Chapter ch = bossWorld(b);
        return ch != null ? reached(p, ch) : rank(p) >= bossRank(b);
    }

    /** Кого победить раньше этого босса (ключ перевода; null - условий нет). */
    public static String bossNeedKey(Entity boss) {
        Boss b = Boss.of(boss);
        if (b == null) return boss instanceof EntityAyeraco ? Boss.ANCIENT_ENTITY.langKey() : null;
        com.divinerpgextended.bestiary.Bestiary.Chapter ch = bossWorld(b);
        if (ch != null) return needKey(ch);
        int r = bossRank(b);
        return r == 1 ? Boss.CARCINUS.langKey() : r == 2 ? Boss.LICH_KING.langKey() : r == 3 ? Boss.ANCIENT_ENTITY.langKey() : null;
    }

    /** Последним: что бы ни добавили другие обработчики, у помеченного ничего не падает. */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent event) {
        if (event.entityLiving.getEntityData()
            .getBoolean(NO_DROPS)) {
            event.drops.clear();
            com.divinerpgextended.debug.Debug.note(event.source.getEntity(), "Убит не на своём этапе: дроп и опыт не выпали");
            return;
        }
        // эндер-смотритель у Divine в половине случаев не роняет ничего, а его глаз нужен на призыв Смотрителя - хоть один набор
        if (event.entityLiving instanceof EntityEnderWatcher && event.recentlyHit && event.drops.isEmpty() && !event.entityLiving.worldObj.isRemote) {
            EntityLivingBase w = event.entityLiving;
            event.drops.add(new net.minecraft.entity.item.EntityItem(w.worldObj, w.posX, w.posY, w.posZ, new net.minecraft.item.ItemStack(net.divinerpg.utils.items.VanillaItemsOther.enderShards, 2)));
            event.drops.add(new net.minecraft.entity.item.EntityItem(w.worldObj, w.posX, w.posY, w.posZ, new net.minecraft.item.ItemStack(net.divinerpg.utils.items.VanillaItemsOther.watchingEye, 1)));
        }
    }
}
