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
 * Divine mobs in the Overworld, the Nether and the End unlock by the player's rank.
 * <ul>
 * <li>Rank 0 (start): crabs only.</li>
 * <li>Rank 1 (Carcinus killed): in the Nether Wildfire and Hell Spiders, in the snow Glacons and Frosts, in the ocean sharks, whales and
 * liopleurodons.</li>
 * <li>Rank 2 (Lich King killed): the remaining Divine mobs of these worlds (desert, jungle, plains, forest, dungeons,
 * Nether).</li>
 * <li>Rank 3 (Ancient Entity killed): End mobs - Ender Spiders, Ender Triplets and Ender Watchers (they drop the eye for
 * summoning the Watcher, and the Watcher unlocks Arcana).</li>
 * </ul>
 * In the End it is the other way round - the strongest decides: a mob spawns if there is at least one player of the needed rank nearby (a player of a lower
 * rank can't deliberately interfere with farming; such mobs do not touch him).
 * A mob spawns only if all players around whom the game spawns mobs at all (8 chunks from the player's chunk -
 * up to ~180 blocks diagonally; it used to be 128 blocks, and mobs were born beyond that circle and came over) have a rank no lower than needed - the weakest decides, so that someone
 * who is not leveling Divine does not suffer because of a neighbor. If the mob already exists and a player of a lower rank is nearby, the mob does not touch him.
 * A player can kill such a mob (so it does not get in the way if it wandered to his home), but no drops or experience fall from it
 * (lowRankNoDrops in the config) - no free farming before your own tier.
 * Peaceful mobs and Divine merchants, summoned bosses and Iceika are not affected.
 * <p>
 * It is cut off at the selection level: the game picks the mob kind for a pack from the biome list ({@code WorldServer.spawnRandomCreature}), and
 * kinds above the allowed rank are removed from this list ({@link #candidates}, via {@link com.divinerpgextended.asm.SpawnTransformer}) -
 * the pack is chosen from the allowed ones, the spawn density does not drop. Creatures that the game places during
 * chunk generation (SpawnerAnimals.performWorldGenSpawning) are filtered the same way - there is no spawn-time check at all, and previously
 * a rank 0 player got Glacons that way (Divine also registers them as creatures). Previously a mob was chosen from the whole list and rejected only
 * on spawn - the whole pack attempt was lost. The refusal on spawn ({@link #onCheckSpawn}) remains as a safety net: spawners,
 * torch calls, other mods' spawners, a pack mob at the border of another player's zone.
 */
public class ProgressSpawns {

    private static final Map<Class<?>, Integer> TIER = new HashMap<Class<?>, Integer>();

    static {
        // rank 1: Carcinus defeated
        for (Class<?> c : new Class<?>[] { EntityWildfire.class, EntityHellSpider.class, EntityGlacon.class,
            EntityFrost.class, EntityShark.class, EntityWhale.class, EntityLiopleurodon.class }) {
            TIER.put(c, 1);
        }
        // rank 2: Lich King defeated
        for (Class<?> c : new Class<?>[] { EntityDesertCrawler.class, EntityAridWarrior.class, EntitySaguaroWorm.class,
            EntityJungleBat.class, EntityJungleSpider.class, EntityJungleDramcryx.class, EntityKobblin.class,
            EntityCyclops.class, EntityPumpkinSpider.class, EntityMiner.class, EntityCaveCrawler.class,
            EntityRotatick.class, EntityEnthralledDramcryx.class, EntityTheEye.class, EntityTheGrue.class,
            EntityCaveclops.class, EntityHellPig.class, EntityScorcher.class }) {
            TIER.put(c, 2);
        }
        // rank 3: Ancient Entity defeated
        for (Class<?> c : new Class<?>[] { EntityEnderSpider.class, EntityEnderTriplets.class, EntityEnderWatcher.class }) {
            TIER.put(c, 3);
        }
    }

    /** Player rank by killed bosses. */
    public static int rank(EntityPlayer p) {
        if (BossProgress.has(p, Boss.LICH_KING)) return BossProgress.has(p, Boss.ANCIENT_ENTITY) ? 3 : 2;
        if (BossProgress.has(p, Boss.CARCINUS)) return 1;
        return 0;
    }

    /** The rank the mob needs, or 0 if the mob is not restricted. */
    public static int tier(Entity e) {
        if (e == null) return 0;
        Integer t = TIER.get(e.getClass());
        return t == null ? 0 : t;
    }

    /** Radius in chunks in which the game spawns mobs around the player (SpawnerAnimals: 8). */
    public static final int SPAWN_CHUNKS = 8;

    /** Remove the forbidden ones from the kind selection (turned off only by selftest - for comparison with the old way). */
    public static boolean filterCandidates = true;

    private static boolean gated(World w) {
        int d = w.provider.dimensionId;
        return Config.progressSpawns && (d == 0 || d == -1 || d == 1);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (denied(event.world, event.entityLiving, event.x, event.z)) event.setResult(Event.Result.DENY);
    }

    /** Whether this mob can't spawn at this point right now by the ranks of players nearby (both for natural spawning and for a torch call). */
    public static boolean denied(World w, Entity mob, double x, double z) {
        if (w.isRemote) return false;
        if (Config.liopleurodonBoss && mob.getClass() == EntityLiopleurodon.class) return true; // liopleurodon - only a bait boss
        if (!gated(w)) return false;
        int need = tier(mob);
        return need > 0 && need > allowedRank(w, MathHelper.floor_double(x) >> 4, MathHelper.floor_double(z) >> 4);
    }

    /**
     * Up to which rank mobs can spawn in a chunk: that of the weakest of the players around whom the game spawns mobs in this chunk; in the End -
     * that of the strongest (a player of a lower rank does not interfere with the spawn for the one who needs these mobs; they do not touch him).
     * No players nearby - no restrictions.
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
     * Which kinds the game picks a mob from for a pack at the point (x, z): the biome list without the kinds above the allowed rank. The biome
     * list is not changed - if there is nothing to remove, it is returned as is, otherwise a copy.
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

    /** The required rank for a mob kind (0 - not restricted). */
    public static int tierOf(Class<?> c) {
        Integer t = TIER.get(c);
        return t == null ? 0 : t;
    }

    /** A mob does not target a player below its rank. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent event) {
        if (!(event.target instanceof EntityPlayer) || !(event.entityLiving instanceof EntityLiving)
            || event.entityLiving.worldObj.isRemote || !gated(event.entityLiving.worldObj)) return;
        if (ready((EntityPlayer) event.target, event.entityLiving)) return;
        EntityLiving m = (EntityLiving) event.entityLiving;
        m.setAttackTarget(null);
        if (m instanceof EntityCreature) ((EntityCreature) m).setTarget(null);
    }

    /** A mob does not damage a player below its rank (or one who has not reached its world). */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer) || event.entityLiving.worldObj.isRemote
            || !gated(event.entityLiving.worldObj)) return;
        Entity src = event.source.getEntity();
        if (src != null && !(src instanceof EntityPlayer) && !ready((EntityPlayer) event.entityLiving, src)) {
            event.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(event.entityLiving, "Mob above your rank: damage cancelled");
        }
    }

    private static final String NO_DROPS = "dreNoDrops";

    /** The killer is a player (directly, by an arrow) or the one who last wounded the mob (if flame etc. finished it). */
    private static EntityPlayer killer(EntityLivingBase dead, Entity source) {
        if (source instanceof EntityPlayer) return (EntityPlayer) source;
        EntityLivingBase last = dead.getAITarget();
        return last instanceof EntityPlayer ? (EntityPlayer) last : null;
    }

    /** Killed by a player below the rank: no experience and (in onDrops) no drop. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!Config.lowRankNoDrops || event.entityLiving.worldObj.isRemote || !gated(event.entityLiving.worldObj)
            || !(event.entityLiving instanceof EntityLiving)) return;
        if (tier(event.entityLiving) == 0 && foreign(event.entityLiving) == null) return; // bosses are decided by BossTracker
        EntityPlayer k = killer(event.entityLiving, event.source.getEntity());
        if (k == null || ready(k, event.entityLiving)) return;
        markNoDrops(event.entityLiving);
    }

    /** No drop and no experience (the drop is removed by {@link #onDrops} last). */
    public static void markNoDrops(EntityLivingBase e) {
        e.getEntityData().setBoolean(NO_DROPS, true);
        if (e instanceof EntityLiving) ObfuscationReflectionHelper.setPrivateValue(EntityLiving.class, (EntityLiving) e, 0, "experienceValue", "field_70728_aV");
    }

    public static boolean noDrops(Entity e) {
        return e != null && e.getEntityData().getBoolean(NO_DROPS);
    }

    // ---------------------------------------------------------------- mobs of other Divine worlds in the Overworld, and bosses

    /** The Divine world the mob is from, by the class package (Twilight - by the bestiary chapter); null - Overworld, Nether, End or not Divine. */
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
     * A mob of another Divine world that ended up in the Overworld (spawner, egg, portal): the world the player must reach for
     * this mob to notice him and drop something. Bosses (decided by {@link BossTracker}), merchants and tamed ones - null.
     */
    public static com.divinerpgextended.bestiary.Bestiary.Chapter foreign(Entity e) {
        if (e == null || e.worldObj == null || e.worldObj.provider.dimensionId != 0 || !Config.foreignMobsByStage || !(e instanceof EntityLiving)) return null;
        if (bossLike(e) || e instanceof net.minecraft.entity.IMerchant || e instanceof net.minecraft.entity.INpc) return null;
        if (e instanceof net.minecraft.entity.passive.EntityTameable && ((net.minecraft.entity.passive.EntityTameable) e).isTamed()) return null;
        return home(e);
    }

    /** The six Mortum bosses: soul sparks of three different ones - Twilight Spirit. */
    static final Boss[] MORTUM_SIX = { Boss.TWILIGHT_DEMON, Boss.KAROT, Boss.ETERNAL_ARCHER, Boss.TWINS, Boss.SOUL_FIEND, Boss.VAMACHERON };

    /** Vethea: the bosses of the Spirit of the Universe sparks besides the Mortum ones (Ayeraco is not recorded as a victory). */
    static final Boss[] VETHEA_NEED = { Boss.ANCIENT_ENTITY, Boss.JUNGLE_KING, Boss.KING_OF_SCORCHERS, Boss.DRAMIX, Boss.PARASECTA };
    /** How many different Mortum bosses Vethea needs - as many as sparks in the Twilight Spirit. */
    static final int VETHEA_MORTUM = com.divinerpgextended.halite.RecipeTwilightSpirit.NEED;

    static int mortumBosses(EntityPlayer p) {
        int m = 0;
        for (Boss b : MORTUM_SIX) if (BossProgress.has(p, b)) m++;
        return m;
    }

    /**
     * Whether the player has reached the world: Iceika - after Carcinus (like the snow mobs of the Overworld), Eden and Arcana - after the Watcher,
     * the next Twilight world - after the guardian of the previous one (its gear opens the portal), Vethea - when the Spirit of the Universe bosses are defeated
     * ({@link #VETHEA_NEED} and three different Mortum bosses). Portals and the Nightmare Bed let in by the same rule ({@link WorldGates}).
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
                for (Boss b : VETHEA_NEED) if (!BossProgress.has(p, b)) return false;
                return mortumBosses(p) >= VETHEA_MORTUM;
            }
            default: return true;
        }
    }

    /** Whom to defeat first to reach the world (translation key). */
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

    /** Whether the mob notices the player and whether something drops from it: rank for Overworld, Nether and End mobs, world - for foreign ones in the Overworld. */
    public static boolean ready(EntityPlayer p, Entity mob) {
        int t = tier(mob);
        if (t > 0) return rank(p) >= t;
        com.divinerpgextended.bestiary.Bestiary.Chapter ch = foreign(mob);
        return ch == null || reached(p, ch);
    }

    /** Boss stage: the world to reach (null - rank by {@link #bossRank} or no conditions). */
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
            case LIOPLEURODON: return com.divinerpgextended.bestiary.Bestiary.Chapter.SKYTHERN; // after the Apalachia guardian
            default: return null;
        }
    }

    /** The rank an Overworld, Nether and End boss needs: the chain Carcinus - Lich King - Ancient Entity - Watcher. */
    static int bossRank(Boss b) {
        switch (b) {
            case SNOW_GLACON: case LICH_KING: return 1;
            case ANCIENT_ENTITY: case KING_OF_SCORCHERS: case JUNGLE_DRAMCRYX: case JUNGLE_KING: return 2;
            case WATCHER: return 3;
            default: return 0;
        }
    }

    /**
     * A boss in the Overworld, the Nether or the End: whether the player has reached its stage. If not - no share of the loot and no victory
     * ({@link BossTracker}, {@link com.divinerpgextended.halite.HaliteDrops}). In other worlds - always yes.
     */
    public static boolean bossReady(EntityPlayer p, Entity boss) {
        if (boss == null || boss.worldObj == null || !gated(boss.worldObj)) return true;
        Boss b = Boss.of(boss);
        if (b == null) return !(boss instanceof EntityAyeraco) || rank(p) >= 3; // the Ayeraco horde in the End - after the Ancient Entity
        com.divinerpgextended.bestiary.Bestiary.Chapter ch = bossWorld(b);
        return ch != null ? reached(p, ch) : rank(p) >= bossRank(b);
    }

    /** Whom to defeat before this boss (translation key; null - no conditions). */
    public static String bossNeedKey(Entity boss) {
        Boss b = Boss.of(boss);
        if (b == null) return boss instanceof EntityAyeraco ? Boss.ANCIENT_ENTITY.langKey() : null;
        com.divinerpgextended.bestiary.Bestiary.Chapter ch = bossWorld(b);
        if (ch != null) return needKey(ch);
        int r = bossRank(b);
        return r == 1 ? Boss.CARCINUS.langKey() : r == 2 ? Boss.LICH_KING.langKey() : r == 3 ? Boss.ANCIENT_ENTITY.langKey() : null;
    }

    /** Last: whatever other handlers add, nothing drops from a marked one. */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent event) {
        if (event.entityLiving.getEntityData()
            .getBoolean(NO_DROPS)) {
            event.drops.clear();
            com.divinerpgextended.debug.Debug.note(event.source.getEntity(), "Killed at the wrong stage: no drop or experience");
            return;
        }
        // Divine's Ender Watcher drops nothing half of the time, and its eye is needed to summon the Watcher - at least one set
        if (event.entityLiving instanceof EntityEnderWatcher && event.recentlyHit && event.drops.isEmpty() && !event.entityLiving.worldObj.isRemote) {
            EntityLivingBase w = event.entityLiving;
            event.drops.add(new net.minecraft.entity.item.EntityItem(w.worldObj, w.posX, w.posY, w.posZ, new net.minecraft.item.ItemStack(net.divinerpg.utils.items.VanillaItemsOther.enderShards, 2)));
            event.drops.add(new net.minecraft.entity.item.EntityItem(w.worldObj, w.posX, w.posY, w.posZ, new net.minecraft.item.ItemStack(net.divinerpg.utils.items.VanillaItemsOther.watchingEye, 1)));
        }
    }
}
