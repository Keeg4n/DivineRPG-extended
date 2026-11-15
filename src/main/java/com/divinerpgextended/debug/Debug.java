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
 * Debugging for the modpack: /dredebug. For the player who turned it on, details of damage, healing, effects and addon mechanics go to
 * chat, and everything (plus actions, keys and state snapshots) goes to a CSV in logs/dre-debug.
 * <p>
 * Addon mechanics report themselves through {@link #note}: this is cheap while nobody has turned debugging on.
 */
public final class Debug {

    private Debug() {}

    public static class Flags {

        public boolean chat = true, file = true, mobs;
    }

    static final Map<UUID, Flags> ON = new ConcurrentHashMap<UUID, Flags>();
    /** Client: the server turned debugging on for this player - record keys and windows. */
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

    /** An addon mechanic fired: it attaches to the current hit or goes to the debugger as a separate line. */
    /** A note about a mob (boss) - into the log for players nearby. */
    public static void noteNear(Entity mob, double r, String text) {
        if (ON.isEmpty() || mob == null || mob.worldObj.isRemote) return;
        for (Object o : mob.worldObj.getEntitiesWithinAABB(net.minecraft.entity.player.EntityPlayer.class, mob.boundingBox.expand(r, r, r)))
            DebugEvents.note((Entity) o, text);
    }

    public static void note(Entity who, String text) {
        if (ON.isEmpty() || who == null || who.worldObj.isRemote) return;
        DebugEvents.note(who, text);
    }

    /** A debug line in chat: the outgoing chat interception does not write it to the file a second time. */
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

    // ---------------- To the client: whether to record keys ----------------

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
