package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.block.BlockSkyRail;
import com.divinerpgextended.registry.ModBlocks;

/**
 * Apalachia Rails: on the floor they are placed as regular bending rails ({@link com.divinerpgextended.block.BlockApalachiaRail}: turns,
 * slopes, any minecarts), on a wall and ceiling - as hanging ones ({@link BlockSkyRail}); at the foot of a wall with a rail - an arc.
 * Arcana Rails (arcana) are the same but speed the cart up: on the floor - Divine's Arcana Rails (like powered rails without redstone), on
 * a wall and ceiling - hanging Arcana Rails.
 * Deliberately not an ItemBlock: the client checks the ItemBlock of a floor rail (needs a floor under the rail) and a click on a wall did not reach the server.
 */
public class ItemApalachiaRails extends Item {

    private final boolean arcana;

    public ItemApalachiaRails(boolean arcana) {
        this.arcana = arcana;
        setUnlocalizedName(arcana ? "dre.arcanaRails" : "dre.apalachiaRails");
        setTextureName(arcana ? "divinerpgextended:arcana_rails" : "divinerpgextended:apalachia_rails");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public boolean onItemUse(ItemStack s, EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        Block place = arcana ? ModBlocks.skyBoostRail : ModBlocks.skyRail;
        if (side == 1) {
            Block clicked = w.getBlock(x, y, z);
            int ty = clicked == Blocks.snow_layer || clicked.isReplaceable(w, x, y, z) ? y : y + 1;
            // under a rail on a wall - an arc (hanging on the floor), otherwise a floor rail
            Block above = w.getBlock(x, ty + 1, z);
            boolean underWall = above instanceof BlockSkyRail && BlockSkyRail.support(w.getBlockMetadata(x, ty + 1, z)).offsetY == 0;
            if (!underWall) place = arcana ? net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails : ModBlocks.apalachiaRail;
        }
        ItemStack tmp = new ItemStack(place);
        if (!tmp.getItem().onItemUse(tmp, p, w, x, y, z, side, hx, hy, hz)) return false;
        if (tmp.stackSize == 0 && !p.capabilities.isCreativeMode) s.stackSize--;
        return true;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(arcana ? "dre.tooltip.arcana_rails" : "dre.tooltip.apalachia_rails"));
    }
}
