package com.divinerpgextended.asm;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.vethea.Nightmare;

/**
 * Area breaking by other mods ({@link AreaBreakTransformer}): each block asks the ordinary break event, like breaking
 * by hand. Then it is seen by protected regions (both Forge and Bukkit regions of hybrid servers: Forge sends their event from the same
 * ForgeHooks.onBlockBreakEvent), protected addon blocks and the Vethea nightmare.
 */
public final class AreaBreakHooks {

    private AreaBreakHooks() {}

    /** Blood Magic bound tools: instead of the single spawn protection (canMineBlock) - the break event as well. */
    public static boolean canMine(World w, EntityPlayer p, int x, int y, int z) {
        return w.canMineBlock(p, x, y, z) && mayBreak(w, p, x, y, z);
    }

    /** Whether the player may break this block: the break event is not cancelled. */
    public static boolean mayBreak(World w, EntityPlayer p, int x, int y, int z) {
        if (w == null || w.isRemote || !(p instanceof EntityPlayerMP) || w.isAirBlock(x, y, z)) return true;
        EntityPlayerMP mp = (EntityPlayerMP) p;
        if (mp.playerNetServerHandler == null) { // fake player of machines: no network packets, only the event
            BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(x, y, z, w, w.getBlock(x, y, z), w.getBlockMetadata(x, y, z), mp);
            MinecraftForge.EVENT_BUS.post(e);
            return !e.isCanceled();
        }
        return !ForgeHooks.onBlockBreakEvent(w, mp.theItemInWorldManager.getGameType(), mp, x, y, z).isCanceled();
    }

    /** Treecapitator: in the dream before completion, another's axe does not fell the whole tree (only the block under the hand breaks). */
    public static boolean treecapitatorDenied(EntityPlayer p) {
        return p != null && Nightmare.active(p) && !Nightmare.vethean(p.getHeldItem());
    }
}
