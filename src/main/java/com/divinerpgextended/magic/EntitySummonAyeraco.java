package com.divinerpgextended.magic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Small Ayeraco of the Ayeraco Prism ({@link AnchorSummons}): flies after the owner through blocks like the shark, keeps shifting through
 * the five horde colors (on the client, {@link #hue}), and when its target is farther than {@link #BLINK_FROM} blocks it teleports right
 * next to it, no more than once per {@link #BLINK_EVERY} ticks. The owner's attacker becomes the target, so it blinks to whoever hits the owner.
 */
public class EntitySummonAyeraco extends EntitySummonShark {

    public static final int BLINK_EVERY = 40, HUES = 5, HUE_TICKS = 10;
    public static final double BLINK_FROM = 3;
    private int blink;

    public EntitySummonAyeraco(World w) {
        super(w);
        setSize(0.7F, 0.4F);
    }

    public EntitySummonAyeraco(World w, EntityPlayer owner) {
        super(w, owner);
        setSize(0.7F, 0.4F);
    }

    @Override
    public AnchorSummons.Kind kind() {
        return AnchorSummons.Kind.AYERACO;
    }

    /** Current color (0..4): changes every {@link #HUE_TICKS} ticks, two Ayeraco of one owner are out of step. */
    public static int hue(Entity e, float partial) {
        return (int) ((e.ticksExisted + partial) / HUE_TICKS + e.getEntityId()) % HUES;
    }

    @Override
    protected double orbitRadius() {
        return 2.2;
    }

    @Override
    protected double orbitHeight() {
        return 2.0;
    }

    @Override
    protected void chase(Entity t, EntityPlayer o) {
        if (--blink > 0 || getDistanceSqToEntity(t) <= BLINK_FROM * BLINK_FROM) return;
        blink = BLINK_EVERY;
        double dx = posX - t.posX, dz = posZ - t.posZ, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        double r = t.width / 2 + 1.0;
        sparks();
        setPosition(t.posX + dx / len * r, t.posY + t.height * 0.5, t.posZ + dz / len * r);
        motionX = motionY = motionZ = 0;
        sparks();
        worldObj.playSoundAtEntity(this, "mob.endermen.portal", 0.5F, 1.5F);
    }

    private void sparks() {
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("portal", posX, posY + height / 2, posZ, 16, 0.3, 0.3, 0.3, 0.3);
    }

    @Override
    protected void onHit(EntityLivingBase t, EntityPlayer o) {
        worldObj.playSoundAtEntity(this, "mob.endermen.hit", 0.4F, 1.4F);
    }
}
