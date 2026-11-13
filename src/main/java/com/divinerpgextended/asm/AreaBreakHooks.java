package com.divinerpgextended.asm;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.vethea.Nightmare;

/**
 * Площадная ломка чужих модов ({@link AreaBreakTransformer}): каждый блок спрашивает обычное событие ломки, как ломка
 * рукой. Тогда его видят приваты (и Forge, и Bukkit-приваты гибридных серверов: их событие Forge шлёт из того же
 * ForgeHooks.onBlockBreakEvent), защищённые блоки аддона и кошмар Ветеи.
 */
public final class AreaBreakHooks {

    private AreaBreakHooks() {}

    /** Связанные инструменты Blood Magic: вместо одной защиты спавна (canMineBlock) - ещё и событие ломки. */
    public static boolean canMine(World w, EntityPlayer p, int x, int y, int z) {
        return w.canMineBlock(p, x, y, z) && mayBreak(w, p, x, y, z);
    }

    /** Можно ли игроку сломать этот блок: событие ломки не отменено. */
    public static boolean mayBreak(World w, EntityPlayer p, int x, int y, int z) {
        if (w == null || w.isRemote || !(p instanceof EntityPlayerMP) || w.isAirBlock(x, y, z)) return true;
        EntityPlayerMP mp = (EntityPlayerMP) p;
        if (mp.playerNetServerHandler == null) { // фальшивый игрок механизмов: без сетевых пакетов, только событие
            BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(x, y, z, w, w.getBlock(x, y, z), w.getBlockMetadata(x, y, z), mp);
            MinecraftForge.EVENT_BUS.post(e);
            return !e.isCanceled();
        }
        return !ForgeHooks.onBlockBreakEvent(w, mp.theItemInWorldManager.getGameType(), mp, x, y, z).isCanceled();
    }

    /** Treecapitator: во сне до прохождения чужим топором дерево целиком не валится (ломается только блок под рукой). */
    public static boolean treecapitatorDenied(EntityPlayer p) {
        return p != null && Nightmare.active(p) && !Nightmare.vethean(p.getHeldItem());
    }
}
