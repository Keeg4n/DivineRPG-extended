package com.divinerpgextended.asm;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Вызовы из телепортирующих предметов Divine ({@link TeleportTransformer}). */
public final class TeleportHooks {

    private TeleportHooks() {}

    /** true - телепорт предметом запрещён (приват): предмет не срабатывает. Точка - куда предмет перенёс бы. */
    public static boolean deny(EntityPlayer p, Object item) {
        if (p == null || p.worldObj.isRemote) return false;
        double[] to = target(p, item);
        return to != null && !com.divinerpgextended.portal.TeleportGuard.check(p, to[0], to[1], to[2]);
    }

    static double[] target(EntityPlayer p, Object item) {
        if (item.getClass().getName().endsWith("ItemEnderScepter")) {
            // как у скипетра: луч взгляда на 50 блоков, точка у грани блока
            Vec3 from = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
            Vec3 look = p.getLookVec();
            MovingObjectPosition hit = p.worldObj.rayTraceBlocks(from, from.addVector(look.xCoord * 50, look.yCoord * 50, look.zCoord * 50));
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null; // скипетр и сам не сработает
            return new double[] { hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord };
        }
        // кристалл: на точку возрождения обычного мира
        net.minecraft.util.ChunkCoordinates s = net.minecraft.server.MinecraftServer.getServer().worldServerForDimension(0).getSpawnPoint();
        return new double[] { s.posX + 0.5, s.posY, s.posZ + 0.5 };
    }
}
