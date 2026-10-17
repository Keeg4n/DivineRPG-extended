package com.divinerpgextended.perk;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Добыча сумеречной руды - свойство всей сумеречной брони, по частям: каждая часть сета любого мира даёт в среднем
 * {@link #PER_PIECE} осколка сверху, полный сет - +3, как сет Эдема в Divine. Так при переходе на сет следующего мира
 * (часть съедается в крафте) бонус не пропадает и второй сет фармить не надо. Полный сет Эдема обрабатывает Divine.
 */
public class TwilightOreBonus {

    public static final float PER_PIECE = 0.75F;

    /** Сколько частей сумеречной брони (любых миров) надето. */
    public static int pieces(EntityPlayer p) {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(3 - i);
            if (s != null && isTwilightArmor(s.getItem(), i)) n++;
        }
        return n;
    }

    public static boolean isTwilightArmor(Item item, int slot) {
        for (TwilightWorld w : TwilightWorld.values()) if (w.armor()[slot] == item) return true;
        return false;
    }

    private static boolean isOre(net.minecraft.block.Block b) {
        for (TwilightWorld w : TwilightWorld.values()) if (w.ore() == b) return true;
        return false;
    }

    @SubscribeEvent
    public void onDrops(BlockEvent.HarvestDropsEvent e) {
        if (!Config.worldCrafts || e.harvester == null || e.isSilkTouching || e.drops.isEmpty() || !isOre(e.block)) return;
        if (HomeArmor.wears(e.harvester, TwilightWorld.EDEN, false)) return; // Divine уже добавил свои 3 осколка
        float extra = pieces(e.harvester) * PER_PIECE;
        int n = (int) extra + (e.world.rand.nextFloat() < extra - (int) extra ? 1 : 0);
        ItemStack frag = e.drops.get(0);
        for (int i = 0; i < n; i++) e.drops.add(frag.copy());
    }
}
