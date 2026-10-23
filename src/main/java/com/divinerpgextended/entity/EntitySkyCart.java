package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.divinerpgextended.block.BlockSkyRail;
import com.divinerpgextended.block.SkyTrack;
import com.divinerpgextended.registry.ModItems;

/**
 * Вагонетка Апалачии: едет по рельсам Апалачии - по полу, вверх по стене и по потолку (заготовка под американские
 * горки). Путь - ломаная "середина рельса - стык - середина следующего" ({@link SkyTrack}): прямо, если можно, иначе
 * на другой соседний рельс; на углу пол - стена - горкой. Едет, пока в ней сидят; путь кончился - едет обратно.
 * Седок сидит внутри и поворачивается вместе с ней (клиент рисует его по нормали пути).
 */
public class EntitySkyCart extends Entity {

    public static final double SPEED = 0.35;
    /** Рельсы арканы разгоняют до MAX_SPEED на BOOST за тик; без них скорость спадает к обычной на DRAG за тик. */
    public static final double MAX_SPEED = 0.9, BOOST = 0.06, DRAG = 0.005;
    private double speed = SPEED;
    /** Разгон от толчка (без игрока внутри): катится по инерции и тормозит, как ванильная вагонетка. */
    private double momentum;
    public static final double PUSH = 0.25, FRICTION = 0.006;
    /** Середина вагонетки над точкой касания колёс; глаза седока над серединой (как у ванильной вагонетки). */
    public static final double BODY = 0.4, EYES = 0.82, SEAT = 0.8;
    private static final int DW_N = 20, DW_F = 23; // нормаль и курс: по три float

    private int cx, cy, cz, nx, ny, nz;
    private double t;
    private boolean placed;
    /** Направление первого шага при постановке (по взгляду игрока). */
    private int sx = 1, sz;
    /** Кто сидел на прошлом тике: слез - поставить в безопасное место (не в потолок и не в стену). */
    private Entity lastRider;
    /** Клиент: нормаль на прошлом тике - для плавного поворота между тиками. */
    public float[] prevNormal = { 0, 1, 0 };
    /** Клиент: куда подвести вагонетку (позиция с сервера), за сколько тиков; сглаженные нормаль и курс. */
    private double lx, ly, lz;
    private int lerp;
    private float[] smoothN = { 0, 1, 0 }, smoothF = { 1, 0, 0 };

    public EntitySkyCart(World w) {
        super(w);
        setSize(0.98F, 0.7F);
        preventEntitySpawning = true;
        noClip = true; // едет вплотную к стене и потолку
    }

