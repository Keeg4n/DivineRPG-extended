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
 * Apalachia Cart: rides on Apalachia Rails - on the floor, up the wall and on the ceiling (a blank for roller
 * coasters). The path is a polyline "middle of the rail - joint - middle of the next" ({@link SkyTrack}): straight if possible, otherwise
 * onto another neighboring rail; at a floor - wall corner - in an arc. With a player it rides like a vanilla one: only while "forward" is held and
 * where he looks (looking against the direction - a turn around); when released - it coasts by inertia. When the path ends - it goes back.
 * The rider sits inside and turns together with it (the client draws him along the path normal).
 */
public class EntitySkyCart extends Entity {

    public static final double SPEED = 0.35;
    /** Arcana Rails accelerate to MAX_SPEED by BOOST per tick; without them the speed decays to normal by DRAG per tick. */
    public static final double MAX_SPEED = 0.9, BOOST = 0.06, DRAG = 0.005;
    /** Braking with the "back" button per tick. */
    public static final double BRAKE = 0.03;
    private double speed = SPEED;
    /** Acceleration from a push (without a player inside): coasts by inertia and brakes, like a vanilla minecart. */
    private double momentum;
    /** It was moving on the previous tick (throttle or inertia): starting off by the view direction - only from a standstill. */
    private boolean moving;
    public static final double PUSH = 0.25, FRICTION = 0.006;
    /** The middle of the cart above the wheel contact point; the rider's eyes above the middle (like a vanilla minecart). */
    public static final double BODY = 0.4, EYES = 0.82, SEAT = 0.8;
    private static final int DW_N = 20, DW_F = 23; // normal and heading: three floats each
    /**
     * The client brings the cart to the server position over this many ticks. Vanilla 4 at full speed (0.9 blocks per tick) cut
     * the outer edge of the loop with a chord - the rider's camera passed through a block; over 2 ticks the path holds, no jerks.
     */
    public static final int CLIENT_LERP = 2;

    private int cx, cy, cz, nx, ny, nz;
    private double t;
    private boolean placed;
    /** Direction of the first step on placement (by the player's view). */
    private int sx = 1, sz;
    /** Who was sitting on the previous tick: got off - put in a safe place (not in the ceiling or a wall). */
    private Entity lastRider;
    /** Client: the normal on the previous tick - for a smooth turn between ticks. */
    public float[] prevNormal = { 0, 1, 0 };
    /** Client: where to bring the cart (position from the server), over how many ticks; smoothed normal and heading. */
    private double lx, ly, lz;
    private int lerp;
    private float[] smoothN = { 0, 1, 0 }, smoothF = { 1, 0, 0 };

