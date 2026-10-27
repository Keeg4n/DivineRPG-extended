package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.ProgressSync;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Арканитовая кирка: единственное, чем ломаются спавнеры подземелий Арканы (у Divine они неломаемые). Продаёт Генерал
 * войны тем, кто победил всех боссов Мортума ({@link #MORTUM}).
 */
public class ItemArcanePick extends ItemPickaxe {

    public static final Item.ToolMaterial MATERIAL = EnumHelper.addToolMaterial("DRE_ARCANE", 3, 1561, 12F, 4F, 18);
    public static final int PRICE = 40;
    /** Боссы Мортума: после всех - кирка в продаже. */
    public static final Boss[] MORTUM = { Boss.TWILIGHT_DEMON, Boss.KAROT, Boss.ETERNAL_ARCHER, Boss.TWINS, Boss.SOUL_FIEND, Boss.VAMACHERON };
    /** Спавнеры Арканы (поля ArcanaBlocks). */
    private static final String[] SPAWNERS = { "dungenSpawnerPrisoner", "razorbackSpawner", "deathHoundSpawner", "deathcryxSpawner",
        "livingStatueSpawner", "roamerSpawner" };
    private static java.util.Set<Block> spawners;

    public ItemArcanePick() {
        super(MATERIAL);
        setUnlocalizedName("dre.arcanePick");
        setTextureName("divinerpgextended:arcane_pick");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static boolean mortumDone(EntityPlayer p) {
        for (Boss b : MORTUM) if (!ProgressSync.has(p, b)) return false;
        return true;
    }

    public static synchronized java.util.Set<Block> spawners() {
        if (spawners != null) return spawners;
        java.util.Set<Block> s = new java.util.HashSet<Block>();
        for (String n : SPAWNERS) {
            try {
                Object o = net.divinerpg.utils.blocks.ArcanaBlocks.class.getField(n).get(null);
                if (o instanceof Block) s.add((Block) o);
            } catch (Throwable ignored) {}
        }
        return spawners = s;
    }

    /** Спавнеры Арканы ломаемы (у Divine прочность бесконечная) - но только этой киркой (BreakSpeed, BreakEvent). */
    public static void makeBreakable() {
        for (Block b : spawners()) {
            try {
                cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(Block.class, b, 5.0F, "blockHardness", "field_149782_v");
                b.setHarvestLevel("pickaxe", 3);
            } catch (Throwable t) {
                com.divinerpgextended.DivineRPGExtended.LOG.warn("Спавнер Арканы не сделан ломаемым: " + t);
            }
        }
    }

    static boolean holds(EntityPlayer p) {
        ItemStack h = p.getHeldItem();
        return h != null && h.getItem() == ModItems.arcanePick;
    }

    @Override
    public float getDigSpeed(ItemStack s, Block b, int meta) {
        return spawners().contains(b) ? 6F : super.getDigSpeed(s, b, meta);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("dre.tooltip.arcane_pick"));
    }

    public static class Handler {

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onSpeed(PlayerEvent.BreakSpeed e) {
            if (spawners().contains(e.block) && !e.entityPlayer.capabilities.isCreativeMode && !holds(e.entityPlayer)) e.newSpeed = 0;
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onBreak(BlockEvent.BreakEvent e) {
            EntityPlayer p = e.getPlayer();
            if (p == null || !spawners().contains(e.block) || p.capabilities.isCreativeMode || holds(p)) return;
            e.setCanceled(true);
        }
    }
}
