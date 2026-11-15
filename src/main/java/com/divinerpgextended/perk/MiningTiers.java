package com.divinerpgextended.perk;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.items.VanillaItemsTools;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.world.twilight.TwilightCrafts;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Pickaxe tiers of the twilight worlds: 0 - divine (and the divine shickaxe), 1 - Eden ... 5 - Mortum, 6 - halite pickaxe and
 * multitool (break everything). A world's ore is broken only by the pickaxe of that
 * world or higher (the first pickaxe of a world is assembled from the souls of its mobs), twilight stone in a world - by the pickaxe of the previous world or
 * higher (in Eden - divine). A weak pickaxe does not break the block and clangs off it with sparks.
 */
public class MiningTiers {

    private static final String TINK = "dreMiningTink";

    /** Tooltip line of a chain pickaxe: which ore and stone it takes; null - not a chain pickaxe or tiers are off. */
    public static String tooltip(ItemStack s) {
        int t = tier(s);
        if (t < 0 || !Config.miningTiers) return null;
        TwilightWorld[] w = TwilightWorld.values();
        // above all twilight worlds (halite): its own line, it has no world number
        if (t > w.length) return StatCollector.translateToLocal("dre.tooltip.pick_all");
        String stone = StatCollector.translateToLocal("dre.world." + w[Math.min(t, w.length - 1)].key());
        return t == 0 ? StatCollector.translateToLocalFormatted("dre.tooltip.pick_stone", stone)
            : StatCollector.translateToLocalFormatted("dre.tooltip.pick_tier", StatCollector.translateToLocal("dre.world." + w[t - 1].key()), stone);
    }

    /** Pickaxe tier (-1 - not a chain pickaxe). */
    public static int tier(ItemStack s) {
        if (s == null) return -1;
        Item i = s.getItem();
        if (i == VanillaItemsTools.divinePickaxe || i == VanillaItemsTools.divineShickaxe) return 0;
        if (com.divinerpgextended.halite.HaliteTools.minesJungle(s)) return TwilightWorld.values().length + 1; // halite ones are above Mortum
        for (TwilightWorld w : TwilightWorld.values()) if (TwilightCrafts.tools(w)[0] == i) return w.ordinal() + 1;
        return -1;
    }

    /** Which pickaxe tier is needed for the block here (-1 - no restriction). */
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
        // no text: the pickaxe clangs off a block that is too hard and strikes sparks
        double x = e.x + 0.5, y = e.y + 0.5, z = e.z + 0.5;
        double dx = e.entityPlayer.posX - x, dy = e.entityPlayer.posY + e.entityPlayer.getEyeHeight() - y, dz = e.entityPlayer.posZ - z;
        double d = Math.max(0.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
        x += dx / d * 0.6; y += dy / d * 0.6; z += dz / d * 0.6; // sparks on the block face toward the player
        e.entityPlayer.worldObj.playSoundEffect(x, y, z, "random.anvil_land", 0.25F, 1.8F + e.entityPlayer.worldObj.rand.nextFloat() * 0.2F);
        ((net.minecraft.world.WorldServer) e.entityPlayer.worldObj).func_147487_a("crit", x, y, z, 4, 0.3, 0.3, 0.3, 0.15);
    }

    /** Safety net: instant breaking (enchantments, effects) does not go through either. */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (blocked(e.getPlayer(), e.block)) e.setCanceled(true);
    }
}
