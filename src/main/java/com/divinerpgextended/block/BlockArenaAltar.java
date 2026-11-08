package com.divinerpgextended.block;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.divinerpgextended.world.twilight.MortumArenas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Алтарь арены Мортума: кристалл босса работает только по клику на него ({@link com.divinerpgextended.boss.LairSummons}),
 * сам кристалл Divine и вызывает босса над алтарём. Пустой рукой - подсказка, чей это алтарь; в творческом режиме пустая рука
 * сама вызывает босса арены (кристалл не нужен). Неразрушим в выживании; если его всё же сломал особый инструмент - ничего не роняет.
 */
public class BlockArenaAltar extends Block {

    public BlockArenaAltar() {
        super(Material.rock);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightLevel(0.8F);
        setBlockName("dre.arenaAltar");
        setBlockTextureName("minecraft:redstone_block");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    /** Над алтарём поднимаются багровые искры. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        for (int i = 0; i < 2; i++)
            world.spawnParticle("reddust", x + rand.nextDouble(), y + 1.0 + rand.nextDouble() * 0.8, z + rand.nextDouble(), 0.6, 0.02, 0.05);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        if (player.getHeldItem() != null) return false; // кристалл Divine сам вызовет босса
        if (!world.isRemote) {
            int[] c = MortumArenas.nearest(world, x, z);
            MortumArenas.Type t = c == null ? null : MortumArenas.typeAt(world, c);
            if (t != null && player.capabilities.isCreativeMode) summon(world, x, y, z, player, t, c);
            else if (t != null) player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_arena_" + t.key()));
        }
        return true;
    }

    /** Вызов без кристалла (творческий режим): кристалл арены применяется сам - туда же, куда его поставил бы игрок. true - вызван. */
    public static boolean summon(World world, int x, int y, int z, EntityPlayer player, MortumArenas.Type t, int[] c) {
        net.minecraft.util.AxisAlignedBB area = net.minecraft.util.AxisAlignedBB.getBoundingBox(c[0] - 48, y - 24, c[2] - 48, c[0] + 48, y + 32, c[2] + 48);
        for (Object o : world.getEntitiesWithinAABB(net.minecraft.entity.boss.IBossDisplayData.class, area))
            if (((net.minecraft.entity.Entity) o).isEntityAlive()) {
                player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_busy"));
                return false;
            }
        Item crystal = MortumArenas.crystal(t);
        int[] s = MortumArenas.summonBlock(world, t, c);
        if (s == null) s = new int[] { x, y, z };
        return crystal.onItemUse(new ItemStack(crystal), player, world, s[0], s[1], s[2], 1, 0.5F, 1.0F, 0.5F);
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return null;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int meta) {
        return false;
    }
}
