package com.divinerpgextended.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.divinerpgextended.block.BlockSkyRail;
import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.registry.ModItems;

/**
 * Вагонетка Апалачии: едет только по рельсам Апалачии, по полу, стенам и потолку. Идёт от центра одного рельса к
 * центру следующего: прямо, если дальше рельс; иначе поворачивает на соседний рельс (с пола на стену, со стены на
 * потолок и т.д.) - так замыкается кольцо. Едет, пока в ней сидят; рельсы кончились - разворачивается. Игрок на
 * потолке висит под вагонеткой.
 */
public class EntitySkyCart extends Entity {

    public static final double SPEED = 0.35;
    private static final int DW_FACE = 20;
    private static final int[][] DIRS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

    private int cx, cy, cz, nx, ny, nz;
    private int dx = 1, dy, dz;
    private double t;
    private boolean placed;
    /** Кто сидел на прошлом тике: слез - поставить в безопасное место (не в потолок и не в стену). */
    private Entity lastRider;

    public EntitySkyCart(World w) {
        super(w);
        setSize(0.98F, 0.7F);
        preventEntitySpawning = true;
    }

    public EntitySkyCart(World w, int x, int y, int z, float yaw) {
        this(w);
        cx = x;
        cy = y;
        cz = z;
        int f = net.minecraft.util.MathHelper.floor_double(yaw * 4.0F / 360.0F + 0.5D) & 3;
        int[][] h = { { 0, 0, 1 }, { -1, 0, 0 }, { 0, 0, -1 }, { 1, 0, 0 } };
        dx = h[f][0];
        dy = 0;
        dz = h[f][2];
        placed = true;
        pickNext(true);
        updatePos();
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(DW_FACE, Byte.valueOf((byte) 0));
    }

    /** Грань опоры текущего рельса (для отрисовки: на потолке - вверх колёсами). */
    public ForgeDirection face() {
        return ForgeDirection.getOrientation(dataWatcher.getWatchableObjectByte(DW_FACE));
    }

    private boolean rail(int x, int y, int z) {
        return worldObj.getBlock(x, y, z) == ModBlocks.skyRail;
    }

    /** Следующий рельс: прямо, иначе поворот на соседний (не назад); start - можно развернуться сразу. */
    private boolean pickNext(boolean start) {
        if (rail(cx + dx, cy + dy, cz + dz)) {
            nx = cx + dx;
            ny = cy + dy;
            nz = cz + dz;
            return true;
        }
        for (int[] d : DIRS) {
            if (d[0] == -dx && d[1] == -dy && d[2] == -dz && !start) continue;
            if (d[0] == dx && d[1] == dy && d[2] == dz) continue;
            if (rail(cx + d[0], cy + d[1], cz + d[2])) {
                dx = d[0];
                dy = d[1];
                dz = d[2];
                nx = cx + dx;
                ny = cy + dy;
                nz = cz + dz;
                return true;
            }
        }
        // тупик - разворот
        if (rail(cx - dx, cy - dy, cz - dz)) {
            dx = -dx;
            dy = -dy;
            dz = -dz;
            nx = cx + dx;
            ny = cy + dy;
            nz = cz + dz;
            return true;
        }
        nx = cx;
        ny = cy;
        nz = cz;
        return false;
    }

    /** Позиция между центрами рельсов, прижата к опоре. */
    private void updatePos() {
        double px = cx + 0.5 + (nx - cx) * t, py = cy + 0.5 + (ny - cy) * t, pz = cz + 0.5 + (nz - cz) * t;
        ForgeDirection sup = rail(cx, cy, cz) ? BlockSkyRail.support(worldObj.getBlockMetadata(cx, cy, cz)) : ForgeDirection.DOWN;
        if (!worldObj.isRemote) dataWatcher.updateObject(DW_FACE, Byte.valueOf((byte) sup.ordinal()));
        px += sup.offsetX * 0.3;
        py += sup.offsetY * 0.3 - 0.35;
        pz += sup.offsetZ * 0.3;
        setPosition(px, py, pz);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) return;
        if (!placed) {
            cx = (int) Math.floor(posX);
            cy = (int) Math.floor(posY + 0.4);
            cz = (int) Math.floor(posZ);
            placed = true;
            pickNext(true);
        }
        if (!rail(cx, cy, cz)) { // рельс убрали - вагонетка выпадает предметом
            dropAsItem();
            return;
        }
        if (lastRider != null && riddenByEntity != lastRider) safeDismount(lastRider);
        lastRider = riddenByEntity;
        if (riddenByEntity != null) {
            riddenByEntity.fallDistance = 0;
            t += SPEED;
            int guard = 0;
            while (t >= 1 && guard++ < 4) {
                t -= 1;
                cx = nx;
                cy = ny;
                cz = nz;
                if (!pickNext(false)) {
                    t = 0;
                    break;
                }
            }
        }
        updatePos();
    }

    /** Седок: на полу - сверху, на стене - рядом стоя, на потолке - висит под вагонеткой. */
    @Override
    public void updateRiderPosition() {
        if (riddenByEntity == null) return;
        ForgeDirection f = face();
        double y = f == ForgeDirection.UP ? posY - 1.3 : posY + 0.35;
        riddenByEntity.setPosition(posX - f.offsetX * 0.2, y + riddenByEntity.getYOffset(), posZ - f.offsetZ * 0.2);
    }

    /** Высадка: на потолке - под вагонетку, на стене - шаг от стены, на полу - рядом. */
    private void safeDismount(Entity e) {
        if (e.isDead || e.ridingEntity != null) return;
        ForgeDirection f = face();
        double x = cx + 0.5 - f.offsetX * 0.6, y = f == ForgeDirection.UP ? cy - 1.0 : cy, z = cz + 0.5 - f.offsetZ * 0.6;
        if (f == ForgeDirection.DOWN) y = cy + 0.1;
        e.fallDistance = 0;
        if (e instanceof net.minecraft.entity.EntityLivingBase) ((net.minecraft.entity.EntityLivingBase) e).setPositionAndUpdate(x, y, z);
        else e.setPosition(x, y, z);
    }

    @Override
    public double getMountedYOffset() {
        return 0.35;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    @Override
    public boolean interactFirst(EntityPlayer p) {
        if (riddenByEntity != null && riddenByEntity != p) return true;
        if (!worldObj.isRemote) p.mountEntity(riddenByEntity == p ? null : this);
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (worldObj.isRemote || isDead) return true;
        if (src.getEntity() instanceof EntityPlayer) dropAsItem();
        return true;
    }

    private void dropAsItem() {
        if (riddenByEntity != null) riddenByEntity.mountEntity(null);
        entityDropItem(new ItemStack(ModItems.skyCart), 0.5F);
        setDead();
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        int[] c = tag.getIntArray("Rail");
        if (c.length == 6) {
            cx = c[0];
            cy = c[1];
            cz = c[2];
            dx = c[3];
            dy = c[4];
            dz = c[5];
            placed = true;
            nx = cx + dx;
            ny = cy + dy;
            nz = cz + dz;
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setIntArray("Rail", new int[] { cx, cy, cz, dx, dy, dz });
    }

    public int[] railPos() {
        return new int[] { cx, cy, cz };
    }
}
