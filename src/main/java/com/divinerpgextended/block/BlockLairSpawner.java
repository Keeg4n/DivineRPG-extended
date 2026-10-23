package com.divinerpgextended.block;

import java.util.List;

import net.minecraft.block.BlockMobSpawner;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Спавнер логова: выглядит и крутится как обычный, но мобов логова ставит при любом свете. Обычный спавнер
 * спрашивает моба, можно ли ему здесь появиться, и Глакон на свету отказывается - а логово освещено целиком, чтобы
 * в нём не появлялись чужие мобы (на серверах мобы Grimoire of Gaia живут во всех мирах). Работает, только когда
 * игрок в 16 блоках; рядом не больше {@link #MAX_NEAR} своих мобов.
 */
public class BlockLairSpawner extends BlockMobSpawner {

    public static final int MAX_NEAR = 6, RANGE = 16;

    public BlockLairSpawner() {
        setHardness(5.0F);
        setStepSound(soundTypeMetal);
        setBlockName("dre.lairSpawner");
        setBlockTextureName("mob_spawner");
        disableStats();
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new Tile();
    }

    public static class Tile extends TileEntityMobSpawner {

        public Tile() {
            ReflectionHelper.setPrivateValue(TileEntityMobSpawner.class, this, new Logic(this), "field_145882_a");
        }
    }

    static class Logic extends MobSpawnerBaseLogic {

        private final Tile te;
        private int wait = 20;

        Logic(Tile te) {
            this.te = te;
        }

        @Override
        public void updateSpawner() {
            World w = getSpawnerWorld();
            if (w.isRemote) {
                super.updateSpawner(); // клиент: только вращение и искры
                return;
            }
            double cx = te.xCoord + 0.5, cy = te.yCoord + 0.5, cz = te.zCoord + 0.5;
            if (w.getClosestPlayer(cx, cy, cz, RANGE) == null) return;
            if (--wait > 0) return;
            wait = 200 + w.rand.nextInt(600);
            String name = getEntityNameToSpawn();
            Class<?> cls = (Class<?>) EntityList.stringToClassMapping.get(name);
            if (cls == null) return;
            List<?> near = w.getEntitiesWithinAABB(cls, AxisAlignedBB.getBoundingBox(cx - 8, cy - 4, cz - 8, cx + 8, cy + 4, cz + 8));
            int room = MAX_NEAR - near.size(), spawned = 0;
            for (int n = 0; n < 16 && spawned < Math.min(2, room); n++) { // в узких ходах многие точки в стене
                Entity e = EntityList.createEntityByName(name, w);
                if (!(e instanceof EntityLiving)) return;
                double x = te.xCoord + w.rand.nextInt(7) - 3 + 0.5, y = te.yCoord + 1 + w.rand.nextInt(2), z = te.zCoord + w.rand.nextInt(7) - 3 + 0.5;
                e.setLocationAndAngles(x, y, z, w.rand.nextFloat() * 360F, 0F);
                // место свободно, не в жидкости; свет не важен
                if (!w.checkNoEntityCollision(e.boundingBox) || !w.getCollidingBoundingBoxes(e, e.boundingBox).isEmpty() || w.isAnyLiquid(e.boundingBox)) continue;
                w.spawnEntityInWorld(e);
                ((EntityLiving) e).onSpawnWithEgg(null);
                ((EntityLiving) e).spawnExplosionParticle();
                spawned++;
            }
            if (spawned > 0) func_98267_a(1);
        }

        @Override
        public void func_98267_a(int id) {
            te.getWorldObj().addBlockEvent(te.xCoord, te.yCoord, te.zCoord, te.getBlockType(), id, 0);
        }

        @Override
        public World getSpawnerWorld() {
            return te.getWorldObj();
        }

        @Override
        public int getSpawnerX() {
            return te.xCoord;
        }

        @Override
        public int getSpawnerY() {
            return te.yCoord;
        }

        @Override
        public int getSpawnerZ() {
            return te.zCoord;
        }
    }
}
