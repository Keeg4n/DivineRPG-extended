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
 * Client: someone inverted in Dream Water ({@link BlockDreamWater#inverted}). The upside-down picture is in {@link CameraTilt};
 * here the eyes move to the bottom of the hitbox (the head of the inverted one is at the bottom, in the air; CameraTilt lowers the camera there too), strafe is swapped (the screen is mirrored
 * horizontally), and the player himself and other inverted ones are drawn upside down.
 */
@SideOnly(Side.CLIENT)
public class DreamWaterView {

    /** Eye height above the bottom of the hitbox for the inverted one and the normal one. */
    public static final float EYES_INVERTED = 0.18F, EYES = 1.62F;

    /** How much to lower the inverted one's camera from the heels to the head (the game keeps it at eye height from the bottom of the hitbox). */
    public static final double HEAD_DROP = EYES - EYES_INVERTED;

    /** Keyboard input: when flipped, left and right are swapped. */
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

    /** Eyes at the bottom or the top of the hitbox: the hitbox does not move, only the look point moves. */
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
        } else if (!inv && moved) { // restore only if we moved them ourselves (Morph and others change the eyes in their own way)
            eyes(p, EYES);
            moved = false;
        }
    }

    /** The eyes were moved by this class. */
    private static boolean moved;

    private final Set<EntityLivingBase> flipped = new HashSet<EntityLivingBase>();

    /** The real head rotations for the duration of drawing: {yawHead, prevYawHead, pitch, prevPitch}. */
    private final java.util.Map<EntityLivingBase, float[]> head = new java.util.HashMap<EntityLivingBase, float[]>();

    /**
     * An inverted one (a player and any creature) is drawn upside down: a 180 degree rotation around the look axis through
     * the middle of the hitbox. Such a rotation mirrors head turns (turned the head left - the model goes right), so for the duration of
     * drawing the head turn relative to the body and the pitch are taken with the opposite sign: the head looks where the player looks.
     */
    /** Fraction of the tick of the current frame: the body rotation between ticks is smoothed, as in the model itself (otherwise the flip axis moved in jerks). */
    private static float frame;

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) frame = e.renderTickTime;
    }

    /** The creature's flip axis for this frame: {x, middle of the height, z, body rotation in radians}. */
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

    /** The nickname over the head is drawn without the flip: otherwise it hung mirrored and did not turn toward the camera. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onNamePre(RenderLivingEvent.Specials.Pre e) {
        if (flipped.contains(e.entity) && axis.containsKey(e.entity) && unflipped.add(e.entity)) GL11.glPopMatrix();
    }

    /** The nickname is being drawn: the flip is removed and not yet restored. */
    private final Set<EntityLivingBase> unflipped = new HashSet<EntityLivingBase>();

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onNamePost(RenderLivingEvent.Specials.Post e) {
        double[] a = unflipped.remove(e.entity) ? axis.get(e.entity) : null;
        if (a != null) flip(a); // again, so that onPost removes exactly one level
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPost(RenderLivingEvent.Post e) {
        if (!flipped.remove(e.entity)) return;
        axis.remove(e.entity);
        if (!unflipped.remove(e.entity)) GL11.glPopMatrix(); // the nickname was cancelled after the flip was removed - nothing left to remove
        float[] h = head.remove(e.entity);
        if (h == null) return;
        e.entity.rotationYawHead = h[0];
        e.entity.prevRotationYawHead = h[1];
        e.entity.rotationPitch = h[2];
        e.entity.prevRotationPitch = h[3];
    }
}
