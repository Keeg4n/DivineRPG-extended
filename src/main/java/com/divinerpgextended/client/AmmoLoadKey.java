package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.perk.Ammo;
import com.divinerpgextended.perk.AmmoLoad;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Присесть и ПКМ оружием - зарядить его из инвентаря. Только если есть чем и есть куда: иначе это обычный выстрел
 * (стрелять присев можно, когда оружие полное или патронов в инвентаре нет).
 */
@SideOnly(Side.CLIENT)
public class AmmoLoadKey {

    private long sent = -1;

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (!p.worldObj.isRemote || e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        if (p != Minecraft.getMinecraft().thePlayer || !p.isSneaking() || Ammo.loadable(p) <= 0) return;
        e.setCanceled(true);
        // клик по блоку даёт два события подряд (по блоку и в воздух) - просим один раз за тик
        long t = p.worldObj.getTotalWorldTime();
        if (t == sent) return;
        sent = t;
        ColdHandler.NET.sendToServer(new AmmoLoad.Load());
    }
}
