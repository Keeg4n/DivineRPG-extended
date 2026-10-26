package com.divinerpgextended.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.block.BlockDreamWater;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Клиент: перевёрнутый в воде мечтаний ({@link BlockDreamWater#inverted}). Экран и управление не переворачиваются: глаза
 * переезжают вниз хитбокса (голова перевёрнутого внизу, в воздухе; камеру туда же опускает {@link CameraTilt}), а сам
 * игрок и другие перевёрнутые рисуются вверх ногами.
 */
@SideOnly(Side.CLIENT)
public class DreamWaterView {

    /** Высота глаз над низом хитбокса у перевёрнутого и обычная. */
    public static final float EYES_INVERTED = 0.18F, EYES = 1.62F;

    /** На сколько камеру перевёрнутого опускать от пяток к голове. */
    public static final double HEAD_DROP = EYES - EYES_INVERTED;

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

    /** Перевёрнутый игрок рисуется вверх ногами: поворот на 180 градусов вокруг оси взгляда через середину хитбокса. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPre(RenderLivingEvent.Pre e) {
        if (!(e.entity instanceof EntityPlayer) || !BlockDreamWater.inverted(e.entity)) return;
        double yaw = Math.toRadians(e.entity.renderYawOffset), cy = e.y + e.entity.height / 2;
        GL11.glPushMatrix();
        GL11.glTranslated(e.x, cy, e.z);
        GL11.glRotated(180, -Math.sin(yaw), 0, Math.cos(yaw));
        GL11.glTranslated(-e.x, -cy, -e.z);
        flipped.add(e.entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPost(RenderLivingEvent.Post e) {
        if (flipped.remove(e.entity)) GL11.glPopMatrix();
    }
}
