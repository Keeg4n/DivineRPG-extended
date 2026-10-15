package com.divinerpgextended.arcana;

import io.netty.buffer.ByteBuf;

import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/** Сеть Кристалла памяти: сервер открывает окно с портретами, клиент просит позвать торговца. */
public final class MerchantMessages {

    private MerchantMessages() {}

    public static void register() {
        ColdHandler.NET.registerMessage(OpenHandler.class, Open.class, 1, Side.CLIENT);
        ColdHandler.NET.registerMessage(SummonHandler.class, Summon.class, 2, Side.SERVER);
        ColdHandler.NET.registerMessage(ArcanaMaxHandler.class, ArcanaMax.class, 3, Side.CLIENT);
    }

    /** Сервер -> клиент: максимум арканы игрока (задания Ваттикуса поднимают его выше 200). */
    public static class ArcanaMax implements IMessage {

        public float max;

        public ArcanaMax() {}

        public ArcanaMax(float max) {
            this.max = max;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            max = buf.readFloat();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeFloat(max);
        }
    }

    public static class ArcanaMaxHandler implements IMessageHandler<ArcanaMax, IMessage> {

        @Override
        public IMessage onMessage(ArcanaMax msg, MessageContext ctx) {
            com.divinerpgextended.asm.ArcanaHooks.clientMax = msg.max;
            return null;
        }
    }

    /** Сервер -> клиент: кого игрок встретил и кто уже доступен по прогрессу. */
    public static class Open implements IMessage {

        public int met, unlocked;

        public Open() {}

        public Open(int met, int unlocked) {
            this.met = met;
            this.unlocked = unlocked;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            met = buf.readInt();
            unlocked = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(met);
            buf.writeInt(unlocked);
        }
    }

    public static class OpenHandler implements IMessageHandler<Open, IMessage> {

        @Override
        public IMessage onMessage(Open msg, MessageContext ctx) {
            com.divinerpgextended.client.GuiMemoryCrystal.open(msg.met, msg.unlocked);
            return null;
        }
    }

    /** Клиент -> сервер: позвать торговца с этим номером. */
    public static class Summon implements IMessage {

        public int index;

        public Summon() {}

        public Summon(int index) {
            this.index = index;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            index = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(index);
        }
    }

    public static class SummonHandler implements IMessageHandler<Summon, IMessage> {

        @Override
        public IMessage onMessage(Summon msg, MessageContext ctx) {
            ArcanaMerchants.summon(ctx.getServerHandler().playerEntity, msg.index);
            return null;
        }
    }
}
