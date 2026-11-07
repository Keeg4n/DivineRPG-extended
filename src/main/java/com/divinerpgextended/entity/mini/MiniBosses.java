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
 * Появление мини-боссов: в сумеречных мирах и на этажах 1-3 Ветеи, не чаще раза в {@link Config#miniBossMinutes} минут на игрока, в 20-32 блоках от
 * него, не у факела мира, не рядом с боссом и другим мини-боссом.
 */
public class MiniBosses {

    private static final String NEXT = "dreMiniNext";
    private static final int FIRST_DELAY = 6000, CHECK_EVERY = 40;
    private static final double CHANCE = 0.05;
    /** За секунду метели: ель появляется в первые десятки секунд. */
    public static double SPRUCE_CHANCE = 0.05;

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** Когда этому игроку можно ждать следующего мини-босса. */
    public static long next(EntityPlayer p) {
        return data(p).getLong(NEXT);
    }

    public static void setNext(EntityPlayer p, long at) {
        data(p).setLong(NEXT, at);
    }

    /** Мини-босс мира игрока (null - здесь их нет). */
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

    /** Поставить мини-босса рядом с игроком (null - негде или нельзя). */
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

    /** Проверка игрока: пришло ли время, и если повезло - мини-босс. */
    public static EntityMiniBoss tick(EntityPlayer p) {
        if (!Config.miniBosses || p.capabilities.isCreativeMode || p.dimension == ConfigurationHelper.iceika) return null; // ель приходит с метелью
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

    /** Секунда метели: ель приходит раз за метель, к тому, кого достаёт ветер. true - пришла. */
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

    /** Смерть моба рядом кормит Плакальщика. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote || e.entityLiving.dimension != ConfigurationHelper.mortum || !(e.entityLiving instanceof IMob) || e.entityLiving instanceof EntityMiniBoss) return;
        double r = EntityMourner.REACH;
        for (Object o : e.entityLiving.worldObj.getEntitiesWithinAABB(EntityMourner.class, e.entityLiving.boundingBox.expand(r, r, r))) ((EntityMourner) o).feed();
    }
}
