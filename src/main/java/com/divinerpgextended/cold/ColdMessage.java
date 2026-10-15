package com.divinerpgextended.cold;

import io.netty.buffer.ByteBuf;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** Сервер -> клиент: холод игрока (0-100), сколько секунд ещё "Согрет", фаза вьюги и направление ветра. */
public class ColdMessage implements IMessage {

    public float cold;
    public int warm, phase;
    public float windX, windZ;

    public ColdMessage() {}

    public ColdMessage(float cold, int warm, int phase, float windX, float windZ) {
        this.cold = cold;
        this.warm = warm;
        this.phase = phase;
        this.windX = windX;
        this.windZ = windZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        cold = buf.readFloat();
        warm = buf.readShort();
        phase = buf.readByte();
        windX = buf.readFloat();
        windZ = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(cold);
        buf.writeShort(warm);
        buf.writeByte(phase);
        buf.writeFloat(windX);
        buf.writeFloat(windZ);
    }

    /** Клиентская копия холода для HUD. */
    public static float clientCold;
    public static int clientWarm, clientPhase;
    public static float clientWindX, clientWindZ;

    public static class Handler implements IMessageHandler<ColdMessage, IMessage> {

        @Override
        public IMessage onMessage(ColdMessage msg, MessageContext ctx) {
            clientCold = msg.cold;
            clientWarm = msg.warm;
            clientPhase = msg.phase;
            clientWindX = msg.windX;
            clientWindZ = msg.windZ;
            return null;
        }
    }
}