    public EntitySkyCart(World w, int x, int y, int z, float yaw) {
        this(w);
        cx = nx = x;
        cy = ny = y;
        cz = nz = z;
        int f = net.minecraft.util.MathHelper.floor_double(yaw * 4.0F / 360.0F + 0.5D) & 3;
        int[][] h = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };
        sx = h[f][0];
        sz = h[f][1];
        placed = true;
        start();
        updatePos();
    }

    @Override
    protected void entityInit() {
        for (int i = 0; i < 6; i++) dataWatcher.addObject(DW_N + i, Float.valueOf(i == 1 ? 1 : 0));
    }

    /** Нормаль пути; на клиенте - сглаженная вслед за позицией. */
    public float[] normal() {
        return worldObj.isRemote ? smoothN.clone() : watchedNormal();
    }

    private float[] watchedNormal() {
        return new float[] { dataWatcher.getWatchableObjectFloat(DW_N), dataWatcher.getWatchableObjectFloat(DW_N + 1), dataWatcher.getWatchableObjectFloat(DW_N + 2) };
    }

    public float[] course() {
        return worldObj.isRemote ? smoothF.clone() : watchedCourse();
    }

    private float[] watchedCourse() {
        return new float[] { dataWatcher.getWatchableObjectFloat(DW_F), dataWatcher.getWatchableObjectFloat(DW_F + 1), dataWatcher.getWatchableObjectFloat(DW_F + 2) };
    }

    private boolean rail(int x, int y, int z) {
        return BlockSkyRail.track(worldObj.getBlock(x, y, z));
    }

    /** Первый шаг: по взгляду игрока, если туда есть путь, иначе любой. */
    private void start() {
        List<int[]> ex = SkyTrack.exits(worldObj, cx, cy, cz);
        int[] pick = null;
        for (int[] o : ex) if (pick == null && o[0] == sx && o[2] == sz) pick = o;
        if (pick == null && !ex.isEmpty()) pick = ex.get(0);
        if (pick != null) {
            nx = cx + pick[0];
            ny = cy + pick[1];
            nz = cz + pick[2];
        }
    }

    /** Доехали до (cx,cy,cz) из (px,py,pz): дальше прямо, иначе на другой выход; тупик - обратно (false). */
    private boolean pickNext(int px, int py, int pz) {
        int dx = cx - px, dy = cy - py, dz = cz - pz;
        boolean flat = dx != 0 || dz != 0;
        int[] best = null;
        for (int[] o : SkyTrack.exits(worldObj, cx, cy, cz)) {
            if (cx + o[0] == px && cy + o[1] == py && cz + o[2] == pz) continue; // назад
            if (!rail(cx + o[0], cy + o[1], cz + o[2])) continue;
            boolean straight = flat ? o[0] == dx && o[2] == dz : o[0] == 0 && o[2] == 0 && o[1] == dy;
            if (best == null || straight) best = o;
            if (straight) break;
        }
        if (best != null) {
            nx = cx + best[0];
            ny = cy + best[1];
            nz = cz + best[2];
            return true;
        }
        boolean back = rail(px, py, pz);
        nx = back ? px : cx;
        ny = back ? py : cy;
        nz = back ? pz : cz;
        return false;
    }

    private static double[] lerp(double[] a, double[] b, double k) {
        return new double[] { a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k };
    }

    private static double[] unit(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-6 ? new double[] { 0, 1, 0 } : new double[] { a[0] / l, a[1] / l, a[2] / l };
    }

    /** Позиция на ломаной: первая половина шага - от середины рельса к стыку, вторая - от стыка к середине следующего. */
    private void updatePos() {
        double[] ca = SkyTrack.contact(worldObj, cx, cy, cz), na = SkyTrack.normal(worldObj, cx, cy, cz);
        double[] p, n, f;
        if (nx == cx && ny == cy && nz == cz) {
            p = ca;
            n = na;
            f = new double[] { sx, 0, sz };
        } else {
            double[] cb = SkyTrack.contact(worldObj, nx, ny, nz), nb = SkyTrack.normal(worldObj, nx, ny, nz);
            double[] j = SkyTrack.joint(worldObj, cx, cy, cz, nx, ny, nz), nj = unit(lerp(na, nb, 0.5));
            if (t < 0.5) {
                p = lerp(ca, j, t * 2);
                n = unit(lerp(na, nj, t * 2));
                f = new double[] { j[0] - ca[0], j[1] - ca[1], j[2] - ca[2] };
            } else {
                p = lerp(j, cb, t * 2 - 1);
                n = unit(lerp(nj, nb, t * 2 - 1));
                f = new double[] { cb[0] - j[0], cb[1] - j[1], cb[2] - j[2] };
            }
        }
        f = unit(f);
        if (!worldObj.isRemote) for (int i = 0; i < 3; i++) {
            dataWatcher.updateObject(DW_N + i, Float.valueOf((float) n[i]));
            dataWatcher.updateObject(DW_F + i, Float.valueOf((float) f[i]));
        }
        // середина вагонетки - над точкой касания по нормали; posY - низ рамки
        setPosition(p[0] + n[0] * BODY, p[1] + n[1] * BODY - height / 2, p[2] + n[2] * BODY);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            clientSmooth();
            return;
        }
        if (!placed) {
            cx = nx = (int) Math.floor(posX);
            cy = ny = (int) Math.floor(posY + 0.4);
            cz = nz = (int) Math.floor(posZ);
            placed = true;
            start();
        }
        if (!rail(cx, cy, cz)) { // рельс убрали - вагонетка выпадает предметом
            com.divinerpgextended.debug.Debug.noteNear(this, 32, "Вагонетка Апалачии: под ней нет рельса (" + cx + " " + cy + " " + cz + ") - выпала предметом");
            dropAsItem();
            return;
        }
        if (lastRider != null && riddenByEntity != lastRider) safeDismount(lastRider);
        lastRider = riddenByEntity;
        boolean driven = riddenByEntity instanceof net.minecraft.entity.player.EntityPlayer; // игрок едет сам
        if (riddenByEntity != null) riddenByEntity.fallDistance = 0;
        if (driven || momentum > 0.01) {
            boolean still = nx == cx && ny == cy && nz == cz;
            if (still || !rail(nx, ny, nz)) { // стояла без пути или следующий рельс убрали - искать заново
                nx = cx;
                ny = cy;
                nz = cz;
                t = 0;
                start();
            }
            if (driven) speed = SkyTrack.boost(worldObj, cx, cy, cz) ? Math.min(MAX_SPEED, speed + BOOST) : Math.max(SPEED, speed - DRAG);
            else speed = momentum = SkyTrack.boost(worldObj, cx, cy, cz) ? Math.min(MAX_SPEED, momentum + BOOST) : Math.max(0, momentum - FRICTION);
            if (nx != cx || ny != cy || nz != cz) {
                t += speed;
                int guard = 0;
                while (t >= 1 && guard++ < 4) {
                    t -= 1;
                    int px = cx, py = cy, pz = cz;
                    cx = nx;
                    cy = ny;
                    cz = nz;
                    if (!pickNext(px, py, pz)) {
                        com.divinerpgextended.debug.Debug.noteNear(this, 32, "Вагонетка Апалачии: конец пути в " + cx + " " + cy + " " + cz + ", обратно");
                        if (nx == cx && ny == cy && nz == cz) t = 0;
                        if (!driven) momentum *= 0.5; // отскок от тупика
                    }
                }
            }
        }
        updatePos();
    }

    /** Седок сидит внутри: глаза - над серединой по нормали пути (на стене - в сторону от стены, на потолке - вниз). */
    @Override
    public void updateRiderPosition() {
        if (riddenByEntity == null) return;
        float[] n = normal();
        double my = posY + height / 2;
        // у игрока на клиенте posY - глаза, на сервере - ноги: getYOffset() это учитывает (как у ванильной вагонетки)
        riddenByEntity.setPosition(posX + n[0] * EYES, my + n[1] * EYES - 1.62 + 0.5 + riddenByEntity.getYOffset(), posZ + n[2] * EYES);
    }

    /** Грань опоры по нормали (для высадки). */
    private ForgeDirection face() {
        float[] n = normal();
        float ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ay >= ax && ay >= az) return n[1] > 0 ? ForgeDirection.DOWN : ForgeDirection.UP;
        if (ax >= az) return n[0] > 0 ? ForgeDirection.WEST : ForgeDirection.EAST;
        return n[2] > 0 ? ForgeDirection.NORTH : ForgeDirection.SOUTH;
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

    /**
     * Клиент: позицию с сервера не ставить сразу, а подводить за несколько тиков (как ванильная вагонетка) - иначе
     * вагонетка дёргалась рывками. Ванильную версию не зовём: она выталкивает сущность вверх из блоков, а вагонетка на
     * стене и потолке прижата к ним - её подбрасывало на верх стены вместе с седоком.
     */
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int steps) {
        lx = x;
        ly = y;
        lz = z;
        lerp = steps + 1;
        setRotation(yaw, pitch);
    }

    /** Клиент: шаг подвода к позиции с сервера; нормаль и курс догоняют свои значения с той же скоростью. */
    private void clientSmooth() {
        prevNormal = smoothN.clone();
        float[] tn = watchedNormal(), tf = watchedCourse();
        float k = lerp > 0 ? 1.0F / lerp : 1;
        if (lerp > 0) {
            setPosition(posX + (lx - posX) / lerp, posY + (ly - posY) / lerp, posZ + (lz - posZ) / lerp);
            lerp--;
        }
        for (int i = 0; i < 3; i++) {
            smoothN[i] += (tn[i] - smoothN[i]) * k;
            smoothF[i] += (tf[i] - smoothF[i]) * k;
        }
    }

    @Override
    public double getMountedYOffset() {
        return height / 2 + EYES - 1.62 + 0.5;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    /** Её можно толкать (иначе игрок и мобы проходят сквозь, не задевая). */
    @Override
    public boolean canBePushed() {
        return true;
    }

    /**
     * Как ванильная вагонетка: задели - покатилась вдоль рельса в сторону толчка; движущаяся пустая вагонетка сажает
     * в себя моба, которого задела (не игрока и не железного голема).
     */
    @Override
    public void applyEntityCollision(net.minecraft.entity.Entity e) {
        if (worldObj.isRemote || e == riddenByEntity || e.ridingEntity == this || riddenByEntity instanceof EntityPlayer) return;
        if (e instanceof net.minecraft.entity.EntityLivingBase && !(e instanceof EntityPlayer) && !(e instanceof net.minecraft.entity.monster.EntityIronGolem)
            && !(e instanceof net.minecraft.entity.boss.IBossDisplayData) && riddenByEntity == null && e.ridingEntity == null && momentum > 0.05) {
            e.mountEntity(this);
            return;
        }
        push(posX - e.posX, posY - e.posY, posZ - e.posZ);
    }

    /** Толчок в сторону (dx, dy, dz): разгон вдоль рельса; толкнули против хода - едет обратно. */
    public void push(double dx, double dy, double dz) {
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-4) return;
        dx /= l;
        dy /= l;
        dz /= l;
        if (nx == cx && ny == cy && nz == cz) { // стояла: ехать к соседу, который лучше всего по толчку
            int[] best = null;
            double bd = 0.2;
            for (int[] o : SkyTrack.exits(worldObj, cx, cy, cz)) {
                double ol = Math.sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2]), d = (o[0] * dx + o[1] * dy + o[2] * dz) / ol;
                if (d > bd) {
                    bd = d;
                    best = o;
                }
            }
            if (best == null) return;
            nx = cx + best[0];
            ny = cy + best[1];
            nz = cz + best[2];
            t = 0;
        } else {
            float[] f = watchedCourse();
            if (f[0] * dx + f[1] * dy + f[2] * dz < -0.2) { // против хода - разворот на том же отрезке
                int ox = cx, oy = cy, oz = cz;
                cx = nx;
                cy = ny;
                cz = nz;
                nx = ox;
                ny = oy;
                nz = oz;
                t = 1 - t;
            }
        }
        momentum = Math.max(momentum, PUSH);
    }

    /** Разгон от толчков (для замеров и автотеста). */
    public double momentum() {
        return momentum;
    }

    @Override
    public boolean interactFirst(EntityPlayer p) {
        if (riddenByEntity != null && riddenByEntity != p) return true;
        if (!worldObj.isRemote) {
            p.mountEntity(riddenByEntity == p ? null : this);
            com.divinerpgextended.debug.Debug.note(p, "Вагонетка Апалачии: " + (riddenByEntity == p ? "сел" : "слез") + " на " + cx + " " + cy + " " + cz
                + ", дальше " + nx + " " + ny + " " + nz + ", выходов " + SkyTrack.exits(worldObj, cx, cy, cz).size());
        }
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
        int[] c = tag.getIntArray("Path");
        if (c.length == 6) {
            cx = c[0];
            cy = c[1];
            cz = c[2];
            nx = c[3];
            ny = c[4];
            nz = c[5];
            t = tag.getDouble("T");
            speed = Math.max(SPEED, tag.getDouble("Speed"));
            placed = true;
            return;
        }
        c = tag.getIntArray("Rail"); // старый формат: только текущий рельс
        if (c.length == 6) {
            cx = nx = c[0];
            cy = ny = c[1];
            cz = nz = c[2];
            placed = true;
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setIntArray("Path", new int[] { cx, cy, cz, nx, ny, nz });
        tag.setDouble("T", t);
        tag.setDouble("Speed", speed);
    }

    public double speed() {
        return speed;
    }

    public int[] railPos() {
        return new int[] { cx, cy, cz };
    }
}
