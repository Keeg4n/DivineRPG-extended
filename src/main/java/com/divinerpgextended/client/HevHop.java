package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;

import com.divinerpgextended.easter.Easter;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Bunny hop in the full H.E.V. suit: a backward jump (back + Shift) sets the speed backward from the look, and while the player is in the air
 * and holds back + Shift, the speed does not fade. A new jump no later than {@link #CHAIN} ticks after landing multiplies it
 * by {@link #GAIN} (up to {@link #MAX}); if late, hit a wall, or released the keys - everything starts over. The direction is from the look,
 * so you steer with the mouse. The client calculates player movement and the server accepts it - so it is only here.
 */
@SideOnly(Side.CLIENT)
public class HevHop {

    /** Speed of the first jump (like sprinting), the gain per jump in the chain and the limit, blocks per tick. */
    public static final double START = 0.30, GAIN = 1.15, MAX = 0.90;
    /** How many ticks on the ground are forgiven between jumps of the chain. */
    public static final int CHAIN = 2;

    private double speed;
    private boolean airborne;
    private int grounded;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP p = mc.thePlayer;
        if (p == null || mc.isGamePaused()) return;
        boolean keys = p.movementInput != null && p.movementInput.moveForward < 0 && p.isSneaking();
        if (!keys || !Easter.fullHev(p) || p.capabilities.isFlying || p.ridingEntity != null || p.isInWater() || p.handleLavaMovement()
            || p.isOnLadder()) {
            reset();
            return;
        }
        if (p.onGround) {
            if (airborne) {
                airborne = false;
                grounded = 0;
            }
            if (++grounded > CHAIN) speed = 0; // hesitated on the ground - the chain broke
            return;
        }
        if (!airborne) {
            if (p.motionY <= 0) return; // fell off an edge instead of jumping
            airborne = true;
            speed = speed > 0 ? Math.min(MAX, speed * GAIN) : START;
        }
        if (p.isCollidedHorizontally) {
            reset();
            return;
        }
        double yaw = Math.toRadians(p.rotationYaw);
        p.motionX = Math.sin(yaw) * speed; // backward from the look
        p.motionZ = -Math.cos(yaw) * speed;
    }

    private void reset() {
        speed = 0;
        airborne = false;
        grounded = 0;
    }

    /** The current chain speed (for selftest and debugging). */
    public double speed() {
        return speed;
    }
}
