package com.divinerpgextended.boss;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.divinerpgextended.block.BlockArenaAltar;
import com.divinerpgextended.block.BlockBossAltar;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Алтари боссов (и запертые сундуки) неразрушимы, но в сборках есть инструменты, которые ломают и неразрушимое (пробуждённая ихорная кирка
 * Thaumic Tinkerer и подобные). Сломанный алтарь ничего не роняет (сам блок), а если инструмент всё же отдал его предметом -
 * предмет на земле не появляется, а из инвентаря игрока в выживании исчезает. В творческом режиме алтари можно носить и ставить.
 * Здесь же раз в 5 секунд ставится алтарь орды в гнездовьях Айрако, где его ещё нет, и засыпаются пустоты под полом старых
 * арен Мортума ({@link com.divinerpgextended.world.twilight.MortumArenas#heal}).
 */
public class AltarGuard {

    public static boolean altar(Block b) {
        return b instanceof BlockBossAltar || b instanceof BlockArenaAltar || b instanceof com.divinerpgextended.block.BlockLockedChest;
    }

    public static boolean altar(ItemStack s) {
        return s != null && s.getItem() != null && altar(Block.getBlockFromItem(s.getItem()));
    }

    /** Убрать алтари из инвентаря игрока. Сколько стопок убрано. */
    public static int sweep(EntityPlayer p) {
        int n = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (!altar(inv[i])) continue;
            inv[i] = null;
            n++;
        }
        if (n > 0) p.inventoryContainer.detectAndSendChanges();
        return n;
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (!e.world.isRemote && e.entity instanceof EntityItem && altar(((EntityItem) e.entity).getEntityItem())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.ticksExisted % 20 != 0) return;
        if (!p.capabilities.isCreativeMode) sweep(p);
        if (p.ticksExisted % 100 == 0) {
            com.divinerpgextended.world.WorldGenOuterLairs.keepAyeracoAltar(p);
            com.divinerpgextended.world.twilight.MortumArenas.heal(p); // и пустоты под старыми аренами Мортума
        }
    }
}