    public EntitySkyCart(World w) {
        super(w);
        setSize(0.98F, 0.7F);
        preventEntitySpawning = true;
        noClip = true; // rides right up against the wall and ceiling
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

    /** Path normal; on the client - smoothed following the position. */
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

    /** First step: by the player's view if there is a path that way, otherwise any. */
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

    /** Arrived at (cx,cy,cz) from (px,py,pz): onward straight, otherwise to another exit; dead end - back (false). */
    private boolean pickNext(int px, int py, int pz) {
        int dx = cx - px, dy = cy - py, dz = cz - pz;
        boolean flat = dx != 0 || dz != 0;
        int[] best = null;
        for (int[] o : SkyTrack.exits(worldObj, cx, cy, cz)) {
            if (cx + o[0] == px && cy + o[1] == py && cz + o[2] == pz) continue; // back
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

    /** Position on the polyline: the first half of the step - from the middle of the rail to the joint, the second - from the joint to the middle of the next. */
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
        // the middle of the cart is above the contact point along the normal; posY - bottom of the frame
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
        if (!rail(cx, cy, cz)) { // the rail was removed - the cart drops as an item
            com.divinerpgextended.debug.Debug.noteNear(this, 32, "Apalachia Cart: no rail below (" + cx + " " + cy + " " + cz + ") - dropped as an item");
            dropAsItem();
            return;
        }
        if (lastRider != null && riddenByEntity != lastRider) safeDismount(lastRider);
        if (riddenByEntity instanceof EntityPlayer && riddenByEntity != lastRider) { // seated - stands until "forward" is pressed
            momentum = 0;
            moving = false;
        }
        lastRider = riddenByEntity;
        EntityPlayer pilot = riddenByEntity instanceof EntityPlayer ? (EntityPlayer) riddenByEntity : null;
        // the player throttles with the "forward" button, by view: standing - starts that way, moving against the view - turns around
        boolean driven = pilot != null && pilot.moveForward > 0;
        if (driven && !moving) { // starting: where it looks (does not turn while moving - on a loop the view is not along the path)
            net.minecraft.util.Vec3 look = pilot.getLookVec();
            if (t == 0) { // at the middle of the rail - choose the exit anew
                nx = cx;
                ny = cy;
                nz = cz;
            }
            push(look.xCoord, look.yCoord, look.zCoord);
            if (nx == cx && ny == cy && nz == cz) driven = false; // there is no path where it looks
        } else if (!driven && pilot != null && moving && momentum < 0.01 && speed > 0.01) momentum = speed; // released the throttle while moving - coasting by inertia
        // (only while moving: a standing cart has the speed from the previous ride or the initial one - before, it started by itself as soon as you sat down)
        // brake: "back" while moving kills inertia, the cart stops and stands until pushed again
        if (pilot != null && !driven && pilot.moveForward < 0 && moving) momentum = momentum > BRAKE ? momentum - BRAKE : 0;
        if (pilot != null && !driven) speed = Math.max(0, Math.min(speed, momentum));
        if (riddenByEntity != null) riddenByEntity.fallDistance = 0;
        moving = driven || momentum > 0.01;
        if (moving) {
            boolean still = nx == cx && ny == cy && nz == cz;
            if (!driven && pilot == null && still || !rail(nx, ny, nz)) { // stood without a path or the next rail was removed - search anew
                nx = cx;
                ny = cy;
                nz = cz;
                t = 0;
                start();
            }
            if (driven) {
                speed = SkyTrack.boost(worldObj, cx, cy, cz) ? Math.min(MAX_SPEED, Math.max(SPEED, speed) + BOOST) : Math.max(SPEED, speed - DRAG);
                momentum = 0;
            }
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
                        com.divinerpgextended.debug.Debug.noteNear(this, 32, "Apalachia Cart: end of track at " + cx + " " + cy + " " + cz + ", turning back");
                        if (nx == cx && ny == cy && nz == cz) t = 0;
                        if (!driven) momentum *= 0.5; // bounce off a dead end
                    }
                }
            }
        }
        updatePos();
    }

    /** The rider sits inside: eyes above the middle along the path normal (on a wall - away from the wall, on a ceiling - downward). */
    @Override
    public void updateRiderPosition() {
        if (riddenByEntity == null) return;
        float[] n = normal();
        double my = posY + height / 2;
        // for a player on the client posY is the eyes, on the server it is the feet: getYOffset() accounts for this (like a vanilla minecart)
        riddenByEntity.setPosition(posX + n[0] * EYES, my + n[1] * EYES - 1.62 + 0.5 + riddenByEntity.getYOffset(), posZ + n[2] * EYES);
    }

    /** The face of the support along the normal (for dismounting). */
    private ForgeDirection face() {
        float[] n = normal();
        float ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ay >= ax && ay >= az) return n[1] > 0 ? ForgeDirection.DOWN : ForgeDirection.UP;
        if (ax >= az) return n[0] > 0 ? ForgeDirection.WEST : ForgeDirection.EAST;
        return n[2] > 0 ? ForgeDirection.NORTH : ForgeDirection.SOUTH;
    }

    /** Dismount: on a ceiling - under the cart, on a wall - a step from the wall, on the floor - next to it. */
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
     * Client: do not set the server position at once, but bring it over several ticks (like a vanilla minecart) - otherwise
     * the cart jerked. We do not call the vanilla version: it pushes the entity up out of blocks, while a cart on
     * a wall and ceiling is pressed against them - it was tossed to the top of the wall together with the rider.
     */
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int steps) {
        lx = x;
        ly = y;
        lz = z;
        lerp = CLIENT_LERP;
        setRotation(yaw, pitch);
    }

    /** Client: step of approach to the server position; the normal and heading catch up to their values at the same rate. */
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

    /** It can be pushed (otherwise players and mobs pass through without touching it). */
    @Override
    public boolean canBePushed() {
        return true;
    }

    /**
     * Like a vanilla minecart: bumped - it rolls along the rail in the direction of the push; a moving empty cart seats
     * the mob it bumped into (not a player or an iron golem).
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

    /** Push in a direction (dx, dy, dz): acceleration along the rail; pushed against the direction - goes back. */
    public void push(double dx, double dy, double dz) {
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-4) return;
        dx /= l;
        dy /= l;
        dz /= l;
        if (nx == cx && ny == cy && nz == cz) { // was standing: go to the neighbor that is best by the push
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
            if (f[0] * dx + f[1] * dy + f[2] * dz < -0.2) { // against the direction - turn around on the same segment
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

    /** Acceleration from pushes (for measurements and the selftest). */
    public double momentum() {
        return momentum;
    }

    /** Middle button in creative - the cart item. */
    @Override
    public ItemStack getPickedResult(net.minecraft.util.MovingObjectPosition target) {
        return new ItemStack(ModItems.skyCart);
    }

    @Override
    public boolean interactFirst(EntityPlayer p) {
        if (riddenByEntity != null && riddenByEntity != p) return true;
        if (!worldObj.isRemote) {
            p.mountEntity(riddenByEntity == p ? null : this);
            com.divinerpgextended.debug.Debug.note(p, "Apalachia Cart: " + (riddenByEntity == p ? "boarded" : "got off") + " at " + cx + " " + cy + " " + cz
                + ", next " + nx + " " + ny + " " + nz + ", exits " + SkyTrack.exits(worldObj, cx, cy, cz).size());
        }
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (worldObj.isRemote || isDead) return true;
        if (src.getEntity() instanceof EntityPlayer) {
            if (((EntityPlayer) src.getEntity()).capabilities.isCreativeMode) { // like a vanilla one: in creative it simply disappears
                if (riddenByEntity != null) riddenByEntity.mountEntity(null);
                setDead();
            } else dropAsItem();
        }
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
        c = tag.getIntArray("Rail"); // old format: only the current rail
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
