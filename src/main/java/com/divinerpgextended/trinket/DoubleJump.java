package com.divinerpgextended.trinket;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.potion.Potion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Движение в воздухе от аксессуаров. Движение игрока считает клиент, поэтому прыгает и парит он сам; сервер сверяет,
 * что вещь надета и запас не исчерпан, и только тогда снимает наказания (высоту падения, кик за полёт). Что доступно,
 * сервер сообщает клиенту (при изменении и раз в 5 секунд).
 * <ul>
 * <li>Крыло Айрако: прыжок в воздухе, в Сумеречных мирах - два.</li>
 * <li>Ковёр-самолёт (только в Сумеречных мирах): прыжок зажат в воздухе - высота держится, летишь по горизонтали.
 * Запас 3 с, дольше за Великих кадиллионов, без предела после Сумеречного демона; копится только на земле. Падение
 * не гасит.</li>
 * </ul>
 */
public class DoubleJump {

    /** Ковёр без предела по времени. */
    public static final int UNLIMITED = -1;

    private static class State {

        /** Прыжков с последнего касания земли. */
        int used;
        /** Сколько тиков ковра осталось до земли. */
        int carpetLeft;
        /** Что ушло клиенту в последний раз. */
        int sentJumps = -2, sentCarpet = -2;
        double lastY;
    }

    private static final Map<UUID, State> STATE = new HashMap<UUID, State>();
    /** Клиент: прыжков в воздухе и тиков ковра (присылает сервер). */
    static int clientAllowed, clientCarpet;

    public static void register() {
        ColdHandler.NET.registerMessage(SyncHandler.class, Sync.class, 6, Side.CLIENT);
        ColdHandler.NET.registerMessage(JumpHandler.class, Jump.class, 7, Side.SERVER);
    }

    public static boolean twilight(int d) {
        return d == ConfigurationHelper.eden || d == ConfigurationHelper.wildwood || d == ConfigurationHelper.apalachia
            || d == ConfigurationHelper.skythern || d == ConfigurationHelper.mortum;
    }

    public static int allowed(EntityPlayer p) {
        if (!Accessories.wears(p, Trinket.AYERACO_WING)) return 0;
        return twilight(p.dimension) ? 2 : 1;
    }

    /** Запас ковра в тиках: 0 - не работает, {@link #UNLIMITED} - без предела. */
    public static int carpetTicks(EntityPlayer p) {
        if (!twilight(p.dimension) || !Accessories.wears(p, Trinket.MAGIC_CARPET)) return 0;
        if (BossProgress.has(p, Boss.TWILIGHT_DEMON)) return UNLIMITED;
        if (BossProgress.has(p, Boss.CADILLION_SKYTHERN)) return 200;
        if (BossProgress.has(p, Boss.CADILLION_APALACHIA)) return 140;
        if (BossProgress.has(p, Boss.CADILLION_WILDWOOD)) return 100;
        if (BossProgress.has(p, Boss.CADILLION_EDEN)) return 80;
        return 60;
    }

    /** Касается опоры: прыжки и ковёр восстанавливаются. */
    private static boolean grounded(EntityPlayer p) {
        return p.onGround || p.isInWater() || p.handleLavaMovement() || p.isOnLadder() || p.capabilities.isFlying
            || p.ridingEntity != null;
    }

