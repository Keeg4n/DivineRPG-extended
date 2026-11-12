package com.divinerpgextended.entity;

import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * Молния оружия (Ударная серенада, Меч бури, Грозовой лук): бьёт только врагов ({@link com.divinerpgextended.perk.EarlyWeapons#onStruck}),
 * не жжёт добычу и опыт на земле и не поджигает блоки. Огонь молния Minecraft ставит в конструкторе и при каждой вспышке
 * - смотрим, где огня не было до неё, и гасим новый там же.
 */
public class EntitySafeLightning extends EntityLightningBolt {

    private static final ThreadLocal<boolean[]> BEFORE = new ThreadLocal<boolean[]>();

    public EntitySafeLightning(World w, double x, double y, double z) {
        super(snapshot(w, x, y, z), x, y, z);
        unfire(w, x, y, z, BEFORE.get());
        BEFORE.remove();
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

    /** Где горит огонь в кубе 3x3x3 у точки (молния ставит огонь только в нём). */
    static boolean[] fire(World w, double x, double y, double z) {
        boolean[] f = new boolean[27];
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z), i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            f[i++] = w.blockExists(bx + dx, by + dy, bz + dz) && w.getBlock(bx + dx, by + dy, bz + dz) == Blocks.fire;
        return f;
    }

    /** Погасить огонь, которого не было до молнии. */
    static void unfire(World w, double x, double y, double z, boolean[] was) {
        if (was == null || w == null || w.isRemote) return;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z), i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (!was[i++] && w.blockExists(bx + dx, by + dy, bz + dz) && w.getBlock(bx + dx, by + dy, bz + dz) == Blocks.fire) w.setBlockToAir(bx + dx, by + dy, bz + dz);
        }
    }
}
