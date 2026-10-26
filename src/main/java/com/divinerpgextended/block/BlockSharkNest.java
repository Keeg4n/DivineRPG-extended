package com.divinerpgextended.block;

import java.util.List;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.divinerpgextended.entity.EntityLairCrab;

/**
 * Скрытое гнездо крабов под полом норы Карцинуса (снаружи выглядит как песок). Когда рядом игрок, выпускает норных
 * крабов на дно ходов вокруг, не больше двух на гнездо; иногда вылезает норный король крабов. Имя в реестре
 * осталось от акул (sharkNest), чтобы уже сгенерированные гнёзда не пропали.
 */
public class BlockSharkNest extends BlockContainer {

    public BlockSharkNest() {
        super(Material.sand);
        setHardness(2.5F);
        setStepSound(soundTypeSand);
        setBlockName("dre.sharkNest");
        setBlockTextureName("minecraft:sand");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new Tile();
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, net.minecraft.entity.EntityLivingBase e, net.minecraft.item.ItemStack s) {
        BlockBurrow.Convert.placed(w, x, y, z, e, s);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!world.isRemote && !player.capabilities.isCreativeMode) BlockBurrow.collapse(world, x, y, z);
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    public static class Tile extends TileEntity {

        private int tick;

        @Override
        public void updateEntity() {
            if (worldObj.isRemote || ++tick % 80 != 0) return;
            if (worldObj.getClosestPlayer(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, 20) == null) return;
            @SuppressWarnings("unchecked")
            List<EntityLairCrab> crabs = worldObj.getEntitiesWithinAABB(EntityLairCrab.class,
                AxisAlignedBB.getBoundingBox(xCoord - 10, yCoord - 6, zCoord - 10, xCoord + 11, yCoord + 7, zCoord + 11));
            if (crabs.size() >= 2) return;
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = xCoord + worldObj.rand.nextInt(13) - 6;
                int y = yCoord + worldObj.rand.nextInt(4) + 1;
                int z = zCoord + worldObj.rand.nextInt(13) - 6;
                // дно хода: снизу твёрдо, сверху два блока воды
                if (!worldObj.getBlock(x, y - 1, z)
                    .getMaterial()
                    .isSolid()
                    || worldObj.getBlock(x, y, z)
                        .getMaterial() != Material.water
                    || worldObj.getBlock(x, y + 1, z)
                        .getMaterial() != Material.water) continue;
                EntityLairCrab crab = worldObj.rand.nextInt(5) == 0 ? new EntityLairCrab.King(worldObj)
                    : new EntityLairCrab(worldObj);
                crab.setLocationAndAngles(x + 0.5, y, z + 0.5, worldObj.rand.nextFloat() * 360, 0);
                worldObj.spawnEntityInWorld(crab);
                return;
            }
        }
    }
}
