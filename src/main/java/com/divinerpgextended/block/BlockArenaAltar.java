package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.divinerpgextended.world.twilight.MortumArenas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Алтарь арены Мортума: кристалл босса работает только по клику на него ({@link com.divinerpgextended.boss.LairSummons}),
 * сам кристалл Divine и вызывает босса над алтарём. Пустой рукой - подсказка, чей это алтарь. Неразрушим в выживании.
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
            if (t != null) player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_arena_" + t.key()));
        }
        return true;
    }
}
