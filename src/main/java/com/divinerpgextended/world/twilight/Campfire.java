package com.divinerpgextended.world.twilight;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.block.BlockBrazier;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * The camp campfire of wanderers as a respawn point in a twilight dimension (a bed does not work there: the dimensions do not allow
 * respawning). Sneak and right-click a burning brazier in a twilight dimension - to remember it; after death the player appears at
 * it while it stands and burns. A new bed in the overworld does not cancel the campfire - it can be reset at the campfire itself again.
 */
public class Campfire {

    private static final String KEY = "dreCampfire";

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote || !e.entityPlayer.isSneaking()) return;
        if (e.world.getBlock(e.x, e.y, e.z) != ModBlocks.brazier || TwilightWorld.of(e.world.provider.dimensionId) == null) return;
        EntityPlayer p = e.entityPlayer;
        if (!BlockBrazier.isLit(e.world, e.x, e.y, e.z)) {
            p.addChatMessage(new ChatComponentTranslation("dre.chat.campfire_unlit"));
            return;
        }
        // a campfire in someone else's region: the player would respawn right inside his base
        if (!com.divinerpgextended.perk.Protection.canBuild(p, e.x, e.y, e.z)) {
            e.setCanceled(true);
            return;
        }
        NBTTagCompound ps = persisted(p);
        int[] cur = ps.getIntArray(KEY);
        int[] here = { e.world.provider.dimensionId, e.x, e.y, e.z };
        if (java.util.Arrays.equals(cur, here)) {
            ps.removeTag(KEY);
            p.addChatMessage(new ChatComponentTranslation("dre.chat.campfire_forget"));
        } else {
            ps.setIntArray(KEY, here);
            p.addChatMessage(new ChatComponentTranslation("dre.chat.campfire_set"));
        }
        com.divinerpgextended.audit.Audit.log(p, "Campfire", ps.hasKey(KEY) ? "respawn point at campfire " + java.util.Arrays.toString(here)
            : "respawn point at campfire reset");
        e.setCanceled(true);
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** Respawn: if a campfire is remembered, stands and burns - move to it. */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.player instanceof EntityPlayerMP) || e.player.worldObj.isRemote) return;
        respawn((EntityPlayerMP) e.player);
    }

    public static boolean respawn(EntityPlayerMP p) {
        int[] c = persisted(p).getIntArray(KEY);
        if (c.length != 4) return false;
        WorldServer target = p.mcServer.worldServerForDimension(c[0]);
        if (target == null) return false;
        target.theChunkProviderServer.loadChunk(c[1] >> 4, c[3] >> 4);
        if (target.getBlock(c[1], c[2], c[3]) != ModBlocks.brazier || !BlockBrazier.isLit(target, c[1], c[2], c[3])) {
            persisted(p).removeTag(KEY);
            p.addChatMessage(new ChatComponentTranslation("dre.chat.campfire_lost"));
            return false;
        }
        final double x = c[1] + 1.5, y = c[2], z = c[3] + 0.5;
        if (p.dimension != c[0]) {
            p.mcServer.getConfigurationManager().transferPlayerToDimension(p, c[0], new Teleporter(target) {

                @Override
                public void placeInPortal(Entity en, double px, double py, double pz, float yaw) {
                    en.setLocationAndAngles(x, y, z, en.rotationYaw, en.rotationPitch);
                    en.motionX = en.motionY = en.motionZ = 0;
                }
            });
        }
        p.setPositionAndUpdate(x, y, z);
        p.fallDistance = 0;
        return true;
    }

    /** For selftest: the remembered campfire or null. */
    public static int[] get(EntityPlayer p) {
        int[] c = persisted(p).getIntArray(KEY);
        return c.length == 4 ? c : null;
    }
}
