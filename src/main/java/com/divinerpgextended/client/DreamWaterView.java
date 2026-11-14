package com.divinerpgextended.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.RenderLivingEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.block.BlockDreamWater;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Клиент: перевёрнутый в воде мечтаний ({@link BlockDreamWater#inverted}). Картинка вверх ногами - в {@link CameraTilt};
 * здесь глаза переезжают вниз хитбокса (голова перевёрнутого внизу, в воздухе; камеру туда же опускает CameraTilt), стрейф меняется местами (экран зеркален
 * по горизонтали), а сам игрок и другие перевёрнутые рисуются вверх ногами.
 */
@SideOnly(Side.CLIENT)
public class DreamWaterView {

    /** Высота глаз над низом хитбокса у перевёрнутого и обычная. */
    public static final float EYES_INVERTED = 0.18F, EYES = 1.62F;

    /** На сколько камеру перевёрнутого опускать от пяток к голове (игра держит её на высоте глаз от низа хитбокса). */
    public static final double HEAD_DROP = EYES - EYES_INVERTED;

    /** Ввод с клавиатуры: при перевороте влево и вправо меняются. */
    static class FlipInput extends MovementInput {

        final MovementInput base;
        final EntityPlayer p;

        FlipInput(MovementInput base, EntityPlayer p) {
            this.base = base;
            this.p = p;
        }

        @Override
        public void updatePlayerMoveState() {
            base.updatePlayerMoveState();
            moveForward = base.moveForward;
            moveStrafe = BlockDreamWater.inverted(p) ? -base.moveStrafe : base.moveStrafe;
            jump = base.jump;
            sneak = base.sneak;
        }
    }

    /** Глаза у низа или у верха хитбокса: хитбокс не двигается, двигается только точка взгляда. */
    static void eyes(EntityPlayer p, float h) {
        float d = h - p.yOffset;
        p.yOffset = h;
        p.posY += d;
        p.prevPosY += d;
        p.lastTickPosY += d;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.START || !p.worldObj.isRemote || p != Minecraft.getMinecraft().thePlayer) return;
        if (p instanceof net.minecraft.client.entity.EntityPlayerSP) {
            net.minecraft.client.entity.EntityPlayerSP sp = (net.minecraft.client.entity.EntityPlayerSP) p;
            if (sp.movementInput != null && !(sp.movementInput instanceof FlipInput)) sp.movementInput = new FlipInput(sp.movementInput, p);
        }
        boolean inv = BlockDreamWater.inverted(p);
        if (inv && !moved && Math.abs(p.yOffset - EYES) < 0.01F) {
            eyes(p, EYES_INVERTED);
            moved = true;
        } else if (!inv && moved) { // возвращаем, только если двигали сами (Morph и др. меняют глаза по-своему)
            eyes(p, EYES);
            moved = false;
        }
    }

    /** Глаза перенесены этим классом. */
    private static boolean moved;

    private final Set<EntityLivingBase> flipped = new HashSet<EntityLivingBase>();

    /** Настоящие повороты головы на время рисования: {yawHead, prevYawHead, pitch, prevPitch}. */
    private final java.util.Map<EntityLivingBase, float[]> head = new java.util.HashMap<EntityLivingBase, float[]>();

    /**
     * Перевёрнутый (игрок и любое существо) рисуется вверх ногами: поворот на 180 градусов вокруг оси взгляда через
     * середину хитбокса. Такой поворот зеркалит повороты головы (повернул голову влево - модель вправо), поэтому на время
     * рисования поворот головы относительно тела и наклон берутся с обратным знаком: голова смотрит туда же, куда игрок.
     */
    /** Доля тика текущего кадра: поворот тела между тиками сглаживается, как у самой модели (иначе ось переворота шла рывками). */
    private static float frame;

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) frame = e.renderTickTime;
    }

    /** Ось переворота существа на этот кадр: {x, середина по высоте, z, поворот тела в радианах}. */
    private final java.util.Map<EntityLivingBase, double[]> axis = new java.util.HashMap<EntityLivingBase, double[]>();

    private static void flip(double[] a) {
        GL11.glPushMatrix();
        GL11.glTranslated(a[0], a[1], a[2]);
        GL11.glRotated(180, -Math.sin(a[3]), 0, Math.cos(a[3]));
        GL11.glTranslated(-a[0], -a[1], -a[2]);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPre(RenderLivingEvent.Pre e) {
        EntityLivingBase m = e.entity;
        if (!BlockDreamWater.inverted(m)) return;
        double[] a = { e.x, e.y + m.height / 2, e.z, Math.toRadians(m.prevRenderYawOffset + (m.renderYawOffset - m.prevRenderYawOffset) * frame) };
        flip(a);
        axis.put(m, a);
        flipped.add(m);
        head.put(m, new float[] { m.rotationYawHead, m.prevRotationYawHead, m.rotationPitch, m.prevRotationPitch });
        m.rotationYawHead = 2 * m.renderYawOffset - m.rotationYawHead;
        m.prevRotationYawHead = 2 * m.prevRenderYawOffset - m.prevRotationYawHead;
        m.rotationPitch = -m.rotationPitch;
        m.prevRotationPitch = -m.prevRotationPitch;
    }

    /** Ник над головой рисуется без переворота: иначе он висел зеркально и не поворачивался к камере. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onNamePre(RenderLivingEvent.Specials.Pre e) {
        if (flipped.contains(e.entity) && axis.containsKey(e.entity) && unflipped.add(e.entity)) GL11.glPopMatrix();
    }

    /** Ник рисуется: переворот снят и ещё не возвращён. */
    private final Set<EntityLivingBase> unflipped = new HashSet<EntityLivingBase>();

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onNamePost(RenderLivingEvent.Specials.Post e) {
        double[] a = unflipped.remove(e.entity) ? axis.get(e.entity) : null;
        if (a != null) flip(a); // снова, чтобы onPost снял ровно один уровень
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPost(RenderLivingEvent.Post e) {
        if (!flipped.remove(e.entity)) return;
        axis.remove(e.entity);
        if (!unflipped.remove(e.entity)) GL11.glPopMatrix(); // ник отменили после снятия переворота - снимать уже нечего
        float[] h = head.remove(e.entity);
        if (h == null) return;
        e.entity.rotationYawHead = h[0];
        e.entity.prevRotationYawHead = h[1];
        e.entity.rotationPitch = h[2];
        e.entity.prevRotationPitch = h[3];
    }
}
