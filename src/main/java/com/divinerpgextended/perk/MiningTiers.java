package com.divinerpgextended.perk;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.items.VanillaItemsTools;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.world.twilight.TwilightCrafts;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Уровни кирок сумеречных миров: 0 - божественная (и божественный шикакс), 1 - Эдема ... 5 - Мортума, 6 - халитовые кирка и
 * мультиинструмент (берут всё). Руду мира ломает только кирка этого
 * мира или выше (первую кирку мира собирают из душ его мобов), сумеречный камень в мире - кирка прошлого мира или
 * выше (в Эдеме - божественная). Слабая кирка не ломает блок и звенит о него с искрами.
 */
public class MiningTiers {

    private static final String TINK = "dreMiningTink";

    /** Уровень кирки (-1 - не кирка цепочки). */
    public static int tier(ItemStack s) {
        if (s == null) return -1;
        Item i = s.getItem();
        if (i == VanillaItemsTools.divinePickaxe || i == VanillaItemsTools.divineShickaxe) return 0;
        if (com.divinerpgextended.halite.HaliteTools.minesJungle(s)) return TwilightWorld.values().length + 1; // халитовые - выше Мортума
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
        if (now - e.entityPlayer.getEntityData().getLong(TINK) < 5) return;
        e.entityPlayer.getEntityData().setLong(TINK, now);
        // без текста: кирка звенит о слишком твёрдый блок и высекает искры
        double x = e.x + 0.5, y = e.y + 0.5, z = e.z + 0.5;
        double dx = e.entityPlayer.posX - x, dy = e.entityPlayer.posY + e.entityPlayer.getEyeHeight() - y, dz = e.entityPlayer.posZ - z;
        double d = Math.max(0.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
        x += dx / d * 0.6; y += dy / d * 0.6; z += dz / d * 0.6; // искры на грани блока со стороны игрока
        e.entityPlayer.worldObj.playSoundEffect(x, y, z, "random.anvil_land", 0.25F, 1.8F + e.entityPlayer.worldObj.rand.nextFloat() * 0.2F);
        ((net.minecraft.world.WorldServer) e.entityPlayer.worldObj).func_147487_a("crit", x, y, z, 4, 0.3, 0.3, 0.3, 0.15);
    }

    /** Подстраховка: мгновенная ломка (зачарования, эффекты) тоже не проходит. */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (blocked(e.getPlayer(), e.block)) e.setCanceled(true);
    }
}
