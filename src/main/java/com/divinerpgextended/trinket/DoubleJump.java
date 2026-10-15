package com.divinerpgextended.trinket;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;

import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Крыло Айрако: прыжок в воздухе, в Сумеречных мирах - два. Движение игрока считает клиент, поэтому прыгает он сам;
 * сервер сверяет, что крыло надето и прыжки не исчерпаны, и только тогда обнуляет высоту падения. Сколько прыжков
 * доступно, сервер сообщает клиенту (при изменении и раз в 5 секунд).
 */
public class DoubleJump {

    /** Сервер: [прыжков сделано с последнего касания земли, сколько доступно по последней отправке]. */
    private static final Map<UUID, int[]> STATE = new HashMap<UUID, int[]>();
    /** Клиент: сколько прыжков в воздухе доступно (присылает сервер). */
    static int clientAllowed;

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

    /** Касается опоры: прыжки восстанавливаются. */
    private static boolean grounded(EntityPlayer p) {
        return p.onGround || p.isInWater() || p.handleLavaMovement() || p.isOnLadder() || p.capabilities.isFlying
            || p.ridingEntity != null;
    }

    private static int[] state(EntityPlayer p) {
        int[] s = STATE.get(p.getUniqueID());
        if (s == null) STATE.put(p.getUniqueID(), s = new int[] { 0, -1 });
        return s;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayer p = e.player;
        int[] s = state(p);
        if (grounded(p)) s[0] = 0;
        if (p.ticksExisted % 10 != 0) return;
        int a = allowed(p);
        if (a != s[1] || p.ticksExisted % 100 == 0) {
            s[1] = a;
            ColdHandler.NET.sendTo(new Sync(a), (EntityPlayerMP) p);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATE.remove(e.player.getUniqueID());
    }

    // ---------------- Сообщения ----------------

    public static class Sync implements IMessage {

        int allowed;

        public Sync() {}

        Sync(int allowed) {
            this.allowed = allowed;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            allowed = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(allowed);
        }
    }

    public static class SyncHandler implements IMessageHandler<Sync, IMessage> {

        @Override
        public IMessage onMessage(Sync msg, MessageContext ctx) {
            clientAllowed = msg.allowed;
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
            int[] s = state(p);
            if (s[0] >= allowed(p)) return null; // крыла нет или прыжки кончились - падение считается как обычно
            s[0]++;
            p.fallDistance = 0;
            p.worldObj.playSoundAtEntity(p, "mob.enderdragon.wings", 0.35F, 1.6F);
            return null;
        }
    }

    // ---------------- Клиент ----------------

    @SideOnly(Side.CLIENT)
    public static class Client {

        private int used;
        private boolean wasDown;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            EntityClientPlayerMP p = mc.thePlayer;
            if (p == null) {
                clientAllowed = 0;
                return;
            }
            boolean down = mc.gameSettings.keyBindJump.getIsKeyPressed();
            boolean pressed = down && !wasDown;
            wasDown = down;
            if (grounded(p)) {
                used = 0;
                return;
            }
            // в творческом двойной пробел включает полёт - не мешаем ему
            if (!pressed || mc.currentScreen != null || p.capabilities.allowFlying || used >= clientAllowed) return;
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
    }
}
