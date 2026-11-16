package com.divinerpgextended.perk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Auto-smelting without Fortune for ores from {@link Config#smeltNoFortune} (rupee, arlemite). The Tinkers' Construct "Lava" modifier
 * smelts the broken ore itself and drops the result at the block center, and with Fortune on the tool it multiplies it (by 1..Fortune+1
 * times) - bypassing the block's normal drop. Here a block of such ore broken by a player is remembered until the end of the tick, and a smelt result
 * that appears in that block is cut down to a single smelt.
 */
public class SmeltNoFortune {

    private static Set<Block> ores;
    private final List<Broken> broken = new ArrayList<Broken>();

    private static class Broken {

        final int dim, x, y, z;
        final long time;
        final ItemStack smelted;

        Broken(int dim, int x, int y, int z, long time, ItemStack smelted) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
            this.smelted = smelted;
        }
    }

    /** Ores from the config (parsed on first access, when the blocks are already registered). */
    public static Set<Block> ores() {
        if (ores != null) return ores;
        Set<Block> s = new HashSet<Block>();
        for (String name : Config.smeltNoFortune) {
            Object b = Block.blockRegistry.getObject(name.trim());
            if (b instanceof Block && b != Blocks.air) s.add((Block) b);
        }
        return ores = s;
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (e.world.isRemote || com.divinerpgextended.perk.Protection.probing || !ores().contains(e.block)) return;
        Item drop = e.block.getItemDropped(e.blockMetadata, e.world.rand, 0);
        ItemStack smelted = drop == null ? null : FurnaceRecipes.smelting().getSmeltingResult(new ItemStack(drop, 1, e.block.damageDropped(e.blockMetadata)));
        if (smelted == null) return;
        long now = e.world.getTotalWorldTime();
        for (Iterator<Broken> it = broken.iterator(); it.hasNext();) if (it.next().time != now) it.remove();
        broken.add(new Broken(e.world.provider.dimensionId, e.x, e.y, e.z, now, smelted));
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (broken.isEmpty() || e.world.isRemote || !(e.entity instanceof EntityItem)) return;
        EntityItem ei = (EntityItem) e.entity;
        ItemStack s = ei.getEntityItem();
        long now = e.world.getTotalWorldTime();
        for (Iterator<Broken> it = broken.iterator(); it.hasNext();) {
            Broken b = it.next();
            if (b.time != now) {
                it.remove();
                continue;
            }
            if (b.dim != e.world.provider.dimensionId || MathHelper.floor_double(ei.posX) != b.x || MathHelper.floor_double(ei.posY) != b.y
                || MathHelper.floor_double(ei.posZ) != b.z) continue;
            if (s == null || s.getItem() != b.smelted.getItem() || s.getItemDamage() != b.smelted.getItemDamage()) continue;
            it.remove();
            if (s.stackSize <= b.smelted.stackSize) return;
            ItemStack one = s.copy();
            one.stackSize = b.smelted.stackSize;
            ei.setEntityItemStack(one);
            return;
        }
    }
}
