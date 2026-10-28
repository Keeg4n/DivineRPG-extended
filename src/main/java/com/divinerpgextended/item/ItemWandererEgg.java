package com.divinerpgextended.item;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.divinerpgextended.entity.EntityWanderer;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Яйцо призыва Странника. В сумеречном мире, в его родном биоме, - Странник этого мира со своими сделками; в любом другом
 * месте (обычный мир, чужой биом после ритуала Гайи и т.п.) - Странник без мира: в зелёной робе и без сделок.
 */
public class ItemWandererEgg extends Item {

    @SideOnly(Side.CLIENT)
    private IIcon overlay;

    public ItemWandererEgg() {
        setUnlocalizedName("dre.wandererEgg");
        setTextureName("spawn_egg");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    /** Мир, чьего Странника тут можно призвать, или null: мир сумеречный и биом в точке - родной биом этого мира. */
    public static TwilightWorld homeAt(World w, int x, int z) {
        TwilightWorld tw = TwilightWorld.of(w.provider.dimensionId);
        if (tw == null) return null;
        BiomeGenBase here = w.getBiomeGenForCoords(x, z), nat = w.provider.worldChunkMgr.getBiomeGenAt(x, z);
        return here == nat ? tw : null;
    }

    @Override
    public boolean onItemUse(ItemStack s, EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        x += Facing.offsetsXForSide[side];
        y += Facing.offsetsYForSide[side];
        z += Facing.offsetsZForSide[side];
        EntityWanderer v = new EntityWanderer(w);
        v.setLocationAndAngles(x + 0.5, y, z + 0.5, w.rand.nextFloat() * 360F, 0F);
        TwilightWorld home = homeAt(w, x, z);
        if (home != null) v.setHome(home);
        else {
            v.setStray();
            p.addChatMessage(new ChatComponentTranslation("dre.chat.wanderer_stray"));
        }
        if (s.hasDisplayName()) v.setCustomNameTag(s.getDisplayName());
        w.spawnEntityInWorld(v);
        if (!p.capabilities.isCreativeMode) s.stackSize--;
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
        return pass > 0 ? overlay : itemIcon;
    }

    /** Цвета яйца: плащ и кожа Странника. */
    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack s, int pass) {
        return pass == 0 ? 0x2E6B2A : 0xC8936A;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        itemIcon = reg.registerIcon("spawn_egg");
        overlay = reg.registerIcon("spawn_egg_overlay");
    }
}
