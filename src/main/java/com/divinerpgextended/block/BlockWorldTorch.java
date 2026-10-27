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
 * ифрита, факелы Iceika и Ветеи - из ледяных осколков и серого жемчуга. Монстров Divine гонит только в своём мире
 * ({@link com.divinerpgextended.boss.TorchWards}).
 */
public class BlockWorldTorch extends BlockTorch {

    /** Мир факела (null - не сумеречный: ад, Iceika, Ветея - см. {@link #key}). */
    public final TwilightWorld world;
    /** Ключ факела: мир сумерек, "nether", "iceika" или "vethea" (текстура, название, частицы). */
    public final String key;
    private final float r, g, b;

    public BlockWorldTorch(TwilightWorld world, int color) {
        this(world, world.key(), color);
    }

    /** Адский факел. */
    public BlockWorldTorch(int color) {
        this(null, "nether", color);
    }

    /** Факел другого мира Divine: "iceika" или "vethea". */
    public BlockWorldTorch(String key, int color) {
        this(null, key, color);
    }

    private BlockWorldTorch(TwilightWorld world, String key, int color) {
        this.world = world;
        this.key = key;
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
        if (world != null) return world.dimension();
        if ("iceika".equals(key)) return net.divinerpg.utils.config.ConfigurationHelper.iceika;
        if ("vethea".equals(key)) return net.divinerpg.utils.config.ConfigurationHelper.vethea;
        return -1;
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
        com.divinerpgextended.client.TorchFX.spawn(w, world, key, px, py, pz, rand);
    }
}
