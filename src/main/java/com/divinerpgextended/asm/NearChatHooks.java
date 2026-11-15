package com.divinerpgextended.asm;

/** Divine messages that went to the whole server ({@link NearChatTransformer}): only to players near the source. */
public class NearChatHooks {

    /** How far it is heard (blocks). */
    public static final int RADIUS = 64;

    public static void near(net.minecraft.util.ChatComponentTranslation msg, Object source) {
        net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) source;
        if (te.getWorldObj() == null) return;
        for (Object o : te.getWorldObj().playerEntities) {
            net.minecraft.entity.player.EntityPlayer p = (net.minecraft.entity.player.EntityPlayer) o;
            if (p.getDistanceSq(te.xCoord + 0.5, te.yCoord + 0.5, te.zCoord + 0.5) <= RADIUS * RADIUS) p.addChatMessage(msg);
        }
    }
}
