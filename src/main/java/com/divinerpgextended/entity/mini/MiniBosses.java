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
 * Mini-boss spawning: in the twilight dimensions and on Vethea floors 1-3, no more often than once per {@link Config#miniBossMinutes} minutes per player, 20-32 blocks from
 * them, not at a world torch, not near a boss or another mini-boss.
 */
public class MiniBosses {

    private static final String NEXT = "dreMiniNext";
    private static final int FIRST_DELAY = 6000, CHECK_EVERY = 40;
    private static final double CHANCE = 0.05;
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
        if (d == ConfigurationHelper.vethea && VetheaFloors.floorOf(p.posY) <= 3) {
            EntitySleepwalker s = new EntitySleepwalker(w);
            s.setFloor(VetheaFloors.floorOf(p.posY));
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
        EntityMiniBoss m = spawnNear(p);
        if (m != null) setNext(p, now + Config.miniBossMinutes * 1200L);
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

    /** A mob death nearby feeds the Mourner. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote || e.entityLiving.dimension != ConfigurationHelper.mortum || !(e.entityLiving instanceof IMob) || e.entityLiving instanceof EntityMiniBoss) return;
        double r = EntityMourner.REACH;
        for (Object o : e.entityLiving.worldObj.getEntitiesWithinAABB(EntityMourner.class, e.entityLiving.boundingBox.expand(r, r, r))) ((EntityMourner) o).feed();
    }
}
