package com.divinerpgextended.debug;

import io.netty.buffer.ByteBuf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * Отладка для сборки: /dredebug. У включившего игрока подробности урона, лечения, эффектов и механик аддона идут в
 * чат, а всё (плюс действия, клавиши и снимки состояния) - в CSV в logs/dre-debug.
 * <p>
 * Механики аддона сообщают о себе через {@link #note}: это дёшево, пока отладку никто не включил.
 */
public final class Debug {

    private Debug() {}

    public static class Flags {

        public boolean chat = true, file = true, mobs;
    }

    static final Map<UUID, Flags> ON = new ConcurrentHashMap<UUID, Flags>();
    /** Клиент: сервер включил отладку этому игроку - писать клавиши и окна. */
    static volatile boolean clientOn;
    private static DebugLog log;

    public static void register() {
        ColdHandler.NET.registerMessage(StateHandler.class, State.class, 8, Side.CLIENT);
    }

    public static boolean any() {
        return !ON.isEmpty();
    }

    static Flags flags(Entity e) {
        return e instanceof EntityPlayerMP ? ON.get(e.getUniqueID()) : null;
    }

    static DebugLog log() {
        if (log == null) log = new DebugLog(MinecraftServer.getServer().getFile("."), "server");
        return log;
    }

    static void closeLog() {
        if (log != null) log.close();
        log = null;
    }

    /** Сработала механика аддона: прицепится к текущему удару или уйдёт отладчику отдельной строкой. */
    public static void note(Entity who, String text) {
        if (ON.isEmpty() || who == null || who.worldObj.isRemote) return;
        DebugEvents.note(who, text);
    }

    /** Строка отладки в чате: перехват исходящего чата её не пишет в файл второй раз. */
    public static class DebugText extends ChatComponentText {

        public DebugText(String text) {
            super(text);
        }
    }

    static void chat(EntityPlayer p, EnumChatFormatting color, String text) {
        IChatComponent c = new DebugText(text);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    static void file(Entity who, String category, String event, String details) {
        log().write(who, category, event, details);
    }

    // ---------------- Клиенту: писать ли клавиши ----------------

    public static class State implements IMessage {

        boolean on;

        public State() {}

        public State(boolean on) {
            this.on = on;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
        }
    }

    public static class StateHandler implements IMessageHandler<State, IMessage> {

        @Override
        public IMessage onMessage(State msg, MessageContext ctx) {
            clientOn = msg.on;
            return null;
        }
    }

    static void sendState(EntityPlayerMP p, boolean on) {
        ColdHandler.NET.sendTo(new State(on), p);
    }
}