    private static State state(EntityPlayer p) {
        State s = STATE.get(p.getUniqueID());
        if (s == null) STATE.put(p.getUniqueID(), s = new State());
        return s;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        State s = state(p);
        double dy = p.posY - s.lastY;
        s.lastY = p.posY;
        int carpet = carpetTicks(p);
        if (grounded(p)) {
            s.used = 0;
            s.carpetLeft = carpet;
        } else if (carpet != 0 && (carpet == UNLIMITED || s.carpetLeft > 0) && Math.abs(dy) < 0.05) {
            // парит на ковре: сервер не должен принимать это за запрещённый полёт
            if (carpet != UNLIMITED) s.carpetLeft = Math.min(s.carpetLeft, carpet) - 1;
            ReflectionHelper.setPrivateValue(NetHandlerPlayServer.class, p.playerNetServerHandler, 0, "floatingTickCount",
                "field_147365_f");
            if (p.ticksExisted % 4 == 0) {
                ((WorldServer) p.worldObj).func_147487_a("cloud", p.posX, p.boundingBox.minY, p.posZ, 2, 0.3, 0.02, 0.3, 0.01);
            }
        }
        if (p.ticksExisted % 10 != 0) return;
        int a = allowed(p);
        if (a != s.sentJumps || carpet != s.sentCarpet || p.ticksExisted % 100 == 0) {
            s.sentJumps = a;
            s.sentCarpet = carpet;
            ColdHandler.NET.sendTo(new Sync(a, carpet), p);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATE.remove(e.player.getUniqueID());
    }

    // ---------------- Сообщения ----------------

    public static class Sync implements IMessage {

        int allowed, carpet;

        public Sync() {}

        Sync(int allowed, int carpet) {
            this.allowed = allowed;
            this.carpet = carpet;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            allowed = buf.readByte();
            carpet = buf.readShort();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(allowed);
            buf.writeShort(carpet);
        }
    }

    public static class SyncHandler implements IMessageHandler<Sync, IMessage> {

        @Override
        public IMessage onMessage(Sync msg, MessageContext ctx) {
            clientAllowed = msg.allowed;
            clientCarpet = msg.carpet;
            return null;
        }
    }

    public static class Jump implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {}

        @Override
        public void toBytes(ByteBuf buf) {}
    }

    public static class JumpHandler implements IMessageHandler<Jump, IMessage> {

        @Override
        public IMessage onMessage(Jump msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            State s = state(p);
            if (s.used >= allowed(p)) return null; // крыла нет или прыжки кончились - падение считается как обычно
            s.used++;
            p.fallDistance = 0;
            p.worldObj.playSoundAtEntity(p, "mob.enderdragon.wings", 0.35F, 1.6F);
            return null;
        }
    }

    // ---------------- Клиент ----------------

    @SideOnly(Side.CLIENT)
    public static class Client {

        private int used, carpetLeft;
        private boolean wasDown, airborne;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            EntityClientPlayerMP p = mc.thePlayer;
            if (p == null) {
                clientAllowed = clientCarpet = 0;
                return;
            }
            boolean down = mc.gameSettings.keyBindJump.getIsKeyPressed();
            boolean pressed = down && !wasDown;
            wasDown = down;
            airborne = !grounded(p);
            if (!airborne) {
                used = 0;
                carpetLeft = clientCarpet;
                return;
            }
            // в творческом двойной пробел включает полёт - не мешаем ему
            if (mc.currentScreen != null || p.capabilities.allowFlying) return;
            if (pressed && used < clientAllowed) {
                jump(p);
                return;
            }
            // ковёр: прыжок зажат, игрок уже не поднимается - держим высоту
            if (down && p.motionY <= 0 && clientCarpet != 0 && (clientCarpet == UNLIMITED || carpetLeft > 0)) {
                p.motionY = 0;
                if (clientCarpet != UNLIMITED) carpetLeft--;
                if (p.ticksExisted % 2 == 0) {
                    p.worldObj.spawnParticle("reddust", p.posX + (p.getRNG().nextDouble() - 0.5), p.boundingBox.minY,
                        p.posZ + (p.getRNG().nextDouble() - 0.5), 0.9, 0.2, 0.1);
                }
            }
        }

        private void jump(EntityClientPlayerMP p) {
            used++;
            p.motionY = 0.48;
            if (p.isPotionActive(Potion.jump)) p.motionY += (p.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
            p.fallDistance = 0;
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                p.worldObj.spawnParticle("cloud", p.posX + Math.cos(a) * 0.5, p.boundingBox.minY, p.posZ + Math.sin(a) * 0.5,
                    Math.cos(a) * 0.08, -0.05, Math.sin(a) * 0.08);
            }
            ColdHandler.NET.sendToServer(new Jump());
        }

        /** Полоска запаса ковра под прицелом, пока он тратится. */
        @SubscribeEvent
        public void onOverlay(RenderGameOverlayEvent.Post e) {
            if (e.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
            if (!airborne || clientCarpet <= 0 || carpetLeft >= clientCarpet) return;
            ScaledResolution r = e.resolution;
            int x = r.getScaledWidth() / 2 - 20, y = r.getScaledHeight() / 2 + 10;
            Gui.drawRect(x - 1, y - 1, x + 41, y + 3, 0x90000000);
            Gui.drawRect(x, y, x + 40 * Math.max(carpetLeft, 0) / clientCarpet, y + 2, carpetLeft > clientCarpet / 4 ? 0xFFE0B040 : 0xFFE04030);
        }
    }
}
