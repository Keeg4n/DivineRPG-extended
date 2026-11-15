package com.divinerpgextended.boss;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * To the client - which bosses are killed. Needed by Divine merchants: their window builds the trade list on the client, and for the list by
 * progress to match the server's, the client must know the progress. Sent on login, right after a boss is counted, and
 * once a second (in case of desync).
 */
public class ProgressSync {

    private static int clientMask, clientDream;

    /**
     * The player's Dream gifts (Vethea bosses) - on the client from what was sent; for other players the client does not know and treats the dream as
     * conquered (their nightmare does not interfere with anything on this client).
     */
    public static int dream(EntityPlayer p) {
        return p == com.divinerpgextended.DivineRPGExtended.proxy.clientPlayer() ? clientDream : -1;
    }

    public static void register() {
        ColdHandler.NET.registerMessage(Handler.class, Mask.class, 5, Side.CLIENT);
    }

    /** Whether the boss is killed - on the server from the player's data, on the client from the sent mask. */
    public static boolean has(EntityPlayer p, Boss b) {
        if (p.worldObj.isRemote) return (clientMask & b.bit()) != 0;
        return BossProgress.has(p, b);
    }

    public static void send(EntityPlayer p) {
        if (p instanceof EntityPlayerMP) ColdHandler.NET.sendTo(new Mask(BossProgress.mask(p), com.divinerpgextended.vethea.VetheaFloors.dreamMask(p)), (EntityPlayerMP) p);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        send(e.player);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.worldObj.isRemote && e.player.ticksExisted % 20 == 0) send(e.player);
    }

    public static class Mask implements IMessage {

        int mask, dream;

        public Mask() {}

        Mask(int mask, int dream) {
            this.mask = mask;
            this.dream = dream;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            mask = buf.readInt();
            dream = buf.isReadable(4) ? buf.readInt() : 0;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(mask);
            buf.writeInt(dream);
        }
    }

    public static class Handler implements IMessageHandler<Mask, IMessage> {

        @Override
        public IMessage onMessage(Mask msg, MessageContext ctx) {
            clientMask = msg.mask;
            clientDream = msg.dream;
            return null;
        }
    }
}
