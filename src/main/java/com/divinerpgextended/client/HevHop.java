package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;

import com.divinerpgextended.easter.Easter;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Банихоп в полном костюме H.E.V.: прыжок спиной (назад + Shift) задаёт скорость назад от взгляда, и пока игрок в воздухе
 * и держит назад + Shift, скорость не гаснет. Новый прыжок не позже {@link #CHAIN} тиков после приземления умножает её
 * на {@link #GAIN} (до {@link #MAX}); опоздал, упёрся в стену, отпустил клавиши - всё с начала. Направление - от взгляда,
 * поэтому рулят мышью. Движение игрока считает клиент, сервер его принимает - поэтому только здесь.
 */
@SideOnly(Side.CLIENT)
public class HevHop {

    /** Скорость первого прыжка (как бег), прибавка за прыжок в цепочке и предел, блоков за тик. */
    public static final double START = 0.30, GAIN = 1.15, MAX = 0.90;
    /** Сколько тиков на земле прощается между прыжками цепочки. */
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
            if (++grounded > CHAIN) speed = 0; // замешкался на земле - цепочка оборвалась
            return;
        }
        if (!airborne) {
            if (p.motionY <= 0) return; // упал с края, а не прыгнул
            airborne = true;
            speed = speed > 0 ? Math.min(MAX, speed * GAIN) : START;
        }
        if (p.isCollidedHorizontally) {
            reset();
            return;
        }
        double yaw = Math.toRadians(p.rotationYaw);
        p.motionX = Math.sin(yaw) * speed; // назад от взгляда
        p.motionZ = -Math.cos(yaw) * speed;
    }

    private void reset() {
        speed = 0;
        airborne = false;
        grounded = 0;
    }

    /** Текущая скорость цепочки (для самопроверки и отладки). */
    public double speed() {
        return speed;
    }
}
