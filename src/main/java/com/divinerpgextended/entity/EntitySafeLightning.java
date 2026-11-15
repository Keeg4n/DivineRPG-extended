package com.divinerpgextended.entity;

import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * Weapon lightning (Strike Serenade, Storm Sword, Storm Bow): hits enemies and other players where PvP is allowed, but not the one who summoned it
 * ({@link com.divinerpgextended.perk.EarlyWeapons#onStruck}),
 * does not burn loot and experience on the ground and does not ignite blocks. Minecraft lightning places fire in the constructor and on every flash
 * - we look at where there was no fire before it and put out the new one in the same place.
 */
public class EntitySafeLightning extends EntityLightningBolt {

    private static final ThreadLocal<boolean[]> BEFORE = new ThreadLocal<boolean[]>();
    /** Who is currently striking with lightning: Divine weapons record the player before their lightning ({@link com.divinerpgextended.asm.LightningTransformer}). */
    private static final ThreadLocal<EntityPlayer> CASTER = new ThreadLocal<EntityPlayer>();

    /** The player who summoned the lightning (null - unknown): it does not hit them, and hits other players only where PvP is allowed. */
    public EntityPlayer caster;

    public EntitySafeLightning(World w, double x, double y, double z) {
        super(snapshot(w, x, y, z), x, y, z);
        unfire(w, x, y, z, BEFORE.get());
        BEFORE.remove();
        caster = CASTER.get();
    }

    public EntitySafeLightning(World w, double x, double y, double z, EntityPlayer by) {
        this(w, x, y, z);
        caster = by;
    }

    /** Called from a Divine weapon at the start of the method with lightning. */
    public static void cast(EntityPlayer p) {
        CASTER.set(p);
    }

    @Override
    public void onUpdate() {
        boolean[] was = worldObj.isRemote ? null : fire(worldObj, posX, posY, posZ);
        super.onUpdate();
        unfire(worldObj, posX, posY, posZ, was);
    }

    private static World snapshot(World w, double x, double y, double z) {
        BEFORE.set(w == null || w.isRemote ? null : fire(w, x, y, z));
        return w;
    }

    /** Where fire burns in the 3x3x3 cube around the point (lightning places fire only there). */
    static boolean[] fire(World w, double x, double y, double z) {
        boolean[] f = new boolean[27];
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z), i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            f[i++] = w.blockExists(bx + dx, by + dy, bz + dz) && w.getBlock(bx + dx, by + dy, bz + dz) == Blocks.fire;
        return f;
    }

    /** Put out fire that was not there before the lightning. */
    static void unfire(World w, double x, double y, double z, boolean[] was) {
        if (was == null || w == null || w.isRemote) return;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z), i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (!was[i++] && w.blockExists(bx + dx, by + dy, bz + dz) && w.getBlock(bx + dx, by + dy, bz + dz) == Blocks.fire) w.setBlockToAir(bx + dx, by + dy, bz + dz);
        }
    }
}
