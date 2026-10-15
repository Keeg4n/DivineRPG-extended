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
 * Клиенту - какие боссы убиты. Нужно торговцам Divine: их окно строит список сделок на клиенте, и чтобы список по
 * прогрессу совпадал с серверным, клиент должен знать прогресс. Шлём при входе, сразу после засчитанного босса и
 * раз в секунду (на случай рассинхрона).
 */
public class ProgressSync {

    private static int clientMask;

    public static void register() {
        ColdHandler.NET.registerMessage(Handler.class, Mask.class, 5, Side.CLIENT);
    }

    /** Убит ли босс - на сервере из данных игрока, на клиенте из присланной маски. */
    public static boolean has(EntityPlayer p, Boss b) {
        if (p.worldObj.isRemote) return (clientMask & b.bit()) != 0;
        return BossProgress.has(p, b);
    }

    public static void send(EntityPlayer p) {
        if (p instanceof EntityPlayerMP) ColdHandler.NET.sendTo(new Mask(BossProgress.mask(p)), (EntityPlayerMP) p);
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

        int mask;

        public Mask() {}

        Mask(int mask) {
            this.mask = mask;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            mask = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(mask);
        }
    }

    public static class Handler implements IMessageHandler<Mask, IMessage> {

        @Override
        public IMessage onMessage(Mask msg, MessageContext ctx) {
            clientMask = msg.mask;
            return null;
        }
    }
}
