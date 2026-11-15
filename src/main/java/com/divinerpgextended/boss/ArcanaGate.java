package com.divinerpgextended.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.entities.vanilla.EntityTheWatcher;
import net.divinerpg.utils.blocks.ArcanaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Entrance to Arcana - after the Watcher.
 * <ul>
 * <li>The Eye of the Watcher drops from the Watcher; Arcana portal frames are crafted only with it (the Divine recipe is replaced).</li>
 * <li>The portal admits only those who took part in killing the Watcher themselves. Others, already on approach to the portal,
 * get a transfer pause (Divine transfers only at zero pause, and on the very first touch - checking
 * only the block underfoot is too late) and are told what is needed.</li>
 * <li>If one still slipped through (a fall, a teleport) - we return him at once to where the player stood recently, far from the portal.</li>
 * </ul>
 */
public class ArcanaGate {

    /** The player's last safe place outside Arcana: dimension and coordinates. */
    private static final Map<UUID, double[]> SAFE = new HashMap<UUID, double[]>();

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.entityLiving instanceof EntityTheWatcher) || event.entityLiving.worldObj.isRemote) return;
        EntityItem eye = new EntityItem(event.entityLiving.worldObj, event.entityLiving.posX, event.entityLiving.posY + 1,
            event.entityLiving.posZ, new ItemStack(ModItems.watcherEye));
        event.drops.add(eye);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        if (event.phase != TickEvent.Phase.START || p.worldObj.isRemote || !(p instanceof EntityPlayerMP)) return;
        if (BossProgress.has(p, Boss.WATCHER) || p.capabilities.isCreativeMode) return;
        if (p.dimension == ConfigurationHelper.arcana) {
            sendBack((EntityPlayerMP) p);
            return;
        }
        if (p.ticksExisted % 4 != 0) return;
        if (nearPortal(p)) {
            p.timeUntilPortal = 20; // the Divine portal will not transfer until the pause ends
            if (p.ticksExisted % 60 == 0) p.addChatMessage(new ChatComponentTranslation("dre.chat.arcana_locked"));
            com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.lockedPortal);
        } else if (p.ticksExisted % 20 == 0 && p.onGround) {
            SAFE.put(p.getUniqueID(), new double[] { p.dimension, p.posX, p.posY, p.posZ });
        }
    }

    /** A portal within a couple of blocks around (and below - with a margin for falling speed). */
    private static boolean nearPortal(EntityPlayer p) {
        int x0 = MathHelper.floor_double(p.boundingBox.minX) - 2, x1 = MathHelper.floor_double(p.boundingBox.maxX) + 2;
        int z0 = MathHelper.floor_double(p.boundingBox.minZ) - 2, z1 = MathHelper.floor_double(p.boundingBox.maxZ) + 2;
        int y1 = MathHelper.floor_double(p.boundingBox.minY) + 1;
        int y0 = MathHelper.floor_double(p.boundingBox.minY + Math.min(0, p.motionY * 4)) - 2; // every 4 ticks: margin for a fall in 4 ticks
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++)
                    if (p.worldObj.getBlock(x, y, z) == ArcanaBlocks.arcanaPortal) return true;
        return false;
    }

    private static void sendBack(EntityPlayerMP p) {
        double[] s = SAFE.get(p.getUniqueID());
        int dim = s == null ? 0 : (int) s[0];
        WorldServer target = p.mcServer.worldServerForDimension(dim);
        final double x, y, z;
        if (s != null) {
            x = s[1];
            y = s[2];
            z = s[3];
        } else {
            ChunkCoordinates sp = target.getSpawnPoint();
            x = sp.posX + 0.5;
            y = target.getTopSolidOrLiquidBlock(sp.posX, sp.posZ) + 1;
            z = sp.posZ + 0.5;
        }
        p.timeUntilPortal = 100;
        p.mcServer.getConfigurationManager().transferPlayerToDimension(p, dim, new Teleporter(target) {

            @Override
            public void placeInPortal(Entity e, double px, double py, double pz, float yaw) {
                e.setLocationAndAngles(x, y, z, e.rotationYaw, e.rotationPitch);
                e.motionX = e.motionY = e.motionZ = 0;
            }

            @Override
            public boolean placeInExistingPortal(Entity e, double px, double py, double pz, float yaw) {
                placeInPortal(e, px, py, pz, yaw);
                return true;
            }

            @Override
            public boolean makePortal(Entity e) {
                return true;
            }

            @Override
            public void removeStalePortalLocations(long time) {}
        });
        p.setPositionAndUpdate(x, y, z);
        p.addChatMessage(new ChatComponentTranslation("dre.chat.arcana_locked"));
    }
}
