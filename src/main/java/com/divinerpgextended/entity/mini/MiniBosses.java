package com.divinerpgextended.entity.mini;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.TorchWards;
import com.divinerpgextended.vethea.VetheaFloors;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Mini-boss spawning: in the twilight dimensions and on Vethea floor 3, no more often than once per {@link Config#miniBossMinutes} minutes for a player and
 * everyone near him (a group does not get one mini-boss per member), 20-32 blocks from them, not at a world torch, not near a boss or another mini-boss.
 */
public class MiniBosses {

    private static final String NEXT = "dreMiniNext";
    private static final int FIRST_DELAY = 6000, CHECK_EVERY = 40;
    private static final double CHANCE = 0.05;
    /** Players this close to the one who got a mini-boss share his pause. */
    public static final double GROUP_R = 96;
    /** Per second of blizzard: the spruce appears in the first tens of seconds. */
    public static double SPRUCE_CHANCE = 0.05;

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** When this player can expect the next mini-boss. */
    public static long next(EntityPlayer p) {
        return data(p).getLong(NEXT);
    }

    public static void setNext(EntityPlayer p, long at) {
        data(p).setLong(NEXT, at);
    }

    /** The mini-boss of the player's dimension (null - there are none here). */
    public static EntityMiniBoss create(EntityPlayer p) {
        World w = p.worldObj;
        int d = p.dimension;
        if (d == ConfigurationHelper.eden) return new EntityNoonCori(w);
        if (d == ConfigurationHelper.wildwood) return new EntityOldVerek(w);
        if (d == ConfigurationHelper.apalachia) return new EntityStonebreaker(w);
        if (d == ConfigurationHelper.skythern) return new EntitySkyGrabber(w);
        if (d == ConfigurationHelper.mortum) return new EntityMourner(w);
        if (d == ConfigurationHelper.iceika) return com.divinerpgextended.cold.Blizzard.get(w).phase == com.divinerpgextended.cold.Blizzard.STORM ? new EntityBlizzardSpruce(w) : null;
        if (d == ConfigurationHelper.vethea && VetheaFloors.floorOf(p.posY) == 3) { // floors 1-2 - too early, 4 - the arena bosses
            EntitySleepwalker s = new EntitySleepwalker(w);
            s.setFloor(3);
            return s;
        }
        return null;
    }

    /** Place a mini-boss next to the player (null - no room or not allowed). */
    public static EntityMiniBoss spawnNear(EntityPlayer p) {
        EntityMiniBoss m = create(p);
        if (m == null) return null;
        World w = p.worldObj;
        AxisAlignedBB around = p.boundingBox.expand(64, 32, 64);
        if (!w.getEntitiesWithinAABB(EntityMiniBoss.class, around).isEmpty() || !w.getEntitiesWithinAABB(IBossDisplayData.class, around).isEmpty()) return null;
        for (int tries = 0; tries < 16; tries++) {
            double a = w.rand.nextDouble() * Math.PI * 2, r = 20 + w.rand.nextDouble() * 12;
            int x = MathHelper.floor_double(p.posX + Math.cos(a) * r), z = MathHelper.floor_double(p.posZ + Math.sin(a) * r);
            for (int y = MathHelper.floor_double(p.posY) + 8; y >= MathHelper.floor_double(p.posY) - 8 && y > 1; y--) {
                Block under = w.getBlock(x, y - 1, z);
                if (!under.getMaterial().blocksMovement() || !w.isAirBlock(x, y, z)) continue;
                m.setLocationAndAngles(x + 0.5, y, z + 0.5, w.rand.nextFloat() * 360, 0);
                if (!w.getCollidingBoundingBoxes(m, m.boundingBox).isEmpty() || TorchWards.warded(w, m, x, y, z)) break;
                w.spawnEntityInWorld(m);
                ChatComponentTranslation name = new ChatComponentTranslation("dre.entity.mini." + m.key());
                for (Object o : w.getEntitiesWithinAABB(EntityPlayer.class, m.boundingBox.expand(48, 24, 48))) ((EntityPlayer) o).addChatMessage(new ChatComponentTranslation("dre.chat.mini.near", name));
                return m;
            }
        }
        return null;
    }

    /** Check a player: whether the time has come, and if lucky - a mini-boss. */
    public static EntityMiniBoss tick(EntityPlayer p) {
        if (!Config.miniBosses || p.capabilities.isCreativeMode || p.dimension == ConfigurationHelper.iceika) return null; // the spruce comes with the blizzard
        long now = p.worldObj.getTotalWorldTime();
        if (!data(p).hasKey(NEXT)) setNext(p, now + FIRST_DELAY);
        if (now < next(p) || p.worldObj.rand.nextDouble() >= CHANCE) return null;
        if (Config.miniBossConditions && !ready(p)) return null;
        EntityMiniBoss m = spawnNear(p);
        if (m == null) return null;
        // the pause is for everyone around: two players together got two mini-bosses in a few minutes
        for (Object o : p.worldObj.getEntitiesWithinAABB(EntityPlayer.class, p.boundingBox.expand(GROUP_R, GROUP_R, GROUP_R)))
            setNext((EntityPlayer) o, now + Config.miniBossMinutes * 1200L);
        setNext(p, now + Config.miniBossMinutes * 1200L);
        return m;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.worldObj.isRemote && e.player.ticksExisted % CHECK_EVERY == 0) tick(e.player);
    }

    /** A second of blizzard: the spruce comes once per blizzard, to whoever the wind reaches. true - it came. */
    public static boolean spruceStep(World w, com.divinerpgextended.cold.Blizzard b) {
        if (!Config.miniBosses || b.spruceCame || w.rand.nextDouble() >= SPRUCE_CHANCE) return false;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.capabilities.isCreativeMode || !com.divinerpgextended.cold.Blizzard.exposed(p) || spawnNear(p) == null) continue;
            b.spruceCame = true;
            b.markDirty();
            return true;
        }
        return false;
    }

    // ---------------- the moment of each world

    /** Kill streak: Mortum and Vethea mini-bosses come to a slaughter; a pause longer than this breaks the streak. */
    public static final int STREAK_GAP = 1200, MORTUM_KILLS = 6, VETHEA_KILLS = 8, VOID_REACH = 4;
    private static final String STREAK = "dreKillStreak", STREAK_AT = "dreKillStreakAt", STREAK_DIM = "dreKillStreakDim";

    /**
     * Besides the pause, each world waits for its moment - otherwise a mini-boss just came every 20 minutes:
     * <ul>
     * <li>Eden, Noon Cori - the player is overheated (half the scale or more) and still heating up (under open sky, not in water);</li>
     * <li>Wildwood, Old Verek - only in a downpour;</li>
     * <li>Apalachia, Stonebreaker - only in a rockfall;</li>
     * <li>Skythern, Sky Grabber - the player is at an island edge or over the abyss (a drop of {@link #ABYSS_DEPTH} blocks within {@link #VOID_REACH} blocks);</li>
     * <li>Mortum, Mourner - the player has just killed {@link #MORTUM_KILLS} mobs in a row (pauses no longer than a minute);</li>
     * <li>Vethea floor 3, Sleepwalker - the same, {@link #VETHEA_KILLS} dream mobs.</li>
     * </ul>
     * A world's weather turned off in the config does not block its mini-boss.
     */
    public static boolean ready(EntityPlayer p) {
        World w = p.worldObj;
        int d = p.dimension;
        if (d == ConfigurationHelper.eden) return !Config.edenSun || heating(p);
        if (d == ConfigurationHelper.wildwood) return !Config.wildwoodRain || com.divinerpgextended.world.twilight.WorldWeather.rain(w);
        if (d == ConfigurationHelper.apalachia) return !Config.apalachiaRockfall || com.divinerpgextended.world.twilight.WorldWeather.quake(w);
        if (d == ConfigurationHelper.skythern) return nearVoid(p);
        if (d == ConfigurationHelper.mortum) return streak(p) >= MORTUM_KILLS;
        if (d == ConfigurationHelper.vethea) return streak(p) >= VETHEA_KILLS;
        return true;
    }

    /** Eden: overheated to half and still heating (the scale grows under open sky, out of water). */
    static boolean heating(EntityPlayer p) {
        return com.divinerpgextended.world.twilight.EdenSun.heat(p) >= 50 && !p.isInWater() && com.divinerpgextended.world.twilight.WorldWeather.open(p)
            && com.divinerpgextended.world.twilight.EdenSun.rate(p) > 0;
    }

    /** How deep a drop counts as the abyss (Skythern has clouds and stone far below - the world bottom itself is not empty). */
    public static final int ABYSS_DEPTH = 24;

    /** Skythern: within a few blocks around there is a drop of {@link #ABYSS_DEPTH} blocks of air under the player's feet - an island edge or open sky. */
    static boolean nearVoid(EntityPlayer p) {
        World w = p.worldObj;
        int px = MathHelper.floor_double(p.posX), py = MathHelper.floor_double(p.posY), pz = MathHelper.floor_double(p.posZ);
        for (int dx = -VOID_REACH; dx <= VOID_REACH; dx += 2) {
            for (int dz = -VOID_REACH; dz <= VOID_REACH; dz += 2) {
                boolean empty = true;
                for (int y = py; y >= Math.max(0, py - ABYSS_DEPTH) && empty; y--) if (!w.isAirBlock(px + dx, y, pz + dz)) empty = false;
                if (empty) return true;
            }
        }
        return false;
    }

    /** Kills in a row in the current world (0 - the streak broke: another world or more than a minute without a kill). */
    public static int streak(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (d.getInteger(STREAK_DIM) != p.dimension || p.worldObj.getTotalWorldTime() - d.getLong(STREAK_AT) > STREAK_GAP) return 0;
        return d.getInteger(STREAK);
    }

    /** One more kill by the player (for the selftest - also directly). */
    public static void countKill(EntityPlayer p) {
        int s = streak(p) + 1;
        NBTTagCompound d = p.getEntityData();
        d.setInteger(STREAK, s);
        d.setLong(STREAK_AT, p.worldObj.getTotalWorldTime());
        d.setInteger(STREAK_DIM, p.dimension);
    }

    /** Kill streaks in Mortum and Vethea: hostile mobs killed by a player. */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOW)
    public void onKill(LivingDeathEvent e) {
        if (e.isCanceled() || e.entityLiving.worldObj.isRemote || !(e.entityLiving instanceof IMob) || e.entityLiving instanceof EntityMiniBoss) return;
        int d = e.entityLiving.dimension;
        if (d != ConfigurationHelper.mortum && d != ConfigurationHelper.vethea) return;
        if (e.source.getEntity() instanceof EntityPlayer) countKill((EntityPlayer) e.source.getEntity());
    }

    /** A mob death nearby feeds the Mourner. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote || e.entityLiving.dimension != ConfigurationHelper.mortum || !(e.entityLiving instanceof IMob) || e.entityLiving instanceof EntityMiniBoss) return;
        double r = EntityMourner.REACH;
        for (Object o : e.entityLiving.worldObj.getEntitiesWithinAABB(EntityMourner.class, e.entityLiving.boundingBox.expand(r, r, r))) ((EntityMourner) o).feed();
    }
}
