package com.divinerpgextended.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModBlocks;

/**
 * Сердце норы из версий 0.3.x, где Карцинус вылезал сам. Теперь его призывают на алтаре (на сервере один
 * самопризывающийся босс на логово - плохая идея), поэтому старое сердце при первом тике превращается в пол норы
 * с алтарём Карцинуса сверху. Новые логова его уже не ставят.
 */
public class BlockCarcinusHeart extends BlockContainer {

    public BlockCarcinusHeart() {
        super(Material.rock);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setBlockName("dre.carcinusHeart");
        setBlockTextureName("minecraft:sandstone_carved");
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, net.minecraft.entity.EntityLivingBase e, net.minecraft.item.ItemStack s) {
        BlockBurrow.Convert.placed(w, x, y, z, e, s);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new Tile();
    }

    public static class Tile extends TileEntity {

        @Override
        public void updateEntity() {
            if (worldObj.isRemote) return;
            int x = xCoord, y = yCoord, z = zCoord;
            worldObj.setBlock(x, y, z, ModBlocks.burrow, 1, 3);
            worldObj.setBlock(x, y + 1, z, ModBlocks.carcinusAltar, 0, 3);
        }
    }
}
