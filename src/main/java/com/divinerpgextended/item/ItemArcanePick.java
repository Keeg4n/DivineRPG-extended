package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.ProgressSync;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Арканитовая кирка: единственное, чем ломаются спавнеры подземелий Арканы (у Divine они неломаемые). Продаёт Генерал
 * войны тем, кто победил всех боссов Мортума ({@link #MORTUM}). Заряженная ({@link #charged}) копает 3x3 (присев - один
 * блок): Генерал меняет на неё обычную и {@link #CHARGE_PRICE} арканиума после всех боссов Divine ({@link #DIVINE}).
 */
public class ItemArcanePick extends ItemPickaxe {

    public static final Item.ToolMaterial MATERIAL = EnumHelper.addToolMaterial("DRE_ARCANE", 3, 1561, 12F, 4F, 18);
    public static final int PRICE = 20, CHARGE_PRICE = 20;
    /** Боссы Мортума: после всех - кирка в продаже. */
    public static final Boss[] MORTUM = { Boss.TWILIGHT_DEMON, Boss.KAROT, Boss.ETERNAL_ARCHER, Boss.TWINS, Boss.SOUL_FIEND, Boss.VAMACHERON };
    /** Все боссы Divine, которых ведёт аддон: после них - заряженная кирка. */
    public static final Boss[] DIVINE = { Boss.ANCIENT_ENTITY, Boss.WATCHER, Boss.KING_OF_SCORCHERS, Boss.TWILIGHT_DEMON, Boss.KAROT, Boss.ETERNAL_ARCHER,
        Boss.TWINS, Boss.SOUL_FIEND, Boss.VAMACHERON, Boss.DRAMIX, Boss.PARASECTA, Boss.WRECK, Boss.RAGLOK, Boss.LADY_LUNA };
    /** Спавнеры Арканы (поля ArcanaBlocks). */
    private static final String[] SPAWNERS = { "dungenSpawnerPrisoner", "razorbackSpawner", "deathHoundSpawner", "deathcryxSpawner",
        "livingStatueSpawner", "roamerSpawner" };
    private static java.util.Set<Block> spawners;

    /** Заряженная: копает 3x3. */
    public final boolean charged;
    private boolean breaking;

    public ItemArcanePick(boolean charged) {
        super(MATERIAL);
        this.charged = charged;
        setUnlocalizedName(charged ? "dre.arcanePickCharged" : "dre.arcanePick");
        setTextureName(charged ? "divinerpgextended:arcane_pick_charged" : "divinerpgextended:arcane_pick");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static boolean mortumDone(EntityPlayer p) {
        for (Boss b : MORTUM) if (!ProgressSync.has(p, b)) return false;
        return true;
    }

    public static boolean divineDone(EntityPlayer p) {
        for (Boss b : DIVINE) if (!ProgressSync.has(p, b)) return false;
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

    private static java.util.Set<Block> locked;

    /**
     * Все неломаемые блоки Арканы (у Divine прочность бесконечная: кирпичи и камень подземелий, лампы, спавнеры...),
     * кроме портала. Снимается до {@link #makeBreakable}, пока прочность ещё родная.
     */
    public static synchronized java.util.Set<Block> locked() {
        if (locked != null) return locked;
        java.util.Set<Block> s = new java.util.HashSet<Block>(spawners());
        for (java.lang.reflect.Field f : net.divinerpg.utils.blocks.ArcanaBlocks.class.getFields()) {
            try {
                Object o = f.get(null);
                if (!(o instanceof Block) || o instanceof net.minecraft.block.BlockPortal || f.getName().toLowerCase().contains("portal")) continue;
                float h = (Float) cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(Block.class, (Block) o, "blockHardness", "field_149782_v");
                if (h < 0) s.add((Block) o);
            } catch (Throwable ignored) {}
        }
        return locked = s;
    }

    /** Неломаемые блоки Арканы ломаемы (у Divine прочность бесконечная) - но только этой киркой (BreakSpeed, BreakEvent). */
    public static void makeBreakable() {
        for (Block b : locked()) {
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
        return h != null && h.getItem() instanceof ItemArcanePick;
    }

    @Override
    public float getDigSpeed(ItemStack s, Block b, int meta) {
        return locked().contains(b) ? 6F : super.getDigSpeed(s, b, meta);
    }

    @Override
    public EnumRarity getRarity(ItemStack s) {
        return charged ? EnumRarity.rare : super.getRarity(s);
    }

    /** Заряженная ломает соседей 3x3 в плоскости, на которую смотрит игрок, обычным путём игрока (как кошмарный бур). */
    @Override
    public boolean onBlockStartBreak(ItemStack s, int x, int y, int z, EntityPlayer p) {
        World w = p.worldObj;
        if (!charged || w.isRemote || breaking || p.isSneaking() || !(p instanceof EntityPlayerMP)) return false;
        MovingObjectPosition hit = getMovingObjectPositionFromPlayer(w, p, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
        float center = w.getBlock(x, y, z).getBlockHardness(w, x, y, z);
        int side = hit.sideHit;
        breaking = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    int nx = x, ny = y, nz = z;
                    if (side == 0 || side == 1) {
                        nx += a;
                        nz += b;
                    } else if (side == 2 || side == 3) {
                        nx += a;
                        ny += b;
                    } else {
                        nz += a;
                        ny += b;
                    }
                    Block nb = w.getBlock(nx, ny, nz);
                    if (nb.getMaterial() == Material.air || nb.getMaterial().isLiquid()) continue;
                    float h = nb.getBlockHardness(w, nx, ny, nz);
                    if (h < 0 || h > center * 4 + 2) continue; // бедрок и портал не берёт, обсидиан вокруг камня не срывает
                    ((EntityPlayerMP) p).theItemInWorldManager.tryHarvestBlock(nx, ny, nz);
                }
            }
        } finally {
            breaking = false;
        }
        return false;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("dre.tooltip.arcane_pick"));
        if (charged) list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.arcane_pick_charged"));
    }

    public static class Handler {

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onSpeed(PlayerEvent.BreakSpeed e) {
            if (locked().contains(e.block) && !e.entityPlayer.capabilities.isCreativeMode && !holds(e.entityPlayer)) e.newSpeed = 0;
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onBreak(BlockEvent.BreakEvent e) {
            EntityPlayer p = e.getPlayer();
            if (p == null || !locked().contains(e.block) || p.capabilities.isCreativeMode || holds(p)) return;
            e.setCanceled(true);
        }

        /** Сломанный киркой блок Арканы выпадает сам (у многих из них своего дропа нет). */
        @SubscribeEvent
        public void onDrops(BlockEvent.HarvestDropsEvent e) {
            if (e.harvester == null || e.isSilkTouching || !e.drops.isEmpty() || !locked().contains(e.block) || !holds(e.harvester)) return;
            Item it = Item.getItemFromBlock(e.block);
            if (it != null) e.drops.add(new ItemStack(it, 1, e.block.damageDropped(e.blockMetadata)));
        }
    }
}
