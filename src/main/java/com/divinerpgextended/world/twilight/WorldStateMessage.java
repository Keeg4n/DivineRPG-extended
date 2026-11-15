package com.divinerpgextended.world.twilight;

import io.netty.buffer.ByteBuf;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/** Server -> client: player overheating (0-100) and the weather phase of their world. */
public class WorldStateMessage implements IMessage {

    public float heat;
    public int phase;

    public WorldStateMessage() {}

    public WorldStateMessage(float heat, int phase) {
        this.heat = heat;
        this.phase = phase;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        heat = buf.readFloat();
        phase = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(heat);
        buf.writeByte(phase);
    }

    /** Client copy for the screen and the rain. */
    public static float clientHeat;
    public static int clientPhase;

    public static class Handler implements IMessageHandler<WorldStateMessage, IMessage> {

        @Override
        public IMessage onMessage(WorldStateMessage msg, MessageContext ctx) {
            clientHeat = msg.heat;
            clientPhase = msg.phase;
            return null;
        }
    }
}
