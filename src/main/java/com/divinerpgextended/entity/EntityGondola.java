package com.divinerpgextended.entity;

import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Ладья Харона: лодка из чёрного дерева. Лета её не разъедает и не трогает того, кто в ней сидит
 * ({@link com.divinerpgextended.block.BlockLethe}). Разбитая (ударом или о берег) выпадает ладьёй, а не досками.
 */
public class EntityGondola extends EntityBoat {

    private boolean droppedSelf;

    public EntityGondola(World w) {
        super(w);
    }

    public EntityGondola(World w, double x, double y, double z) {
        super(w, x, y, z);
    }

    /** Во сколько раз ладья с гребцом быстрее ванильной лодки. */
    public static final int SPEED = 2;

    /**
     * Ванильная лодка за тик проходит свою скорость один раз (и сама её ограничивает) - ладья с гребцом на плаву делает
     * ещё SPEED - 1 таких же шагов. Считается и на сервере, и у гребца на клиенте, как сама лодка.
     */
    @Override
    public void onUpdate() {
        super.onUpdate();
        if (isDead || riddenByEntity == null || !isInWater()) return;
        for (int i = 1; i < SPEED; i++) moveEntity(motionX, 0, motionZ);
    }

    /** Ванильная лодка роняет доски и палки (о берег) или лодку (ударом) - ладья роняет себя один раз. */
    @Override
    public EntityItem func_145778_a(Item item, int n, float off) {
        if (item == Item.getItemFromBlock(Blocks.planks) || item == Items.stick || item == Items.boat) {
            if (droppedSelf) return null;
            droppedSelf = true;
            return super.func_145778_a(ModItems.gondola, 1, off);
        }
        return super.func_145778_a(item, n, off);
    }
}
