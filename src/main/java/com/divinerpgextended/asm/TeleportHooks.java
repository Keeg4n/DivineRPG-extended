package com.divinerpgextended.asm;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Calls from Divine teleporting items ({@link TeleportTransformer}). */
public final class TeleportHooks {

    private TeleportHooks() {}

    /** true - teleporting by an item is forbidden (protected region): the item does not work. The point is where the item would take you. */
    public static boolean deny(EntityPlayer p, Object item) {
        if (p == null || p.worldObj.isRemote) return false;
        if (com.divinerpgextended.perk.EnderGear.pinned(p)) { // pinned by an ender weapon
            p.addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.chat.teleport_pinned"));
            return true;
        }
        double[] to = target(p, item);
        return to != null && !com.divinerpgextended.portal.TeleportGuard.check(p, to[0], to[1], to[2]);
    }

    static double[] target(EntityPlayer p, Object item) {
        if (item.getClass().getName().endsWith("ItemEnderScepter")) {
            // like the scepter: a view ray of 50 blocks, the point at the block face
            Vec3 from = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
            Vec3 look = p.getLookVec();
            MovingObjectPosition hit = p.worldObj.rayTraceBlocks(from, from.addVector(look.xCoord * 50, look.yCoord * 50, look.zCoord * 50));
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null; // the scepter would not work either
            return new double[] { hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord };
        }
        // crystal: to the Overworld respawn point
        net.minecraft.util.ChunkCoordinates s = net.minecraft.server.MinecraftServer.getServer().worldServerForDimension(0).getSpawnPoint();
        return new double[] { s.posX + 0.5, s.posY, s.posZ + 0.5 };
    }
}
