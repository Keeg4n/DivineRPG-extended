package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.BlockTorch;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.world.World;

import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Факел сумеречного мира: палка мира с пылью мира, светит ярче обычного и искрит цветом мира. Адский факел - из стержня
 * ифрита. Монстров Divine гонит только в своём мире ({@link com.divinerpgextended.boss.TorchWards}).
 */
public class BlockWorldTorch extends BlockTorch {

    /** Мир факела (null - адский). */
    public final TwilightWorld world;
    private final float r, g, b;

    public BlockWorldTorch(TwilightWorld world, int color) {
        this(world, world.key(), color);
    }

    /** Адский факел. */
    public BlockWorldTorch(int color) {
        this(null, "nether", color);
    }

    private BlockWorldTorch(TwilightWorld world, String key, int color) {
        this.world = world;
        r = (color >> 16 & 255) / 255F;
        g = (color >> 8 & 255) / 255F;
        b = (color & 255) / 255F;
        setLightLevel(1.0F);
        setHardness(0.0F);
        setStepSound(soundTypeWood);
        setBlockName("dre.worldTorch." + key);
        setBlockTextureName("divinerpgextended:world_torch_" + key);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    /** Измерение, где факел гонит монстров. */
    public int dimension() {
        return world != null ? world.dimension() : -1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon(getTextureName());
    }

    /** Частицы мира над головкой факела (как у ванильного, со сдвигом к стене): {@link com.divinerpgextended.client.TorchFX}. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World w, int x, int y, int z, Random rand) {
        int meta = w.getBlockMetadata(x, y, z);
        double px = x + 0.5, py = y + 0.7, pz = z + 0.5, up = 0.22, off = 0.27;
        if (meta == 1) { px -= off; py += up; }
        else if (meta == 2) { px += off; py += up; }
        else if (meta == 3) { pz -= off; py += up; }
        else if (meta == 4) { pz += off; py += up; }
        com.divinerpgextended.client.TorchFX.spawn(w, world, px, py, pz, rand);
    }
}
