package com.divinerpgextended.entity;

import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.world.World;

import com.divinerpgextended.registry.ModItems;

/**
 * Charon's Barge: a boat made of black wood. Lethe does not corrode it and does not harm the one sitting in it
 * ({@link com.divinerpgextended.block.BlockLethe}). When broken (by a hit or against the shore) it drops as a barge, not as planks.
 */
public class EntityGondola extends EntityBoat {

    private boolean droppedSelf;

    public EntityGondola(World w) {
        super(w);
    }

    public EntityGondola(World w, double x, double y, double z) {
        super(w, x, y, z);
    }

    /** How many times faster the barge with a rower is than a vanilla boat. */
    public static final int SPEED = 2;

    /**
     * A vanilla boat covers its speed once per tick (and limits it itself) - a barge with a rower afloat makes
     * another SPEED - 1 equal steps. Computed both on the server and on the rower's client, like the boat itself.
     */
    @Override
    public void onUpdate() {
        super.onUpdate();
        if (isDead || riddenByEntity == null || !isInWater()) return;
        for (int i = 1; i < SPEED; i++) moveEntity(motionX, 0, motionZ);
    }

    /** A vanilla boat drops planks and sticks (against the shore) or a boat (when hit) - the barge drops itself once. */
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
