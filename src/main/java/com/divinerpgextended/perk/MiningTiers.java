package com.divinerpgextended.perk;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.items.VanillaItemsTools;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.world.twilight.TwilightCrafts;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Уровни кирок сумеречных миров: 0 - божественная, 1 - Эдема ... 5 - Мортума. Руду мира ломает только кирка этого
 * мира или выше (первую кирку мира собирают из душ его мобов), сумеречный камень в мире - кирка прошлого мира или
 * выше (в Эдеме - божественная).
 */
public class MiningTiers {

    private static final String HINT = "dreMiningHint";

    /** Уровень кирки (-1 - не кирка цепочки). */
    public static int tier(ItemStack s) {
        if (s == null) return -1;
        Item i = s.getItem();
        if (i == VanillaItemsTools.divinePickaxe) return 0;
        for (TwilightWorld w : TwilightWorld.values()) if (TwilightCrafts.tools(w)[0] == i) return w.ordinal() + 1;
        return -1;
    }

    /** Какой уровень кирки нужен для блока здесь (-1 - без ограничений). */
    public static int required(Block b, int dim) {
        TwilightWorld[] ws = TwilightWorld.values();
        for (TwilightWorld w : ws) if (b == w.ore()) return w.ordinal() + 1;
        if (b == TwilightBlocks.twilightStone) {
            TwilightWorld here = TwilightWorld.of(dim);
            if (here != null) return here.ordinal();
        }
        return -1;
    }

    private static boolean blocked(EntityPlayer p, Block b) {
        if (!Config.miningTiers || p == null || p.capabilities.isCreativeMode) return false;
        int need = required(b, p.dimension);
        return need >= 0 && tier(p.getHeldItem()) < need;
    }

    @SubscribeEvent
    public void onSpeed(PlayerEvent.BreakSpeed e) {
        if (!blocked(e.entityPlayer, e.block)) return;
        e.newSpeed = 0;
        if (e.entityPlayer.worldObj.isRemote) return;
        long now = e.entityPlayer.worldObj.getTotalWorldTime();
        if (now - e.entityPlayer.getEntityData().getLong(HINT) < 60) return;
        e.entityPlayer.getEntityData().setLong(HINT, now);
        int need = required(e.block, e.entityPlayer.dimension);
        Item pick = need == 0 ? VanillaItemsTools.divinePickaxe : TwilightCrafts.tools(TwilightWorld.values()[need - 1])[0];
        e.entityPlayer.addChatMessage(new ChatComponentTranslation("dre.chat.need_pick", new ItemStack(pick).getDisplayName()));
    }

    /** Подстраховка: мгновенная ломка (зачарования, эффекты) тоже не проходит. */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (blocked(e.getPlayer(), e.block)) e.setCanceled(true);
    }
}
